package com.therighthandapp.agentmesh.agents.developer.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.therighthandapp.agentmesh.agents.developer.domain.CodeArtifact;
import com.therighthandapp.agentmesh.agents.developer.ports.CodeRepository;
import com.therighthandapp.agentmesh.blackboard.BlackboardEntry;
import com.therighthandapp.agentmesh.blackboard.BlackboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Infrastructure Adapter: Blackboard Code Repository
 * 
 * Stores code artifacts in the Blackboard with type "CODE"
 */
@Slf4j
@RequiredArgsConstructor
public class BlackboardCodeRepository implements CodeRepository {
    
    private static final String AGENT_ID = "developer-agent";
    private static final String ENTRY_TYPE = "CODE";
    
    private final BlackboardService blackboardService;
    private final ObjectMapper objectMapper;
    
    @Override
    public CodeArtifact save(CodeArtifact artifact) {
        log.debug("Saving code artifact to blackboard: {}", artifact.getProjectTitle());
        
        try {
            // Generate ID if not present
            CodeArtifact artifactToSave = artifact.getArtifactId() == null 
                ? artifact.withArtifactId(UUID.randomUUID().toString())
                : artifact;
            
            // Serialize to JSON
            String jsonContent = objectMapper.writeValueAsString(artifactToSave);
            
            // Post to Blackboard
            BlackboardEntry entry = blackboardService.post(
                AGENT_ID,
                ENTRY_TYPE,
                "Code Artifact: " + artifactToSave.getProjectTitle(),
                jsonContent
            );
            
            log.info("Code artifact saved to Blackboard: artifactId={}, entryId={}", 
                     artifactToSave.getArtifactId(), entry.getId());
            
            return artifactToSave;
            
        } catch (Exception e) {
            log.error("Failed to save code artifact to Blackboard", e);
            throw new RuntimeException("Failed to save code artifact", e);
        }
    }
    
    @Override
    public Optional<CodeArtifact> findById(String artifactId) {
        log.debug("Finding code artifact by ID: {}", artifactId);
        
        try {
            List<BlackboardEntry> entries = blackboardService.readByType(ENTRY_TYPE);
            
            for (BlackboardEntry entry : entries) {
                try {
                    CodeArtifact artifact = objectMapper.readValue(
                        entry.getContent(), 
                        CodeArtifact.class
                    );
                    
                    if (artifactId.equals(artifact.getArtifactId())) {
                        log.debug("Found code artifact: {}", artifactId);
                        return Optional.of(artifact);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse Blackboard entry: {}", entry.getId(), e);
                }
            }
            
            log.debug("Code artifact not found: {}", artifactId);
            return Optional.empty();
            
        } catch (Exception e) {
            log.error("Failed to find code artifact", e);
            return Optional.empty();
        }
    }
    
    @Override
    public Optional<CodeArtifact> findLatestByPlanId(String planId) {
        log.debug("Finding latest code artifact for plan: {}", planId);
        
        try {
            List<BlackboardEntry> entries = blackboardService.readByType(ENTRY_TYPE);
            CodeArtifact latestArtifact = null;
            
            for (BlackboardEntry entry : entries) {
                try {
                    CodeArtifact artifact = objectMapper.readValue(
                        entry.getContent(), 
                        CodeArtifact.class
                    );
                    
                    if (planId.equals(artifact.getPlanId())) {
                        if (latestArtifact == null || 
                            artifact.getCreatedAt().isAfter(latestArtifact.getCreatedAt())) {
                            latestArtifact = artifact;
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse Blackboard entry: {}", entry.getId(), e);
                }
            }
            
            if (latestArtifact != null) {
                log.debug("Found latest code artifact for plan {}: {}", planId, latestArtifact.getArtifactId());
            }
            
            return Optional.ofNullable(latestArtifact);
            
        } catch (Exception e) {
            log.error("Failed to find latest code artifact for plan: {}", planId, e);
            return Optional.empty();
        }
    }
    
    @Override
    public List<CodeArtifact> findByProjectId(String projectId) {
        throw new UnsupportedOperationException("findByProjectId not yet implemented");
    }
}
