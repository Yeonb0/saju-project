package com.sajuppugi.fortune.generation.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.sajuppugi.fortune.generation.application.LinerResponseValidator.InvalidGenerationException;
import com.sajuppugi.fortune.generation.domain.GenerationModels.GeneratedReading;
import com.sajuppugi.fortune.generation.domain.GenerationModels.GenerationCommand;
import com.sajuppugi.fortune.generation.domain.GenerationModels.LinerRequest;
import com.sajuppugi.fortune.generation.domain.GenerationModels.LinerResponse;
import com.sajuppugi.fortune.generation.port.GenerationSnapshotStore;
import com.sajuppugi.fortune.generation.port.LinerProvider;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class ReadingGenerationService {
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration GENERATION_LEASE = Duration.ofMinutes(2);

    private final LinerProvider provider;
    private final GenerationSnapshotStore snapshots;
    private final LinerFactProjector projector;
    private final GenerationKeyFactory keys;
    private final LinerResponseValidator validator;
    private final ConcurrentHashMap<String, Object> localLocks = new ConcurrentHashMap<>();

    public ReadingGenerationService(LinerProvider provider, GenerationSnapshotStore snapshots,
                                    LinerFactProjector projector, GenerationKeyFactory keys,
                                    LinerResponseValidator validator) {
        this.provider = provider;
        this.snapshots = snapshots;
        this.projector = projector;
        this.keys = keys;
        this.validator = validator;
    }

    public GeneratedReading generate(GenerationCommand command) {
        Objects.requireNonNull(command.requesterUserId(), "requesterUserId");
        JsonNode safeFacts = projector.project(command.calculationFacts());
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
                        command.fortuneType(), command.referenceDate(), command.interestKey(), safeFacts,
                        command.missingFields(), command.allowedSections());
                if (!snapshots.tryClaim(request, command.contentVersion(), GENERATION_LEASE)) {
                    cached = snapshots.findSucceeded(generationKey).orElse(null);
                    if (cached != null) return reused(cached);
                    throw new GenerationInProgressException(generationKey);
                }
                return invokeWithRetry(request, command.contentVersion());
            }
        } finally {
            localLocks.remove(generationKey, lock);
        }
    }

    private GeneratedReading invokeWithRetry(LinerRequest request, String contentVersion) {
        String inputHash = keys.hashInput(request);
        InvalidGenerationException lastValidation = null;
        RuntimeException lastProviderFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            long started = System.nanoTime();
            try {
                LinerResponse response = provider.generate(request);
                validator.validate(request, response);
                snapshots.recordAttempt(request.generationKey(), attempt, provider.name(), inputHash,
                        "SUCCEEDED", elapsedMillis(started), null);
                return snapshots.saveSucceeded(request, response, contentVersion);
            } catch (InvalidGenerationException exception) {
                lastValidation = exception;
                snapshots.recordAttempt(request.generationKey(), attempt, provider.name(), inputHash,
                        "VALIDATION_FAILED", elapsedMillis(started), exception.code());
            } catch (RuntimeException exception) {
                lastProviderFailure = exception;
                snapshots.recordAttempt(request.generationKey(), attempt, provider.name(), inputHash,
                        "PROVIDER_FAILED", elapsedMillis(started), "LINER_PROVIDER_FAILED");
            }
        }
        String code = lastValidation != null ? lastValidation.code() : "LINER_PROVIDER_FAILED";
        snapshots.markFailed(request.generationKey(), code);
        if (lastValidation != null) throw lastValidation;
        throw new GenerationFailedException(code, lastProviderFailure);
    }

    private long elapsedMillis(long started) {
        return Duration.ofNanos(System.nanoTime() - started).toMillis();
    }

    private GeneratedReading reused(GeneratedReading cached) {
        return new GeneratedReading(cached.resultId(), cached.generationKey(), cached.fortuneType(), cached.response(),
                cached.calculationVersion(), cached.generationVersion(), cached.contentVersion(), true);
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
