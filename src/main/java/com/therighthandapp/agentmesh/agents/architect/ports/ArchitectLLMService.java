package com.therighthandapp.agentmesh.agents.architect.ports;

import java.util.Map;

/**
 * Port (Interface): LLM Service for Architecture Generation
 * 
 * Abstracts the LLM interaction for generating system architectures.
 */
public interface ArchitectLLMService {
    
    /**
     * Generate system architecture using LLM
     */
    ArchitectureResponse generateArchitecture(String prompt, Map<String, Object> parameters);
    
    /**
     * LLM Response wrapper
     */
    record ArchitectureResponse(
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
