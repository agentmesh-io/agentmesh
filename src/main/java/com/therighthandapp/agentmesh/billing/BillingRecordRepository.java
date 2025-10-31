package com.therighthandapp.agentmesh.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface BillingRecordRepository extends JpaRepository<BillingRecord, Long> {

    List<BillingRecord> findByTenantIdAndTimestampBetween(
        String tenantId, Instant start, Instant end);

    List<BillingRecord> findByTenantIdAndProjectIdAndTimestampBetween(
        String tenantId, String projectId, Instant start, Instant end);

    @Query("SELECT SUM(r.amount) FROM BillingRecord r " +
           "WHERE r.tenantId = :tenantId AND r.timestamp BETWEEN :start AND :end")
    Double sumAmountByTenantAndPeriod(
        @Param("tenantId") String tenantId,
        @Param("start") Instant start,
        @Param("end") Instant end);

    @Query("SELECT SUM(r.amount) FROM BillingRecord r " +
           "WHERE r.tenantId = :tenantId AND r.projectId = :projectId " +
           "AND r.timestamp BETWEEN :start AND :end")
    Double sumAmountByProjectAndPeriod(
        @Param("tenantId") String tenantId,
        @Param("projectId") String projectId,
        @Param("start") Instant start,
        @Param("end") Instant end);

    @Query("SELECT COUNT(r) FROM BillingRecord r " +
           "WHERE r.tenantId = :tenantId AND r.billingType = 'OUTCOME' " +
           "AND r.success = true AND r.timestamp BETWEEN :start AND :end")
    Long countSuccessfulOutcomes(
        @Param("tenantId") String tenantId,
        @Param("start") Instant start,
        @Param("end") Instant end);
}

