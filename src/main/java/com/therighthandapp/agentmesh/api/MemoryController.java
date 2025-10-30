package com.therighthandapp.agentmesh.api;

import com.therighthandapp.agentmesh.memory.MemoryArtifact;
import com.therighthandapp.agentmesh.memory.WeaviateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for Long-Term Memory (Weaviate) operations
 */
@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    private final WeaviateService weaviateService;

    public MemoryController(WeaviateService weaviateService) {
        this.weaviateService = weaviateService;
    }

    @PostMapping("/artifacts")
    public ResponseEntity<StoreResponse> storeArtifact(@RequestBody MemoryArtifact artifact) {
        String id = weaviateService.store(artifact);
        return ResponseEntity.ok(new StoreResponse(id));
    }

    @GetMapping("/search")
    public ResponseEntity<List<MemoryArtifact>> semanticSearch(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit) {

        List<MemoryArtifact> results = weaviateService.semanticSearch(query, limit);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/artifacts/type/{artifactType}")
    public ResponseEntity<List<MemoryArtifact>> findByType(
            @PathVariable String artifactType,
            @RequestParam(defaultValue = "10") int limit) {

        List<MemoryArtifact> results = weaviateService.findByType(artifactType, limit);
        return ResponseEntity.ok(results);
    }

    public static class StoreResponse {
        private final String id;

        public StoreResponse(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }
}

