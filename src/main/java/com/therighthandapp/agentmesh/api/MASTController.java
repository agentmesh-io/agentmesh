package com.therighthandapp.agentmesh.api;

import com.therighthandapp.agentmesh.mast.MASTFailureMode;
import com.therighthandapp.agentmesh.mast.MASTValidator;
import com.therighthandapp.agentmesh.mast.MASTViolation;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API for MAST violation monitoring and agent health.
 *
 * <p>RBAC (M13.2): readable by any authenticated user (admin / developer / viewer).
 */
@RestController
@RequestMapping("/api/mast")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"})
@PreAuthorize("@rbac.any()")
public class MASTController {

    private final MASTValidator mastValidator;

    public MASTController(MASTValidator mastValidator) {
        this.mastValidator = mastValidator;
    }

    /**
     * Get all recent violations (last 24 hours)
     */
    @GetMapping("/violations/recent")
    public ResponseEntity<List<ViolationDTO>> getRecentViolations() {
        List<MASTViolation> violations = mastValidator.getRecentViolations();
        List<ViolationDTO> dtos = violations.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get unresolved violations
     */
    @GetMapping("/violations/unresolved")
    public ResponseEntity<List<ViolationDTO>> getUnresolvedViolations() {
        List<MASTViolation> violations = mastValidator.getUnresolvedViolations();
        List<ViolationDTO> dtos = violations.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get violations for a specific agent
     */
    @GetMapping("/violations/agent/{agentId}")
    public ResponseEntity<List<ViolationDTO>> getViolationsByAgent(@PathVariable String agentId) {
        List<MASTViolation> violations = mastValidator.getRecentViolations().stream()
                .filter(v -> agentId.equals(v.getAgentId()))
                .collect(Collectors.toList());
        List<ViolationDTO> dtos = violations.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Get failure mode statistics
     */
    @GetMapping("/statistics/failure-modes")
    public ResponseEntity<Map<String, Long>> getFailureModeStats() {
        Map<MASTFailureMode, Long> stats = mastValidator.getFailureModeStats();
        Map<String, Long> response = stats.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey().getCode(),
                        Map.Entry::getValue
                ));
        return ResponseEntity.ok(response);
    }

    /**
     * Get agent health score
     */
    @GetMapping("/health/{agentId}")
    public ResponseEntity<HealthScoreDTO> getAgentHealth(@PathVariable String agentId) {
        double score = mastValidator.getAgentHealthScore(agentId);
        String status = score >= 80 ? "HEALTHY" : score >= 50 ? "WARNING" : "CRITICAL";

        HealthScoreDTO dto = new HealthScoreDTO(agentId, score, status);
        return ResponseEntity.ok(dto);
    }

    /**
     * Resolve a violation
     */
    @PostMapping("/violations/{violationId}/resolve")
    public ResponseEntity<Void> resolveViolation(
            @PathVariable Long violationId,
            @RequestBody ResolveRequest request) {

        mastValidator.resolveViolation(violationId, request.getResolution());
        return ResponseEntity.ok().build();
    }

    /**
     * Get all MAST failure mode definitions
     */
    @GetMapping("/failure-modes")
    public ResponseEntity<List<FailureModeDTO>> getFailureModes() {
        List<FailureModeDTO> modes = java.util.Arrays.stream(MASTFailureMode.values())
                .map(fm -> new FailureModeDTO(
                        fm.getCode(),
                        fm.getName(),
                        fm.getDescription(),
                        fm.getMitigationStrategy(),
                        fm.getCategory().name()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(modes);
    }

    // DTOs
    private ViolationDTO toDTO(MASTViolation violation) {
        return new ViolationDTO(
                violation.getId(),
                violation.getAgentId(),
                violation.getFailureMode().getCode(),
                violation.getFailureMode().getName(),
                violation.getTaskId(),
                violation.getEvidence(),
                violation.getDetectedAt().toString(),
                violation.getSeverity(),
                violation.isResolved(),
                violation.getResolution()
        );
    }

    public static class ViolationDTO {
        private final Long id;
        private final String agentId;
        private final String failureModeCode;
        private final String failureModeName;
        private final String taskId;
        private final String evidence;
        private final String detectedAt;
        private final String severity;
        private final boolean resolved;
        private final String resolution;

        public ViolationDTO(Long id, String agentId, String failureModeCode, String failureModeName,
                           String taskId, String evidence, String detectedAt, String severity,
                           boolean resolved, String resolution) {
            this.id = id;
            this.agentId = agentId;
            this.failureModeCode = failureModeCode;
            this.failureModeName = failureModeName;
            this.taskId = taskId;
            this.evidence = evidence;
            this.detectedAt = detectedAt;
            this.severity = severity;
            this.resolved = resolved;
            this.resolution = resolution;
        }

        // Getters
        public Long getId() { return id; }
        public String getAgentId() { return agentId; }
        public String getFailureModeCode() { return failureModeCode; }
        public String getFailureModeName() { return failureModeName; }
        public String getTaskId() { return taskId; }
        public String getEvidence() { return evidence; }
        public String getDetectedAt() { return detectedAt; }
        public String getSeverity() { return severity; }
        public boolean isResolved() { return resolved; }
        public String getResolution() { return resolution; }
    }

    public static class HealthScoreDTO {
        private final String agentId;
        private final double score;
        private final String status;

        public HealthScoreDTO(String agentId, double score, String status) {
            this.agentId = agentId;
            this.score = score;
            this.status = status;
        }

        public String getAgentId() { return agentId; }
        public double getScore() { return score; }
        public String getStatus() { return status; }
    }

    public static class ResolveRequest {
        private String resolution;

        public String getResolution() { return resolution; }
        public void setResolution(String resolution) { this.resolution = resolution; }
    }

    public static class FailureModeDTO {
        private final String code;
        private final String name;
        private final String description;
        private final String mitigation;
        private final String category;

        public FailureModeDTO(String code, String name, String description, String mitigation, String category) {
            this.code = code;
            this.name = name;
            this.description = description;
            this.mitigation = mitigation;
            this.category = category;
        }

        public String getCode() { return code; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getMitigation() { return mitigation; }
        public String getCategory() { return category; }
    }
}

