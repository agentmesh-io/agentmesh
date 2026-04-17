package com.therighthandapp.agentmesh.github;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for GitHub Projects (v2) integration via GraphQL
 */
@Service
@ConditionalOnProperty(name = "agentmesh.github.projects.enabled", havingValue = "true")
public class GitHubProjectsService {
    private static final Logger log = LoggerFactory.getLogger(GitHubProjectsService.class);
    private static final String GITHUB_GRAPHQL_URL = "https://api.github.com/graphql";

    @Value("${agentmesh.github.token}")
    private String githubToken;

    @Value("${agentmesh.github.projects.project-id}")
    private String projectId;

    private final RestTemplate restTemplate;

    public GitHubProjectsService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Add issue to project
     */
    public String addIssueToProject(String issueNodeId) {
        String mutation = """
            mutation {
              addProjectV2ItemById(input: {
                projectId: "%s"
                contentId: "%s"
              }) {
                item {
                  id
                }
              }
            }
            """.formatted(projectId, issueNodeId);

        try {
            Map<String, Object> response = executeGraphQL(mutation);
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            Map<String, Object> addProjectItem = (Map<String, Object>) data.get("addProjectV2ItemById");
            Map<String, Object> item = (Map<String, Object>) addProjectItem.get("item");
            String itemId = (String) item.get("id");

            log.info("Added issue {} to project, item ID: {}", issueNodeId, itemId);
            return itemId;

        } catch (Exception e) {
            log.error("Error adding issue to project", e);
            return null;
        }
    }

    /**
     * Update project card status
     */
    public void updateProjectCardStatus(String itemId, String status) {
        // This requires knowing the status field ID
        // For now, we'll add a comment with status
        log.info("Status update for item {}: {}", itemId, status);
    }

    /**
     * Add comment with iteration metrics
     */
    public void addIterationMetrics(String issueNumber, int iterations, boolean success,
                                    String failureReason) {
        String comment = """
            🤖 **AgentMesh Completion Report**
            
            **Status:** %s
            **Self-Correction Iterations:** %d
            **Timestamp:** %s
            
            %s
            """.formatted(
            success ? "✅ Success" : "❌ Failed",
            iterations,
            java.time.Instant.now(),
            success ? "Code generated successfully and ready for review." :
                "**Failure Reason:** " + failureReason + "\\n\\nPlease review requirements and try again."
        );

        log.info("Metrics for issue #{}: {} iterations, success: {}", issueNumber, iterations, success);
        // Comment will be added via GitHubIntegrationService
    }

    /**
     * Update custom field value
     */
    public void updateCustomField(String itemId, String fieldId, Object value) {
        String mutation = """
            mutation {
              updateProjectV2ItemFieldValue(input: {
                projectId: "%s"
                itemId: "%s"
                fieldId: "%s"
                value: { number: %s }
              }) {
                projectV2Item {
                  id
                }
              }
            }
            """.formatted(projectId, itemId, fieldId, value);

        try {
            executeGraphQL(mutation);
            log.info("Updated custom field {} for item {}", fieldId, itemId);
        } catch (Exception e) {
            log.error("Error updating custom field", e);
        }
    }

    /**
     * Execute GraphQL query/mutation
     */
    private Map<String, Object> executeGraphQL(String query) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + githubToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of("query", query);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
            GITHUB_GRAPHQL_URL, request, Map.class);

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            return response.getBody();
        }

        throw new RuntimeException("GraphQL query failed: " + response.getStatusCode());
    }
}

