package com.therighthandapp.agentmesh.agents.planner.application;

import com.therighthandapp.agentmesh.agents.planner.domain.ExecutionPlan;
import com.therighthandapp.agentmesh.agents.planner.ports.*;
import com.therighthandapp.agentmesh.integration.dto.SrsHandoffDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application Service (Use Case): Generate Execution Plan
 * 
 * This is the primary use case for the Planner Agent.
 * It orchestrates the domain logic and coordinates between ports.
 * 
 * Clean Architecture: This is in the application layer, not the domain.
 * It depends on domain models and port interfaces, not implementations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlannerAgentService {
    
    private final SrsRepository srsRepository;
    private final ExecutionPlanRepository planRepository;
    private final LLMService llmService;
    private final VectorMemoryService vectorMemory;
    private final PromptBuilder promptBuilder;
    private final PlanParser planParser;
    
    /**
     * Main use case: Generate execution plan from SRS
     * 
     * @param srsId The SRS document identifier from Auto-BADS
     * @return Generated and validated execution plan
     */
    @Transactional
    public ExecutionPlan generateExecutionPlan(String srsId) {
        log.info("Starting execution plan generation for SRS: {}", srsId);
        long startTime = System.currentTimeMillis();
        
        try {
            // Step 1: Retrieve SRS from Auto-BADS
            log.debug("Retrieving SRS from Auto-BADS...");
            SrsHandoffDto srsData = srsRepository.retrieveSrs(srsId);
            log.info("Retrieved SRS: {} (title: {})", srsId, srsData.getIdeaTitle());
            
            // Step 2: Query vector memory for similar projects
            log.debug("Querying vector memory for similar projects...");
            List<VectorMemoryService.SimilarPlan> similarPlans = vectorMemory.searchSimilarPlans(
                buildSearchQuery(srsData), 
                5 // Top 5 similar projects
            );
            
            List<String> similarProjectContexts = similarPlans.stream()
                .map(this::formatSimilarProject)
                .collect(Collectors.toList());
            
            log.info("Found {} similar projects for context", similarPlans.size());
            
            // Step 3: Build LLM prompt
            log.debug("Building LLM prompt...");
            String prompt = promptBuilder.buildExecutionPlanPrompt(srsData, similarProjectContexts);
            log.debug("Prompt size: {} characters", prompt.length());
            
            // Step 4: Call LLM to generate plan
            log.info("Calling LLM to generate execution plan...");
            Map<String, Object> llmParameters = buildLLMParameters();
            LLMService.LLMPlanResponse llmResponse = llmService.generatePlan(prompt, llmParameters);
            
            if (!llmResponse.success()) {
                log.error("LLM failed to generate plan: {}", llmResponse.errorMessage());
                throw new PlanGenerationException("LLM failed: " + llmResponse.errorMessage());
            }
            
            log.info("LLM response received: {} tokens (cost: ${})", 
                     llmResponse.usage().totalTokens(), 
                     llmResponse.usage().estimatedCost());
            
            // Step 5: Parse LLM response into domain model
            log.debug("Parsing LLM response...");
            ExecutionPlan plan = planParser.parse(llmResponse.content(), srsData);
            
            // Add generated metadata
            plan = enrichPlanWithMetadata(plan, srsId, llmResponse.usage());
            
            log.info("Parsed execution plan: {} modules, {} total files", 
                     plan.getModules().size(), 
                     plan.getTotalFileCount());
            
            // Step 6: Store plan in repository (Blackboard)
            log.debug("Storing execution plan in repository...");
            ExecutionPlan savedPlan = planRepository.save(plan);
            log.info("Execution plan saved with ID: {}", savedPlan.getPlanId());
            
            // Step 7: Store embeddings in vector memory for future context
            log.debug("Storing plan embeddings in vector memory...");
            storePlanInVectorMemory(savedPlan);
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Execution plan generation completed in {}ms", duration);
            
            return savedPlan;
            
        } catch (SrsRepository.SrsNotFoundException e) {
            log.error("SRS not found: {}", srsId);
            throw new PlanGenerationException("SRS not found: " + srsId, e);
        } catch (SrsRepository.SrsRetrievalException e) {
            log.error("Failed to retrieve SRS: {}", srsId, e);
            throw new PlanGenerationException("Failed to retrieve SRS", e);
        } catch (PlanParser.PlanParsingException e) {
            log.error("Failed to parse LLM response", e);
            throw new PlanGenerationException("Failed to parse plan from LLM response", e);
        } catch (Exception e) {
            log.error("Unexpected error during plan generation", e);
            throw new PlanGenerationException("Unexpected error", e);
        }
    }
    
    /**
     * Retrieve existing execution plan
     */
    public ExecutionPlan getExecutionPlan(String planId) {
        log.debug("Retrieving execution plan: {}", planId);
        return planRepository.findById(planId);
    }
    
    /**
     * Get latest plan for a project
     */
    public ExecutionPlan getLatestPlanForProject(String projectId) {
        log.debug("Retrieving latest plan for project: {}", projectId);
        return planRepository.findLatestByProjectId(projectId);
    }
    
    // Private helper methods
    
    /**
     * Build search query from SRS for vector memory
     */
    private String buildSearchQuery(SrsHandoffDto srsData) {
        StringBuilder query = new StringBuilder();
        
        query.append(srsData.getIdeaTitle()).append(". ");
        
        if (srsData.getProblemStatement() != null) {
            query.append(srsData.getProblemStatement()).append(". ");
        }
        
        if (srsData.getSrs() != null && srsData.getSrs().getArchitecture() != null) {
            query.append("Architecture: ")
                 .append(srsData.getSrs().getArchitecture().getArchitectureStyle())
                 .append(". ");
        }
        
        return query.toString();
    }
    
    /**
     * Format similar project for context
     */
    private String formatSimilarProject(VectorMemoryService.SimilarPlan plan) {
        return String.format(
            "- **%s** (similarity: %.2f)\n  Tech Stack: %s\n  Summary: %s",
            plan.title(),
            plan.similarityScore(),
            String.join(", ", plan.techStack()),
            plan.summary()
        );
    }
    
    /**
     * Build LLM parameters
     */
    private Map<String, Object> buildLLMParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("temperature", 0.7); // Balance creativity and consistency
        params.put("max_tokens", 4000); // Enough for comprehensive plan
        params.put("top_p", 0.9);
        return params;
    }
    
    /**
     * Enrich plan with metadata
     */
    private ExecutionPlan enrichPlanWithMetadata(
            ExecutionPlan plan, 
            String srsId, 
            LLMService.UsageStats usage) {
        
        Map<String, String> metadata = new HashMap<>();
        if (plan.getMetadata() != null) {
            metadata.putAll(plan.getMetadata());
        }
        
        metadata.put("srsId", srsId);
        metadata.put("llmTokensUsed", String.valueOf(usage.totalTokens()));
        metadata.put("llmCost", String.format("%.4f", usage.estimatedCost()));
        metadata.put("generatedBy", "planner-agent");
        metadata.put("agentVersion", "1.0.0");
        
        return plan.withPlanId(UUID.randomUUID().toString())
                   .withSrsId(srsId)
                   .withGeneratedAt(LocalDateTime.now())
                   .withMetadata(metadata);
    }
    
    /**
     * Store plan in vector memory
     */
    private void storePlanInVectorMemory(ExecutionPlan plan) {
        try {
            // Create searchable content
            StringBuilder content = new StringBuilder();
            content.append(plan.getProjectTitle()).append(". ");
            
            for (ExecutionPlan.Module module : plan.getModules()) {
                content.append(module.getName()).append(": ")
                       .append(module.getDescription()).append(". ");
            }
            
            // Metadata for filtering
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("planId", plan.getPlanId());
            metadata.put("projectTitle", plan.getProjectTitle());
            metadata.put("moduleCount", plan.getModules().size());
            metadata.put("fileCount", plan.getTotalFileCount());
            metadata.put("techStack", plan.getTechStack().getPrimaryLanguages());
            metadata.put("architecture", plan.getTechStack().getArchitecturePattern());
            
            vectorMemory.storePlanEmbedding(plan.getPlanId(), content.toString(), metadata);
            log.debug("Stored plan embeddings in vector memory");
            
        } catch (Exception e) {
            log.warn("Failed to store plan in vector memory (non-critical)", e);
        }
    }
    
    /**
     * Custom exception for plan generation failures
     */
    public static class PlanGenerationException extends RuntimeException {
        public PlanGenerationException(String message) {
            super(message);
        }
        
        public PlanGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
