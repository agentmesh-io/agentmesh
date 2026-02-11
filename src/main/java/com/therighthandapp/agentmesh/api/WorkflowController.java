package com.therighthandapp.agentmesh.api;

import com.therighthandapp.agentmesh.orchestration.AgentActivity;
import com.therighthandapp.agentmesh.websocket.AgentMeshWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * REST API controller for Workflow Orchestration.
 * Manages the execution lifecycle of multi-agent workflows.
 */
@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"})
public class WorkflowController {

    private final AgentActivity agentActivity;
    private final AgentMeshWebSocketHandler webSocketHandler;
    
    // In-memory workflow store for demo (should be replaced with database)
    private final Map<String, Map<String, Object>> workflows = new HashMap<>();

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
            
            // Generate workflow ID
            String workflowId = UUID.randomUUID().toString();
            
            // Create workflow record first
            Map<String, Object> workflow = new HashMap<>();
            workflow.put("id", workflowId);
            workflow.put("projectName", projectName);
            workflow.put("status", "RUNNING");
            workflow.put("currentPhase", "PLANNING");
            workflow.put("startedAt", Instant.now().toString());
            workflow.put("lastUpdatedAt", Instant.now().toString());
            workflow.put("progress", 10);
            workflow.put("phases", createPhases());
            
            workflows.put(workflowId, workflow);
            
            // Execute workflow asynchronously
            executeWorkflowAsync(workflowId, srsContent);
            
            log.info("Workflow {} started successfully", workflowId);
            return ResponseEntity.ok(workflow);
            
        } catch (Exception e) {
            log.error("Error starting workflow: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }
    
    private void executeWorkflowAsync(String workflowId, String srsContent) {
        new Thread(() -> {
            try {
                Map<String, Object> workflow = workflows.get(workflowId);
                if (workflow == null) return;
                
                // Phase 1: Planning
                log.info("Workflow {}: Starting PLANNING phase", workflowId);
                updatePhase(workflowId, "PLANNING", "RUNNING", 50);
                String planId = agentActivity.executePlanning(srsContent);
                workflow.put("planId", planId);
                updatePhase(workflowId, "PLANNING", "COMPLETED", 100);
                workflow.put("progress", 15);
                Thread.sleep(2000);
                
                // Phase 2: Architecture
                log.info("Workflow {}: Starting ARCHITECTURE phase", workflowId);
                workflow.put("currentPhase", "ARCHITECTURE");
                updatePhase(workflowId, "ARCHITECTURE", "RUNNING", 50);
                String architectureId = agentActivity.executeArchitecture(planId);
                workflow.put("architectureId", architectureId);
                updatePhase(workflowId, "ARCHITECTURE", "COMPLETED", 100);
                workflow.put("progress", 30);
                Thread.sleep(2000);
                
                // Phase 3: Code Generation
                log.info("Workflow {}: Starting CODE_GENERATION phase", workflowId);
                workflow.put("currentPhase", "CODE_GENERATION");
                updatePhase(workflowId, "CODE_GENERATION", "RUNNING", 50);
                String codeId = agentActivity.executeCodeGeneration(planId, architectureId);
                workflow.put("codeId", codeId);
                updatePhase(workflowId, "CODE_GENERATION", "COMPLETED", 100);
                workflow.put("progress", 50);
                Thread.sleep(2000);
                
                // Phase 4: Testing
                log.info("Workflow {}: Starting TESTING phase", workflowId);
                workflow.put("currentPhase", "TESTING");
                updatePhase(workflowId, "TESTING", "RUNNING", 50);
                String testId = agentActivity.executeTestGeneration(codeId);
                workflow.put("testId", testId);
                updatePhase(workflowId, "TESTING", "COMPLETED", 100);
                workflow.put("progress", 70);
                Thread.sleep(2000);
                
                // Phase 5: Review
                log.info("Workflow {}: Starting REVIEW phase", workflowId);
                workflow.put("currentPhase", "REVIEW");
                updatePhase(workflowId, "REVIEW", "RUNNING", 50);
                String reviewId = agentActivity.executeCodeReview(codeId);
                workflow.put("reviewId", reviewId);
                updatePhase(workflowId, "REVIEW", "COMPLETED", 100);
                workflow.put("progress", 90);
                Thread.sleep(2000);
                
                // Phase 6: Complete
                log.info("Workflow {}: Workflow COMPLETED", workflowId);
                workflow.put("currentPhase", "DEPLOYMENT");
                workflow.put("status", "COMPLETED");
                workflow.put("progress", 100);
                updatePhase(workflowId, "DEPLOYMENT", "COMPLETED", 100);
                
                webSocketHandler.broadcastWorkflowUpdate(
                    workflowId, 
                    "COMPLETED", 
                    "DEPLOYMENT", 
                    100, 
                    "Workflow completed successfully"
                );
                
            } catch (Exception e) {
                log.error("Workflow {} failed: {}", workflowId, e.getMessage(), e);
                Map<String, Object> workflow = workflows.get(workflowId);
                if (workflow != null) {
                    workflow.put("status", "FAILED");
                    workflow.put("error", e.getMessage());
                }
            }
        }).start();
    }
    
    private void updatePhase(String workflowId, String phaseName, String status, int progress) {
        Map<String, Object> workflow = workflows.get(workflowId);
        if (workflow == null) return;
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> phases = (List<Map<String, Object>>) workflow.get("phases");
        for (int i = 0; i < phases.size(); i++) {
            Map<String, Object> phase = phases.get(i);
            if (phaseName.equals(phase.get("name"))) {
                // Create a mutable copy
                Map<String, Object> updatedPhase = new HashMap<>(phase);
                updatedPhase.put("status", status);
                updatedPhase.put("progress", progress);
                phases.set(i, updatedPhase);
                break;
            }
        }
        
        workflow.put("lastUpdatedAt", Instant.now().toString());
        
        webSocketHandler.broadcastWorkflowUpdate(
            workflowId,
            (String) workflow.get("status"),
            (String) workflow.get("currentPhase"),
            (Integer) workflow.get("progress"),
            phaseName + " phase " + status.toLowerCase()
        );
    }

    /**
     * Get workflow status
     * GET /api/workflows/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getWorkflow(@PathVariable String id) {
        log.info("GET /api/workflows/{} - Fetching workflow status", id);
        
        Map<String, Object> workflow = workflows.get(id);
        if (workflow == null) {
            return ResponseEntity.notFound().build();
        }
        
        // Update timestamp
        workflow.put("lastUpdatedAt", Instant.now().toString());
        
        return ResponseEntity.ok(workflow);
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
        
        List<Map<String, Object>> allWorkflows = new ArrayList<>(workflows.values());
        
        // Filter by status if provided
        if (status != null && !status.isEmpty()) {
            allWorkflows = allWorkflows.stream()
                .filter(w -> status.equalsIgnoreCase((String) w.get("status")))
                .toList();
        }
        
        // Simple pagination
        int start = page * size;
        int end = Math.min(start + size, allWorkflows.size());
        List<Map<String, Object>> paged = start < allWorkflows.size() 
            ? allWorkflows.subList(start, end) 
            : List.of();
        
        return ResponseEntity.ok(paged);
    }

    /**
     * Pause workflow
     * POST /api/workflows/{id}/pause
     */
    @PostMapping("/{id}/pause")
    public ResponseEntity<Map<String, Object>> pauseWorkflow(@PathVariable String id) {
        log.info("POST /api/workflows/{}/pause - Pausing workflow", id);
        
        Map<String, Object> workflow = workflows.get(id);
        if (workflow == null) {
            return ResponseEntity.notFound().build();
        }
        
        workflow.put("status", "PAUSED");
        workflow.put("lastUpdatedAt", Instant.now().toString());
        
        return ResponseEntity.ok(workflow);
    }

    /**
     * Resume workflow
     * POST /api/workflows/{id}/resume
     */
    @PostMapping("/{id}/resume")
    public ResponseEntity<Map<String, Object>> resumeWorkflow(@PathVariable String id) {
        log.info("POST /api/workflows/{}/resume - Resuming workflow", id);
        
        Map<String, Object> workflow = workflows.get(id);
        if (workflow == null) {
            return ResponseEntity.notFound().build();
        }
        
        workflow.put("status", "RUNNING");
        workflow.put("lastUpdatedAt", Instant.now().toString());
        
        return ResponseEntity.ok(workflow);
    }

    /**
     * Cancel workflow
     * POST /api/workflows/{id}/cancel
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelWorkflow(@PathVariable String id) {
        log.info("POST /api/workflows/{}/cancel - Cancelling workflow", id);
        
        Map<String, Object> workflow = workflows.get(id);
        if (workflow == null) {
            return ResponseEntity.notFound().build();
        }
        
        workflow.put("status", "CANCELLED");
        workflow.put("lastUpdatedAt", Instant.now().toString());
        
        return ResponseEntity.ok(workflow);
    }

    /**
     * Get workflow execution graph (for visualization)
     * GET /api/workflows/{id}/graph
     */
    @GetMapping("/{id}/graph")
    public ResponseEntity<Map<String, Object>> getWorkflowGraph(@PathVariable String id) {
        log.info("GET /api/workflows/{}/graph - Fetching workflow execution graph", id);
        
        Map<String, Object> workflow = workflows.get(id);
        if (workflow == null) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> graph = new HashMap<>();
        graph.put("nodes", createGraphNodes());
        graph.put("edges", createGraphEdges());
        
        return ResponseEntity.ok(graph);
    }

    private List<Map<String, Object>> createPhases() {
        return new ArrayList<>(List.of(
            new HashMap<>(Map.of("name", "PLANNING", "status", "RUNNING", "progress", 100)),
            new HashMap<>(Map.of("name", "ARCHITECTURE", "status", "PENDING", "progress", 0)),
            new HashMap<>(Map.of("name", "CODE_GENERATION", "status", "PENDING", "progress", 0)),
            new HashMap<>(Map.of("name", "TESTING", "status", "PENDING", "progress", 0)),
            new HashMap<>(Map.of("name", "REVIEW", "status", "PENDING", "progress", 0)),
            new HashMap<>(Map.of("name", "DEBUGGING", "status", "PENDING", "progress", 0)),
            new HashMap<>(Map.of("name", "DEPLOYMENT", "status", "PENDING", "progress", 0))
        ));
    }

    private List<Map<String, Object>> createGraphNodes() {
        return List.of(
            Map.of("id", "start", "type", "start", "label", "Start", "status", "COMPLETED"),
            Map.of("id", "planner", "type", "PLANNER", "label", "Planner Agent", "status", "RUNNING"),
            Map.of("id", "coder", "type", "CODER", "label", "Coder Agent", "status", "PENDING"),
            Map.of("id", "tester", "type", "TESTER", "label", "Tester Agent", "status", "PENDING"),
            Map.of("id", "reviewer", "type", "REVIEWER", "label", "Reviewer Agent", "status", "PENDING"),
            Map.of("id", "debugger", "type", "DEBUGGER", "label", "Debugger Agent", "status", "PENDING"),
            Map.of("id", "end", "type", "end", "label", "End", "status", "PENDING")
        );
    }

    private List<Map<String, Object>> createGraphEdges() {
        return List.of(
            Map.of("id", "e1", "source", "start", "target", "planner", "label", "Initialize"),
            Map.of("id", "e2", "source", "planner", "target", "coder", "label", "Plan Ready"),
            Map.of("id", "e3", "source", "coder", "target", "tester", "label", "Code Generated"),
            Map.of("id", "e4", "source", "tester", "target", "reviewer", "label", "Tests Created"),
            Map.of("id", "e5", "source", "reviewer", "target", "debugger", "label", "Review Complete"),
            Map.of("id", "e6", "source", "debugger", "target", "end", "label", "All Fixed")
        );
    }
}
