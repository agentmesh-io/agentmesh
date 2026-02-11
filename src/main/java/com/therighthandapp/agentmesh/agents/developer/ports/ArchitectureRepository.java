package com.therighthandapp.agentmesh.agents.developer.ports;

import com.therighthandapp.agentmesh.agents.architect.domain.SystemArchitecture;

import java.util.Optional;

/**
 * Port (Interface): Repository adapter to access SystemArchitectures
 * 
 * Allows DeveloperAgent to retrieve architectures from ArchitectAgent's repository.
 */
public interface ArchitectureRepository {
    
    /**
     * Find architecture by ID
     */
    Optional<SystemArchitecture> findById(String architectureId);
    
    /**
     * Find latest architecture for a plan
     */
    Optional<SystemArchitecture> findLatestByPlanId(String planId);
}
