package com.therighthandapp.agentmesh.llm;

/**
 * Token usage and cost tracking for LLM calls
 */
public class LLMUsage {
    private final int promptTokens;
    private final int completionTokens;
    private final int totalTokens;
    private final double estimatedCost;

    public LLMUsage(int promptTokens, int completionTokens, double estimatedCost) {
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = promptTokens + completionTokens;
        this.estimatedCost = estimatedCost;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public double getEstimatedCost() {
        return estimatedCost;
    }

    @Override
    public String toString() {
        return "Usage{prompt=%d, completion=%d, total=%d, cost=$%.4f}".formatted(
            promptTokens, completionTokens, totalTokens, estimatedCost);
    }
}

