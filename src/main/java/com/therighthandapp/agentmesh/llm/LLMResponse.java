package com.therighthandapp.agentmesh.llm;

import java.time.Instant;

/**
 * Represents a response from an LLM
 */
public class LLMResponse {
    private final String content;
    private final String modelName;
    private final LLMUsage usage;
    private final Instant timestamp;
    private final boolean success;
    private final String errorMessage;

    public LLMResponse(String content, String modelName, LLMUsage usage) {
        this.content = content;
        this.modelName = modelName;
        this.usage = usage;
        this.timestamp = Instant.now();
        this.success = true;
        this.errorMessage = null;
    }

    public LLMResponse(String errorMessage) {
        this.content = null;
        this.modelName = null;
        this.usage = null;
        this.timestamp = Instant.now();
        this.success = false;
        this.errorMessage = errorMessage;
    }

    public String getContent() {
        return content;
    }

    public String getModelName() {
        return modelName;
    }

    public LLMUsage getUsage() {
        return usage;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}

