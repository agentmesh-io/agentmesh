package com.therighthandapp.agentmesh.agents.planner.ports;

import java.util.List;
import java.util.Map;

/**
 * Port (Interface): LLM Service
 * 
 * Hexagonal Architecture: Output port for LLM interactions.
 * Abstracts away the specific LLM provider (OpenAI, etc.)
 */
public interface LLMService {
    
    /**
     * Generate execution plan from SRS using LLM
     * 
     * @param prompt The constructed prompt with SRS context
     * @param parameters LLM parameters (temperature, max_tokens, etc.)
     * @return The LLM response containing the plan
     */
    LLMPlanResponse generatePlan(String prompt, Map<String, Object> parameters);
    
    /**
     * LLM Response wrapper
     */
    record LLMPlanResponse(
        String content,
        boolean success,
        String errorMessage,
        UsageStats usage
    ) {}
    
    /**
     * Token usage statistics
     */
    record UsageStats(
        int promptTokens,
        int completionTokens,
        int totalTokens,
        double estimatedCost
    ) {}
}
