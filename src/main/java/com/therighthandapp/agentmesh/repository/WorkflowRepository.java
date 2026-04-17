package com.therighthandapp.agentmesh.repository;

import com.therighthandapp.agentmesh.model.Workflow;
import com.therighthandapp.agentmesh.model.Workflow.WorkflowStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Workflow entity persistence.
 */
@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, String> {

    /**
     * Find workflows by status
     */
    List<Workflow> findByStatus(WorkflowStatus status);

    /**
     * Find workflows by status with pagination
     */
    Page<Workflow> findByStatus(WorkflowStatus status, Pageable pageable);

    /**
     * Find workflows by tenant
     */
    List<Workflow> findByTenantId(String tenantId);

    /**
     * Find workflows by tenant with pagination
     */
    Page<Workflow> findByTenantId(String tenantId, Pageable pageable);

    /**
     * Find workflows by tenant and status
     */
    List<Workflow> findByTenantIdAndStatus(String tenantId, WorkflowStatus status);

    /**
     * Find recent workflows ordered by start time
     */
    List<Workflow> findTop10ByOrderByStartedAtDesc();

    /**
     * Find running workflows (for monitoring)
     */
    @Query("SELECT w FROM Workflow w WHERE w.status = 'RUNNING' ORDER BY w.startedAt DESC")
    List<Workflow> findRunningWorkflows();

    /**
     * Count workflows by status
     */
    long countByStatus(WorkflowStatus status);

    /**
     * Find workflows started after a certain time
     */
    List<Workflow> findByStartedAtAfter(Instant time);

    /**
     * Find workflows by project name (partial match)
     */
    @Query("SELECT w FROM Workflow w WHERE LOWER(w.projectName) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Workflow> findByProjectNameContaining(@Param("name") String name);

    /**
     * Get workflow statistics
     */
    @Query("SELECT w.status, COUNT(w) FROM Workflow w GROUP BY w.status")
    List<Object[]> getWorkflowStatusCounts();
}

