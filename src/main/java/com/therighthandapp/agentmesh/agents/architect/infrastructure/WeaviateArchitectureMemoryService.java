package com.therighthandapp.agentmesh.agents.architect.infrastructure;

import com.therighthandapp.agentmesh.agents.architect.domain.SystemArchitecture;
import com.therighthandapp.agentmesh.agents.architect.ports.ArchitectureMemoryService;
import com.therighthandapp.agentmesh.agents.architect.ports.ArchitectureMemoryService.SimilarArchitecture;
import com.therighthandapp.agentmesh.memory.MemoryArtifact;
import com.therighthandapp.agentmesh.memory.WeaviateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Infrastructure Adapter: Weaviate Architecture Memory Service
 * 
 * Stores and retrieves architecture patterns from Weaviate vector database.
 */
@Slf4j
@RequiredArgsConstructor
public class WeaviateArchitectureMemoryService implements ArchitectureMemoryService {
    
    private final WeaviateService weaviateService;
    
    private static final String AGENT_ID = "architect-agent";
    private static final String ARTIFACT_TYPE = "ARCHITECTURE";
    
    @Override
    public List<SimilarArchitecture> searchSimilarArchitectures(String query, int limit) {
        log.debug("Searching for similar architectures: query={}, limit={}", query, limit);
        
        try {
            // Search Weaviate for similar architecture patterns using multi-vector search
            var results = weaviateService.multiVectorSearch(query, limit, AGENT_ID);
            
            List<SimilarArchitecture> similarArchitectures = new ArrayList<>();
            
            for (var artifact : results) {
                // Check if this is an architecture artifact
                if (ARTIFACT_TYPE.equals(artifact.getArtifactType())) {
                    SimilarArchitecture similar = new SimilarArchitecture(
                        artifact.getId(),
                        artifact.getMetadata() != null ? 
                            String.valueOf(artifact.getMetadata().getOrDefault("projectTitle", "Unknown")) : "Unknown",
                        artifact.getMetadata() != null ? 
                            String.valueOf(artifact.getMetadata().getOrDefault("architectureStyle", "Unknown")) : "Unknown",
                        artifact.getContent(),
                        0.85, // Weaviate doesn't expose score directly
                        artifact.getMetadata() != null ? 
                            (Map<String, String>) artifact.getMetadata().entrySet().stream()
                                .collect(java.util.stream.Collectors.toMap(
                                    Map.Entry::getKey,
                                    e -> String.valueOf(e.getValue())
                                )) : Map.of()
                    );
                    
                    similarArchitectures.add(similar);
                }
            }
            
            log.info("Found {} similar architectures", similarArchitectures.size());
            return similarArchitectures;
            
        } catch (Exception e) {
            log.error("Failed to search for similar architectures", e);
            return List.of(); // Return empty list on failure
        }
    }
    
    @Override
    public void storeArchitecture(String architectureId, String content, Map<String, String> metadata) {
        log.debug("Storing architecture in vector memory: {}", architectureId);
        
        try {
            // Create memory artifact
            MemoryArtifact artifact = new MemoryArtifact();
            artifact.setAgentId(AGENT_ID);
            artifact.setArtifactType(ARTIFACT_TYPE);
            artifact.setTitle("Architecture: " + metadata.getOrDefault("projectTitle", architectureId));
            artifact.setContent(content);
            artifact.setProjectId(architectureId);
            
            // Convert String metadata to Object metadata
            Map<String, Object> objectMetadata = new HashMap<>(metadata);
            objectMetadata.put("artifactType", ARTIFACT_TYPE);
            objectMetadata.put("agentId", AGENT_ID);
            artifact.setMetadata(objectMetadata);
            
            // Store in Weaviate
            String storedId = weaviateService.store(artifact);
            log.info("Architecture stored in vector memory: {} (id: {})", architectureId, storedId);
            
        } catch (Exception e) {
            log.error("Failed to store architecture in vector memory", e);
            // Non-critical, don't throw
        }
    }
}
