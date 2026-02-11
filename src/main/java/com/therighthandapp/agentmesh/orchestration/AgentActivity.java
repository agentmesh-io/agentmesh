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
     * Execute architecture design task
     */
    @ActivityMethod
    String executeArchitecture(String planId);

    /**
     * Execute code generation task with clean architecture
     */
    @ActivityMethod
    String executeDevelopment(String planId, String architectureId);

    /**
     * Execute a code generation task (legacy)
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

