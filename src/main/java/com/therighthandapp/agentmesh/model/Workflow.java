package com.therighthandapp.agentmesh.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JPA Entity for persisting workflow execution state.
 * Replaces the in-memory HashMap in WorkflowController.
 */
@Entity
@Table(name = "workflows")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Workflow {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "project_name", nullable = false)
    private String projectName;

    @Column(name = "tenant_id")
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkflowStatus status;

    @Column(name = "current_phase")
    private String currentPhase;

    @Column(name = "progress")
    private Integer progress;

    @Column(name = "srs_content", columnDefinition = "TEXT")
    private String srsContent;

    @Column(name = "plan_id")
    private String planId;

    @Column(name = "architecture_id")
    private String architectureId;

    @Column(name = "code_id")
    private String codeId;

    @Column(name = "test_id")
    private String testId;

    @Column(name = "review_id")
    private String reviewId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "phases_json", columnDefinition = "TEXT")
    private String phasesJson;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "last_updated_at")
    private Instant lastUpdatedAt;

    @Version
    private Long version;

    public enum WorkflowStatus {
        PENDING,
        RUNNING,
        PAUSED,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) {
            startedAt = Instant.now();
        }
        lastUpdatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastUpdatedAt = Instant.now();
    }
}

