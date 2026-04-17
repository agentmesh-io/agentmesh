package com.therighthandapp.agentmesh.api;

import com.therighthandapp.agentmesh.AgentMeshApplication;
import com.therighthandapp.agentmesh.blackboard.BlackboardEntry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = AgentMeshApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class BlackboardControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void testPostAndRetrieveBlackboardEntry() {
        String baseUrl = "http://localhost:" + port + "/api/blackboard";

        // Post an entry
        String postUrl = baseUrl + "/entries?agentId=test-agent&entryType=CODE&title=HelloWorld";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        HttpEntity<String> request = new HttpEntity<>("public class Hello {}", headers);

        ResponseEntity<String> postResponse = restTemplate.postForEntity(postUrl, request, String.class);
        assertThat(postResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Retrieve all entries
        ResponseEntity<String> getResponse = restTemplate.getForEntity(baseUrl + "/entries", String.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).contains("test-agent");
        assertThat(getResponse.getBody()).contains("CODE");
    }

    @Test
    public void testGetEntriesByType() {
        String baseUrl = "http://localhost:" + port + "/api/blackboard";

        // Post a CODE entry
        String postUrl = baseUrl + "/entries?agentId=coder&entryType=CODE&title=Sample";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        HttpEntity<String> request = new HttpEntity<>("code content", headers);
        restTemplate.postForEntity(postUrl, request, String.class);

        // Retrieve CODE entries
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/entries/type/CODE",
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("CODE");
    }

    @Test
    public void testCreateSnapshot() {
        String baseUrl = "http://localhost:" + port + "/api/blackboard";

        // Post some entries
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);

        String postUrl1 = baseUrl + "/entries?agentId=agent1&entryType=CODE&title=Entry1";
        restTemplate.postForEntity(postUrl1, new HttpEntity<>("content1", headers), String.class);

        String postUrl2 = baseUrl + "/entries?agentId=agent2&entryType=TEST&title=Entry2";
        restTemplate.postForEntity(postUrl2, new HttpEntity<>("content2", headers), String.class);

        // Create snapshot
        ResponseEntity<String> snapshotResponse = restTemplate.postForEntity(
                baseUrl + "/snapshot",
                null,
                String.class
        );

        assertThat(snapshotResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(snapshotResponse.getBody()).contains("timestamp");
        assertThat(snapshotResponse.getBody()).contains("entryCount");
    }
}

