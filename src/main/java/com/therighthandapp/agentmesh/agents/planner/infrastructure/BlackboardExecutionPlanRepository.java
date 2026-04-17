package com.therighthandapp.agentmesh.agents.planner.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.therighthandapp.agentmesh.agents.planner.domain.ExecutionPlan;
import com.therighthandapp.agentmesh.agents.planner.ports.ExecutionPlanRepository;
import com.therighthandapp.agentmesh.blackboard.BlackboardEntry;
import com.therighthandapp.agentmesh.blackboard.BlackboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Infrastructure Adapter: Blackboard Execution Plan Repository
 * 
 * Implements ExecutionPlanRepository by storing plans in the Blackboard.
 * Plans are serialized as JSON and stored with type "PLAN".
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlackboardExecutionPlanRepository implements ExecutionPlanRepository {
    
    private final BlackboardService blackboardService;
    private final ObjectMapper objectMapper;
    
    private static final String AGENT_ID = "planner-agent";
    private static final String ENTRY_TYPE = "PLAN";
    
    @Override
    public ExecutionPlan save(ExecutionPlan plan) {
        log.debug("Saving execution plan to blackboard: {}", plan.getProjectTitle());
        
        try {
            // Ensure plan has an ID
            ExecutionPlan planToSave = plan.getPlanId() == null 
                ? plan.withPlanId(UUID.randomUUID().toString())
                : plan;
            
            // Serialize to JSON
            String contentJson = objectMapper.writeValueAsString(planToSave);
            
            // Store in blackboard
            BlackboardEntry entry = blackboardService.post(
                AGENT_ID,
                ENTRY_TYPE,
                "Execution Plan: " + planToSave.getProjectTitle(),
                contentJson
            );
            
            log.info("Execution plan saved to blackboard: planId={}, entryId={}", 
                     planToSave.getPlanId(), entry.getId());
            
            return planToSave;
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize execution plan", e);
            throw new RuntimeException("Failed to save execution plan", e);
        }
    }
    
    @Override
    public ExecutionPlan findById(String planId) {
        log.info("Finding execution plan by ID: {}", planId);
        
        try {
            // Query blackboard for PLAN entries
            var entries = blackboardService.readByType(ENTRY_TYPE);
            log.info("Found {} PLAN entries to search", entries.size());
            
            // Find matching plan
            for (BlackboardEntry entry : entries) {
                try {
                    log.info("Attempting to parse entry {}: {}", entry.getId(), 
                             entry.getContent().substring(0, Math.min(200, entry.getContent().length())));
                    ExecutionPlan plan = objectMapper.readValue(
                        entry.getContent(), 
                        ExecutionPlan.class
                    );
                    log.info("Successfully parsed entry {}, planId={}", entry.getId(), plan.getPlanId());
                    
                    if (planId.equals(plan.getPlanId())) {
                        log.info("Found matching execution plan: {}", planId);
                        return plan;
                    }
                } catch (JsonProcessingException e) {
                    log.warn("Failed to deserialize blackboard entry {}: {}", entry.getId(), e.getMessage());
                }
            }
            
            log.warn("Execution plan not found after searching {} entries: {}", entries.size(), planId);
            return null;
            
        } catch (Exception e) {
            log.error("Error retrieving execution plan", e);
            throw new RuntimeException("Failed to retrieve execution plan", e);
        }
    }
    
    @Override
    public ExecutionPlan findLatestByProjectId(String projectId) {
        log.debug("Finding latest execution plan for project: {}", projectId);
        
        try {
            var entries = blackboardService.readByType(ENTRY_TYPE);
            
            ExecutionPlan latestPlan = null;
            
            for (BlackboardEntry entry : entries) {
                try {
                    ExecutionPlan plan = objectMapper.readValue(
                        entry.getContent(),
                        ExecutionPlan.class
                    );
                    
                    // Match by project ID in metadata or title
                    if (matches(plan, projectId)) {
                        if (latestPlan == null || 
                            plan.getGeneratedAt().isAfter(latestPlan.getGeneratedAt())) {
                            latestPlan = plan;
                        }
                    }
                } catch (JsonProcessingException e) {
                    log.warn("Failed to deserialize blackboard entry: {}", entry.getId(), e);
                }
            }
            
            if (latestPlan != null) {
                log.info("Found latest execution plan for project: {}", projectId);
            } else {
                log.warn("No execution plan found for project: {}", projectId);
            }
            
            return latestPlan;
            
        } catch (Exception e) {
            log.error("Error retrieving latest execution plan", e);
            throw new RuntimeException("Failed to retrieve execution plan", e);
        }
    }
    
    @Override
    public boolean exists(String planId) {
        return findById(planId) != null;
    }
    
    /**
     * Check if plan matches project ID
     */
    private boolean matches(ExecutionPlan plan, String projectId) {
        if (plan.getMetadata() != null && 
            projectId.equals(plan.getMetadata().get("projectId"))) {
            return true;
        }
        
        // Fallback: check if projectId is in title or SRS ID
        return plan.getProjectTitle() != null && 
               plan.getProjectTitle().contains(projectId);
    }
}
