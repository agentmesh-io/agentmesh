package com.therighthandapp.agentmesh.service;

import com.therighthandapp.agentmesh.model.Agent;
import com.therighthandapp.agentmesh.model.AgentMessage;
import com.therighthandapp.agentmesh.model.AgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class AgentRegistry {
    private static final Logger log = LoggerFactory.getLogger(AgentRegistry.class);

    private final Map<String, Agent> agents = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<AgentMessage> messageLog = new CopyOnWriteArrayList<>();

    public Agent create(String id) {
        Agent agent = new Agent(id);
        Agent existing = agents.putIfAbsent(id, agent);
        if (existing != null) {
            log.debug("Agent {} already exists", id);
            return existing;
        }
        log.info("Created agent {}", id);
        return agent;
    }

    public Optional<Agent> get(String id) {
        return Optional.ofNullable(agents.get(id));
    }

    public Collection<Agent> list() {
        return agents.values();
    }

    public boolean startAgent(String id) {
        Agent agent = agents.get(id);
        if (agent == null) return false;
        agent.start();
        log.info("Started agent {}", id);
        return true;
    }

    public boolean stopAgent(String id) {
        Agent agent = agents.get(id);
        if (agent == null) return false;
        agent.stop();
        log.info("Stopped agent {}", id);
        return true;
    }

    public boolean sendMessage(AgentMessage msg) {
        log.debug("Attempting to send message from {} to {}", msg.getFromAgentId(), msg.getToAgentId());
        Agent to = agents.get(msg.getToAgentId());
        Agent from = agents.get(msg.getFromAgentId());
        if (to == null || from == null) {
            log.warn("Message target or source not found: from={} to={}", msg.getFromAgentId(), msg.getToAgentId());
            return false;
        }
        if (to.getState() != AgentState.RUNNING || from.getState() != AgentState.RUNNING) {
            log.warn("Either source or target agent is not running: fromState={}, toState={}", from.getState(), to.getState());
            return false;
        }
        messageLog.add(msg);
        log.info("Delivered message from {} to {}", msg.getFromAgentId(), msg.getToAgentId());
        return true;
    }

    public Collection<AgentMessage> messageLog() {
        return messageLog;
    }
}

