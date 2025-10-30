package com.therighthandapp.agentmesh.selfcorrection;

import java.time.Instant;

/**
 * Represents the result of a self-correction iteration
 */
public class CorrectionResult {
    private final boolean success;
    private final String output;
    private final int iterationCount;
    private final String failureReason;
    private final Instant timestamp;

    public CorrectionResult(boolean success, String output, int iterationCount, String failureReason) {
        this.success = success;
        this.output = output;
        this.iterationCount = iterationCount;
        this.failureReason = failureReason;
        this.timestamp = Instant.now();
    }

    public static CorrectionResult success(String output, int iterations) {
        return new CorrectionResult(true, output, iterations, null);
    }

    public static CorrectionResult failure(String reason, int iterations) {
        return new CorrectionResult(false, null, iterations, reason);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getOutput() {
        return output;
    }

    public int getIterationCount() {
        return iterationCount;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getOutput() {
        return content;
    }
}

