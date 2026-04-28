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

    /**
     * Detailed evidence of the violation.
     *
     * <p><b>M13.3 c2 — F2 fix.</b> Previously {@code @Lob @Column(columnDefinition = "TEXT")},
     * which on PostgreSQL caused Hibernate to bind the column to {@code ClobJdbcType}
     * and read it through {@code LargeObjectManager}, which throws
     * {@code "Large Objects may not be used in auto-commit mode"} when the
     * surrounding query is not wrapped in a transaction. The MASTValidator
     * read methods ({@code getRecentViolations}, {@code getUnresolvedViolations})
     * are intentionally non-transactional read paths, so any controller call
     * to {@code MASTController#getRecentViolations} / {@code getUnresolvedViolations}
     * / {@code getViolationsByAgent} blew up with HTTP 500 (surfaced as 403 by
     * Spring's exception handling) whenever there was at least one row in
     * {@code mast_violations}. H2 (used by the test slice) silently tolerates
     * the same code path, so the bug only manifested on the dev-postgres
     * runtime and was missed until the M13.3 c1 verification matrix.
     *
     * <p>The underlying PostgreSQL column is already {@code text} (Flyway V1
     * has always declared it that way), so dropping {@code @Lob} is a pure
     * Hibernate-side change — Hibernate now uses {@code VarcharJdbcType} and
     * reads the value as a regular String at result-set time, no transaction
     * required, no schema migration required. See
     * {@code docs/ACCEPTANCE_M13.3.md} Finding F2 and the regression test in
     * {@code MASTViolationLobMappingTest}.
     */
    @Column(columnDefinition = "TEXT")
    private String evidence;

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

