package com.therighthandapp.agentmesh.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.therighthandapp.agentmesh.integration.ProjectInitializationResult;
import com.therighthandapp.agentmesh.integration.ProjectInitializationService;
import com.therighthandapp.agentmesh.service.WorkflowService;
import com.therighthandapp.agentmesh.tenant.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Contract test for ProjectController.
 * Validates the /api/projects/initialize endpoint matches
 * what Auto-BADS AgentMeshIntegrationService expects.
 */
@WebMvcTest(ProjectController.class)
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProjectInitializationService projectInitializationService;

    @MockBean
    private ProjectRepository projectRepository;

    @MockBean
    private WorkflowService workflowService;

    @Test
    void initializeProject_validRequest_returnsSuccess() throws Exception {
        // Given: a successful initialization result
        ProjectInitializationResult successResult = ProjectInitializationResult.success(
                "proj-123", "ECOM", "tenant-1", null, "corr-456", Map.of()
        );
        when(projectInitializationService.initializeProject(any(), any())).thenReturn(successResult);

        // Build request matching Auto-BADS ProjectInitializationDto format
        Map<String, Object> request = Map.of(
                "projectId", UUID.randomUUID().toString(),
                "projectName", "E-Commerce Platform",
                "projectDescription", "An online marketplace",
                "requirements", Map.of(
                        "ideaId", UUID.randomUUID().toString(),
                        "ideaTitle", "E-Commerce Platform",
                        "problemStatement", "Need an online marketplace",
                        "businessCase", "Growing market opportunity"
                ),
                "priority", "MEDIUM"
        );

        // When/Then
        mockMvc.perform(post("/api/projects/initialize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value("proj-123"))
                .andExpect(jsonPath("$.projectKey").value("ECOM"))
                .andExpect(jsonPath("$.status").value("INITIALIZED"));
    }

    @Test
    void initializeProject_missingRequirements_returnsBadRequest() throws Exception {
        Map<String, Object> request = Map.of(
                "projectName", "Test Project"
                // Missing 'requirements' field
        );

        mockMvc.perform(post("/api/projects/initialize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void initializeProject_serviceFailure_returns500() throws Exception {
        ProjectInitializationResult failResult = ProjectInitializationResult.failure("corr-789", "DB connection failed");
        when(projectInitializationService.initializeProject(any(), any())).thenReturn(failResult);

        Map<String, Object> request = Map.of(
                "requirements", Map.of(
                        "ideaId", UUID.randomUUID().toString(),
                        "ideaTitle", "Test",
                        "problemStatement", "Test problem"
                )
        );

        mockMvc.perform(post("/api/projects/initialize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.message").value("DB connection failed"));
    }
}

