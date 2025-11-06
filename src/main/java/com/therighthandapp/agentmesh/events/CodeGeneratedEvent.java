package com.therighthandapp.agentmesh.events;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Event published when code generation is completed by the Coder agent.
 * Contains metadata about generated files and repository location.
 */
public record CodeGeneratedEvent(
    String projectId,
    String projectName,
    String repositoryUrl,
    String commitSha,
    int filesGenerated,
    Map<String, Integer> languageStats,
    LocalDateTime generatedAt,
    String correlationId
) {
    public static CodeGeneratedEvent of(
        String projectId, 
        String projectName, 
        String repositoryUrl, 
        String commitSha,
        int filesGenerated,
        Map<String, Integer> languageStats,
        String correlationId
    ) {
        return new CodeGeneratedEvent(
            projectId,
            projectName,
            repositoryUrl,
            commitSha,
            filesGenerated,
            languageStats,
            LocalDateTime.now(),
            correlationId
        );
    }
}
