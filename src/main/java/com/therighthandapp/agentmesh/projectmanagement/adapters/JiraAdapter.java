package com.therighthandapp.agentmesh.projectmanagement.adapters;

import com.therighthandapp.agentmesh.projectmanagement.ProjectItem;
import com.therighthandapp.agentmesh.projectmanagement.ProjectManagementProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Jira adapter implementing ProjectManagementProvider interface
 *
 * Configuration:
 * agentmesh.projectmanagement.provider=jira
 * agentmesh.jira.url=https://your-domain.atlassian.net
 * agentmesh.jira.username=your-email@example.com
 * agentmesh.jira.api-token=${JIRA_API_TOKEN}
 * agentmesh.jira.project-key=PROJ
 */
@Component("jiraProjectManagementProvider")
@ConditionalOnProperty(name = "agentmesh.projectmanagement.provider", havingValue = "jira")
public class JiraAdapter implements ProjectManagementProvider {
    private static final Logger log = LoggerFactory.getLogger(JiraAdapter.class);

    @Value("${agentmesh.jira.url}")
    private String jiraUrl;

    @Value("${agentmesh.jira.username}")
    private String jiraUsername;

    @Value("${agentmesh.jira.api-token}")
    private String jiraApiToken;

    @Value("${agentmesh.jira.project-key}")
    private String projectKey;

    private final RestTemplate restTemplate;

    public JiraAdapter() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public String getProviderName() {
        return "jira";
    }

    @Override
    public String addIssueToProject(String issueId) {
        log.info("Adding Jira issue {} to project {}", issueId, projectKey);

        // Jira issues are already in a project, so we just return the issue key
        // In a real implementation, you might move it to a specific board
        return issueId;
    }

    @Override
    public void updateProjectItem(String itemId, String status, Map<String, Object> customFields) {
        log.info("Updating Jira issue {} to status {}", itemId, status);

        // Transition issue to new status
        transitionIssue(itemId, status);

        // Update custom fields
        if (customFields != null && !customFields.isEmpty()) {
            updateCustomFields(itemId, customFields);
        }
    }

    @Override
    public void addCompletionMetrics(String itemId, int iterations, boolean success, String failureReason) {
        log.info("Adding completion metrics to Jira issue {}", itemId);

        // Add comment with metrics
        String comment = """
            🤖 *AgentMesh Completion Report*
            
            *Status:* %s
            *Self-Correction Iterations:* %d
            *Timestamp:* %s
            
            %s
            """.formatted(
            success ? "✅ Success" : "❌ Failed",
            iterations,
            java.time.Instant.now(),
            success ? "Code generated successfully and ready for review." :
                "*Failure Reason:* " + failureReason
        );

        addComment(itemId, comment);
    }

    @Override
    public ProjectItem getProjectItem(String itemId) {
        log.info("Getting Jira issue {}", itemId);

        String url = "%s/rest/api/3/issue/%s".formatted(jiraUrl, itemId);

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET,
            new HttpEntity<>(getHeaders()), Map.class);

        if (response.getBody() != null) {
            Map<String, Object> data = response.getBody();
            Map<String, Object> fields = (Map<String, Object>) data.get("fields");

            ProjectItem item = new ProjectItem();
            item.setId((String) data.get("key"));
            item.setIssueId((String) data.get("key"));
            item.setTitle((String) fields.get("summary"));

            Map<String, Object> status = (Map<String, Object>) fields.get("status");
            if (status != null) {
                item.setStatus((String) status.get("name"));
            }

            return item;
        }

        return null;
    }

    @Override
    public List<ProjectItem> getProjectItemsByStatus(String status) {
        log.info("Querying Jira issues with status {}", status);

        String jql = "project = %s AND status = '%s'".formatted(projectKey, status);
        String url = "%s/rest/api/3/search?jql=%s".formatted(jiraUrl, jql);

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET,
            new HttpEntity<>(getHeaders()), Map.class);

        if (response.getBody() != null) {
            List<Map<String, Object>> issues = (List<Map<String, Object>>)
                response.getBody().get("issues");

            return issues.stream()
                .map(issueData -> {
                    Map<String, Object> fields = (Map<String, Object>) issueData.get("fields");
                    ProjectItem item = new ProjectItem();
                    item.setId((String) issueData.get("key"));
                    item.setTitle((String) fields.get("summary"));
                    return item;
                })
                .toList();
        }

        return Collections.emptyList();
    }

    @Override
    public void updateCustomField(String itemId, String fieldName, Object value) {
        log.info("Updating custom field {} for Jira issue {}", fieldName, itemId);

        Map<String, Object> customFields = Map.of(fieldName, value);
        updateCustomFields(itemId, customFields);
    }

    private void transitionIssue(String issueKey, String status) {
        // Get available transitions
        String transitionsUrl = "%s/rest/api/3/issue/%s/transitions".formatted(
            jiraUrl, issueKey);

        ResponseEntity<Map> transitionsResponse = restTemplate.exchange(transitionsUrl,
            HttpMethod.GET, new HttpEntity<>(getHeaders()), Map.class);

        if (transitionsResponse.getBody() != null) {
            List<Map<String, Object>> transitions = (List<Map<String, Object>>)
                transitionsResponse.getBody().get("transitions");

            // Find transition ID for target status
            Optional<String> transitionId = transitions.stream()
                .filter(t -> {
                    Map<String, Object> to = (Map<String, Object>) t.get("to");
                    return status.equalsIgnoreCase((String) to.get("name"));
                })
                .map(t -> (String) t.get("id"))
                .findFirst();

            if (transitionId.isPresent()) {
                // Execute transition
                Map<String, Object> body = Map.of("transition", Map.of("id", transitionId.get()));
                HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, getHeaders());
                restTemplate.postForEntity(transitionsUrl, request, String.class);
            }
        }
    }

    private void updateCustomFields(String issueKey, Map<String, Object> customFields) {
        String url = "%s/rest/api/3/issue/%s".formatted(jiraUrl, issueKey);

        Map<String, Object> body = Map.of("fields", customFields);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, getHeaders());

        restTemplate.exchange(url, HttpMethod.PUT, request, String.class);
    }

    private void addComment(String issueKey, String comment) {
        String url = "%s/rest/api/3/issue/%s/comment".formatted(jiraUrl, issueKey);

        Map<String, Object> body = Map.of(
            "body", Map.of(
                "type", "doc",
                "version", 1,
                "content", List.of(
                    Map.of(
                        "type", "paragraph",
                        "content", List.of(
                            Map.of("type", "text", "text", comment)
                        )
                    )
                )
            )
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, getHeaders());
        restTemplate.postForEntity(url, request, String.class);
    }

    private HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String auth = jiraUsername + ":" + jiraApiToken;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        headers.set("Authorization", "Basic " + encodedAuth);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}

