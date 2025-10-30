package com.therighthandapp.agentmesh.api;

import com.therighthandapp.agentmesh.AgentMeshApplication;
import com.therighthandapp.agentmesh.model.Agent;
import com.therighthandapp.agentmesh.model.AgentMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = AgentMeshApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AgentControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void fullAgentFlow() {
        String base = "http://localhost:" + port + "/api/agents";

        // Create agents
        ResponseEntity<Agent> resA = restTemplate.postForEntity(base + "?id=agentA", null, Agent.class);
        assertThat(resA.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ResponseEntity<Agent> resB = restTemplate.postForEntity(base + "?id=agentB", null, Agent.class);
        assertThat(resB.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Start agents
        ResponseEntity<Void> startA = restTemplate.postForEntity(base + "/agentA/start", null, Void.class);
        assertThat(startA.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<Void> startB = restTemplate.postForEntity(base + "/agentB/start", null, Void.class);
        assertThat(startB.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Send message
        AgentMessage msg = new AgentMessage("agentA", "agentB", "hi from A", Instant.now());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<AgentMessage> request = new HttpEntity<>(msg, headers);
        ResponseEntity<Void> send = restTemplate.postForEntity(base + "/message", request, Void.class);
        assertThat(send.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify messages
        ResponseEntity<AgentMessage[]> messages = restTemplate.getForEntity(base + "/messages", AgentMessage[].class);
        assertThat(messages.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(messages.getBody()).isNotNull();
        assertThat(messages.getBody().length).isGreaterThanOrEqualTo(1);
    }
}
