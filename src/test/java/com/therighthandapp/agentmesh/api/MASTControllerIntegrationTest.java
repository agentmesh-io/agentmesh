package com.therighthandapp.agentmesh.api;

import com.therighthandapp.agentmesh.mast.MASTFailureMode;
import com.therighthandapp.agentmesh.mast.MASTValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class MASTControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MASTValidator mastValidator;

    @Test
    public void testGetFailureModes() {
        String url = "http://localhost:" + port + "/api/mast/failure-modes";

        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("FM-1.1");
        assertThat(response.getBody()).contains("FM-2.1");
        assertThat(response.getBody()).contains("FM-3.1");
    }

    @Test
    public void testGetAgentHealth() {
        String agentId = "test-agent";
        String url = "http://localhost:" + port + "/api/mast/health/" + agentId;

        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("score");
        assertThat(response.getBody()).contains("status");
    }

    @Test
    public void testGetRecentViolations() {
        // Create a violation first
        mastValidator.recordViolation("test-agent", MASTFailureMode.FM_1_1_SPECIFICATION_VIOLATION,
                "task-123", "Test violation");

        String url = "http://localhost:" + port + "/api/mast/violations/recent";

        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("FM-1.1");
    }

    @Test
    public void testGetUnresolvedViolations() {
        // Create a violation
        mastValidator.recordViolation("test-agent", MASTFailureMode.FM_3_1_OUTPUT_QUALITY,
                "task-456", "Test violation");

        String url = "http://localhost:" + port + "/api/mast/violations/unresolved";

        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    public void testGetFailureModeStatistics() {
        // Create some violations
        mastValidator.recordViolation("agent1", MASTFailureMode.FM_1_1_SPECIFICATION_VIOLATION,
                "task1", "evidence1");
        mastValidator.recordViolation("agent2", MASTFailureMode.FM_1_1_SPECIFICATION_VIOLATION,
                "task2", "evidence2");

        String url = "http://localhost:" + port + "/api/mast/statistics/failure-modes";

        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("FM-1.1");
    }
}

