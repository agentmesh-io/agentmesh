package com.therighthandapp.agentmesh.mast;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

public class MASTFailureModeTest {

    @Test
    public void testAllFailureModesAreDefined() {
        MASTFailureMode[] modes = MASTFailureMode.values();
        assertThat(modes).hasSize(14);
    }

    @Test
    public void testCategoryMapping() {
        assertThat(MASTFailureMode.FM_1_1_SPECIFICATION_VIOLATION.getCategory())
                .isEqualTo(MASTFailureMode.Category.SPECIFICATION_ISSUES);

        assertThat(MASTFailureMode.FM_2_1_COORDINATION_FAILURE.getCategory())
                .isEqualTo(MASTFailureMode.Category.INTER_AGENT_MISALIGNMENT);

        assertThat(MASTFailureMode.FM_3_1_OUTPUT_QUALITY.getCategory())
                .isEqualTo(MASTFailureMode.Category.TASK_VERIFICATION);
    }

    @Test
    public void testFailureModeProperties() {
        MASTFailureMode mode = MASTFailureMode.FM_1_3_STEP_REPETITION;

        assertThat(mode.getCode()).isEqualTo("FM-1.3");
        assertThat(mode.getName()).isEqualTo("Step Repetition / Looping");
        assertThat(mode.getDescription()).contains("repeats the same steps");
        assertThat(mode.getMitigationStrategy()).contains("timeout");
    }

    @Test
    public void testSpecificationIssuesCategory() {
        List<MASTFailureMode> specIssues = Arrays.asList(
                MASTFailureMode.FM_1_1_SPECIFICATION_VIOLATION,
                MASTFailureMode.FM_1_2_ROLE_VIOLATION,
                MASTFailureMode.FM_1_3_STEP_REPETITION,
                MASTFailureMode.FM_1_4_CONTEXT_LOSS
        );

        for (MASTFailureMode mode : specIssues) {
            assertThat(mode.getCategory())
                    .isEqualTo(MASTFailureMode.Category.SPECIFICATION_ISSUES);
        }
    }

    @Test
    public void testInterAgentMisalignmentCategory() {
        List<MASTFailureMode> misalignment = Arrays.asList(
                MASTFailureMode.FM_2_1_COORDINATION_FAILURE,
                MASTFailureMode.FM_2_2_COMMUNICATION_BREAKDOWN,
                MASTFailureMode.FM_2_3_DEPENDENCY_VIOLATION,
                MASTFailureMode.FM_2_4_STATE_INCONSISTENCY
        );

        for (MASTFailureMode mode : misalignment) {
            assertThat(mode.getCategory())
                    .isEqualTo(MASTFailureMode.Category.INTER_AGENT_MISALIGNMENT);
        }
    }

    @Test
    public void testTaskVerificationCategory() {
        List<MASTFailureMode> verification = Arrays.asList(
                MASTFailureMode.FM_3_1_OUTPUT_QUALITY,
                MASTFailureMode.FM_3_2_INCOMPLETE_OUTPUT,
                MASTFailureMode.FM_3_3_FORMAT_VIOLATION,
                MASTFailureMode.FM_3_4_HALLUCINATION,
                MASTFailureMode.FM_3_5_TIMEOUT,
                MASTFailureMode.FM_3_6_TOOL_INVOCATION_FAILURE
        );

        for (MASTFailureMode mode : verification) {
            assertThat(mode.getCategory())
                    .isEqualTo(MASTFailureMode.Category.TASK_VERIFICATION);
        }
    }

    @Test
    public void testToString() {
        String description = MASTFailureMode.FM_1_1_SPECIFICATION_VIOLATION.toString();
        assertThat(description).contains("FM-1.1");
        assertThat(description).contains("Specification Violation");
    }
}

