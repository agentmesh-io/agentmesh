package com.therighthandapp.agentmesh.agents.architect.application;

import com.therighthandapp.agentmesh.agents.architect.domain.SystemArchitecture;
import com.therighthandapp.agentmesh.agents.architect.ports.*;
import com.therighthandapp.agentmesh.agents.planner.domain.ExecutionPlan;
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
 * Application Service (Use Case): Generate System Architecture
 * 
 * This is the primary use case for the Architect Agent.
 * It orchestrates domain logic and coordinates between ports.
 * 
 * Clean Architecture: Application layer, depends on domain models and ports.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArchitectAgentService {
    
    private final PlanRepository planRepository;
    private final ArchitectureRepository architectureRepository;
    private final ArchitectLLMService llmService;
    private final ArchitectureMemoryService vectorMemory;
    private final ArchitecturePromptBuilder promptBuilder;
    private final ArchitectureParser parser;
    
    /**
     * Main use case: Generate system architecture from execution plan
     * 
     * @param planId The execution plan identifier from PlannerAgent
     * @return Generated and validated system architecture
     */
    @Transactional
    public SystemArchitecture generateArchitecture(String planId) {
        log.info("Starting architecture generation for plan: {}", planId);
        long startTime = System.currentTimeMillis();
        
        try {
            // Step 1: Retrieve execution plan
            log.debug("Retrieving execution plan...");
            ExecutionPlan plan = planRepository.findById(planId);
            log.info("Retrieved plan: {} (project: {})", planId, plan.getProjectTitle());
            
            // Step 2: Query vector memory for similar architectures
            log.debug("Searching for similar architecture patterns...");
            List<ArchitectureMemoryService.SimilarArchitecture> similarArchitectures = 
                    vectorMemory.searchSimilarArchitectures(
                        buildArchitectureSearchQuery(plan), 
                        5 // Top 5 similar architectures
                    );
            
            List<String> architectureContexts = similarArchitectures.stream()
                    .map(this::formatSimilarArchitecture)
                    .collect(Collectors.toList());
            
            log.info("Found {} similar architectures for context", similarArchitectures.size());
            
            // Step 3: Build LLM prompt
            log.debug("Building architecture prompt...");
            String prompt = promptBuilder.buildArchitecturePrompt(plan, architectureContexts);
            log.debug("Prompt size: {} characters", prompt.length());
            
            // Step 4: Call LLM to generate architecture
            log.info("Calling LLM to generate system architecture...");
            Map<String, Object> llmParameters = buildLLMParameters();
            ArchitectLLMService.ArchitectureResponse llmResponse = 
                    llmService.generateArchitecture(prompt, llmParameters);
            
            if (!llmResponse.success()) {
                log.error("LLM failed to generate architecture: {}", llmResponse.errorMessage());
                throw new ArchitectureGenerationException("LLM failed: " + llmResponse.errorMessage());
            }
            
            log.info("LLM response received: {} tokens (cost: ${})", 
                     llmResponse.usage().totalTokens(), 
                     llmResponse.usage().estimatedCost());
            
            // Step 5: Parse LLM response into domain model
            log.debug("Parsing LLM response...");
            SystemArchitecture architecture = parser.parse(llmResponse.content(), plan);
            
            // Add generated metadata
            architecture = enrichArchitectureWithMetadata(architecture, llmResponse.usage());
            
            log.info("Parsed architecture: {} components, {} data flows", 
                     architecture.getComponents() != null ? architecture.getComponents().size() : 0,
                     architecture.getDataFlows() != null ? architecture.getDataFlows().size() : 0);
            
            // Step 6: Validate architecture
            log.debug("Validating architecture...");
            SystemArchitecture.ArchitectureValidationResult validationResult = architecture.validate();
            if (!validationResult.isValid()) {
                log.warn("Architecture validation warnings: {}", validationResult.getErrors());
                // Continue with warnings, but log them
            }
            
            // Step 7: Store architecture in repository (Blackboard)
            log.debug("Storing architecture in repository...");
            SystemArchitecture savedArchitecture = architectureRepository.save(architecture);
            log.info("Architecture saved with ID: {}", savedArchitecture.getArchitectureId());
            
            // Step 8: Store embeddings in vector memory for future context
            log.debug("Storing architecture embeddings in vector memory...");
            storeArchitectureInVectorMemory(savedArchitecture);
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Architecture generation completed in {}ms", duration);
            
            return savedArchitecture;
            
        } catch (PlanRepository.PlanNotFoundException e) {
            log.error("Plan not found: {}", planId);
            throw new ArchitectureGenerationException("Plan not found: " + planId, e);
        } catch (ArchitectureParser.ArchitectureParsingException e) {
            log.error("Failed to parse LLM response", e);
            throw new ArchitectureGenerationException("Failed to parse architecture from LLM response", e);
        } catch (Exception e) {
            log.error("Unexpected error during architecture generation", e);
            throw new ArchitectureGenerationException("Unexpected error", e);
        }
    }
    
    /**
     * Retrieve existing architecture
     */
    public SystemArchitecture getArchitecture(String architectureId) {
        log.debug("Retrieving architecture: {}", architectureId);
        return architectureRepository.findById(architectureId);
    }
    
    /**
     * Get architecture for a plan
     */
    public SystemArchitecture getArchitectureForPlan(String planId) {
        log.debug("Retrieving architecture for plan: {}", planId);
        return architectureRepository.findLatestByPlanId(planId);
    }
    
    // Private helper methods
    
    /**
     * Build search query from plan for finding similar architectures
     */
    private String buildArchitectureSearchQuery(ExecutionPlan plan) {
        StringBuilder query = new StringBuilder();
        
        query.append(plan.getProjectTitle()).append(". ");
        
        if (plan.getTechStack() != null) {
            query.append("Technologies: ");
            query.append(String.join(", ", plan.getTechStack().getPrimaryLanguages()));
            query.append(". ");
        }
        
        if (plan.getModules() != null && !plan.getModules().isEmpty()) {
            query.append("Modules: ");
            query.append(plan.getModules().stream()
                    .map(ExecutionPlan.Module::getName)
                    .collect(Collectors.joining(", ")));
        }
        
        return query.toString();
    }
    
    /**
     * Format similar architecture for context
     */
    private String formatSimilarArchitecture(ArchitectureMemoryService.SimilarArchitecture similar) {
        return String.format(
                "%s (%s) - Style: %s - Similarity: %.2f - %s",
                similar.projectTitle(),
                similar.architectureId(),
                similar.architectureStyle(),
                similar.similarityScore(),
                similar.summary()
        );
    }
    
    /**
     * Build LLM parameters
     */
    private Map<String, Object> buildLLMParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("temperature", 0.3); // Lower temperature for more structured output
        params.put("max_tokens", 4000); // Larger for detailed architecture
        params.put("model", "gpt-4"); // Use GPT-4 for complex architecture decisions
        return params;
    }
    
    /**
     * Enrich architecture with metadata
     */
    private SystemArchitecture enrichArchitectureWithMetadata(
            SystemArchitecture architecture,
            ArchitectLLMService.TokenUsage usage) {
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("architectureId", UUID.randomUUID().toString());
        metadata.put("generatedAt", LocalDateTime.now().toString());
        metadata.put("llmTokens", String.valueOf(usage.totalTokens()));
        metadata.put("llmCost", String.valueOf(usage.estimatedCost()));
        metadata.put("agentType", "ArchitectAgent");
        metadata.put("version", "1.0");
        
        return architecture
                .withArchitectureId(metadata.get("architectureId"))
                .withCreatedAt(LocalDateTime.now())
                .withMetadata(metadata);
    }
    
    /**
     * Store architecture in vector memory for future reference
     */
    private void storeArchitectureInVectorMemory(SystemArchitecture architecture) {
        try {
            String content = buildArchitectureContent(architecture);
            
            Map<String, String> metadata = new HashMap<>();
            metadata.put("architectureId", architecture.getArchitectureId());
            metadata.put("planId", architecture.getPlanId());
            metadata.put("projectTitle", architecture.getProjectTitle());
            metadata.put("architectureStyle", architecture.getArchitectureStyle() != null ? 
                    architecture.getArchitectureStyle().getPrimaryStyle() : "Unknown");
            metadata.put("componentCount", String.valueOf(
                    architecture.getComponents() != null ? architecture.getComponents().size() : 0));
            
            vectorMemory.storeArchitecture(architecture.getArchitectureId(), content, metadata);
            
            log.debug("Stored architecture {} in vector memory", architecture.getArchitectureId());
            
        } catch (Exception e) {
            log.warn("Failed to store architecture in vector memory", e);
            // Non-critical failure, continue
        }
    }
    
    /**
     * Build searchable content from architecture
     */
    private String buildArchitectureContent(SystemArchitecture architecture) {
        StringBuilder content = new StringBuilder();
        
        content.append("Project: ").append(architecture.getProjectTitle()).append("\n\n");
        
        if (architecture.getArchitectureStyle() != null) {
            content.append("Architecture Style: ").append(architecture.getArchitectureStyle().getPrimaryStyle()).append("\n");
            content.append("Patterns: ").append(String.join(", ", architecture.getArchitectureStyle().getPatterns())).append("\n");
            content.append("Justification: ").append(architecture.getArchitectureStyle().getJustification()).append("\n\n");
        }
        
        if (architecture.getComponents() != null) {
            content.append("Components:\n");
            for (SystemArchitecture.Component component : architecture.getComponents()) {
                content.append("- ").append(component.getName()).append(" (").append(component.getType()).append("): ");
                content.append(component.getDescription()).append("\n");
                if (component.getTechnology() != null) {
                    content.append("  Technology: ").append(component.getTechnology().getFramework());
                    content.append(" (").append(component.getTechnology().getLanguage()).append(")\n");
                }
            }
            content.append("\n");
        }
        
        if (architecture.getDeployment() != null) {
            content.append("Deployment: ").append(architecture.getDeployment().getPlatform()).append("\n\n");
        }
        
        if (architecture.getSecurity() != null && architecture.getSecurity().getAuthentication() != null) {
            content.append("Security: ").append(architecture.getSecurity().getAuthentication().getMethod()).append("\n");
        }
        
        return content.toString();
    }
    
    /**
     * Exception for architecture generation failures
     */
    public static class ArchitectureGenerationException extends RuntimeException {
        public ArchitectureGenerationException(String message) {
            super(message);
        }
        
        public ArchitectureGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
