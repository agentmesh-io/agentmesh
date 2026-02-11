package com.therighthandapp.agentmesh.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    /**
     * Start a new workflow
     */
    @Transactional
    public Workflow startWorkflow(String projectName, String srsContent, String tenantId) {
        log.info("Starting new workflow for project: {}", projectName);

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

        workflow = workflowRepository.save(workflow);
        log.info("Workflow {} created and saved", workflowId);

        // Execute workflow asynchronously
        executeWorkflowAsync(workflowId, srsContent);

        return workflow;
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
     * Execute workflow phases asynchronously
     */
    @Async
    public CompletableFuture<Void> executeWorkflowAsync(String workflowId, String srsContent) {
        return CompletableFuture.runAsync(() -> {
            try {
                executeWorkflowPhases(workflowId, srsContent);
            } catch (Exception e) {
                log.error("Workflow {} failed: {}", workflowId, e.getMessage(), e);
                markWorkflowFailed(workflowId, e.getMessage());
            }
        });
    }

    private void executeWorkflowPhases(String workflowId, String srsContent) {
        log.info("Executing workflow phases for: {}", workflowId);

        try {
            // Phase 1: Planning
            log.info("Workflow {}: Starting PLANNING phase", workflowId);
            updateWorkflowPhase(workflowId, "PLANNING", "RUNNING", 10);

            String planId = agentActivity.executePlanning(srsContent);
            updateWorkflowArtifact(workflowId, "planId", planId);
            updateWorkflowPhase(workflowId, "PLANNING", "COMPLETED", 20);

            if (isWorkflowCancelled(workflowId)) return;
            Thread.sleep(1000);

            // Phase 2: Architecture
            log.info("Workflow {}: Starting ARCHITECTURE phase", workflowId);
            updateWorkflowPhase(workflowId, "ARCHITECTURE", "RUNNING", 25);

            String architectureId = agentActivity.executeArchitecture(planId);
            updateWorkflowArtifact(workflowId, "architectureId", architectureId);
            updateWorkflowPhase(workflowId, "ARCHITECTURE", "COMPLETED", 35);

            if (isWorkflowCancelled(workflowId)) return;
            Thread.sleep(1000);

            // Phase 3: Code Generation
            log.info("Workflow {}: Starting CODE_GENERATION phase", workflowId);
            updateWorkflowPhase(workflowId, "CODE_GENERATION", "RUNNING", 40);

            String codeId = agentActivity.executeCodeGeneration(planId, architectureId);
            updateWorkflowArtifact(workflowId, "codeId", codeId);
            updateWorkflowPhase(workflowId, "CODE_GENERATION", "COMPLETED", 60);

            if (isWorkflowCancelled(workflowId)) return;
            Thread.sleep(1000);

            // Phase 4: Testing
            log.info("Workflow {}: Starting TESTING phase", workflowId);
            updateWorkflowPhase(workflowId, "TESTING", "RUNNING", 65);

            String testId = agentActivity.executeTestGeneration(codeId);
            updateWorkflowArtifact(workflowId, "testId", testId);
            updateWorkflowPhase(workflowId, "TESTING", "COMPLETED", 80);

            if (isWorkflowCancelled(workflowId)) return;
            Thread.sleep(1000);

            // Phase 5: Review
            log.info("Workflow {}: Starting REVIEW phase", workflowId);
            updateWorkflowPhase(workflowId, "REVIEW", "RUNNING", 85);

            String reviewId = agentActivity.executeCodeReview(codeId);
            updateWorkflowArtifact(workflowId, "reviewId", reviewId);
            updateWorkflowPhase(workflowId, "REVIEW", "COMPLETED", 95);

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

    @Transactional
    protected void updateWorkflowPhase(String workflowId, String phase, String phaseStatus, int progress) {
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

    @Transactional
    protected void updateWorkflowArtifact(String workflowId, String artifactType, String artifactId) {
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

    @Transactional
    protected void markWorkflowCompleted(String workflowId) {
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

    @Transactional
    protected void markWorkflowFailed(String workflowId, String errorMessage) {
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

