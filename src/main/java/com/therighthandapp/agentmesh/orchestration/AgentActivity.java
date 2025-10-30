package com.therighthandapp.agentmesh.orchestration;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Activities are the building blocks of Temporal workflows.
 * Each activity represents a discrete task that an agent performs.
 */
@ActivityInterface
public interface AgentActivity {

    /**
     * Execute a planning task
     */
    @ActivityMethod
    String executePlanning(String srsContent);

    /**
     * Execute a code generation task
     */
    @ActivityMethod
    String executeCodeGeneration(String planId, String taskDescription);

    /**
     * Execute a code review task
     */
    @ActivityMethod
    String executeCodeReview(String codeId);

    /**
     * Execute a debugging task
     */
    @ActivityMethod
    String executeDebug(String testFailureId);

    /**
     * Execute a test generation task
     */
    @ActivityMethod
    String executeTestGeneration(String codeId);
}

