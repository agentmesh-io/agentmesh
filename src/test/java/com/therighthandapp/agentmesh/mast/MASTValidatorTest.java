package com.therighthandapp.agentmesh.mast;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public class MASTValidatorTest {

    @Autowired
    private MASTValidator mastValidator;

    @Autowired
    private MASTViolationRepository violationRepository;

    @BeforeEach
    public void setUp() {
        violationRepository.deleteAll();
    }

    @Test
    public void testRecordViolation() {
        MASTViolation violation = mastValidator.recordViolation(
                "test-agent",
                MASTFailureMode.FM_1_1_SPECIFICATION_VIOLATION,
                "task-123",
                "Agent did not follow spec"
        );

        assertThat(violation).isNotNull();
        assertThat(violation.getId()).isNotNull();
        assertThat(violation.getAgentId()).isEqualTo("test-agent");
        assertThat(violation.getFailureMode()).isEqualTo(MASTFailureMode.FM_1_1_SPECIFICATION_VIOLATION);
        assertThat(violation.isResolved()).isFalse();
    }

    @Test
    public void testResolveViolation() {
        MASTViolation violation = mastValidator.recordViolation(
                "test-agent",
                MASTFailureMode.FM_1_1_SPECIFICATION_VIOLATION,
                "task-123",
                "Test evidence"
        );

        mastValidator.resolveViolation(violation.getId(), "Fixed by regenerating code");

        MASTViolation resolved = violationRepository.findById(violation.getId()).orElseThrow();
        assertThat(resolved.isResolved()).isTrue();
        assertThat(resolved.getResolution()).isEqualTo("Fixed by regenerating code");
    }

    @Test
    public void testDetectLoop() {
        List<String> recentActions = Arrays.asList(
                "generate_code",
                "generate_code",
                "generate_code",
                "review_code"
        );

        boolean loopDetected = mastValidator.detectLoop("test-agent", "generate_code", recentActions);

        assertThat(loopDetected).isTrue();

        List<MASTViolation> violations = violationRepository.findByAgentId("test-agent");
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).getFailureMode()).isEqualTo(MASTFailureMode.FM_1_3_STEP_REPETITION);
    }

    @Test
    public void testDetectContextLoss() {
        List<String> requiredContext = Arrays.asList("SRS", "Plan", "Dependencies");
        List<String> actualContext = Arrays.asList("SRS", "Plan");

        boolean lossDetected = mastValidator.detectContextLoss(
                "test-agent", "task-123", requiredContext, actualContext
        );

        assertThat(lossDetected).isTrue();

        List<MASTViolation> violations = violationRepository.findByAgentId("test-agent");
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).getFailureMode()).isEqualTo(MASTFailureMode.FM_1_4_CONTEXT_LOSS);
        assertThat(violations.get(0).getEvidence()).contains("Dependencies");
    }

    @Test
    public void testDetectTimeout() {
        Instant startTime = Instant.now().minus(10, ChronoUnit.SECONDS);

        boolean timeoutDetected = mastValidator.detectTimeout(
                "test-agent", "task-123", startTime, 5
        );

        assertThat(timeoutDetected).isTrue();

        List<MASTViolation> violations = violationRepository.findByAgentId("test-agent");
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).getFailureMode()).isEqualTo(MASTFailureMode.FM_3_5_TIMEOUT);
    }

    @Test
    public void testValidateFormat() {
        String validCode = "public class MyClass { }";
        String invalidCode = "not valid code";

        boolean validResult = mastValidator.validateFormat(
                "test-agent", "task-123", validCode, ".*class.*"
        );
        assertThat(validResult).isTrue();

        boolean invalidResult = mastValidator.validateFormat(
                "test-agent", "task-456", invalidCode, ".*class.*"
        );
        assertThat(invalidResult).isFalse();

        List<MASTViolation> violations = violationRepository.findByAgentId("test-agent");
        assertThat(violations).hasSize(1);
    }

    @Test
    public void testGetRecentViolations() {
        mastValidator.recordViolation("agent1", MASTFailureMode.FM_1_1_SPECIFICATION_VIOLATION,
                "task1", "evidence1");
        mastValidator.recordViolation("agent2", MASTFailureMode.FM_3_1_OUTPUT_QUALITY,
                "task2", "evidence2");

        List<MASTViolation> recent = mastValidator.getRecentViolations();
        assertThat(recent).hasSize(2);
    }

    @Test
    public void testGetUnresolvedViolations() {
        MASTViolation v1 = mastValidator.recordViolation("agent1",
                MASTFailureMode.FM_1_1_SPECIFICATION_VIOLATION, "task1", "evidence1");
        MASTViolation v2 = mastValidator.recordViolation("agent2",
                MASTFailureMode.FM_3_1_OUTPUT_QUALITY, "task2", "evidence2");

        mastValidator.resolveViolation(v1.getId(), "Fixed");

        List<MASTViolation> unresolved = mastValidator.getUnresolvedViolations();
        assertThat(unresolved).hasSize(1);
        assertThat(unresolved.get(0).getId()).isEqualTo(v2.getId());
    }

    @Test
    public void testGetFailureModeStats() {
        mastValidator.recordViolation("agent1", MASTFailureMode.FM_1_1_SPECIFICATION_VIOLATION,
                "task1", "evidence1");
        mastValidator.recordViolation("agent2", MASTFailureMode.FM_1_1_SPECIFICATION_VIOLATION,
                "task2", "evidence2");
        mastValidator.recordViolation("agent3", MASTFailureMode.FM_3_1_OUTPUT_QUALITY,
                "task3", "evidence3");

        Map<MASTFailureMode, Long> stats = mastValidator.getFailureModeStats();

        assertThat(stats).hasSize(2);
        assertThat(stats.get(MASTFailureMode.FM_1_1_SPECIFICATION_VIOLATION)).isEqualTo(2L);
        assertThat(stats.get(MASTFailureMode.FM_3_1_OUTPUT_QUALITY)).isEqualTo(1L);
    }

    @Test
    public void testGetAgentHealthScore() {
        // Perfect health (no violations)
        double perfectScore = mastValidator.getAgentHealthScore("perfect-agent");
        assertThat(perfectScore).isEqualTo(100.0);

        // Some violations
        mastValidator.recordViolation("imperfect-agent",
                MASTFailureMode.FM_1_1_SPECIFICATION_VIOLATION, "task1", "evidence1");

        double scoreWithViolation = mastValidator.getAgentHealthScore("imperfect-agent");
        assertThat(scoreWithViolation).isLessThan(100.0);
        assertThat(scoreWithViolation).isGreaterThan(0.0);
    }
}

