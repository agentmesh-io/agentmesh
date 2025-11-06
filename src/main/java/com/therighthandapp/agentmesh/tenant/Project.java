package com.therighthandapp.agentmesh.tenant;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Project entity representing a logical workspace within a tenant.
 * Projects provide isolation boundaries for agent groups, data, and workflows.
 */
@Entity
@Table(name = "projects",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "project_key"}))
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String projectKey; // e.g., "PROJ", "ACME"

    @Column
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status = ProjectStatus.ACTIVE;

    // Kubernetes sub-namespace (within tenant namespace)
    @Column
    private String k8sLabel; // Used for pod selection

    // Resource limits specific to this project
    @Column
    private Integer maxAgents;

    @Column
    private Long maxStorageMb;

    // Data isolation
    @Column(nullable = false)
    private String dataPartitionKey; // For database sharding

    @Column(nullable = false)
    private String vectorNamespace; // For RAG isolation in vector DB

    // Billing
    @Column
    private Boolean trackCosts = true;

    // Temporal workflow tracking
    @Column
    private String workflowId; // Temporal workflow ID for SDLC orchestration

    @Column
    private Instant createdAt;

    @Column
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (dataPartitionKey == null) {
            dataPartitionKey = tenant.getId() + "#" + projectKey;
        }
        if (vectorNamespace == null) {
            vectorNamespace = tenant.getOrganizationId() + "_" + projectKey.toLowerCase();
        }
        if (k8sLabel == null) {
            k8sLabel = "project=" + projectKey.toLowerCase();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public void setStatus(ProjectStatus status) {
        this.status = status;
    }

    public String getK8sLabel() {
        return k8sLabel;
    }

    public void setK8sLabel(String k8sLabel) {
        this.k8sLabel = k8sLabel;
    }

    public Integer getMaxAgents() {
        return maxAgents;
    }

    public void setMaxAgents(Integer maxAgents) {
        this.maxAgents = maxAgents;
    }

    public Long getMaxStorageMb() {
        return maxStorageMb;
    }

    public void setMaxStorageMb(Long maxStorageMb) {
        this.maxStorageMb = maxStorageMb;
    }

    public String getDataPartitionKey() {
        return dataPartitionKey;
    }

    public void setDataPartitionKey(String dataPartitionKey) {
        this.dataPartitionKey = dataPartitionKey;
    }

    public String getVectorNamespace() {
        return vectorNamespace;
    }

    public void setVectorNamespace(String vectorNamespace) {
        this.vectorNamespace = vectorNamespace;
    }

    public Boolean getTrackCosts() {
        return trackCosts;
    }

    public void setTrackCosts(Boolean trackCosts) {
        this.trackCosts = trackCosts;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public enum ProjectStatus {
        ACTIVE,
        ARCHIVED,
        SUSPENDED
    }
}

