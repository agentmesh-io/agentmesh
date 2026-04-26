package com.therighthandapp.agentmesh.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.therighthandapp.agentmesh.blackboard.BlackboardService;
import com.therighthandapp.agentmesh.model.Workflow;
import com.therighthandapp.agentmesh.model.Workflow.WorkflowStatus;
import com.therighthandapp.agentmesh.orchestration.AgentActivity;
import com.therighthandapp.agentmesh.repository.WorkflowRepository;
import com.therighthandapp.agentmesh.websocket.AgentMeshWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Service for managing workflow execution.
 * Handles workflow lifecycle, persistence, and orchestration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final AgentActivity agentActivity;
    private final AgentMeshWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper;
    private final BlackboardService blackboardService;

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private WorkflowAsyncExecutor workflowAsyncExecutor;

    /**
     * Start a new workflow - creates workflow record and triggers async execution.
     * Delegates async execution to WorkflowAsyncExecutor (separate bean) to ensure
     * Spring's @Async proxy is properly invoked.
     *
     * NOTE: This method is NOT @Transactional on purpose. The workflow record is created
     * in its own transaction (REQUIRES_NEW via createWorkflowRecord), and the async
     * execution runs in a completely separate thread/transaction context.
     */
    public Workflow startWorkflow(String projectName, String srsContent, String tenantId) {
        log.info("Starting new workflow for project: {}", projectName);

        // Create and save workflow in a separate transaction
        Workflow workflow = createWorkflowRecord(projectName, srsContent, tenantId);

        log.info("Workflow {} created and saved, starting async execution", workflow.getId());

        // Execute workflow asynchronously via separate bean (ensures @Async proxy works)
        // Catch any exception from the async call itself (not from the async task)
        try {
            workflowAsyncExecutor.executeWorkflowAsync(workflow.getId(), srsContent);
        } catch (Exception e) {
            log.warn("Failed to schedule async workflow execution, workflow {} will continue in background: {}",
                     workflow.getId(), e.getMessage());
        }

        return workflow;
    }

    /**
     * Create the workflow record in the database.
     * Uses REQUIRES_NEW to ensure this transaction is independent and commits immediately.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public Workflow createWorkflowRecord(String projectName, String srsContent, String tenantId) {
        String workflowId = UUID.randomUUID().toString();

        Workflow workflow = Workflow.builder()
                .id(workflowId)
                .projectName(projectName)
                .tenantId(tenantId != null ? tenantId : "default")
                .status(WorkflowStatus.RUNNING)
                .currentPhase("PLANNING")
                .progress(5)
                .srsContent(srsContent)
                .startedAt(Instant.now())
                .lastUpdatedAt(Instant.now())
                .phasesJson(createInitialPhasesJson())
                .build();

        return workflowRepository.save(workflow);
    }

    /**
     * Get workflow by ID
     */
    public Optional<Workflow> getWorkflow(String workflowId) {
        return workflowRepository.findById(workflowId);
    }

    /**
     * List workflows with pagination
     */
    public Page<Workflow> listWorkflows(String status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"));

        if (status != null && !status.isEmpty()) {
            try {
                WorkflowStatus workflowStatus = WorkflowStatus.valueOf(status.toUpperCase());
                return workflowRepository.findByStatus(workflowStatus, pageRequest);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid workflow status: {}", status);
            }
        }

        return workflowRepository.findAll(pageRequest);
    }

    /**
     * Pause a running workflow
     */
    @Transactional
    public Optional<Workflow> pauseWorkflow(String workflowId) {
        return workflowRepository.findById(workflowId)
                .filter(w -> w.getStatus() == WorkflowStatus.RUNNING)
                .map(workflow -> {
                    workflow.setStatus(WorkflowStatus.PAUSED);
                    workflow.setLastUpdatedAt(Instant.now());
                    return workflowRepository.save(workflow);
                });
    }

    /**
     * Resume a paused workflow
     */
    @Transactional
    public Optional<Workflow> resumeWorkflow(String workflowId) {
        return workflowRepository.findById(workflowId)
                .filter(w -> w.getStatus() == WorkflowStatus.PAUSED)
                .map(workflow -> {
                    workflow.setStatus(WorkflowStatus.RUNNING);
                    workflow.setLastUpdatedAt(Instant.now());
                    return workflowRepository.save(workflow);
                });
    }

    /**
     * Cancel a workflow
     */
    @Transactional
    public Optional<Workflow> cancelWorkflow(String workflowId) {
        return workflowRepository.findById(workflowId)
                .filter(w -> w.getStatus() == WorkflowStatus.RUNNING || w.getStatus() == WorkflowStatus.PAUSED)
                .map(workflow -> {
                    workflow.setStatus(WorkflowStatus.CANCELLED);
                    workflow.setLastUpdatedAt(Instant.now());
                    return workflowRepository.save(workflow);
                });
    }

    /**
     * Execute all workflow phases sequentially.
     * Called by WorkflowAsyncExecutor from a separate async thread.
     */
    public void executeWorkflowPhases(String workflowId, String srsContent) {
        log.info("Executing workflow phases for: {}", workflowId);

        try {
            // Phase 1: Planning
            log.info("Workflow {}: Starting PLANNING phase", workflowId);
            updateWorkflowPhase(workflowId, "PLANNING", "RUNNING", 10);
            webSocketHandler.broadcastAgentStatus("planner", "PlannerAgent", "RUNNING");

            String planId = agentActivity.executePlanning(srsContent);
            updateWorkflowArtifact(workflowId, "planId", planId);
            updateWorkflowPhase(workflowId, "PLANNING", "COMPLETED", 20);
            webSocketHandler.broadcastAgentStatus("planner", "PlannerAgent", "IDLE");

            if (isWorkflowCancelled(workflowId)) return;
            Thread.sleep(1000);

            // Phase 2: Architecture
            log.info("Workflow {}: Starting ARCHITECTURE phase", workflowId);
            updateWorkflowPhase(workflowId, "ARCHITECTURE", "RUNNING", 25);
            webSocketHandler.broadcastAgentStatus("architect", "ArchitectAgent", "RUNNING");

            String architectureId = agentActivity.executeArchitecture(planId);
            updateWorkflowArtifact(workflowId, "architectureId", architectureId);
            updateWorkflowPhase(workflowId, "ARCHITECTURE", "COMPLETED", 35);
            webSocketHandler.broadcastAgentStatus("architect", "ArchitectAgent", "IDLE");

            if (isWorkflowCancelled(workflowId)) return;
            Thread.sleep(1000);

            // Phase 3: Code Generation
            log.info("Workflow {}: Starting CODE_GENERATION phase", workflowId);
            updateWorkflowPhase(workflowId, "CODE_GENERATION", "RUNNING", 40);
            webSocketHandler.broadcastAgentStatus("developer", "DeveloperAgent", "RUNNING");

            String codeId = agentActivity.executeCodeGeneration(planId, architectureId);
            updateWorkflowArtifact(workflowId, "codeId", codeId);
            updateWorkflowPhase(workflowId, "CODE_GENERATION", "COMPLETED", 60);
            webSocketHandler.broadcastAgentStatus("developer", "DeveloperAgent", "IDLE");

            if (isWorkflowCancelled(workflowId)) return;
            Thread.sleep(1000);

            // Phase 4: Testing
            log.info("Workflow {}: Starting TESTING phase", workflowId);
            updateWorkflowPhase(workflowId, "TESTING", "RUNNING", 65);
            webSocketHandler.broadcastAgentStatus("tester", "TesterAgent", "RUNNING");

            String testId = agentActivity.executeTestGeneration(codeId);
            updateWorkflowArtifact(workflowId, "testId", testId);
            updateWorkflowPhase(workflowId, "TESTING", "COMPLETED", 80);
            webSocketHandler.broadcastAgentStatus("tester", "TesterAgent", "IDLE");

            if (isWorkflowCancelled(workflowId)) return;
            Thread.sleep(1000);

            // Phase 5: Review
            log.info("Workflow {}: Starting REVIEW phase", workflowId);
            updateWorkflowPhase(workflowId, "REVIEW", "RUNNING", 85);
            webSocketHandler.broadcastAgentStatus("reviewer", "ReviewerAgent", "RUNNING");

            String reviewId = agentActivity.executeCodeReview(codeId);
            updateWorkflowArtifact(workflowId, "reviewId", reviewId);
            updateWorkflowPhase(workflowId, "REVIEW", "COMPLETED", 95);
            webSocketHandler.broadcastAgentStatus("reviewer", "ReviewerAgent", "IDLE");

            if (isWorkflowCancelled(workflowId)) return;

            // Complete workflow
            log.info("Workflow {}: COMPLETED successfully", workflowId);
            markWorkflowCompleted(workflowId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            markWorkflowFailed(workflowId, "Workflow interrupted");
        } catch (Exception e) {
            log.error("Workflow {} failed during execution: {}", workflowId, e.getMessage(), e);
            markWorkflowFailed(workflowId, e.getMessage());
        }
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void updateWorkflowPhase(String workflowId, String phase, String phaseStatus, int progress) {
        workflowRepository.findById(workflowId).ifPresent(workflow -> {
            workflow.setCurrentPhase(phase);
            workflow.setProgress(progress);
            workflow.setLastUpdatedAt(Instant.now());

            // Update phases JSON
            try {
                List<Map<String, Object>> phases = objectMapper.readValue(
                        workflow.getPhasesJson(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class)
                );

                for (Map<String, Object> p : phases) {
                    if (phase.equals(p.get("name"))) {
                        p.put("status", phaseStatus);
                        p.put("progress", phaseStatus.equals("COMPLETED") ? 100 : 50);
                        break;
                    }
                }

                workflow.setPhasesJson(objectMapper.writeValueAsString(phases));
            } catch (JsonProcessingException e) {
                log.warn("Failed to update phases JSON: {}", e.getMessage());
            }

            workflowRepository.save(workflow);

            // Broadcast update via WebSocket
            webSocketHandler.broadcastWorkflowUpdate(
                    workflowId,
                    workflow.getStatus().name(),
                    phase,
                    progress,
                    phase + " " + phaseStatus.toLowerCase()
            );
        });
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void updateWorkflowArtifact(String workflowId, String artifactType, String artifactId) {
        workflowRepository.findById(workflowId).ifPresent(workflow -> {
            switch (artifactType) {
                case "planId" -> workflow.setPlanId(artifactId);
                case "architectureId" -> workflow.setArchitectureId(artifactId);
                case "codeId" -> workflow.setCodeId(artifactId);
                case "testId" -> workflow.setTestId(artifactId);
                case "reviewId" -> workflow.setReviewId(artifactId);
            }
            workflow.setLastUpdatedAt(Instant.now());
            workflowRepository.save(workflow);
        });
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void markWorkflowCompleted(String workflowId) {
        workflowRepository.findById(workflowId).ifPresent(workflow -> {
            workflow.setStatus(WorkflowStatus.COMPLETED);
            workflow.setProgress(100);
            workflow.setCurrentPhase("DEPLOYMENT");
            workflow.setCompletedAt(Instant.now());
            workflow.setLastUpdatedAt(Instant.now());
            workflowRepository.save(workflow);

            webSocketHandler.broadcastWorkflowUpdate(
                    workflowId,
                    "COMPLETED",
                    "DEPLOYMENT",
                    100,
                    "Workflow completed successfully"
            );
        });
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void markWorkflowFailed(String workflowId, String errorMessage) {
        workflowRepository.findById(workflowId).ifPresent(workflow -> {
            workflow.setStatus(WorkflowStatus.FAILED);
            workflow.setErrorMessage(errorMessage);
            workflow.setLastUpdatedAt(Instant.now());
            workflowRepository.save(workflow);

            webSocketHandler.broadcastWorkflowUpdate(
                    workflowId,
                    "FAILED",
                    workflow.getCurrentPhase(),
                    workflow.getProgress(),
                    "Workflow failed: " + errorMessage
            );
        });
    }

    private boolean isWorkflowCancelled(String workflowId) {
        return workflowRepository.findById(workflowId)
                .map(w -> w.getStatus() == WorkflowStatus.CANCELLED)
                .orElse(true);
    }

    private String createInitialPhasesJson() {
        List<Map<String, Object>> phases = List.of(
                Map.of("name", "PLANNING", "status", "PENDING", "progress", 0),
                Map.of("name", "ARCHITECTURE", "status", "PENDING", "progress", 0),
                Map.of("name", "CODE_GENERATION", "status", "PENDING", "progress", 0),
                Map.of("name", "TESTING", "status", "PENDING", "progress", 0),
                Map.of("name", "REVIEW", "status", "PENDING", "progress", 0),
                Map.of("name", "DEPLOYMENT", "status", "PENDING", "progress", 0)
        );

        try {
            return objectMapper.writeValueAsString(phases);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    /**
     * Convert Workflow entity to API response map
     */
    /**
     * Get workflow artifacts with their actual content from Blackboard
     */
    public Optional<Map<String, Object>> getWorkflowArtifacts(String workflowId) {
        return workflowRepository.findById(workflowId).map(workflow -> {
            Map<String, Object> result = new HashMap<>();
            result.put("workflowId", workflowId);
            result.put("projectName", workflow.getProjectName());
            result.put("status", workflow.getStatus().name());
            result.put("currentPhase", workflow.getCurrentPhase());
            result.put("progress", workflow.getProgress());

            // Get plan artifact
            if (workflow.getPlanId() != null) {
                Map<String, Object> planArtifact = new HashMap<>();
                planArtifact.put("id", workflow.getPlanId());
                planArtifact.put("phase", "PLANNING");
                try {
                    Long planEntryId = Long.parseLong(workflow.getPlanId());
                    blackboardService.getById(planEntryId).ifPresent(entry -> {
                        planArtifact.put("content", entry.getContent());
                        planArtifact.put("timestamp", entry.getTimestamp());
                        planArtifact.put("agentId", entry.getAgentId());
                    });
                } catch (NumberFormatException e) {
                    // UUID format - search by type
                    log.debug("Plan ID is not numeric: {}", workflow.getPlanId());
                }
                result.put("plan", planArtifact);
            }

            // Get architecture artifact
            if (workflow.getArchitectureId() != null) {
                Map<String, Object> archArtifact = new HashMap<>();
                archArtifact.put("id", workflow.getArchitectureId());
                archArtifact.put("phase", "ARCHITECTURE");
                try {
                    Long archEntryId = Long.parseLong(workflow.getArchitectureId());
                    blackboardService.getById(archEntryId).ifPresent(entry -> {
                        archArtifact.put("content", entry.getContent());
                        archArtifact.put("timestamp", entry.getTimestamp());
                        archArtifact.put("agentId", entry.getAgentId());
                    });
                } catch (NumberFormatException e) {
                    log.debug("Architecture ID is not numeric: {}", workflow.getArchitectureId());
                }
                result.put("architecture", archArtifact);
            }

            // Get code artifact
            if (workflow.getCodeId() != null) {
                Map<String, Object> codeArtifact = new HashMap<>();
                codeArtifact.put("id", workflow.getCodeId());
                codeArtifact.put("phase", "CODE_GENERATION");
                // Code IDs are UUIDs, search CODE type entries
                blackboardService.readByType("CODE").stream()
                    .filter(entry -> entry.getContent() != null && entry.getContent().contains(workflow.getCodeId()))
                    .findFirst()
                    .ifPresent(entry -> {
                        codeArtifact.put("content", entry.getContent());
                        codeArtifact.put("timestamp", entry.getTimestamp());
                        codeArtifact.put("agentId", entry.getAgentId());
                    });
                result.put("code", codeArtifact);
            }

            // Get test artifact
            if (workflow.getTestId() != null) {
                Map<String, Object> testArtifact = new HashMap<>();
                testArtifact.put("id", workflow.getTestId());
                testArtifact.put("phase", "TESTING");
                try {
                    Long testEntryId = Long.parseLong(workflow.getTestId());
                    blackboardService.getById(testEntryId).ifPresent(entry -> {
                        testArtifact.put("content", entry.getContent());
                        testArtifact.put("timestamp", entry.getTimestamp());
                        testArtifact.put("agentId", entry.getAgentId());
                    });
                } catch (NumberFormatException e) {
                    log.debug("Test ID is not numeric: {}", workflow.getTestId());
                }
                result.put("tests", testArtifact);
            }

            // Get review artifact
            if (workflow.getReviewId() != null) {
                Map<String, Object> reviewArtifact = new HashMap<>();
                reviewArtifact.put("id", workflow.getReviewId());
                reviewArtifact.put("phase", "REVIEW");
                try {
                    Long reviewEntryId = Long.parseLong(workflow.getReviewId());
                    blackboardService.getById(reviewEntryId).ifPresent(entry -> {
                        reviewArtifact.put("content", entry.getContent());
                        reviewArtifact.put("timestamp", entry.getTimestamp());
                        reviewArtifact.put("agentId", entry.getAgentId());
                    });
                } catch (NumberFormatException e) {
                    log.debug("Review ID is not numeric: {}", workflow.getReviewId());
                }
                result.put("review", reviewArtifact);
            }

            // Add timestamps
            result.put("startedAt", workflow.getStartedAt() != null ? workflow.getStartedAt().toString() : null);
            result.put("completedAt", workflow.getCompletedAt() != null ? workflow.getCompletedAt().toString() : null);

            return result;
        });
    }

    public Map<String, Object> toResponseMap(Workflow workflow) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", workflow.getId());
        response.put("projectName", workflow.getProjectName());
        response.put("tenantId", workflow.getTenantId());
        response.put("status", workflow.getStatus().name());
        response.put("currentPhase", workflow.getCurrentPhase());
        response.put("progress", workflow.getProgress());
        response.put("startedAt", workflow.getStartedAt() != null ? workflow.getStartedAt().toString() : null);
        response.put("completedAt", workflow.getCompletedAt() != null ? workflow.getCompletedAt().toString() : null);
        response.put("lastUpdatedAt", workflow.getLastUpdatedAt() != null ? workflow.getLastUpdatedAt().toString() : null);

        // Parse phases JSON
        try {
            List<Map<String, Object>> phases = objectMapper.readValue(
                    workflow.getPhasesJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class)
            );
            response.put("phases", phases);
        } catch (Exception e) {
            response.put("phases", List.of());
        }

        // Add artifact IDs
        if (workflow.getPlanId() != null) response.put("planId", workflow.getPlanId());
        if (workflow.getArchitectureId() != null) response.put("architectureId", workflow.getArchitectureId());
        if (workflow.getCodeId() != null) response.put("codeId", workflow.getCodeId());
        if (workflow.getTestId() != null) response.put("testId", workflow.getTestId());
        if (workflow.getReviewId() != null) response.put("reviewId", workflow.getReviewId());
        if (workflow.getErrorMessage() != null) response.put("error", workflow.getErrorMessage());

        return response;
    }
}

