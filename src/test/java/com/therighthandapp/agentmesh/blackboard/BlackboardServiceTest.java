package com.therighthandapp.agentmesh.blackboard;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public class BlackboardServiceTest {

    @Autowired
    private BlackboardService blackboardService;

    @Test
    public void testPostAndReadEntry() {
        // Post an entry
        BlackboardEntry entry = blackboardService.post(
                "test-agent",
                "CODE",
                "Sample Code",
                "public class Hello {}"
        );

        assertThat(entry).isNotNull();
        assertThat(entry.getId()).isNotNull();
        assertThat(entry.getAgentId()).isEqualTo("test-agent");
        assertThat(entry.getEntryType()).isEqualTo("CODE");

        // Read all entries
        List<BlackboardEntry> all = blackboardService.readAll();
        assertThat(all).isNotEmpty();
        assertThat(all).anyMatch(e -> e.getId().equals(entry.getId()));
    }

    @Test
    public void testReadByType() {
        // Post multiple entries of different types
        blackboardService.post("agent-1", "CODE", "Code 1", "content1");
        blackboardService.post("agent-2", "TEST", "Test 1", "content2");
        blackboardService.post("agent-3", "CODE", "Code 2", "content3");

        // Read CODE entries
        List<BlackboardEntry> codeEntries = blackboardService.readByType("CODE");
        assertThat(codeEntries).hasSizeGreaterThanOrEqualTo(2);
        assertThat(codeEntries).allMatch(e -> e.getEntryType().equals("CODE"));
    }

    @Test
    public void testReadByAgent() {
        blackboardService.post("agent-x", "CODE", "Code X", "content");
        blackboardService.post("agent-y", "CODE", "Code Y", "content");

        List<BlackboardEntry> agentXEntries = blackboardService.readByAgent("agent-x");
        assertThat(agentXEntries).isNotEmpty();
        assertThat(agentXEntries).allMatch(e -> e.getAgentId().equals("agent-x"));
    }

    @Test
    public void testUpdateEntry() {
        BlackboardEntry entry = blackboardService.post("agent-1", "CODE", "Original", "original content");
        Long id = entry.getId();
        Long originalVersion = entry.getVersion();

        BlackboardEntry updated = blackboardService.update(id, "updated content");

        assertThat(updated.getContent()).isEqualTo("updated content");
        assertThat(updated.getVersion()).isGreaterThan(originalVersion);
    }

    @Test
    public void testCreateSnapshot() {
        blackboardService.post("agent-1", "CODE", "Code 1", "content1");
        blackboardService.post("agent-2", "CODE", "Code 2", "content2");

        BlackboardSnapshot snapshot = blackboardService.createSnapshot();

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getEntryCount()).isGreaterThanOrEqualTo(2);
        assertThat(snapshot.getSnapshotTime()).isNotNull();
    }

    @Test
    public void testOptimisticLocking() {
        // Create an entry
        BlackboardEntry entry = blackboardService.post("agent-1", "CODE", "Test", "content");
        Long id = entry.getId();

        // Update it once
        blackboardService.update(id, "new content 1");

        // In a real concurrent scenario, if two threads try to update the same entry
        // with the same version, one should fail with OptimisticLockException
        // This is a basic test to ensure the version field is working
        BlackboardEntry retrieved = blackboardService.getById(id).orElseThrow();
        assertThat(retrieved.getVersion()).isNotNull();
    }
}

