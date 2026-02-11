package com.therighthandapp.agentmesh.agents.architect.ports;

import com.therighthandapp.agentmesh.agents.planner.domain.ExecutionPlan;

/**
 * Port (Interface): Plan Repository
 * 
 * Allows ArchitectAgent to retrieve execution plans.
 * Reuses the existing ExecutionPlanRepository from PlannerAgent.
 */
public interface PlanRepository {
    
    /**
     * Retrieve execution plan by ID
     */
    ExecutionPlan findById(String planId);
    
    /**
     * Exception for plan not found
     */
    class PlanNotFoundException extends RuntimeException {
        public PlanNotFoundException(String message) {
            super(message);
        }
    }
}
