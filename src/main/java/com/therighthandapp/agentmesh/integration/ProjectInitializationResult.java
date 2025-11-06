package com.therighthandapp.agentmesh.integration;

import java.util.Map;

/**
 * Result of project initialization from Auto-BADS SRS data
 */
public record ProjectInitializationResult(
    boolean success,
    String projectId,
    String projectKey,
    String tenantId,
    String githubRepoUrl,
    String correlationId,
    String errorMessage,
    Map<String, Object> metadata
) {
    
    /**
     * Create a successful result
     */
    public static ProjectInitializationResult success(
            String projectId, 
            String projectKey, 
            String tenantId,
            String githubRepoUrl, 
            String correlationId, 
            Map<String, Object> metadata) {
        return new ProjectInitializationResult(
                true, projectId, projectKey, tenantId, 
                githubRepoUrl, correlationId, null, metadata);
    }
    
    /**
     * Create a failure result
     */
    public static ProjectInitializationResult failure(String correlationId, String errorMessage) {
        return new ProjectInitializationResult(
                false, null, null, null, 
                null, correlationId, errorMessage, null);
    }
}
