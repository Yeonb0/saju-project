package com.sajuppugi.fortune.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajuppugi.fortune.calculation.domain.BirthInput;
import com.sajuppugi.fortune.calculation.domain.CalculationPolicy;
import com.sajuppugi.fortune.calculation.infrastructure.DeterministicSajuEngine;
import com.sajuppugi.fortune.generation.application.GenerationKeyFactory;
import com.sajuppugi.fortune.generation.application.DeterministicReadingFallback;
import com.sajuppugi.fortune.generation.application.LinerFactProjector;
import com.sajuppugi.fortune.generation.application.LinerResponseValidator;
import com.sajuppugi.fortune.generation.application.ReadingGenerationService;
import com.sajuppugi.fortune.generation.domain.GenerationModels.*;
import com.sajuppugi.fortune.generation.port.GenerationSnapshotStore;
import com.sajuppugi.fortune.generation.port.LinerProvider;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ReadingGenerationServiceTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void concurrentSameKeyCallsProviderOnlyOnceAndReuseSnapshot() throws Exception {
        MemoryStore store = new MemoryStore();
        AtomicInteger calls = new AtomicInteger();
        LinerProvider provider = provider(request -> {
            calls.incrementAndGet();
            try { Thread.sleep(25); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            return valid(request);
        });
        ReadingGenerationService service = service(provider, store);
        GenerationCommand command = command();
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Callable<GeneratedReading>> tasks = new ArrayList<>();
            for (int index = 0; index < 8; index++) tasks.add(() -> service.generate(command));
            List<GeneratedReading> results = executor.invokeAll(tasks).stream().map(future -> {
                try { return future.get(); } catch (Exception exception) { throw new RuntimeException(exception); }
            }).toList();
            assertThat(results).extracting(GeneratedReading::resultId).containsOnly(results.getFirst().resultId());
            assertThat(results).filteredOn(GeneratedReading::reused).hasSize(7);
        }
        assertThat(calls).hasValue(1);
    }

    @Test
    void retriesValidationFailuresAndPersistsOnlyTheFirstValidResponse() {
        MemoryStore store = new MemoryStore();
        AtomicInteger calls = new AtomicInteger();
        LinerProvider provider = provider(request -> calls.incrementAndGet() < 3
                ? new LinerResponse(List.of(new GeneratedSection(SectionKey.SUMMARY, "반드시 합격한다.", List.of("dayMaster.hanja"))), List.of())
                : valid(request));

        GeneratedReading reading = service(provider, store).generate(command());

        assertThat(reading.response().sections()).hasSize(1);
        assertThat(reading.generationMode()).isEqualTo(GenerationMode.LINER);
        assertThat(calls).hasValue(3);
        assertThat(store.attempts).isEqualTo(3);
    }

    @Test
    void usesValidatedDeterministicFallbackAfterThreeProviderFailures() {
        MemoryStore store = new MemoryStore();
        AtomicInteger calls = new AtomicInteger();
        LinerProvider provider = provider(request -> {
            calls.incrementAndGet();
            throw new IllegalStateException("provider unavailable");
        });

        GeneratedReading reading = service(provider, store).generate(command());

        assertThat(reading.generationMode()).isEqualTo(GenerationMode.FALLBACK);
        assertThat(reading.response().sections()).singleElement()
                .satisfies(section -> {
                    assertThat(section.key()).isEqualTo(SectionKey.SUMMARY);
                    assertThat(section.content()).contains("오행 분포", "합격 여부를 단정하는 예측이 아니라");
                    assertThat(section.sourceFactKeys()).contains("dayMaster.element", "fiveElements.counts.WOOD");
                });
        assertThat(calls).hasValue(3);
        assertThat(store.attempts).isEqualTo(4);
        assertThat(store.lastAttemptStatus).isEqualTo("FALLBACK_SUCCEEDED");
    }

    @Test
    void marksGenerationFailedOnlyWhenProviderAndFallbackBothFail() {
        MemoryStore store = new MemoryStore();
        LinerProvider provider = provider(request -> {
            throw new IllegalStateException("provider unavailable");
        });
        DeterministicReadingFallback brokenFallback = new DeterministicReadingFallback() {
            @Override
            public LinerResponse generate(LinerRequest request) {
                throw new IllegalStateException("fallback unavailable");
            }
        };

        assertThatThrownBy(() -> service(provider, store, brokenFallback).generate(command()))
                .isInstanceOf(ReadingGenerationService.GenerationFailedException.class)
                .hasMessage("FALLBACK_GENERATION_FAILED");
        assertThat(store.attempts).isEqualTo(4);
        assertThat(store.lastAttemptStatus).isEqualTo("FALLBACK_FAILED");
        assertThat(store.failureCode).isEqualTo("FALLBACK_GENERATION_FAILED");
    }

    private ReadingGenerationService service(LinerProvider provider, GenerationSnapshotStore store) {
        return service(provider, store, new DeterministicReadingFallback());
    }

    private ReadingGenerationService service(LinerProvider provider, GenerationSnapshotStore store,
                                             DeterministicReadingFallback fallback) {
        return new ReadingGenerationService(provider, store, new LinerFactProjector(mapper),
                new GenerationKeyFactory(mapper), new LinerResponseValidator(mapper),
                fallback);
    }

    private GenerationCommand command() {
        var facts = new DeterministicSajuEngine().calculate(new BirthInput(LocalDate.of(2004, 3, 15),
                LocalTime.of(14, 32), false, BirthInput.CalendarType.SOLAR, false, BirthInput.Gender.FEMALE),
                CalculationPolicy.CURRENT);
        return new GenerationCommand(UUID.randomUUID(), FortuneType.SUNEUNG, LocalDate.of(2026, 11, 19), null,
                facts, List.of(SectionKey.SUMMARY), List.of(), "gen-v1", "content-v1");
    }

    private LinerResponse valid(LinerRequest request) {
        return new LinerResponse(List.of(new GeneratedSection(SectionKey.SUMMARY,
                "차분하게 순서를 지키는 흐름이에요.", List.of("dayMaster.hanja"))), List.of());
    }

    private LinerProvider provider(java.util.function.Function<LinerRequest, LinerResponse> function) {
        return new LinerProvider() {
            public String name() { return "test"; }
            public LinerResponse generate(LinerRequest request) { return function.apply(request); }
        };
    }

    private static final class MemoryStore implements GenerationSnapshotStore {
        private GeneratedReading reading;
        private boolean claimed;
        private int attempts;
        private String lastAttemptStatus;
        private String failureCode;

        public synchronized Optional<GeneratedReading> findSucceeded(String key) {
            return Optional.ofNullable(reading);
        }
        public synchronized boolean tryClaim(LinerRequest request, String contentVersion, Duration lease) {
            if (claimed) return false;
            claimed = true;
            return true;
        }
        public synchronized GeneratedReading saveSucceeded(LinerRequest request, LinerResponse response,
                                                           String contentVersion, GenerationMode generationMode) {
            reading = new GeneratedReading(UUID.randomUUID(), request.generationKey(), request.fortuneType(), response,
                    request.calculationVersion(), request.generationVersion(), contentVersion, generationMode, false);
            return reading;
        }
        public synchronized void recordAttempt(String key, int attempt, String provider, String inputHash,
                                               String status, long latencyMs, String errorCode) {
            attempts++;
            lastAttemptStatus = status;
        }
        public void markFailed(String key, String failureCode) {
            claimed = false;
            this.failureCode = failureCode;
        }
    }
}
