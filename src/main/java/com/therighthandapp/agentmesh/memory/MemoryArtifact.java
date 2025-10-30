package com.therighthandapp.agentmesh.memory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a memory artifact stored in the Long-Term Memory (vector DB)
 */
public class MemoryArtifact {
    private String id;
    private String agentId;
    private String artifactType; // e.g., "SRS", "CODE", "TEST", "KNOWLEDGE"
    private String title;
    private String content;
    private Map<String, Object> metadata;
    private Instant timestamp;
    private float[] embedding; // Vector representation for semantic search

    public MemoryArtifact() {
        this.metadata = new HashMap<>();
        this.timestamp = Instant.now();
    }

    public MemoryArtifact(String agentId, String artifactType, String title, String content) {
        this();
        this.agentId = agentId;
        this.artifactType = artifactType;
        this.title = title;
        this.content = content;
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getArtifactType() {
        return artifactType;
    }

    public void setArtifactType(String artifactType) {
        this.artifactType = artifactType;
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

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }
}

