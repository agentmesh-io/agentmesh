package com.therighthandapp.agentmesh.agents.developer.ports;

import com.therighthandapp.agentmesh.agents.planner.domain.ExecutionPlan;

import java.util.Optional;

/**
 * Port (Interface): Repository adapter to access ExecutionPlans
 * 
 * Allows DeveloperAgent to retrieve execution plans from PlannerAgent's repository.
 * This is an example of the Adapter pattern in hexagonal architecture.
 */
public interface PlanRepository {
    
    /**
     * Find execution plan by ID
     */
    Optional<ExecutionPlan> findById(String planId);
}
