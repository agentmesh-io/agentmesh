package com.therighthandapp.agentmesh.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Separate component to handle async workflow execution.
 *
 * Spring's @Async only works when the method is called through the Spring proxy,
 * i.e., from a different bean. By extracting async execution into a separate
 * component, we ensure the @Async annotation is properly intercepted.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowAsyncExecutor {

    private final WorkflowService workflowService;

    /**
     * Execute workflow phases asynchronously in a separate thread.
     * This method MUST be called from outside this bean for @Async to work.
     */
    @Async("workflowExecutor")
    public void executeWorkflowAsync(String workflowId, String srsContent) {
        log.info("Starting async workflow execution for workflow: {} on thread: {}",
                workflowId, Thread.currentThread().getName());
        try {
            workflowService.executeWorkflowPhases(workflowId, srsContent);
            log.info("Workflow {} completed successfully", workflowId);
        } catch (Exception e) {
            log.error("Workflow {} failed with exception: {}", workflowId, e.getMessage(), e);
            workflowService.markWorkflowFailed(workflowId,
                    e.getMessage() != null ? e.getMessage() : "Unknown error occurred");
        }
    }
}

