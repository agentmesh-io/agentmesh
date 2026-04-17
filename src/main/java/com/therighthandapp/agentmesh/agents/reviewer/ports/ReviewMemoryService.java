package com.therighthandapp.agentmesh.agents.reviewer.ports;

import com.therighthandapp.agentmesh.agents.reviewer.domain.ReviewReport;

import java.util.List;

/**
 * Port Interface: Review Memory Service
 * 
 * Hexagonal Architecture: This is an output port that abstracts review pattern storage in Weaviate.
 * Enables context-aware reviews by retrieving similar past reviews.
 */
public interface ReviewMemoryService {
    
    /**
     * Stores a review report in the vector database (Weaviate)
     * 
     * @param reviewReport The review report to store
     */
    void storeReview(ReviewReport reviewReport);
    
    /**
     * Finds similar review reports based on code characteristics
     * 
     * @param codeDescription Description of the code being reviewed
     * @param limit Maximum number of similar reviews to return
     * @return List of similar review reports
     */
    List<ReviewReport> findSimilarReviews(String codeDescription, int limit);
    
    /**
     * Gets review statistics for a specific category
     * 
     * @param category The category to analyze (e.g., "Security", "Performance")
     * @return Statistics about reviews in this category
     */
    ReviewStatistics getReviewStatistics(String category);
    
    /**
     * Value Object: Review Statistics
     */
    record ReviewStatistics(
        String category,
        Integer totalReviews,
        Double averageScore,
        Integer commonIssues,
        List<String> topIssueTypes
    ) {}
}
