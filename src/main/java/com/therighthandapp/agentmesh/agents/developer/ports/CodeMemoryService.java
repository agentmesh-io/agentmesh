package com.therighthandapp.agentmesh.agents.developer.ports;

import java.util.List;
import java.util.Map;

/**
 * Port (Interface): Vector Memory Service for code patterns
 * 
 * Provides access to similar code examples from vector memory.
 */
public interface CodeMemoryService {
    
    /**
     * Search for similar code patterns
     */
    List<SimilarCode> searchSimilarCode(String query, int limit);
    
    /**
     * Store code in vector memory for future reference
     */
    void storeCode(String artifactId, String content, Map<String, String> metadata);
    
    /**
     * Similar code result
     */
    record SimilarCode(
        String artifactId,
        String fileName,
        String language,
        String snippet,
        double similarityScore,
        Map<String, String> metadata
    ) {}
}
