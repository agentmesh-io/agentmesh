package com.therighthandapp.agentmesh.model;

import java.util.Objects;

public class Agent {
    private String id;
    private volatile AgentState state;

    // No-arg constructor for frameworks (Jackson, etc.)
    public Agent() {
        this.state = AgentState.INITIALIZED;
    }

    public Agent(String id) {
        this.id = id;
        this.state = AgentState.INITIALIZED;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public AgentState getState() {
        return state;
    }

    public void setState(AgentState state) {
        this.state = state;
    }

    public void start() {
        this.state = AgentState.RUNNING;
    }

    public void stop() {
        this.state = AgentState.STOPPED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Agent agent = (Agent) o;
        return Objects.equals(id, agent.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
