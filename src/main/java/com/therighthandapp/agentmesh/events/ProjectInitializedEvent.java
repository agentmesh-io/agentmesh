package com.therighthandapp.agentmesh.events;

import java.time.LocalDateTime;

/**
 * Event published when a new project is initialized in AgentMesh.
 * Triggers the SDLC workflow orchestration.
 */
public record ProjectInitializedEvent(
    String projectId,
    String projectKey,
    String projectName,
    String tenantId,
    String ideaId,
    String githubRepoUrl,
    LocalDateTime initializedAt,
    String correlationId
) {
    public static ProjectInitializedEvent of(String projectId, String projectName, String tenantId, String workflowId, String correlationId) {
        return new ProjectInitializedEvent(
            projectId,
            null, // projectKey
            projectName,
            tenantId,
            null, // ideaId
            null, // githubRepoUrl
            LocalDateTime.now(),
            correlationId
        );
    }
}
