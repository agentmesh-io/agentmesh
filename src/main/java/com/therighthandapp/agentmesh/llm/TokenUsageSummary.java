package com.therighthandapp.agentmesh.llm;

import java.time.Instant;

/**
 * Summary of token usage and costs for a tenant/project over a time period
 */
public class TokenUsageSummary {
    private final String tenantId;
    private final String projectId;
    private final Instant periodStart;
    private final Instant periodEnd;
    private final long totalTokens;
    private final double totalCost;

    public TokenUsageSummary(String tenantId, String projectId,
                           Instant periodStart, Instant periodEnd,
                           long totalTokens, double totalCost) {
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.totalTokens = totalTokens;
        this.totalCost = totalCost;
    }

    // Getters
    public String getTenantId() { return tenantId; }
    public String getProjectId() { return projectId; }
    public Instant getPeriodStart() { return periodStart; }
    public Instant getPeriodEnd() { return periodEnd; }
    public long getTotalTokens() { return totalTokens; }
    public double getTotalCost() { return totalCost; }

    @Override
    public String toString() {
        return String.format("TokenUsageSummary{tenant=%s, project=%s, tokens=%d, cost=$%.2f, period=%s to %s}",
            tenantId, projectId, totalTokens, totalCost, periodStart, periodEnd);
    }
}

