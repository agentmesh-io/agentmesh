package com.therighthandapp.agentmesh.service;

import com.therighthandapp.agentmesh.model.Agent;
import com.therighthandapp.agentmesh.model.AgentMessage;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class AgentRegistryTest {

    @Test
    public void createStartSendStopFlow() {
        AgentRegistry registry = new AgentRegistry();
        Agent a = registry.create("agentA");
        Agent b = registry.create("agentB");

        assertNotNull(a);
        assertNotNull(b);
        assertEquals("agentA", a.getId());

        assertTrue(registry.startAgent("agentA"));
        assertTrue(registry.startAgent("agentB"));

        AgentMessage msg = new AgentMessage("agentA", "agentB", "hello", Instant.now());
        assertTrue(registry.sendMessage(msg));

        assertFalse(registry.sendMessage(new AgentMessage("unknown", "agentB", "x", Instant.now())));

        assertTrue(registry.stopAgent("agentA"));
        assertTrue(registry.stopAgent("agentB"));
    }
}

