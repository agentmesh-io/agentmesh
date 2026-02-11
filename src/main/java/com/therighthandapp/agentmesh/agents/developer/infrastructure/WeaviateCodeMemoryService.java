package com.therighthandapp.agentmesh.agents.developer.infrastructure;

import com.therighthandapp.agentmesh.agents.developer.ports.CodeMemoryService;
import com.therighthandapp.agentmesh.memory.MemoryArtifact;
import com.therighthandapp.agentmesh.memory.WeaviateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Infrastructure Adapter: Weaviate Code Memory Service
 */
@Slf4j
@RequiredArgsConstructor
public class WeaviateCodeMemoryService implements CodeMemoryService {
    
    private final WeaviateService weaviateService;
    
    private static final String AGENT_ID = "developer-agent";
    private static final String ARTIFACT_TYPE = "CODE";
    
    @Override
    public List<SimilarCode> searchSimilarCode(String query, int limit) {
        log.debug("Searching for similar code: query={}, limit={}", query, limit);
        
        try {
            var results = weaviateService.multiVectorSearch(query, limit, AGENT_ID);
            
            List<SimilarCode> similarCode = new ArrayList<>();
            
            for (var artifact : results) {
                if (ARTIFACT_TYPE.equals(artifact.getArtifactType())) {
                    SimilarCode similar = new SimilarCode(
                        artifact.getId(),
                        artifact.getMetadata() != null ? 
                            String.valueOf(artifact.getMetadata().getOrDefault("fileName", "Unknown")) : "Unknown",
                        artifact.getMetadata() != null ? 
                            String.valueOf(artifact.getMetadata().getOrDefault("language", "java")) : "java",
                        artifact.getContent(),
                        0.85,
                        artifact.getMetadata() != null ? 
                            artifact.getMetadata().entrySet().stream()
                                .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    e -> String.valueOf(e.getValue())
                                )) : Map.of()
                    );
                    
                    similarCode.add(similar);
                }
            }
            
            log.info("Found {} similar code examples", similarCode.size());
            return similarCode;
            
        } catch (Exception e) {
            log.error("Failed to search for similar code", e);
            return List.of();
        }
    }
    
    @Override
    public void storeCode(String artifactId, String content, Map<String, String> metadata) {
        log.debug("Storing code in vector memory: {}", artifactId);
        
        try {
            MemoryArtifact artifact = new MemoryArtifact();
            artifact.setAgentId(AGENT_ID);
            artifact.setArtifactType(ARTIFACT_TYPE);
            artifact.setTitle("Code: " + metadata.getOrDefault("projectTitle", artifactId));
            artifact.setContent(content);
            artifact.setProjectId(artifactId);
            
            Map<String, Object> objectMetadata = new HashMap<>(metadata);
            objectMetadata.put("artifactType", ARTIFACT_TYPE);
            objectMetadata.put("agentId", AGENT_ID);
            artifact.setMetadata(objectMetadata);
            
            String storedId = weaviateService.store(artifact);
            log.info("Code stored in vector memory: {} (id: {})", artifactId, storedId);
            
        } catch (Exception e) {
            log.error("Failed to store code in vector memory", e);
        }
    }
}
