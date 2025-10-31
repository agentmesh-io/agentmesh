package com.therighthandapp.agentmesh.projectmanagement.adapters;

import com.therighthandapp.agentmesh.github.GitHubProjectsService;
import com.therighthandapp.agentmesh.projectmanagement.ProjectItem;
import com.therighthandapp.agentmesh.projectmanagement.ProjectManagementProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * GitHub Projects adapter implementing ProjectManagementProvider interface
 */
@Component("githubProjectsProvider")
@ConditionalOnBean(GitHubProjectsService.class)
@ConditionalOnProperty(name = "agentmesh.github.projects.enabled", havingValue = "true")
public class GitHubProjectsAdapter implements ProjectManagementProvider {

    private final GitHubProjectsService githubProjectsService;

    public GitHubProjectsAdapter(GitHubProjectsService githubProjectsService) {
        this.githubProjectsService = githubProjectsService;
    }

    @Override
    public String getProviderName() {
        return "github-projects";
    }

    @Override
    public String addIssueToProject(String issueId) {
        return githubProjectsService.addIssueToProject(issueId);
    }

    @Override
    public void updateProjectItem(String itemId, String status, Map<String, Object> customFields) {
        githubProjectsService.updateProjectCardStatus(itemId, status);

        // Update custom fields if provided
        if (customFields != null) {
            for (Map.Entry<String, Object> entry : customFields.entrySet()) {
                // Note: This requires field IDs which should be configured
                // For now, just log
                System.out.println("Custom field update: " + entry.getKey() + " = " + entry.getValue());
            }
        }
    }

    @Override
    public void addCompletionMetrics(String itemId, int iterations, boolean success, String failureReason) {
        githubProjectsService.addIterationMetrics(
            extractIssueNumber(itemId),
            iterations,
            success,
            failureReason
        );
    }

    @Override
    public ProjectItem getProjectItem(String itemId) {
        // TODO: Implement get project item from GitHub Projects API
        ProjectItem item = new ProjectItem();
        item.setId(itemId);
        return item;
    }

    @Override
    public List<ProjectItem> getProjectItemsByStatus(String status) {
        // TODO: Implement query by status
        return Collections.emptyList();
    }

    @Override
    public void updateCustomField(String itemId, String fieldName, Object value) {
        // TODO: Implement custom field update with proper field ID resolution
        // For now, delegate to existing method
        githubProjectsService.updateProjectCardStatus(itemId, fieldName);
    }

    private String extractIssueNumber(String itemId) {
        // Extract issue number from item ID
        // This is a simplified implementation
        return itemId;
    }
}

