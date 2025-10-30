package com.therighthandapp.agentmesh.mast;

import com.therighthandapp.agentmesh.metrics.AgentMeshMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for detecting and recording MAST failure mode violations.
 * This is the defensive layer that monitors agent behavior and enforces quality.
 */
@Service
public class MASTValidator {
    private static final Logger log = LoggerFactory.getLogger(MASTValidator.class);

    private final MASTViolationRepository violationRepository;

    @Autowired(required = false)
    private AgentMeshMetrics metrics;

    public MASTValidator(MASTViolationRepository violationRepository) {
        this.violationRepository = violationRepository;
    }

    /**
     * Record a MAST violation
     */
    @Transactional
    public MASTViolation recordViolation(String agentId, MASTFailureMode failureMode,
                                         String taskId, String evidence) {
        MASTViolation violation = new MASTViolation(agentId, failureMode, taskId, evidence);
        MASTViolation saved = violationRepository.save(violation);

        log.warn("MAST Violation detected: {} for agent {} on task {}",
                 failureMode.getCode(), agentId, taskId);

        // Record metrics
        if (metrics != null) {
            metrics.recordMASTViolation(failureMode);
        }

        return saved;
    }

    /**
     * Mark a violation as resolved
     */
    @Transactional
    public void resolveViolation(Long violationId, String resolution) {
        violationRepository.findById(violationId).ifPresent(v -> {
            v.setResolved(true);
            v.setResolution(resolution);
            violationRepository.save(v);
            log.info("MAST Violation {} resolved: {}", violationId, resolution);
        });
    }

    /**
     * Check for loop/repetition (FM-1.3)
     */
    public boolean detectLoop(String agentId, String currentAction, List<String> recentActions) {
        int repetitionCount = (int) recentActions.stream()
                .filter(action -> action.equals(currentAction))
                .count();

        if (repetitionCount >= 3) {
            recordViolation(agentId, MASTFailureMode.FM_1_3_STEP_REPETITION,
                          "current", "Action repeated " + repetitionCount + " times: " + currentAction);
            return true;
        }
        return false;
    }

    /**
     * Check for context loss (FM-1.4)
     */
    public boolean detectContextLoss(String agentId, String taskId,
                                     List<String> requiredContext, List<String> actualContext) {
        List<String> missingContext = requiredContext.stream()
                .filter(ctx -> !actualContext.contains(ctx))
                .collect(Collectors.toList());

        if (!missingContext.isEmpty()) {
            recordViolation(agentId, MASTFailureMode.FM_1_4_CONTEXT_LOSS,
                          taskId, "Missing context: " + String.join(", ", missingContext));
            return true;
        }
        return false;
    }

    /**
     * Check for timeout (FM-3.5)
     */
    public boolean detectTimeout(String agentId, String taskId,
                                 Instant startTime, long maxDurationSeconds) {
        long elapsed = ChronoUnit.SECONDS.between(startTime, Instant.now());

        if (elapsed > maxDurationSeconds) {
            recordViolation(agentId, MASTFailureMode.FM_3_5_TIMEOUT,
                          taskId, "Task exceeded timeout: " + elapsed + "s > " + maxDurationSeconds + "s");
            return true;
        }
        return false;
    }

    /**
     * Validate output format (FM-3.3)
     */
    public boolean validateFormat(String agentId, String taskId,
                                  String output, String expectedPattern) {
        if (output == null || !output.matches(expectedPattern)) {
            recordViolation(agentId, MASTFailureMode.FM_3_3_FORMAT_VIOLATION,
                          taskId, "Output does not match expected format: " + expectedPattern);
            return false;
        }
        return true;
    }

    /**
     * Get recent violations (last 24 hours)
     */
    public List<MASTViolation> getRecentViolations() {
        Instant yesterday = Instant.now().minus(24, ChronoUnit.HOURS);
        return violationRepository.findRecentViolations(yesterday);
    }

    /**
     * Get unresolved violations
     */
    public List<MASTViolation> getUnresolvedViolations() {
        return violationRepository.findByResolvedFalse();
    }

    /**
     * Get failure mode statistics
     */
    public Map<MASTFailureMode, Long> getFailureModeStats() {
        List<Object[]> results = violationRepository.findFailureModeFrequency();
        return results.stream()
                .collect(Collectors.toMap(
                        row -> (MASTFailureMode) row[0],
                        row -> (Long) row[1]
                ));
    }

    /**
     * Get health score for an agent (0-100)
     */
    public double getAgentHealthScore(String agentId) {
        List<MASTViolation> violations = violationRepository.findByAgentId(agentId);

        if (violations.isEmpty()) {
            return 100.0;
        }

        long unresolved = violations.stream().filter(v -> !v.isResolved()).count();
        long critical = violations.stream()
                .filter(v -> "CRITICAL".equals(v.getSeverity()))
                .count();

        // Score: 100 - (10 * unresolved) - (20 * critical)
        double score = 100.0 - (10.0 * unresolved) - (20.0 * critical);
        return Math.max(0, score);
    }
}

