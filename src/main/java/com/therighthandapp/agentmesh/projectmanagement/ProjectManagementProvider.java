package com.therighthandapp.agentmesh.projectmanagement;

import java.util.List;
import java.util.Map;

/**
 * Interface for Project Management tool integration
 * Supports GitHub Projects, Jira, Azure Boards, Linear, etc.
 */
public interface ProjectManagementProvider {

    /**
     * Get the provider name (github-projects, jira, azure-boards, etc.)
     */
    String getProviderName();

    /**
     * Add issue/ticket to project board
     *
     * @param issueId Issue identifier
     * @return Project item ID
     */
    String addIssueToProject(String issueId);

    /**
     * Update project card/item status
     *
     * @param itemId Project item ID
     * @param status Status (e.g., "In Progress", "Done")
     * @param customFields Additional custom fields to update
     */
    void updateProjectItem(String itemId, String status, Map<String, Object> customFields);

    /**
     * Add metrics/completion report to project item
     *
     * @param itemId Project item ID
     * @param iterations Number of self-correction iterations
     * @param success Whether task succeeded
     * @param failureReason Reason for failure (if any)
     */
    void addCompletionMetrics(String itemId, int iterations, boolean success, String failureReason);

    /**
     * Get project item details
     */
    ProjectItem getProjectItem(String itemId);

    /**
     * Get all project items with specific status
     */
    List<ProjectItem> getProjectItemsByStatus(String status);

    /**
     * Update custom field value
     */
    void updateCustomField(String itemId, String fieldName, Object value);
}

