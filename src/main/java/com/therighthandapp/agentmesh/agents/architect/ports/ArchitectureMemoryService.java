package com.therighthandapp.agentmesh.agents.architect.ports;

import java.util.List;
import java.util.Map;

/**
 * Port (Interface): Vector Memory Service for Architecture Patterns
 * 
 * Provides access to similar architecture patterns from vector memory.
 */
public interface ArchitectureMemoryService {
    
    /**
     * Search for similar architecture patterns
     */
    List<SimilarArchitecture> searchSimilarArchitectures(String query, int limit);
    
    /**
     * Store architecture in vector memory for future reference
     */
    void storeArchitecture(String architectureId, String content, Map<String, String> metadata);
    
    /**
     * Similar architecture result
     */
    record SimilarArchitecture(
        String architectureId,
        String projectTitle,
        String architectureStyle,
        String summary,
        double similarityScore,
        Map<String, String> metadata
    ) {}
}
