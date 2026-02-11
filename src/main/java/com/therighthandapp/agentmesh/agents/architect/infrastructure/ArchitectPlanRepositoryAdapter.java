package com.therighthandapp.agentmesh.agents.architect.infrastructure;

import com.therighthandapp.agentmesh.agents.architect.ports.PlanRepository;
import com.therighthandapp.agentmesh.agents.planner.domain.ExecutionPlan;
import com.therighthandapp.agentmesh.agents.planner.ports.ExecutionPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Infrastructure Adapter: Plan Repository for Architect
 * 
 * Adapts the ExecutionPlanRepository from PlannerAgent for use by ArchitectAgent.
 * This allows ArchitectAgent to retrieve plans without depending on planner internals.
 */
@Slf4j
@RequiredArgsConstructor
public class ArchitectPlanRepositoryAdapter implements PlanRepository {
    
    private final ExecutionPlanRepository executionPlanRepository;
    
    @Override
    public ExecutionPlan findById(String planId) {
        log.debug("Retrieving execution plan: {}", planId);
        
        try {
            ExecutionPlan plan = executionPlanRepository.findById(planId);
            if (plan == null) {
                throw new PlanNotFoundException("Plan not found: " + planId);
            }
            return plan;
        } catch (Exception e) {
            log.error("Failed to retrieve plan: {}", planId, e);
            throw new PlanNotFoundException("Failed to retrieve plan: " + planId);
        }
    }
}
