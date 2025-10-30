package com.therighthandapp.agentmesh.llm;

import java.util.List;
import java.util.Map;

/**
 * Interface for LLM client implementations.
 * Supports multiple providers (OpenAI, Anthropic, etc.) with a unified API.
 */
public interface LLMClient {

    /**
     * Generate a completion from the LLM
     *
     * @param prompt The input prompt
     * @param parameters Optional parameters (temperature, max_tokens, etc.)
     * @return The LLM response
     */
    LLMResponse complete(String prompt, Map<String, Object> parameters);

    /**
     * Generate a completion with conversation history
     *
     * @param messages List of conversation messages
     * @param parameters Optional parameters
     * @return The LLM response
     */
    LLMResponse chat(List<ChatMessage> messages, Map<String, Object> parameters);

    /**
     * Generate embeddings for text (for vector storage)
     *
     * @param text The text to embed
     * @return The embedding vector
     */
    float[] embed(String text);

    /**
     * Get the model name/identifier
     */
    String getModelName();

    /**
     * Get usage statistics for the last call
     */
    LLMUsage getLastUsage();
}

