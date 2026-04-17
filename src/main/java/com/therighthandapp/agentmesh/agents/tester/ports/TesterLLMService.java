package com.therighthandapp.agentmesh.agents.tester.ports;

/**
 * Port Interface: Tester LLM Service
 * 
 * Hexagonal Architecture: This is an output port that abstracts LLM communication.
 * The implementation will communicate with the LLM service to generate test suites.
 */
public interface TesterLLMService {
    
    /**
     * Generates test suite using the LLM
     * 
     * @param prompt The prompt containing code artifact details
     * @return The LLM response with test suite in JSON format
     */
    String generateTests(String prompt);
    
    /**
     * Validates the LLM service is available
     * 
     * @return true if LLM service is available
     */
    boolean isAvailable();
}
