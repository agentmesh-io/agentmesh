package com.therighthandapp.agentmesh.events;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Event published when the project passes all quality gates and is ready for deployment.
 * Triggers deployment pipeline or notifies stakeholders.
 */
public record DeploymentReadyEvent(
    String projectId,
    String projectName,
    String pullRequestUrl,
    Map<String, Object> qualityMetrics,
    boolean allTestsPassed,
    double testCoverage,
    LocalDateTime readyAt,
    String correlationId
) {
    public static DeploymentReadyEvent of(
        String projectId,
        String projectName,
        String pullRequestUrl,
        Map<String, Object> qualityMetrics,
        boolean allTestsPassed,
        double testCoverage,
        String correlationId
    ) {
        return new DeploymentReadyEvent(
            projectId,
            projectName,
            pullRequestUrl,
            qualityMetrics,
            allTestsPassed,
            testCoverage,
            LocalDateTime.now(),
            correlationId
        );
    }
}
