package com.therighthandapp.agentmesh.agents.developer.infrastructure;

import com.therighthandapp.agentmesh.agents.planner.domain.ExecutionPlan;
import com.therighthandapp.agentmesh.agents.planner.ports.ExecutionPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Infrastructure Adapter: Adapts ExecutionPlanRepository for DeveloperAgent
 */
@RequiredArgsConstructor
public class DeveloperPlanRepositoryAdapter implements com.therighthandapp.agentmesh.agents.developer.ports.PlanRepository {
    
    private final ExecutionPlanRepository executionPlanRepository;
    
    @Override
    public Optional<ExecutionPlan> findById(String planId) {
        ExecutionPlan plan = executionPlanRepository.findById(planId);
        return Optional.ofNullable(plan);
    }
}
