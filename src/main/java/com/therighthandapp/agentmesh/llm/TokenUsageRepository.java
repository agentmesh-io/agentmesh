package com.therighthandapp.agentmesh.llm;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface TokenUsageRepository extends JpaRepository<TokenUsageRecord, Long> {

    List<TokenUsageRecord> findByTenantIdAndTimestampBetween(
        String tenantId, Instant start, Instant end);

    List<TokenUsageRecord> findByTenantIdAndProjectIdAndTimestampBetween(
        String tenantId, String projectId, Instant start, Instant end);

    @Query("SELECT SUM(r.totalTokens) FROM TokenUsageRecord r " +
           "WHERE r.tenantId = :tenantId AND r.timestamp BETWEEN :start AND :end")
    Long sumTotalTokensByTenantAndPeriod(
        @Param("tenantId") String tenantId,
        @Param("start") Instant start,
        @Param("end") Instant end);

    @Query("SELECT SUM(r.estimatedCost) FROM TokenUsageRecord r " +
           "WHERE r.tenantId = :tenantId AND r.timestamp BETWEEN :start AND :end")
    Double sumCostByTenantAndPeriod(
        @Param("tenantId") String tenantId,
        @Param("start") Instant start,
        @Param("end") Instant end);

    @Query("SELECT SUM(r.totalTokens) FROM TokenUsageRecord r " +
           "WHERE r.tenantId = :tenantId AND r.projectId = :projectId " +
           "AND r.timestamp BETWEEN :start AND :end")
    Long sumTotalTokensByProjectAndPeriod(
        @Param("tenantId") String tenantId,
        @Param("projectId") String projectId,
        @Param("start") Instant start,
        @Param("end") Instant end);

    @Query("SELECT SUM(r.estimatedCost) FROM TokenUsageRecord r " +
           "WHERE r.tenantId = :tenantId AND r.projectId = :projectId " +
           "AND r.timestamp BETWEEN :start AND :end")
    Double sumCostByProjectAndPeriod(
        @Param("tenantId") String tenantId,
        @Param("projectId") String projectId,
        @Param("start") Instant start,
        @Param("end") Instant end);
}

