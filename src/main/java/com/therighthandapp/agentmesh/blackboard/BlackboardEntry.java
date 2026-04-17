package com.therighthandapp.agentmesh.blackboard;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

/**
 * Represents an artifact or state posted to the Blackboard by an agent.
 * The Blackboard is the shared memory space where all agents read/write context.
 */
@Entity
@Table(name = "blackboard_entry",
       indexes = {
           @Index(name = "idx_tenant_project", columnList = "tenantId, projectId"),
           @Index(name = "idx_data_partition", columnList = "dataPartitionKey")
       })
public class BlackboardEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Multi-tenancy fields
    @Column(nullable = true)
    private String tenantId;

    @Column(nullable = true)
    private String projectId;

    @Column(nullable = true)
    private String dataPartitionKey; // For database sharding

    @Column(nullable = false)
    private String agentId;

    @Column(nullable = false)
    private String entryType; // e.g., "CODE", "SRS", "TEST_RESULT", "PLAN"

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private Instant timestamp;

    @Version
    private Long version; // Optimistic locking for concurrency control

    @Column
    private String parentEntryId; // For tracking dependencies

    public BlackboardEntry() {
        this.timestamp = Instant.now();
    }

    public BlackboardEntry(String agentId, String entryType, String title, String content) {
        this();
        this.agentId = agentId;
        this.entryType = entryType;
        this.title = title;
        this.content = content;
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getEntryType() {
        return entryType;
    }

    public void setEntryType(String entryType) {
        this.entryType = entryType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getDataPartitionKey() {
        return dataPartitionKey;
    }

    public void setDataPartitionKey(String dataPartitionKey) {
        this.dataPartitionKey = dataPartitionKey;
    }

    public String getParentEntryId() {
        return parentEntryId;
    }

    public void setParentEntryId(String parentEntryId) {
        this.parentEntryId = parentEntryId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlackboardEntry)) return false;
        BlackboardEntry that = (BlackboardEntry) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
