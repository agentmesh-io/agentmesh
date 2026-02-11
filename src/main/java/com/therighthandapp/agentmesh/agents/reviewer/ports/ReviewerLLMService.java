package com.therighthandapp.agentmesh.agents.reviewer.ports;

/**
 * Port Interface: Reviewer LLM Service
 * 
 * Hexagonal Architecture: This is an output port that abstracts LLM communication.
 * The implementation will communicate with the LLM service to generate code reviews.
 */
public interface ReviewerLLMService {
    
    /**
     * Generates a code review using the LLM
     * 
     * @param prompt The prompt containing code artifact details
     * @return The LLM response with review analysis in JSON format
     */
    String generateReview(String prompt);
    
    /**
     * Validates the LLM service is available
     * 
     * @return true if LLM service is available
     */
    boolean isAvailable();
}
