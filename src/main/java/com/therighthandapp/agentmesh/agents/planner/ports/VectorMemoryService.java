package com.therighthandapp.agentmesh.agents.planner.ports;

import java.util.List;
import java.util.Map;

/**
 * Port (Interface): Vector Memory Service
 * 
 * Hexagonal Architecture: Output port for vector database operations.
 * Used to retrieve similar projects/patterns and store plan embeddings.
 */
public interface VectorMemoryService {
    
    /**
     * Search for similar execution plans
     * 
     * @param query The search query (natural language or technical description)
     * @param limit Maximum number of results
     * @return Similar plans with similarity scores
     */
    List<SimilarPlan> searchSimilarPlans(String query, int limit);
    
    /**
     * Store execution plan in vector memory for future retrieval
     * 
     * @param planId The plan identifier
     * @param content The plan content to vectorize
     * @param metadata Additional metadata
     */
    void storePlanEmbedding(String planId, String content, Map<String, Object> metadata);
    
    /**
     * Similar plan result
     */
    record SimilarPlan(
        String planId,
        String title,
        String summary,
        List<String> techStack,
        double similarityScore
    ) {}
}
