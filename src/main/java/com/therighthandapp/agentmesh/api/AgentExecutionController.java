package com.therighthandapp.agentmesh.api;

import com.therighthandapp.agentmesh.service.AgentExecutionService;
import com.therighthandapp.agentmesh.service.AgentExecutionService.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for executing agent tasks in the E2E workflow.
 * Provides endpoints for Planner, Implementer, Reviewer, and Tester agents.
 *
 * <p>RBAC (M13.2): admin or developer — agent execution is a write path.
 */
@RestController
@RequestMapping("/api/agents/execute")
@PreAuthorize("@rbac.write()")
public class AgentExecutionController {
    private static final Logger log = LoggerFactory.getLogger(AgentExecutionController.class);

    private final AgentExecutionService executionService;

    public AgentExecutionController(AgentExecutionService executionService) {
        this.executionService = executionService;
    }

    /**
     * Execute Planner Agent
     * Analyzes requirements and creates SRS document
     */
    @PostMapping("/planner")
    public ResponseEntity<AgentExecutionResponse> executePlanner(
            @RequestBody PlannerRequest request) {
        
        log.info("Executing planner agent for tenant={}, project={}", 
                request.getTenantId(), request.getProjectId());
        
        try {
            AgentExecutionResponse response = executionService.executePlanner(
                    request.getTenantId(),
                    request.getProjectId(),
                    request.getUserRequest()
            );
            
            log.info("Planner execution completed: {} artifacts created", 
                    response.getArtifactIds().size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Planner execution failed", e);
            return ResponseEntity.internalServerError()
                    .body(AgentExecutionResponse.error("planner", e.getMessage()));
        }
    }

    /**
     * Execute Implementer Agent
     * Generates code based on SRS
     */
    @PostMapping("/implementer")
    public ResponseEntity<AgentExecutionResponse> executeImplementer(
            @RequestBody ImplementerRequest request) {
        
        log.info("Executing implementer agent for tenant={}, project={}, srsId={}", 
                request.getTenantId(), request.getProjectId(), request.getSrsArtifactId());
        
        try {
            AgentExecutionResponse response = executionService.executeImplementer(
                    request.getTenantId(),
                    request.getProjectId(),
                    request.getSrsArtifactId()
            );
            
            log.info("Implementer execution completed: {} code files generated", 
                    response.getArtifactIds().size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Implementer execution failed", e);
            return ResponseEntity.internalServerError()
                    .body(AgentExecutionResponse.error("implementer", e.getMessage()));
        }
    }

    /**
     * Execute Reviewer Agent
     * Reviews code quality and best practices
     */
    @PostMapping("/reviewer")
    public ResponseEntity<AgentExecutionResponse> executeReviewer(
            @RequestBody ReviewerRequest request) {
        
        log.info("Executing reviewer agent for tenant={}, project={}, codeIds={}", 
                request.getTenantId(), request.getProjectId(), request.getCodeArtifactIds());
        
        try {
            AgentExecutionResponse response = executionService.executeReviewer(
                    request.getTenantId(),
                    request.getProjectId(),
                    request.getCodeArtifactIds()
            );
            
            log.info("Reviewer execution completed: review status={}", 
                    response.getMetadata().get("reviewStatus"));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Reviewer execution failed", e);
            return ResponseEntity.internalServerError()
                    .body(AgentExecutionResponse.error("reviewer", e.getMessage()));
        }
    }

    /**
     * Execute Tester Agent
     * Generates test cases and calculates coverage
     */
    @PostMapping("/tester")
    public ResponseEntity<AgentExecutionResponse> executeTester(
            @RequestBody TesterRequest request) {
        
        log.info("Executing tester agent for tenant={}, project={}, codeIds={}", 
                request.getTenantId(), request.getProjectId(), request.getCodeArtifactIds());
        
        try {
            AgentExecutionResponse response = executionService.executeTester(
                    request.getTenantId(),
                    request.getProjectId(),
                    request.getCodeArtifactIds()
            );
            
            log.info("Tester execution completed: coverage={}%", 
                    response.getMetadata().get("coverage"));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Tester execution failed", e);
            return ResponseEntity.internalServerError()
                    .body(AgentExecutionResponse.error("tester", e.getMessage()));
        }
    }

    /**
     * Request/Response DTOs
     */
    
    public static class PlannerRequest {
        private String tenantId;
        private String projectId;
        private String userRequest;

        // Getters and setters
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        
        public String getProjectId() { return projectId; }
        public void setProjectId(String projectId) { this.projectId = projectId; }
        
        public String getUserRequest() { return userRequest; }
        public void setUserRequest(String userRequest) { this.userRequest = userRequest; }
    }

    public static class ImplementerRequest {
        private String tenantId;
        private String projectId;
        private String srsArtifactId;

        // Getters and setters
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        
        public String getProjectId() { return projectId; }
        public void setProjectId(String projectId) { this.projectId = projectId; }
        
        public String getSrsArtifactId() { return srsArtifactId; }
        public void setSrsArtifactId(String srsArtifactId) { this.srsArtifactId = srsArtifactId; }
    }

    public static class ReviewerRequest {
        private String tenantId;
        private String projectId;
        private String[] codeArtifactIds;

        // Getters and setters
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        
        public String getProjectId() { return projectId; }
        public void setProjectId(String projectId) { this.projectId = projectId; }
        
        public String[] getCodeArtifactIds() { return codeArtifactIds; }
        public void setCodeArtifactIds(String[] codeArtifactIds) { 
            this.codeArtifactIds = codeArtifactIds; 
        }
    }

    public static class TesterRequest {
        private String tenantId;
        private String projectId;
        private String[] codeArtifactIds;

        // Getters and setters
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }
        
        public String getProjectId() { return projectId; }
        public void setProjectId(String projectId) { this.projectId = projectId; }
        
        public String[] getCodeArtifactIds() { return codeArtifactIds; }
        public void setCodeArtifactIds(String[] codeArtifactIds) { 
            this.codeArtifactIds = codeArtifactIds; 
        }
    }
}
