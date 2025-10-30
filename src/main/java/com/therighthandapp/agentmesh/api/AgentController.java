package com.therighthandapp.agentmesh.api;

import com.therighthandapp.agentmesh.model.Agent;
import com.therighthandapp.agentmesh.model.AgentMessage;
import com.therighthandapp.agentmesh.service.AgentRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Collection;

@RestController
@RequestMapping("/api/agents")
public class AgentController {
    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final AgentRegistry registry;

    public AgentController(AgentRegistry registry) {
        this.registry = registry;
    }

    @PostMapping
    public ResponseEntity<Agent> createAgent(@RequestParam String id) {
        Agent agent = registry.create(id);
        return ResponseEntity.created(URI.create("/api/agents/" + agent.getId())).body(agent);
    }

    @GetMapping
    public Collection<Agent> listAgents() {
        return registry.list();
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<Void> startAgent(@PathVariable String id) {
        boolean ok = registry.startAgent(id);
        return ok ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<Void> stopAgent(@PathVariable String id) {
        boolean ok = registry.stopAgent(id);
        return ok ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @PostMapping("/message")
    public ResponseEntity<Void> sendMessage(@RequestBody AgentMessage message) {
        boolean ok = registry.sendMessage(message);
        return ok ? ResponseEntity.ok().build() : ResponseEntity.badRequest().build();
    }

    @GetMapping("/messages")
    public Collection<AgentMessage> messages() {
        return registry.messageLog();
    }
}

