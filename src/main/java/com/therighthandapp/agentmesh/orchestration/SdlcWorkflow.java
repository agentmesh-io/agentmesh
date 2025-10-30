package com.therighthandapp.agentmesh.orchestration;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Temporal workflow for the SDLC automation pipeline.
 * This represents the entire process from requirements to deployment.
 */
@WorkflowInterface
public interface SdlcWorkflow {

    /**
     * Execute the full SDLC workflow for a given feature
     *
     * @param featureRequest The initial feature request/SRS
     * @return The final deployment artifact ID
     */
    @WorkflowMethod
    String executeFeatureDevelopment(String featureRequest);
}

