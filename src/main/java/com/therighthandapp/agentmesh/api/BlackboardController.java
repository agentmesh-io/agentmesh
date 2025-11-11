package com.therighthandapp.agentmesh.api;

import com.therighthandapp.agentmesh.blackboard.BlackboardEntry;
import com.therighthandapp.agentmesh.blackboard.BlackboardService;
import com.therighthandapp.agentmesh.blackboard.BlackboardSnapshot;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for Blackboard operations
 */
@RestController
@RequestMapping("/api/blackboard")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"})
public class BlackboardController {

    private final BlackboardService blackboardService;

    public BlackboardController(BlackboardService blackboardService) {
        this.blackboardService = blackboardService;
    }

    @PostMapping("/entries")
    public ResponseEntity<BlackboardEntry> postEntry(
            @RequestParam String agentId,
            @RequestParam String entryType,
            @RequestParam String title,
            @RequestBody String content) {

        BlackboardEntry entry = blackboardService.post(agentId, entryType, title, content);
        return ResponseEntity.ok(entry);
    }

    @GetMapping("/entries")
    public ResponseEntity<List<BlackboardEntry>> getAllEntries() {
        List<BlackboardEntry> entries = blackboardService.readAll();
        return ResponseEntity.ok(entries);
    }

    @GetMapping("/entries/type/{entryType}")
    public ResponseEntity<List<BlackboardEntry>> getEntriesByType(@PathVariable String entryType) {
        List<BlackboardEntry> entries = blackboardService.readByType(entryType);
        return ResponseEntity.ok(entries);
    }

    @GetMapping("/entries/agent/{agentId}")
    public ResponseEntity<List<BlackboardEntry>> getEntriesByAgent(@PathVariable String agentId) {
        List<BlackboardEntry> entries = blackboardService.readByAgent(agentId);
        return ResponseEntity.ok(entries);
    }

    @GetMapping("/entries/{id}")
    public ResponseEntity<BlackboardEntry> getEntry(@PathVariable Long id) {
        return blackboardService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/entries/{id}")
    public ResponseEntity<BlackboardEntry> updateEntry(
            @PathVariable Long id,
            @RequestBody String content) {

        try {
            BlackboardEntry updated = blackboardService.update(id, content);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/snapshot")
    public ResponseEntity<SnapshotResponse> createSnapshot() {
        BlackboardSnapshot snapshot = blackboardService.createSnapshot();
        return ResponseEntity.ok(new SnapshotResponse(
                snapshot.getSnapshotTime().toString(),
                snapshot.getEntryCount()
        ));
    }

    // DTO for snapshot response
    public static class SnapshotResponse {
        private final String timestamp;
        private final int entryCount;

        public SnapshotResponse(String timestamp, int entryCount) {
            this.timestamp = timestamp;
            this.entryCount = entryCount;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public int getEntryCount() {
            return entryCount;
        }
    }
}

