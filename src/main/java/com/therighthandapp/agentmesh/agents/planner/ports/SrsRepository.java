package com.therighthandapp.agentmesh.agents.planner.ports;

import com.therighthandapp.agentmesh.integration.dto.SrsHandoffDto;

/**
 * Port (Interface): SRS Repository
 * 
 * Hexagonal Architecture: This is an output port that defines the contract
 * for retrieving SRS documents from Auto-BADS system.
 * 
 * The implementation will be in the infrastructure layer.
 */
public interface SrsRepository {
    
    /**
     * Retrieve SRS document from Auto-BADS by ID
     * 
     * @param srsId The SRS document identifier
     * @return The SRS handoff DTO
     * @throws SrsNotFoundException if SRS not found
     * @throws SrsRetrievalException if retrieval fails
     */
    SrsHandoffDto retrieveSrs(String srsId);
    
    /**
     * Check if SRS exists
     */
    boolean exists(String srsId);
    
    /**
     * Custom exceptions
     */
    class SrsNotFoundException extends RuntimeException {
        public SrsNotFoundException(String srsId) {
            super("SRS not found: " + srsId);
        }
    }
    
    class SrsRetrievalException extends RuntimeException {
        public SrsRetrievalException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
