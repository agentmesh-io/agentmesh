package com.therighthandapp.agentmesh.orchestration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.therighthandapp.agentmesh.integration.dto.SrsHandoffDto;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Service for starting and managing Temporal workflows
 */
@Service
@ConditionalOnProperty(name = "agentmesh.temporal.enabled", havingValue = "true")
public class TemporalWorkflowService {
    private static final Logger log = LoggerFactory.getLogger(TemporalWorkflowService.class);

    private final WorkflowClient workflowClient;
    private final ObjectMapper objectMapper;

    @Value("${agentmesh.temporal.task-queue:agentmesh-tasks}")
    private String taskQueue;

    public TemporalWorkflowService(WorkflowClient workflowClient, ObjectMapper objectMapper) {
        this.workflowClient = workflowClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Start SDLC workflow for a project
     * 
     * @param projectId The project ID
     * @param projectKey The project key
     * @param srsData The SRS data from Auto-BADS
     * @return The workflow ID
     */
    public String startSdlcWorkflow(String projectId, String projectKey, SrsHandoffDto srsData) {
        if (workflowClient == null) {
            log.warn("Temporal client not available, skipping workflow start for project: {}", projectKey);
            return null;
        }

        try {
            log.info("Starting SDLC workflow for project: {} ({})", projectKey, projectId);

            // Generate workflow ID based on project
            String workflowId = "sdlc-" + projectKey + "-" + System.currentTimeMillis();

            // Pass the SRS ID instead of the full content
            // The PlannerAgentService will retrieve the full SRS from Auto-BADS using this ID
            String srsId = srsData.getIdeaId().toString();
            log.info("Passing SRS ID to workflow: {}", srsId);

            // Configure workflow options
            WorkflowOptions options = WorkflowOptions.newBuilder()
                    .setTaskQueue(taskQueue)
                    .setWorkflowId(workflowId)
                    .build();

            // Create workflow stub
            SdlcWorkflow workflow = workflowClient.newWorkflowStub(SdlcWorkflow.class, options);

            // Start workflow asynchronously with SRS ID
            WorkflowStub workflowStub = WorkflowStub.fromTyped(workflow);
            workflowStub.start(srsId);

            log.info("Successfully started SDLC workflow: workflowId={}, projectKey={}, srsId={}", 
                     workflowId, projectKey, srsId);
            return workflowId;

        } catch (Exception e) {
            log.error("Failed to start SDLC workflow for project: {}", projectKey, e);
            throw new RuntimeException("Failed to start Temporal workflow", e);
        }
    }

    /**
     * Prepare feature request from SRS data
     */
    private String prepareFeatureRequest(SrsHandoffDto srsData) {
        try {
            StringBuilder request = new StringBuilder();
            
            request.append("Project: ").append(srsData.getIdeaTitle()).append("\n\n");
            
            if (srsData.getProblemStatement() != null) {
                request.append("Problem Statement:\n").append(srsData.getProblemStatement()).append("\n\n");
            }
            
            if (srsData.getSrs() != null && srsData.getSrs().getFunctionalRequirements() != null) {
                request.append("Functional Requirements:\n");
                srsData.getSrs().getFunctionalRequirements().forEach(req -> 
                    request.append("- ").append(req.getRequirement()).append("\n")
                );
                request.append("\n");
            }
            
            if (srsData.getSrs() != null && srsData.getSrs().getArchitecture() != null) {
                request.append("Architecture: ").append(srsData.getSrs().getArchitecture().getArchitectureStyle()).append("\n\n");
            }
            
            if (srsData.getTechnicalConstraints() != null && !srsData.getTechnicalConstraints().isEmpty()) {
                request.append("Constraints:\n");
                srsData.getTechnicalConstraints().forEach(constraint -> 
                    request.append("- ").append(constraint).append("\n")
                );
            }
            
            return request.toString();
            
        } catch (Exception e) {
            log.error("Error preparing feature request", e);
            return "Feature request from project: " + srsData.getIdeaTitle();
        }
    }

    /**
     * Get workflow status
     */
    public String getWorkflowStatus(String workflowId) {
        if (workflowClient == null) {
            return "TEMPORAL_DISABLED";
        }

        try {
            WorkflowStub workflowStub = workflowClient.newUntypedWorkflowStub(workflowId);
            // Check if workflow is still running
            if (workflowStub.getResult(String.class) != null) {
                return "COMPLETED";
            }
            return "RUNNING";
        } catch (Exception e) {
            log.error("Error getting workflow status for: {}", workflowId, e);
            return "ERROR";
        }
    }

    /**
     * Cancel workflow
     */
    public void cancelWorkflow(String workflowId) {
        if (workflowClient == null) {
            log.warn("Temporal client not available, cannot cancel workflow: {}", workflowId);
            return;
        }

        try {
            WorkflowStub workflowStub = workflowClient.newUntypedWorkflowStub(workflowId);
            workflowStub.cancel();
            log.info("Cancelled workflow: {}", workflowId);
        } catch (Exception e) {
            log.error("Error cancelling workflow: {}", workflowId, e);
        }
    }
}
