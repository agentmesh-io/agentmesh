package com.therighthandapp.agentmesh.agents.developer.ports;

import com.therighthandapp.agentmesh.agents.developer.domain.CodeArtifact;

import java.util.List;
import java.util.Optional;

/**
 * Port (Interface): Repository for code artifacts
 * 
 * Hexagonal Architecture: Output port for persisting code artifacts
 */
public interface CodeRepository {
    
    /**
     * Save code artifact to storage
     */
    CodeArtifact save(CodeArtifact artifact);
    
    /**
     * Find artifact by ID
     */
    Optional<CodeArtifact> findById(String artifactId);
    
    /**
     * Find latest artifact for a plan
     */
    Optional<CodeArtifact> findLatestByPlanId(String planId);
    
    /**
     * Find all artifacts for a project
     */
    List<CodeArtifact> findByProjectId(String projectId);
}
