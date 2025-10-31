package com.therighthandapp.agentmesh.llm;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Service for tracking and analyzing LLM token usage for billing
 */
@Service
public class TokenUsageTracker {

    private final TokenUsageRepository repository;

    public TokenUsageTracker(TokenUsageRepository repository) {
        this.repository = repository;
    }

    /**
     * Track a token usage event
     */
    @Transactional
    public void track(TokenUsageRecord record) {
        repository.save(record);
    }

    /**
     * Get usage summary for a tenant/project in a time period
     */
    public TokenUsageSummary getSummary(String tenantId, String projectId,
                                       Instant start, Instant end) {
        Long totalTokens;
        Double totalCost;

        if (projectId != null) {
            totalTokens = repository.sumTotalTokensByProjectAndPeriod(tenantId, projectId, start, end);
            totalCost = repository.sumCostByProjectAndPeriod(tenantId, projectId, start, end);
        } else {
            totalTokens = repository.sumTotalTokensByTenantAndPeriod(tenantId, start, end);
            totalCost = repository.sumCostByTenantAndPeriod(tenantId, start, end);
        }

        return new TokenUsageSummary(
            tenantId,
            projectId,
            start,
            end,
            totalTokens != null ? totalTokens : 0L,
            totalCost != null ? totalCost : 0.0
        );
    }

    /**
     * Get detailed usage records
     */
    public List<TokenUsageRecord> getRecords(String tenantId, String projectId,
                                            Instant start, Instant end) {
        if (projectId != null) {
            return repository.findByTenantIdAndProjectIdAndTimestampBetween(
                tenantId, projectId, start, end);
        } else {
            return repository.findByTenantIdAndTimestampBetween(
                tenantId, start, end);
        }
    }
}

