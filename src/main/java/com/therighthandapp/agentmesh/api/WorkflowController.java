package com.therighthandapp.agentmesh.api;

import com.therighthandapp.agentmesh.model.Workflow;
import com.therighthandapp.agentmesh.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API controller for Workflow Orchestration.
 * Manages the execution lifecycle of multi-agent workflows.
 *
 * Now uses WorkflowService with database persistence instead of in-memory storage.
 */
@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001", "http://localhost:13001"})
public class WorkflowController {

    private final WorkflowService workflowService;

    /**
     * Start a new workflow
     * POST /api/workflows/start
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startWorkflow(@RequestBody Map<String, Object> request) {
        log.info("POST /api/workflows/start - Starting new workflow");
        
        try {
            String srsContent = (String) request.getOrDefault("srs", "Default SRS content");
            String projectName = (String) request.getOrDefault("projectName", "New Project");
            String tenantId = (String) request.getOrDefault("tenantId", "default");

            Workflow workflow = workflowService.startWorkflow(projectName, srsContent, tenantId);

            log.info("Workflow {} started successfully", workflow.getId());
            return ResponseEntity.ok(workflowService.toResponseMap(workflow));

        } catch (Exception e) {
            log.error("Error starting workflow: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get workflow status
     * GET /api/workflows/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getWorkflow(@PathVariable String id) {
        log.info("GET /api/workflows/{} - Fetching workflow status", id);
        
        return workflowService.getWorkflow(id)
            .map(workflow -> ResponseEntity.ok(workflowService.toResponseMap(workflow)))
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * List all workflows
     * GET /api/workflows
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listWorkflows(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        log.info("GET /api/workflows - Listing workflows (status={}, page={}, size={})", status, page, size);
        
        Page<Workflow> workflows = workflowService.listWorkflows(status, page, size);

        List<Map<String, Object>> response = workflows.getContent().stream()
            .map(workflowService::toResponseMap)
            .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Pause workflow
     * POST /api/workflows/{id}/pause
     */
    @PostMapping("/{id}/pause")
    public ResponseEntity<Map<String, Object>> pauseWorkflow(@PathVariable String id) {
        log.info("POST /api/workflows/{}/pause - Pausing workflow", id);
        
        return workflowService.pauseWorkflow(id)
            .map(workflow -> ResponseEntity.ok(workflowService.toResponseMap(workflow)))
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Resume workflow
     * POST /api/workflows/{id}/resume
     */
    @PostMapping("/{id}/resume")
    public ResponseEntity<Map<String, Object>> resumeWorkflow(@PathVariable String id) {
        log.info("POST /api/workflows/{}/resume - Resuming workflow", id);
        
        return workflowService.resumeWorkflow(id)
            .map(workflow -> ResponseEntity.ok(workflowService.toResponseMap(workflow)))
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Cancel workflow
     * POST /api/workflows/{id}/cancel
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelWorkflow(@PathVariable String id) {
        log.info("POST /api/workflows/{}/cancel - Cancelling workflow", id);
        
        return workflowService.cancelWorkflow(id)
            .map(workflow -> ResponseEntity.ok(workflowService.toResponseMap(workflow)))
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get workflow execution graph (for visualization)
     * GET /api/workflows/{id}/graph
     */
    @GetMapping("/{id}/graph")
    public ResponseEntity<Map<String, Object>> getWorkflowGraph(@PathVariable String id) {
        log.info("GET /api/workflows/{}/graph - Fetching workflow execution graph", id);
        
        return workflowService.getWorkflow(id)
            .map(workflow -> {
                Map<String, Object> graph = new HashMap<>();
                graph.put("nodes", createGraphNodes(workflow));
                graph.put("edges", createGraphEdges());
                return ResponseEntity.ok(graph);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    private List<Map<String, Object>> createGraphNodes(Workflow workflow) {
        String currentPhase = workflow.getCurrentPhase();
        String status = workflow.getStatus().name();

        return List.of(
            Map.of("id", "start", "type", "start", "label", "Start", "status", "COMPLETED"),
            Map.of("id", "planner", "type", "PLANNER", "label", "Planner Agent",
                   "status", getNodeStatus("PLANNING", currentPhase, status)),
            Map.of("id", "architect", "type", "ARCHITECT", "label", "Architect Agent",
                   "status", getNodeStatus("ARCHITECTURE", currentPhase, status)),
            Map.of("id", "coder", "type", "CODER", "label", "Developer Agent",
                   "status", getNodeStatus("CODE_GENERATION", currentPhase, status)),
            Map.of("id", "tester", "type", "TESTER", "label", "Tester Agent",
                   "status", getNodeStatus("TESTING", currentPhase, status)),
            Map.of("id", "reviewer", "type", "REVIEWER", "label", "Reviewer Agent",
                   "status", getNodeStatus("REVIEW", currentPhase, status)),
            Map.of("id", "end", "type", "end", "label", "End",
                   "status", status.equals("COMPLETED") ? "COMPLETED" : "PENDING")
        );
    }

    private String getNodeStatus(String phase, String currentPhase, String workflowStatus) {
        if (workflowStatus.equals("FAILED") || workflowStatus.equals("CANCELLED")) {
            return workflowStatus;
        }

        List<String> phases = List.of("PLANNING", "ARCHITECTURE", "CODE_GENERATION", "TESTING", "REVIEW", "DEPLOYMENT");
        int phaseIndex = phases.indexOf(phase);
        int currentIndex = phases.indexOf(currentPhase);

        if (phaseIndex < currentIndex) return "COMPLETED";
        if (phaseIndex == currentIndex) return "RUNNING";
        return "PENDING";
    }

    private List<Map<String, Object>> createGraphEdges() {
        return List.of(
            Map.of("id", "e1", "source", "start", "target", "planner", "label", "Initialize"),
            Map.of("id", "e2", "source", "planner", "target", "architect", "label", "Plan Ready"),
            Map.of("id", "e3", "source", "architect", "target", "coder", "label", "Architecture Ready"),
            Map.of("id", "e4", "source", "coder", "target", "tester", "label", "Code Generated"),
            Map.of("id", "e5", "source", "tester", "target", "reviewer", "label", "Tests Created"),
            Map.of("id", "e6", "source", "reviewer", "target", "end", "label", "Review Complete")
        );
    }
}
