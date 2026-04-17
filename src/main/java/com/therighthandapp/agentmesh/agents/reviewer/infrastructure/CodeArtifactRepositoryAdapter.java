package com.therighthandapp.agentmesh.agents.reviewer.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.therighthandapp.agentmesh.agents.developer.domain.CodeArtifact;
import com.therighthandapp.agentmesh.agents.reviewer.ports.CodeRepository;
import com.therighthandapp.agentmesh.blackboard.BlackboardEntry;
import com.therighthandapp.agentmesh.blackboard.BlackboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Infrastructure Adapter: Code Artifact Repository (for ReviewerAgent)
 * 
 * Hexagonal Architecture: This is an adapter that implements the CodeRepository port.
 * Retrieves code artifacts from the Blackboard.
 */
@Slf4j
@RequiredArgsConstructor
public class CodeArtifactRepositoryAdapter implements CodeRepository {
    
    private final BlackboardService blackboardService;
    private final ObjectMapper objectMapper;
    
    private static final String ENTRY_TYPE = "CODE";
    
    @Override
    public Optional<CodeArtifact> findById(String codeArtifactId) {
        try {
            log.debug("Finding code artifact by ID: {}", codeArtifactId);
            
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
                    
                    CodeArtifact codeArtifact = objectMapper.readValue(content, CodeArtifact.class);
                    
                    if (codeArtifactId.equals(codeArtifact.getArtifactId())) {
                        log.debug("Found code artifact: {} with {} files", 
                                codeArtifactId, 
                                codeArtifact.getSourceFiles() != null ? codeArtifact.getSourceFiles().size() : 0);
                        return Optional.of(codeArtifact);
                    }
                } catch (Exception e) {
                    log.debug("Failed to parse entry as CodeArtifact: {}", e.getMessage());
                }
            }
            
            log.warn("Code artifact not found: {}", codeArtifactId);
            return Optional.empty();
            
        } catch (Exception e) {
            log.error("Failed to find code artifact {}: {}", codeArtifactId, e.getMessage(), e);
            return Optional.empty();
        }
    }
}
