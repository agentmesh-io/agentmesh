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

    /**
     * Hybrid search combining BM25 keyword matching with vector semantic search
     * @param request Search parameters including query, limit, alpha, and agentId
     * @return List of matching memory artifacts ranked by hybrid relevance
     */
    @PostMapping("/hybrid-search")
    public ResponseEntity<HybridSearchResponse> hybridSearch(@RequestBody HybridSearchRequest request) {
        // Validate alpha parameter (0.0 = pure BM25, 1.0 = pure vector, 0.5-0.75 = balanced)
        double alpha = request.getAlpha();
        if (alpha < 0.0 || alpha > 1.0) {
            return ResponseEntity.badRequest()
                    .body(new HybridSearchResponse(null, "Alpha must be between 0.0 and 1.0", 0, alpha));
        }

        List<MemoryArtifact> results = weaviateService.hybridSearch(
                request.getQuery(),
                request.getLimit(),
                alpha,
                request.getAgentId()
        );

        return ResponseEntity.ok(new HybridSearchResponse(
                results,
                "Hybrid search completed successfully",
                results.size(),
                alpha
        ));
    }

    /**
     * Advanced search with metadata filters and optional hybrid mode
     * @param request Search parameters with filters (artifactType, agentId, projectId, etc.)
     * @return List of matching memory artifacts filtered by metadata
     */
    @PostMapping("/search-filtered")
    public ResponseEntity<FilteredSearchResponse> filteredSearch(@RequestBody FilteredSearchRequest request) {
        // Validate hybrid mode parameters
        if (request.isUseHybrid()) {
            double alpha = request.getAlpha();
            if (alpha < 0.0 || alpha > 1.0) {
                return ResponseEntity.badRequest()
                        .body(new FilteredSearchResponse(null, "Alpha must be between 0.0 and 1.0", 0));
            }
        }

        List<MemoryArtifact> results = weaviateService.searchWithFilters(
                request.getQuery(),
                request.getLimit(),
                request.getFilters(),
                request.isUseHybrid(),
                request.getAlpha(),
                request.getAgentId()
        );

        return ResponseEntity.ok(new FilteredSearchResponse(
                results,
                "Filtered search completed successfully",
                results.size()
        ));
    }

    // Request/Response DTOs
    public static class StoreResponse {
        private final String id;

        public StoreResponse(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    public static class HybridSearchRequest {
        private String query;
        private int limit = 10;
        private double alpha = 0.75; // Balanced by default
        private String agentId;

        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
        public int getLimit() { return limit; }
        public void setLimit(int limit) { this.limit = limit; }
        public double getAlpha() { return alpha; }
        public void setAlpha(double alpha) { this.alpha = alpha; }
        public String getAgentId() { return agentId; }
        public void setAgentId(String agentId) { this.agentId = agentId; }
    }

    public static class HybridSearchResponse {
        private final List<MemoryArtifact> results;
        private final String message;
        private final int count;
        private final double alphaUsed;

        public HybridSearchResponse(List<MemoryArtifact> results, String message, int count, double alphaUsed) {
            this.results = results;
            this.message = message;
            this.count = count;
            this.alphaUsed = alphaUsed;
        }

        public List<MemoryArtifact> getResults() { return results; }
        public String getMessage() { return message; }
        public int getCount() { return count; }
        public double getAlphaUsed() { return alphaUsed; }
    }

    public static class FilteredSearchRequest {
        private String query;
        private int limit = 10;
        private java.util.Map<String, Object> filters = new java.util.HashMap<>();
        private boolean useHybrid = true;
        private double alpha = 0.75;
        private String agentId;

        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
        public int getLimit() { return limit; }
        public void setLimit(int limit) { this.limit = limit; }
        public java.util.Map<String, Object> getFilters() { return filters; }
        public void setFilters(java.util.Map<String, Object> filters) { this.filters = filters; }
        public boolean isUseHybrid() { return useHybrid; }
        public void setUseHybrid(boolean useHybrid) { this.useHybrid = useHybrid; }
        public double getAlpha() { return alpha; }
        public void setAlpha(double alpha) { this.alpha = alpha; }
        public String getAgentId() { return agentId; }
        public void setAgentId(String agentId) { this.agentId = agentId; }
    }

    public static class FilteredSearchResponse {
        private final List<MemoryArtifact> results;
        private final String message;
        private final int count;

        public FilteredSearchResponse(List<MemoryArtifact> results, String message, int count) {
            this.results = results;
            this.message = message;
            this.count = count;
        }

        public List<MemoryArtifact> getResults() { return results; }
        public String getMessage() { return message; }
        public int getCount() { return count; }
    }
}

