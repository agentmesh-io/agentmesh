package com.therighthandapp.agentmesh.agents.reviewer.ports;

import com.therighthandapp.agentmesh.agents.reviewer.domain.ReviewReport;

import java.util.Optional;

/**
 * Port Interface: Review Repository
 * 
 * Hexagonal Architecture: This is an output port that abstracts review storage.
 * The implementation will store review reports in the Blackboard.
 */
public interface ReviewRepository {
    
    /**
     * Saves a review report to the Blackboard
     * 
     * @param reviewReport The review report to save
     * @return The saved review report with generated ID
     */
    ReviewReport save(ReviewReport reviewReport);
    
    /**
     * Finds a review report by its ID
     * 
     * @param reportId The review report ID
     * @return The review report if found
     */
    Optional<ReviewReport> findById(String reportId);
    
    /**
     * Finds a review report by code artifact ID
     * 
     * @param codeArtifactId The code artifact ID
     * @return The review report if found
     */
    Optional<ReviewReport> findByCodeArtifactId(String codeArtifactId);
    
    /**
     * Deletes a review report
     * 
     * @param reportId The review report ID to delete
     */
    void deleteById(String reportId);
}
