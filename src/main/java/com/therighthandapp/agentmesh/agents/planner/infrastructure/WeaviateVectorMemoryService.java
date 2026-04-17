package com.therighthandapp.agentmesh.agents.planner.infrastructure;

import com.therighthandapp.agentmesh.agents.planner.ports.VectorMemoryService;
import com.therighthandapp.agentmesh.memory.MemoryArtifact;
import com.therighthandapp.agentmesh.memory.WeaviateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Infrastructure Adapter: Weaviate Vector Memory Service
 * 
 * Implements VectorMemoryService by delegating to WeaviateService.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeaviateVectorMemoryService implements VectorMemoryService {
    
    private final WeaviateService weaviateService;
    
    @Override
    public List<SimilarPlan> searchSimilarPlans(String query, int limit) {
        log.debug("Searching for similar plans: query='{}', limit={}", query, limit);
        
        try {
            // Use semantic search to find similar execution plans
            List<MemoryArtifact> artifacts = weaviateService.semanticSearch(query, limit);
            
            // Convert to SimilarPlan records
            List<SimilarPlan> similarPlans = artifacts.stream()
                .filter(artifact -> "PLAN".equals(artifact.getArtifactType()) || 
                                   "ExecutionPlan".equals(artifact.getArtifactType()))
                .map(this::toSimilarPlan)
                .collect(Collectors.toList());
            
            log.info("Found {} similar plans", similarPlans.size());
            return similarPlans;
            
        } catch (Exception e) {
            log.warn("Error searching for similar plans (non-critical): {}", e.getMessage());
            return List.of(); // Return empty list on error
        }
    }
    
    @Override
    public void storePlanEmbedding(String planId, String content, Map<String, Object> metadata) {
        log.debug("Storing plan embedding: planId={}", planId);
        
        try {
            // Create memory artifact
            MemoryArtifact artifact = new MemoryArtifact();
            artifact.setAgentId("planner-agent");
            artifact.setArtifactType("ExecutionPlan");
            artifact.setTitle(metadata.getOrDefault("projectTitle", "Execution Plan").toString());
            artifact.setContent(content);
            artifact.setProjectId(planId);
            artifact.setMetadata(metadata);
            
            // Store in Weaviate
            String artifactId = weaviateService.store(artifact);
            log.info("Stored plan embedding: artifactId={}", artifactId);
            
        } catch (Exception e) {
            log.error("Failed to store plan embedding (non-critical)", e);
            // Don't throw - this is not critical to plan generation
        }
    }
    
    /**
     * Convert MemoryArtifact to SimilarPlan
     */
    private SimilarPlan toSimilarPlan(MemoryArtifact artifact) {
        // Extract tech stack from metadata
        List<String> techStack = List.of();
        if (artifact.getMetadata() != null && artifact.getMetadata().containsKey("techStack")) {
            Object techStackObj = artifact.getMetadata().get("techStack");
            if (techStackObj instanceof List) {
                techStack = (List<String>) techStackObj;
            }
        }
        
        // Create summary from content (first 200 chars)
        String summary = artifact.getContent();
        if (summary != null && summary.length() > 200) {
            summary = summary.substring(0, 200) + "...";
        }
        
        return new SimilarPlan(
            artifact.getProjectId(),
            artifact.getTitle(),
            summary,
            techStack,
            0.85 // Default similarity score (Weaviate provides this in search results)
        );
    }
}
