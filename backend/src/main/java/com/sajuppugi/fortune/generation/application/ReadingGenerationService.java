package com.sajuppugi.fortune.generation.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.sajuppugi.fortune.generation.application.LinerResponseValidator.InvalidGenerationException;
import com.sajuppugi.fortune.generation.domain.GenerationModels.GeneratedReading;
import com.sajuppugi.fortune.generation.domain.GenerationModels.GenerationCommand;
import com.sajuppugi.fortune.generation.domain.GenerationModels.GenerationMode;
import com.sajuppugi.fortune.generation.domain.GenerationModels.LinerRequest;
import com.sajuppugi.fortune.generation.domain.GenerationModels.LinerResponse;
import com.sajuppugi.fortune.generation.port.GenerationSnapshotStore;
import com.sajuppugi.fortune.generation.port.LinerProvider;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ReadingGenerationService {
    private static final Logger LOG = LoggerFactory.getLogger(ReadingGenerationService.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration GENERATION_LEASE = Duration.ofMinutes(2);

    private final LinerProvider provider;
    private final GenerationSnapshotStore snapshots;
    private final LinerFactProjector projector;
    private final GenerationKeyFactory keys;
    private final LinerResponseValidator validator;
    private final DeterministicReadingFallback fallback;
    private final Executor linerGenerationExecutor;
    private final ConcurrentHashMap<String, Object> localLocks = new ConcurrentHashMap<>();

    public ReadingGenerationService(LinerProvider provider, GenerationSnapshotStore snapshots,
                                    LinerFactProjector projector, GenerationKeyFactory keys,
                                    LinerResponseValidator validator, DeterministicReadingFallback fallback,
                                    @Qualifier("linerGenerationExecutor") Executor linerGenerationExecutor) {
        this.provider = provider;
        this.snapshots = snapshots;
        this.projector = projector;
        this.keys = keys;
        this.validator = validator;
        this.fallback = fallback;
        this.linerGenerationExecutor = linerGenerationExecutor;
    }

    public GeneratedReading generate(GenerationCommand command) {
        Objects.requireNonNull(command.requesterUserId(), "requesterUserId");
        JsonNode safeFacts = projector.project(command.calculationFacts(), command.counterpartCalculationFacts());
        String generationKey = keys.create(command, safeFacts);
        GeneratedReading cached = snapshots.findSucceeded(generationKey).orElse(null);
        if (cached != null) return reused(cached);

        Object lock = localLocks.computeIfAbsent(generationKey, ignored -> new Object());
        try {
            synchronized (lock) {
                cached = snapshots.findSucceeded(generationKey).orElse(null);
                if (cached != null) return reused(cached);
                LinerRequest request = new LinerRequest(generationKey,
                        command.calculationFacts().meta().calculationVersion(), command.generationVersion(),
                        command.fortuneType(), command.referenceDate(), command.interestKey(), command.relationKey(), safeFacts,
                        command.missingFields(), command.allowedSections());
                if (!snapshots.tryClaim(request, command.contentVersion(), GENERATION_LEASE)) {
                    cached = snapshots.findSucceeded(generationKey).orElse(null);
                    if (cached != null) return reused(cached);
                    throw new GenerationInProgressException(generationKey);
                }
                return invokeWithRetry(request, command.contentVersion(), snapshots.nextAttemptNumber(generationKey));
            }
        } finally {
            localLocks.remove(generationKey, lock);
        }
    }

    private GeneratedReading invokeWithRetry(LinerRequest request, String contentVersion, int firstAttempt) {
        InvalidGenerationException lastValidation = null;
        RuntimeException lastProviderFailure = null;
        LinerRequest attemptRequest = request;
        java.util.List<String> validationFeedback = new java.util.ArrayList<>();
        for (int index = 0; index < MAX_ATTEMPTS; index++) {
            int attempt = firstAttempt + index;
            String inputHash = keys.hashInput(attemptRequest);
            long started = System.nanoTime();
            LinerResponse response;
            try {
                response = generateProviderResponse(attemptRequest);
                validator.validate(attemptRequest, response);
            } catch (InvalidGenerationException exception) {
                lastValidation = exception;
                LOG.warn("Liner output validation failed: provider={}, attempt={}, code={}",
                        provider.name(), attempt, exception.code());
                snapshots.recordAttempt(request.generationKey(), attempt, provider.name(), inputHash,
                        "VALIDATION_FAILED", elapsedMillis(started), exception.code());
                if (!validationFeedback.contains(exception.code())) validationFeedback.add(exception.code());
                attemptRequest = request.withValidationFeedback(validationFeedback);
                continue;
            } catch (RuntimeException exception) {
                lastProviderFailure = exception;
                snapshots.recordAttempt(request.generationKey(), attempt, provider.name(), inputHash,
                        "PROVIDER_FAILED", elapsedMillis(started), "LINER_PROVIDER_FAILED");
                continue;
            }
            snapshots.recordAttempt(request.generationKey(), attempt, provider.name(), inputHash,
                    "SUCCEEDED", elapsedMillis(started), null);
            return snapshots.saveSucceeded(attemptRequest, response, contentVersion, GenerationMode.LINER);
        }
        long started = System.nanoTime();
        String inputHash = keys.hashInput(attemptRequest);
        LinerResponse response;
        try {
            response = fallback.generate(attemptRequest);
            validator.validate(attemptRequest, response);
        } catch (RuntimeException fallbackFailure) {
            snapshots.recordAttempt(request.generationKey(), firstAttempt + MAX_ATTEMPTS, fallback.name(), inputHash,
                    "FALLBACK_FAILED", elapsedMillis(started), "FALLBACK_GENERATION_FAILED");
            String code = "FALLBACK_GENERATION_FAILED";
            snapshots.markFailed(request.generationKey(), code);
            GenerationFailedException failure = new GenerationFailedException(code, fallbackFailure);
            if (lastValidation != null) failure.addSuppressed(lastValidation);
            if (lastProviderFailure != null) failure.addSuppressed(lastProviderFailure);
            throw failure;
        }
        snapshots.recordAttempt(request.generationKey(), firstAttempt + MAX_ATTEMPTS, fallback.name(), inputHash,
                "FALLBACK_SUCCEEDED", elapsedMillis(started), null);
        return snapshots.saveSucceeded(attemptRequest, response, contentVersion, GenerationMode.FALLBACK);
    }

    private LinerResponse generateProviderResponse(LinerRequest request) {
        if (request.allowedSections().size() != 10) return provider.generate(request);

        LinerRequest first = withSections(request, request.allowedSections().subList(0, 5));
        LinerRequest second = withSections(request, request.allowedSections().subList(5, 10));
        CompletableFuture<LinerResponse> firstFuture = CompletableFuture.supplyAsync(
                () -> validatedBatch(first), linerGenerationExecutor);
        CompletableFuture<LinerResponse> secondFuture = CompletableFuture.supplyAsync(
                () -> validatedBatch(second), linerGenerationExecutor);
        try {
            CompletableFuture.allOf(firstFuture, secondFuture).join();
            LinerResponse firstResponse = firstFuture.join();
            LinerResponse secondResponse = secondFuture.join();
            var sectionsByKey = Stream.concat(firstResponse.sections().stream(), secondResponse.sections().stream())
                    .collect(java.util.stream.Collectors.toMap(section -> section.key(), section -> section,
                            (left, right) -> left, java.util.LinkedHashMap::new));
            var sections = request.allowedSections().stream().filter(sectionsByKey::containsKey)
                    .map(sectionsByKey::get).toList();
            var omitted = request.allowedSections().stream().filter(key -> !sectionsByKey.containsKey(key)).toList();
            var guide = request.fortuneType() == com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType.SUNEUNG
                    ? SuneungGuideFactory.fromSections(sections) : null;
            return new LinerResponse(sections, omitted, guide);
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException cause) throw cause;
            throw exception;
        }
    }

    private LinerResponse validatedBatch(LinerRequest request) {
        LinerResponse response = provider.generate(request);
        validator.validate(request, response);
        return response;
    }

    private LinerRequest withSections(LinerRequest request,
                                      java.util.List<com.sajuppugi.fortune.generation.domain.GenerationModels.SectionKey> sections) {
        return new LinerRequest(request.generationKey(), request.calculationVersion(), request.generationVersion(),
                request.fortuneType(), request.referenceDate(), request.interestKey(), request.relationKey(),
                request.facts(), request.missingFields(), sections, request.validationFeedback());
    }

    private long elapsedMillis(long started) {
        return Duration.ofNanos(System.nanoTime() - started).toMillis();
    }

    private GeneratedReading reused(GeneratedReading cached) {
        return new GeneratedReading(cached.resultId(), cached.generationKey(), cached.fortuneType(), cached.response(),
                cached.calculationVersion(), cached.generationVersion(), cached.contentVersion(),
                cached.generationMode(), true);
    }

    public static class GenerationInProgressException extends RuntimeException {
        public GenerationInProgressException(String key) { super("Generation already in progress: " + key); }
    }

    public static class GenerationFailedException extends RuntimeException {
        private final String code;
        public GenerationFailedException(String code, Throwable cause) { super(code, cause); this.code = code; }
        public String code() { return code; }
    }
}
