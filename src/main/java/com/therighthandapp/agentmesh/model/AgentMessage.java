package com.therighthandapp.agentmesh.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;

public class AgentMessage {
    private final String fromAgentId;
    private final String toAgentId;
    private final String payload;
    private final Instant timestamp;

    @JsonCreator
    public AgentMessage(
            @JsonProperty("fromAgentId") String fromAgentId,
            @JsonProperty("toAgentId") String toAgentId,
            @JsonProperty("payload") String payload,
            @JsonProperty("timestamp") Instant timestamp) {
        this.fromAgentId = fromAgentId;
        this.toAgentId = toAgentId;
        this.payload = payload;
        this.timestamp = timestamp == null ? Instant.now() : timestamp;
    }

    public String getFromAgentId() {
        return fromAgentId;
    }

    public String getToAgentId() {
        return toAgentId;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AgentMessage)) return false;
        AgentMessage that = (AgentMessage) o;
        return Objects.equals(fromAgentId, that.fromAgentId) && Objects.equals(toAgentId, that.toAgentId) && Objects.equals(payload, that.payload) && Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromAgentId, toAgentId, payload, timestamp);
    }
}

