package com.therighthandapp.agentmesh.agents.tester.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.therighthandapp.agentmesh.agents.developer.domain.CodeArtifact;
import com.therighthandapp.agentmesh.agents.tester.ports.CodeRepository;
import com.therighthandapp.agentmesh.blackboard.BlackboardEntry;
import com.therighthandapp.agentmesh.blackboard.BlackboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Infrastructure Adapter: Code Artifact Repository (for TesterAgent)
 * 
 * Retrieves code artifacts from Blackboard for test generation.
 * Uses qualifier to avoid conflicts with other CodeRepository beans.
 */
@Slf4j
@Qualifier("testerCodeRepository")
@RequiredArgsConstructor
public class CodeArtifactRepositoryAdapter implements CodeRepository {
    
    private static final String ENTRY_TYPE = "CODE";
    
    private final BlackboardService blackboardService;
    private final ObjectMapper objectMapper;
    
    @Override
    public Optional<CodeArtifact> findById(String codeArtifactId) {
        try {
            List<?> entries = blackboardService.readByType(ENTRY_TYPE);
            
            for (Object obj : entries) {
                try {
                    // Handle both BlackboardEntry objects and cached LinkedHashMaps
                    String content;
                    if (obj instanceof BlackboardEntry) {
                        BlackboardEntry entry = (BlackboardEntry) obj;
                        content = entry.getContent();
                    } else if (obj instanceof java.util.Map) {
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Object> map = (java.util.Map<String, Object>) obj;
                        content = (String) map.get("content");
                    } else {
                        log.warn("Unexpected object type in CODE entries: {}", obj.getClass());
                        continue;
                    }
                    
                    CodeArtifact artifact = objectMapper.readValue(content, CodeArtifact.class);
                    
                    if (artifact.getArtifactId().equals(codeArtifactId)) {
                        log.info("Found code artifact: {} (Project: {})",
                            codeArtifactId, artifact.getProjectTitle());
                        return Optional.of(artifact);
                    }
                } catch (Exception e) {
                    log.debug("Failed to parse entry as CodeArtifact: {}", e.getMessage());
                }
            }
            
            log.warn("Code artifact not found: {}", codeArtifactId);
            return Optional.empty();
            
        } catch (Exception e) {
            log.error("Failed to search for code artifact: {}", codeArtifactId, e);
            return Optional.empty();
        }
    }
}
