package com.therighthandapp.agentmesh.api;

import com.therighthandapp.agentmesh.integration.ProjectInitializationResult;
import com.therighthandapp.agentmesh.integration.ProjectInitializationService;
import com.therighthandapp.agentmesh.integration.dto.SrsHandoffDto;
import com.therighthandapp.agentmesh.model.Workflow;
import com.therighthandapp.agentmesh.service.WorkflowService;
import com.therighthandapp.agentmesh.tenant.Project;
import com.therighthandapp.agentmesh.tenant.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST API controller for Project lifecycle management.
 * Provides the /api/projects/initialize endpoint consumed by Auto-BADS,
 * plus project status and unified flow tracking.
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000", "http://localhost:3001", "http://localhost:13001", "http://app.localhost"})
public class ProjectController {

    private final ProjectInitializationService projectInitializationService;
    private final ProjectRepository projectRepository;
    private final WorkflowService workflowService;

    /**
     * Initialize a new project from Auto-BADS SRS handoff.
     * Called by Auto-BADS AgentMeshIntegrationService.
     *
     * POST /api/projects/initialize
     */
    @PostMapping("/initialize")
    public ResponseEntity<Map<String, Object>> initializeProject(@RequestBody Map<String, Object> request) {
        log.info("POST /api/projects/initialize - Received project initialization request");

        try {
            // Extract SRS handoff from the request body
            SrsHandoffDto srsHandoff = extractSrsHandoff(request);
            if (srsHandoff == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "FAILED",
                        "message", "Missing or invalid 'requirements' in request body"
                ));
            }

            String correlationId = UUID.randomUUID().toString();

            // Delegate to existing ProjectInitializationService
            ProjectInitializationResult result = projectInitializationService.initializeProject(srsHandoff, correlationId);

            if (result.success()) {
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("projectId", result.projectId());
                response.put("projectKey", result.projectKey());
                response.put("tenantId", result.tenantId());
                response.put("status", "INITIALIZED");
                response.put("message", "Project initialized successfully");
                response.put("correlationId", result.correlationId());
                response.put("githubRepoUrl", result.githubRepoUrl());
                response.put("metadata", result.metadata());

                log.info("Project initialized: projectId={}, projectKey={}",
                        result.projectId(), result.projectKey());
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                        "status", "FAILED",
                        "message", result.errorMessage() != null ? result.errorMessage() : "Unknown error",
                        "correlationId", correlationId
                ));
            }

        } catch (Exception e) {
            log.error("Error initializing project", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "FAILED",
                    "message", "Internal error: " + e.getMessage()
            ));
        }
    }

    /**
     * Get project status.
     * Called by Auto-BADS AgentMeshIntegrationService.getProjectStatus()
     *
     * GET /api/projects/{projectId}/status
     */
    @GetMapping("/{projectId}/status")
    public ResponseEntity<Map<String, Object>> getProjectStatus(@PathVariable String projectId) {
        log.info("GET /api/projects/{}/status", projectId);

        Optional<Project> projectOpt = projectRepository.findById(projectId);
        if (projectOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Project project = projectOpt.get();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("projectId", project.getId());
        status.put("projectKey", project.getProjectKey());
        status.put("name", project.getName());
        status.put("status", project.getStatus().name());

        // Include workflow status if available
        if (project.getWorkflowId() != null) {
            workflowService.getWorkflow(project.getWorkflowId()).ifPresent(workflow -> {
                status.put("workflowId", workflow.getId());
                status.put("workflowStatus", workflow.getStatus().name());
                status.put("workflowProgress", workflow.getProgress());
                status.put("currentPhase", workflow.getCurrentPhase());
            });
        }

        return ResponseEntity.ok(status);
    }

    /**
     * Get unified flow tracking for a project.
     * Aggregates idea → analysis → SRS → handoff → workflow pipeline status.
     *
     * GET /api/projects/{projectId}/flow
     */
    @GetMapping("/{projectId}/flow")
    public ResponseEntity<Map<String, Object>> getProjectFlow(@PathVariable String projectId) {
        log.info("GET /api/projects/{}/flow", projectId);

        Optional<Project> projectOpt = projectRepository.findById(projectId);
        if (projectOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Project project = projectOpt.get();
        Map<String, Object> flow = new LinkedHashMap<>();
        flow.put("projectId", project.getId());
        flow.put("projectKey", project.getProjectKey());
        flow.put("name", project.getName());

        // Pipeline stages
        List<Map<String, Object>> stages = new ArrayList<>();

        // Stage 1: Idea Submission
        stages.add(Map.of(
                "stage", "IDEA_SUBMISSION",
                "status", "COMPLETED",
                "description", "Business idea submitted to Auto-BADS"
        ));

        // Stage 2: Business Analysis
        stages.add(Map.of(
                "stage", "BUSINESS_ANALYSIS",
                "status", "COMPLETED",
                "description", "SWOT, PESTEL, PMF, Financial analysis completed"
        ));

        // Stage 3: SRS Generation
        stages.add(Map.of(
                "stage", "SRS_GENERATION",
                "status", "COMPLETED",
                "description", "Software Requirements Specification generated"
        ));

        // Stage 4: Project Initialization
        stages.add(Map.of(
                "stage", "PROJECT_INITIALIZATION",
                "status", "COMPLETED",
                "description", "Project created in AgentMesh"
        ));

        // Stage 5: SDLC Workflow
        if (project.getWorkflowId() != null) {
            Optional<Workflow> workflowOpt = workflowService.getWorkflow(project.getWorkflowId());
            if (workflowOpt.isPresent()) {
                Workflow workflow = workflowOpt.get();
                Map<String, Object> workflowStage = new LinkedHashMap<>();
                workflowStage.put("stage", "SDLC_WORKFLOW");
                workflowStage.put("status", workflow.getStatus().name());
                workflowStage.put("progress", workflow.getProgress());
                workflowStage.put("currentPhase", workflow.getCurrentPhase());
                workflowStage.put("description", "Multi-agent SDLC execution");
                stages.add(workflowStage);
            }
        } else {
            stages.add(Map.of(
                    "stage", "SDLC_WORKFLOW",
                    "status", "PENDING",
                    "description", "Waiting for workflow to start"
            ));
        }

        flow.put("stages", stages);
        flow.put("overallStatus", project.getStatus().name());

        return ResponseEntity.ok(flow);
    }

    /**
     * List all projects with optional filtering.
     *
     * GET /api/projects
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listProjects(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("GET /api/projects - page={}, size={}", page, size);

        List<Map<String, Object>> projects = projectRepository.findAll().stream()
                .map(project -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("projectId", project.getId());
                    map.put("projectKey", project.getProjectKey());
                    map.put("name", project.getName());
                    map.put("status", project.getStatus().name());
                    map.put("createdAt", project.getCreatedAt());
                    return map;
                })
                .toList();

        return ResponseEntity.ok(projects);
    }

    /**
     * Extract SrsHandoffDto from the raw request map.
     * The Auto-BADS ProjectInitializationDto wraps SRS in a 'requirements' field.
     */
    @SuppressWarnings("unchecked")
    private SrsHandoffDto extractSrsHandoff(Map<String, Object> request) {
        Object requirements = request.get("requirements");
        if (requirements == null) {
            // Try direct SRS fields (alternative format)
            if (request.containsKey("ideaTitle") || request.containsKey("ideaId")) {
                return mapToSrsHandoff(request);
            }
            return null;
        }

        if (requirements instanceof Map) {
            return mapToSrsHandoff((Map<String, Object>) requirements);
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private SrsHandoffDto mapToSrsHandoff(Map<String, Object> map) {
        SrsHandoffDto dto = new SrsHandoffDto();
        if (map.get("ideaId") != null) {
            try {
                dto.setIdeaId(UUID.fromString(map.get("ideaId").toString()));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid ideaId format: {}", map.get("ideaId"));
            }
        }
        dto.setIdeaTitle((String) map.get("ideaTitle"));
        dto.setProblemStatement((String) map.get("problemStatement"));
        dto.setBusinessCase((String) map.get("businessCase"));
        dto.setStrategicAlignment((String) map.get("strategicAlignment"));
        dto.setRecommendedSolutionType((String) map.get("recommendedSolutionType"));
        if (map.get("metadata") instanceof Map) {
            dto.setMetadata((Map<String, Object>) map.get("metadata"));
        }
        return dto;
    }
}

