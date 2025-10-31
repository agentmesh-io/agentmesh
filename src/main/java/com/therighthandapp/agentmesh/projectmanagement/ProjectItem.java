package com.therighthandapp.agentmesh.projectmanagement;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Generic project management item (card, ticket, issue)
 */
public class ProjectItem {
    private String id;
    private String issueId;
    private String title;
    private String status;
    private Map<String, Object> customFields;
    private Instant createdAt;
    private Instant updatedAt;

    public ProjectItem() {
        this.customFields = new HashMap<>();
    }

    public ProjectItem(String id, String issueId, String title, String status) {
        this();
        this.id = id;
        this.issueId = issueId;
        this.title = title;
        this.status = status;
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getIssueId() {
        return issueId;
    }

    public void setIssueId(String issueId) {
        this.issueId = issueId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Map<String, Object> getCustomFields() {
        return customFields;
    }

    public void setCustomFields(Map<String, Object> customFields) {
        this.customFields = customFields;
    }

    public void setCustomField(String key, Object value) {
        this.customFields.put(key, value);
    }

    public Object getCustomField(String key) {
        return this.customFields.get(key);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

