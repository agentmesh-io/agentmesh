package com.therighthandapp.agentmesh.agents.planner.ports;

import com.therighthandapp.agentmesh.agents.planner.domain.ExecutionPlan;

/**
 * Port (Interface): Execution Plan Repository
 * 
 * Hexagonal Architecture: Output port for persisting execution plans.
 * Implementation uses Blackboard for storage.
 */
public interface ExecutionPlanRepository {
    
    /**
     * Store execution plan
     * 
     * @param plan The execution plan to store
     * @return The stored plan with generated ID
     */
    ExecutionPlan save(ExecutionPlan plan);
    
    /**
     * Retrieve execution plan by ID
     */
    ExecutionPlan findById(String planId);
    
    /**
     * Find latest plan for a project
     */
    ExecutionPlan findLatestByProjectId(String projectId);
    
    /**
     * Check if plan exists
     */
    boolean exists(String planId);
}
