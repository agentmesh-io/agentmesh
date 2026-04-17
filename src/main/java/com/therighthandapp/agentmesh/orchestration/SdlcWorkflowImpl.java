package com.therighthandapp.agentmesh.orchestration;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;

/**
 * Implementation of the SDLC workflow using Temporal.
 * Demonstrates the Generate → Test → Review → Debug self-correction loop.
 */
public class SdlcWorkflowImpl implements SdlcWorkflow {
    private static final Logger log = Workflow.getLogger(SdlcWorkflowImpl.class);

    private final AgentActivity activities;

    public SdlcWorkflowImpl() {
        // Configure activity options with timeouts and retry policies
        ActivityOptions options = ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofMinutes(5))
                .setRetryOptions(
                        io.temporal.common.RetryOptions.newBuilder()
                                .setMaximumAttempts(3)
                                .build()
                )
                .build();

        this.activities = Workflow.newActivityStub(AgentActivity.class, options);
    }

    @Override
    public String executeFeatureDevelopment(String featureRequest) {
        log.info("Starting SDLC workflow for SRS ID: {}", featureRequest);

        // Step 1: Planning (Planner Agent)
        // featureRequest is actually the SRS ID from Auto-BADS
        // PlannerAgentService will retrieve the full SRS content using this ID
        String planId = activities.executePlanning(featureRequest);
        log.info("Planning complete: planId={}", planId);

        // Step 2: Code Generation (Coder Agent)
        String codeId = activities.executeCodeGeneration(planId, "Implement feature");
        log.info("Code generation complete: codeId={}", codeId);

        // Step 3: Test Generation (Test Agent)
        String testId = activities.executeTestGeneration(codeId);
        log.info("Test generation complete: testId={}", testId);

        // Step 4: Code Review (Reviewer Agent)
        String reviewId = activities.executeCodeReview(codeId);
        log.info("Code review complete: reviewId={}", reviewId);

        // In production, this would include:
        // - A loop checking test results
        // - Conditional debugging if tests fail
        // - Multiple iterations of the self-correction loop
        // - Snapshot/rollback capability

        log.info("SDLC workflow completed successfully for SRS ID: {}", featureRequest);
        return codeId;
    }
}

