package com.sajuppugi.fortune.generation.port;

import com.sajuppugi.fortune.generation.domain.GenerationModels.GeneratedReading;
import com.sajuppugi.fortune.generation.domain.GenerationModels.GenerationMode;
import com.sajuppugi.fortune.generation.domain.GenerationModels.LinerRequest;
import com.sajuppugi.fortune.generation.domain.GenerationModels.LinerResponse;
import java.time.Duration;
import java.util.Optional;

public interface GenerationSnapshotStore {
    Optional<GeneratedReading> findSucceeded(String generationKey);
    boolean tryClaim(LinerRequest request, String contentVersion, Duration lease);
    GeneratedReading saveSucceeded(LinerRequest request, LinerResponse response, String contentVersion,
                                   GenerationMode generationMode);
    void recordAttempt(String generationKey, int attempt, String provider, String inputHash,
                       String status, long latencyMs, String errorCode);
    void markFailed(String generationKey, String failureCode);
}
