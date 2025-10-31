package com.therighthandapp.agentmesh.vcs;

import java.time.Instant;
import java.util.List;

/**
 * Generic VCS issue/ticket representation
 */
public class VcsIssue {
    private String id;
    private String number;
    private String title;
    private String description;
    private String status;
    private String author;
    private List<String> labels;
    private Instant createdAt;
    private Instant updatedAt;

    public VcsIssue() {}

    public VcsIssue(String id, String number, String title, String description) {
        this.id = id;
        this.number = number;
        this.title = title;
        this.description = description;
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public List<String> getLabels() {
        return labels;
    }

    public void setLabels(List<String> labels) {
        this.labels = labels;
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

    public boolean hasLabel(String label) {
        return labels != null && labels.contains(label);
    }
}

