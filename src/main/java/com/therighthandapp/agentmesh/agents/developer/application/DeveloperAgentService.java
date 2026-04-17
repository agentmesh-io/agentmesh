package com.therighthandapp.agentmesh.agents.developer.application;

import com.therighthandapp.agentmesh.agents.architect.domain.SystemArchitecture;
import com.therighthandapp.agentmesh.agents.developer.domain.CodeArtifact;
import com.therighthandapp.agentmesh.agents.developer.ports.*;
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

/**
 * Application Service: DeveloperAgent
 * 
 * Orchestrates code generation workflow:
 * 1. Retrieve ExecutionPlan and SystemArchitecture
 * 2. Search for similar code patterns in vector memory
 * 3. Build comprehensive prompt with context
 * 4. Call LLM to generate code
 * 5. Parse and validate generated code
 * 6. Save to Blackboard
 * 7. Store embeddings in vector memory
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeveloperAgentService {
    
    private final PlanRepository planRepository;
    private final ArchitectureRepository architectureRepository;
    private final CodeRepository codeRepository;
    private final DeveloperLLMService llmService;
    private final CodeMemoryService codeMemory;
    private final CodePromptBuilder promptBuilder;
    private final CodeParser parser;
    
    /**
     * Generate code artifacts from execution plan and architecture
     */
    @Transactional
    public CodeArtifact generateCode(String planId, String architectureId) {
        log.info("Starting code generation for plan: {}, architecture: {}", planId, architectureId);
        
        try {
            // Step 1: Retrieve ExecutionPlan
            log.debug("Retrieving execution plan: {}", planId);
            ExecutionPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new PlanNotFoundException("Plan not found: " + planId));
            log.info("Retrieved plan: {} (project: {})", planId, plan.getProjectTitle());
            
            // Step 2: Retrieve SystemArchitecture
            log.debug("Retrieving architecture: {}", architectureId);
            SystemArchitecture architecture = architectureRepository.findById(architectureId)
                .orElseThrow(() -> new ArchitectureNotFoundException("Architecture not found: " + architectureId));
            log.info("Retrieved architecture: {} components", 
                     architecture.getComponents() != null ? architecture.getComponents().size() : 0);
            
            // Step 3: Search for similar code patterns
            log.debug("Searching for similar code patterns...");
            String searchQuery = buildSearchQuery(plan, architecture);
            List<CodeMemoryService.SimilarCode> similarCode = codeMemory.searchSimilarCode(searchQuery, 3);
            log.info("Found {} similar code examples", similarCode.size());
            
            // Step 4: Build prompt with all context
            log.debug("Building code generation prompt...");
            String similarCodeContext = formatSimilarCode(similarCode);
            String prompt = promptBuilder.buildPrompt(plan, architecture, similarCodeContext);
            
            // Step 5: Call LLM to generate code
            log.info("Calling LLM to generate code...");
            Map<String, Object> llmParameters = buildLLMParameters();
            DeveloperLLMService.CodeResponse llmResponse = 
                    llmService.generateCode(prompt, llmParameters);
            
            if (!llmResponse.success()) {
                log.error("LLM failed to generate code: {}", llmResponse.errorMessage());
                throw new CodeGenerationException("LLM failed: " + llmResponse.errorMessage());
            }
            
            log.info("LLM response received: {} tokens (cost: ${})", 
                     llmResponse.usage().totalTokens(), 
                     llmResponse.usage().estimatedCost());
            
            // Step 6: Parse LLM response into domain model
            log.debug("Parsing LLM response...");
            CodeArtifact artifact = parser.parse(
                llmResponse.content(), 
                planId, 
                architectureId, 
                plan.getProjectTitle()
            );
            
            // Add generated metadata
            artifact = enrichArtifactWithMetadata(artifact, llmResponse.usage());
            
            log.info("Parsed code artifact: {} files, {} LOC", 
                     artifact.getSourceFiles().size(),
                     artifact.getTotalLinesOfCode());
            
            // Step 7: Validate artifact
            log.debug("Validating code artifact...");
            CodeArtifact.ArtifactValidationResult validation = artifact.validate();
            if (!validation.isValid()) {
                log.warn("Code artifact validation failed: {}", validation.getErrors());
                throw new CodeValidationException("Generated code validation failed: " + 
                    String.join(", ", validation.getErrors()));
            }
            log.info("Code artifact validation passed");
            
            // Step 8: Save to Blackboard
            log.debug("Saving code artifact to Blackboard...");
            CodeArtifact savedArtifact = codeRepository.save(artifact);
            log.info("Code artifact saved with ID: {}", savedArtifact.getArtifactId());
            
            // Step 9: Store embeddings in vector memory
            log.debug("Storing code embeddings in vector memory...");
            storeInVectorMemory(savedArtifact);
            
            log.info("Code generation completed successfully: {}", savedArtifact.getArtifactId());
            return savedArtifact;
            
        } catch (PlanNotFoundException | ArchitectureNotFoundException e) {
            log.error("Prerequisite not found for code generation", e);
            throw e;
        } catch (Exception e) {
            log.error("Code generation failed for plan: {}", planId, e);
            throw new CodeGenerationException("Code generation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Build search query for finding similar code
     */
    private String buildSearchQuery(ExecutionPlan plan, SystemArchitecture architecture) {
        StringBuilder query = new StringBuilder();
        query.append(plan.getProjectTitle()).append(". ");
        query.append("Language: ").append(plan.getTechStack().getPrimaryLanguages().get(0)).append(". ");
        
        if (architecture != null && architecture.getArchitectureStyle() != null) {
            query.append("Architecture: ").append(architecture.getArchitectureStyle().getPrimaryStyle()).append(". ");
        }
        
        // Add key modules
        plan.getModules().stream()
            .limit(3)
            .forEach(m -> query.append(m.getName()).append(" ").append(m.getDescription()).append(". "));
        
        return query.toString();
    }
    
    /**
     * Format similar code for prompt context
     */
    private String formatSimilarCode(List<CodeMemoryService.SimilarCode> similarCode) {
        if (similarCode.isEmpty()) {
            return "";
        }
        
        StringBuilder context = new StringBuilder();
        for (CodeMemoryService.SimilarCode code : similarCode) {
            context.append("File: ").append(code.fileName()).append("\n");
            context.append("```").append(code.language()).append("\n");
            context.append(code.snippet()).append("\n");
            context.append("```\n\n");
        }
        return context.toString();
    }
    
    /**
     * Build LLM parameters
     */
    private Map<String, Object> buildLLMParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("temperature", 0.2); // Lower temperature for more deterministic code
        params.put("max_tokens", 8000); // More tokens for code generation
        params.put("model", "gpt-4"); // Use GPT-4 for better code quality
        return params;
    }
    
    /**
     * Enrich artifact with metadata
     */
    private CodeArtifact enrichArtifactWithMetadata(
            CodeArtifact artifact,
            DeveloperLLMService.TokenUsage usage) {
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("artifactId", UUID.randomUUID().toString());
        metadata.put("generatedAt", LocalDateTime.now().toString());
        metadata.put("llmTokens", String.valueOf(usage.totalTokens()));
        metadata.put("llmCost", String.valueOf(usage.estimatedCost()));
        metadata.put("agentType", "DeveloperAgent");
        metadata.put("version", "1.0");
        metadata.put("fileCount", String.valueOf(artifact.getSourceFiles().size()));
        metadata.put("totalLOC", String.valueOf(artifact.getTotalLinesOfCode()));
        
        return artifact.withMetadata(metadata)
                      .withArtifactId(metadata.get("artifactId"))
                      .withCreatedAt(LocalDateTime.now());
    }
    
    /**
     * Store code artifact in vector memory
     */
    private void storeInVectorMemory(CodeArtifact artifact) {
        try {
            // Create summary content for embedding
            String content = buildArtifactSummary(artifact);
            
            // Prepare metadata
            Map<String, String> metadata = new HashMap<>();
            metadata.put("artifactId", artifact.getArtifactId());
            metadata.put("projectTitle", artifact.getProjectTitle());
            metadata.put("fileCount", String.valueOf(artifact.getSourceFiles().size()));
            metadata.put("primaryLanguage", 
                artifact.getSourceFiles().isEmpty() ? "unknown" : 
                artifact.getSourceFiles().get(0).getLanguage());
            
            codeMemory.storeCode(artifact.getArtifactId(), content, metadata);
            log.debug("Stored code artifact {} in vector memory", artifact.getArtifactId());
            
        } catch (Exception e) {
            log.warn("Failed to store code in vector memory (non-critical)", e);
        }
    }
    
    /**
     * Build summary content for vector storage
     */
    private String buildArtifactSummary(CodeArtifact artifact) {
        StringBuilder content = new StringBuilder();
        content.append("Project: ").append(artifact.getProjectTitle()).append("\n\n");
        
        content.append("Files:\n");
        artifact.getSourceFiles().forEach(file -> {
            content.append("- ").append(file.getFilePath()).append(" (")
                   .append(file.getLineCount()).append(" lines)\n");
        });
        
        content.append("\nDependencies:\n");
        artifact.getDependencies().forEach(dep -> {
            content.append("- ").append(dep.toMavenFormat()).append("\n");
        });
        
        if (artifact.getBuildConfig() != null) {
            content.append("\nBuild: ").append(artifact.getBuildConfig().getBuildTool())
                   .append(" (Java ").append(artifact.getBuildConfig().getJavaVersion()).append(")\n");
        }
        
        return content.toString();
    }
    
    /**
     * Custom exceptions
     */
    public static class CodeGenerationException extends RuntimeException {
        public CodeGenerationException(String message) {
            super(message);
        }
        public CodeGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    public static class CodeValidationException extends RuntimeException {
        public CodeValidationException(String message) {
            super(message);
        }
    }
    
    public static class PlanNotFoundException extends RuntimeException {
        public PlanNotFoundException(String message) {
            super(message);
        }
    }
    
    public static class ArchitectureNotFoundException extends RuntimeException {
        public ArchitectureNotFoundException(String message) {
            super(message);
        }
    }
}
