package com.therighthandapp.agentmesh.agents.architect.ports;

import com.therighthandapp.agentmesh.agents.architect.domain.SystemArchitecture;

/**
 * Port (Interface): Architecture Repository
 * 
 * Defines contract for persisting and retrieving system architectures.
 * Implementation will store in Blackboard.
 * 
 * Hexagonal Architecture: This is a port (interface) in the domain layer.
 * The adapter (implementation) is in the infrastructure layer.
 */
public interface ArchitectureRepository {
    
    /**
     * Save architecture to persistent storage (Blackboard)
     */
    SystemArchitecture save(SystemArchitecture architecture);
    
    /**
     * Find architecture by ID
     */
    SystemArchitecture findById(String architectureId);
    
    /**
     * Find latest architecture for a plan
     */
    SystemArchitecture findLatestByPlanId(String planId);
    
    /**
     * Find architecture by project ID
     */
    SystemArchitecture findByProjectId(String projectId);
    
    /**
     * Exception for architecture not found
     */
    class ArchitectureNotFoundException extends RuntimeException {
        public ArchitectureNotFoundException(String message) {
            super(message);
        }
    }
}
