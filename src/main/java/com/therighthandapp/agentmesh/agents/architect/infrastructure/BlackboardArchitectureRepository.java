package com.therighthandapp.agentmesh.agents.architect.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.therighthandapp.agentmesh.agents.architect.domain.SystemArchitecture;
import com.therighthandapp.agentmesh.agents.architect.ports.ArchitectureRepository;
import com.therighthandapp.agentmesh.blackboard.BlackboardEntry;
import com.therighthandapp.agentmesh.blackboard.BlackboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Infrastructure Adapter: Blackboard Architecture Repository
 * 
 * Implements ArchitectureRepository by storing architectures in the Blackboard.
 * Architectures are serialized as JSON and stored with type "ARCHITECTURE".
 */
@Slf4j
@RequiredArgsConstructor
public class BlackboardArchitectureRepository implements ArchitectureRepository {
    
    private final BlackboardService blackboardService;
    private final ObjectMapper objectMapper;
    
    private static final String AGENT_ID = "architect-agent";
    private static final String ENTRY_TYPE = "ARCHITECTURE";
    
    @Override
    public SystemArchitecture save(SystemArchitecture architecture) {
        log.debug("Saving architecture to blackboard: {}", architecture.getProjectTitle());
        
        try {
            // Ensure architecture has an ID
            SystemArchitecture architectureToSave = architecture.getArchitectureId() == null 
                ? architecture.withArchitectureId(UUID.randomUUID().toString())
                : architecture;
            
            // Serialize to JSON
            String contentJson = objectMapper.writeValueAsString(architectureToSave);
            
            // Store in blackboard
            BlackboardEntry entry = blackboardService.post(
                AGENT_ID,
                ENTRY_TYPE,
                "System Architecture: " + architectureToSave.getProjectTitle(),
                contentJson
            );
            
            log.info("Architecture saved to blackboard: architectureId={}, entryId={}", 
                     architectureToSave.getArchitectureId(), entry.getId());
            
            return architectureToSave;
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize architecture", e);
            throw new RuntimeException("Failed to save architecture", e);
        }
    }
    
    @Override
    public SystemArchitecture findById(String architectureId) {
        log.debug("Finding architecture by ID: {}", architectureId);
        
        try {
            // Query blackboard for ARCHITECTURE entries
            var entries = blackboardService.readByType(ENTRY_TYPE);
            
            // Find matching architecture
            for (BlackboardEntry entry : entries) {
                try {
                    SystemArchitecture architecture = objectMapper.readValue(
                        entry.getContent(), 
                        SystemArchitecture.class
                    );
                    
                    if (architectureId.equals(architecture.getArchitectureId())) {
                        log.debug("Found architecture: {}", architectureId);
                        return architecture;
                    }
                } catch (JsonProcessingException e) {
                    log.warn("Failed to parse blackboard entry as architecture: {}", entry.getId());
                }
            }
            
            throw new ArchitectureNotFoundException("Architecture not found: " + architectureId);
            
        } catch (ArchitectureNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error finding architecture", e);
            throw new RuntimeException("Failed to find architecture", e);
        }
    }
    
    @Override
    public SystemArchitecture findLatestByPlanId(String planId) {
        log.debug("Finding latest architecture for plan: {}", planId);
        
        try {
            var entries = blackboardService.readByType(ENTRY_TYPE);
            
            SystemArchitecture latestArchitecture = null;
            
            for (BlackboardEntry entry : entries) {
                try {
                    SystemArchitecture architecture = objectMapper.readValue(
                        entry.getContent(), 
                        SystemArchitecture.class
                    );
                    
                    if (planId.equals(architecture.getPlanId())) {
                        if (latestArchitecture == null || 
                            architecture.getCreatedAt().isAfter(latestArchitecture.getCreatedAt())) {
                            latestArchitecture = architecture;
                        }
                    }
                } catch (JsonProcessingException e) {
                    log.warn("Failed to parse blackboard entry: {}", entry.getId());
                }
            }
            
            if (latestArchitecture == null) {
                throw new ArchitectureNotFoundException("No architecture found for plan: " + planId);
            }
            
            log.debug("Found latest architecture for plan {}: {}", planId, latestArchitecture.getArchitectureId());
            return latestArchitecture;
            
        } catch (ArchitectureNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error finding architecture by plan", e);
            throw new RuntimeException("Failed to find architecture by plan", e);
        }
    }
    
    @Override
    public SystemArchitecture findByProjectId(String projectId) {
        log.debug("Finding architecture for project: {}", projectId);
        
        // Implementation would query by project metadata
        // For now, throw unsupported
        throw new UnsupportedOperationException("Finding by project ID not yet implemented");
    }
}
