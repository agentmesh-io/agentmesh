package com.therighthandapp.agentmesh.blackboard;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BlackboardRepository extends JpaRepository<BlackboardEntry, Long> {
    List<BlackboardEntry> findByAgentId(String agentId);

    List<BlackboardEntry> findByEntryType(String entryType);

    @Query("SELECT b FROM BlackboardEntry b ORDER BY b.timestamp DESC")
    List<BlackboardEntry> findAllOrderedByTimestamp();

    @Query("SELECT b FROM BlackboardEntry b WHERE b.entryType = :entryType ORDER BY b.timestamp DESC")
    List<BlackboardEntry> findByEntryTypeOrderedByTimestamp(String entryType);

    // Multi-tenant aware queries
    List<BlackboardEntry> findByTenantIdAndProjectId(String tenantId, String projectId);

    List<BlackboardEntry> findByDataPartitionKey(String dataPartitionKey);

    List<BlackboardEntry> findByTenantIdAndProjectIdAndEntryType(
        String tenantId, String projectId, String entryType);

    List<BlackboardEntry> findByTenantIdAndProjectIdAndAgentId(
        String tenantId, String projectId, String agentId);

    Optional<BlackboardEntry> findByIdAndTenantIdAndProjectId(
        Long id, String tenantId, String projectId);

    @Query("SELECT e FROM BlackboardEntry e WHERE e.tenantId = :tenantId " +
           "AND e.projectId = :projectId ORDER BY e.timestamp DESC")
    List<BlackboardEntry> findRecentByTenantAndProject(
        @Param("tenantId") String tenantId,
        @Param("projectId") String projectId);

    long countByTenantIdAndProjectId(String tenantId, String projectId);
}
