package com.therighthandapp.agentmesh.agents.developer.ports;

import java.util.Map;

/**
 * Port (Interface): LLM Service for code generation
 * 
 * Abstracts the LLM interaction for generating source code.
 */
public interface DeveloperLLMService {
    
    /**
     * Generate code using LLM
     */
    CodeResponse generateCode(String prompt, Map<String, Object> parameters);
    
    /**
     * LLM Response wrapper
     */
    record CodeResponse(
        boolean success,
        String content,
        String errorMessage,
        TokenUsage usage
    ) {}
    
    /**
     * Token usage statistics
     */
    record TokenUsage(
        int promptTokens,
        int completionTokens,
        int totalTokens,
        double estimatedCost
    ) {}
}
