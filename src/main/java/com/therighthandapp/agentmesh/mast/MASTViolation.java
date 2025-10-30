package com.therighthandapp.agentmesh.mast;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Records MAST failure mode violations detected during agent execution.
 * Used for monitoring, alerting, and continuous improvement.
 */
@Entity
@Table(name = "mast_violations")
public class MASTViolation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String agentId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private MASTFailureMode failureMode;

    @Column(nullable = false)
    private String taskId; // Reference to Blackboard entry or workflow execution

    @Lob
    @Column(columnDefinition = "TEXT")
    private String evidence; // Detailed evidence of the violation

    @Column(nullable = false)
    private Instant detectedAt;

    @Column
    private String severity; // "LOW", "MEDIUM", "HIGH", "CRITICAL"

    @Column
    private boolean resolved;

    @Column
    private String resolution; // How the violation was resolved

    public MASTViolation() {
        this.detectedAt = Instant.now();
        this.resolved = false;
    }

    public MASTViolation(String agentId, MASTFailureMode failureMode, String taskId, String evidence) {
        this();
        this.agentId = agentId;
        this.failureMode = failureMode;
        this.taskId = taskId;
        this.evidence = evidence;
        this.severity = determineSeverity(failureMode);
    }

    private String determineSeverity(MASTFailureMode mode) {
        return switch (mode.getCategory()) {
            case SPECIFICATION_ISSUES -> "HIGH";
            case INTER_AGENT_MISALIGNMENT -> "CRITICAL";
            case TASK_VERIFICATION -> "MEDIUM";
            default -> "LOW";
        };
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public MASTFailureMode getFailureMode() {
        return failureMode;
    }

    public void setFailureMode(MASTFailureMode failureMode) {
        this.failureMode = failureMode;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getEvidence() {
        return evidence;
    }

    public void setEvidence(String evidence) {
        this.evidence = evidence;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(Instant detectedAt) {
        this.detectedAt = detectedAt;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public boolean isResolved() {
        return resolved;
    }

    public void setResolved(boolean resolved) {
        this.resolved = resolved;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }
}

