package com.therighthandapp.agentmesh.agents.reviewer.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.therighthandapp.agentmesh.agents.reviewer.domain.ReviewReport;
import com.therighthandapp.agentmesh.agents.reviewer.ports.ReviewMemoryService;
import com.therighthandapp.agentmesh.memory.MemoryArtifact;
import com.therighthandapp.agentmesh.memory.WeaviateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Infrastructure Adapter: Weaviate Review Memory Service
 * 
 * Hexagonal Architecture: This is an adapter that implements the ReviewMemoryService port.
 * Stores review reports in Weaviate for similarity-based retrieval.
 */
@Slf4j
@RequiredArgsConstructor
public class WeaviateReviewMemoryService implements ReviewMemoryService {
    
    private final WeaviateService weaviateService;
    private final ObjectMapper objectMapper;
    
    private static final String AGENT_ID = "reviewer-agent";
    private static final String ARTIFACT_TYPE = "REVIEW";
    
    @Override
    public void storeReview(ReviewReport reviewReport) {
        try {
            log.debug("Storing review in Weaviate: {}", reviewReport.getReportId());
            
            // Build content for embedding (summary of review)
            String content = buildReviewContent(reviewReport);
            
            // Create memory artifact
            MemoryArtifact artifact = new MemoryArtifact();
            artifact.setAgentId(AGENT_ID);
            artifact.setArtifactType(ARTIFACT_TYPE);
            artifact.setTitle("Review: " + reviewReport.getProjectName());
            artifact.setContent(content);
            artifact.setProjectId(reviewReport.getReportId());
            
            // Build metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("reportId", reviewReport.getReportId());
            metadata.put("codeArtifactId", reviewReport.getCodeArtifactId());
            metadata.put("projectName", reviewReport.getProjectName());
            metadata.put("totalScore", reviewReport.getOverallScore().getTotalScore());
            metadata.put("grade", reviewReport.getOverallScore().getGrade());
            metadata.put("totalIssues", reviewReport.getTotalIssues());
            metadata.put("qualityIssues", reviewReport.getQualityIssues() != null ? 
                reviewReport.getQualityIssues().size() : 0);
            metadata.put("securityIssues", reviewReport.getSecurityIssues() != null ? 
                reviewReport.getSecurityIssues().size() : 0);
            metadata.put("passed", reviewReport.isPassed());
            metadata.put("artifactType", ARTIFACT_TYPE);
            metadata.put("agentId", AGENT_ID);
            
            // Store full review report as JSON in metadata
            String reviewJson = objectMapper.writeValueAsString(reviewReport);
            metadata.put("reviewData", reviewJson);
            
            artifact.setMetadata(metadata);
            
            String storedId = weaviateService.store(artifact);
            log.info("Successfully stored review in Weaviate: {} (id: {})", 
                    reviewReport.getReportId(), storedId);
            
        } catch (Exception e) {
            log.error("Failed to store review in Weaviate: {}", e.getMessage(), e);
            // Don't throw - Weaviate storage is optional
        }
    }
    
    @Override
    public List<ReviewReport> findSimilarReviews(String codeDescription, int limit) {
        try {
            log.debug("Finding similar reviews for: {}", codeDescription.substring(0, Math.min(100, codeDescription.length())));
            
            List<MemoryArtifact> results = weaviateService.multiVectorSearch(
                codeDescription,
                limit,
                AGENT_ID
            );
            
            List<ReviewReport> reviews = new ArrayList<>();
            
            for (MemoryArtifact artifact : results) {
                if (ARTIFACT_TYPE.equals(artifact.getArtifactType())) {
                    try {
                        Map<String, Object> metadata = artifact.getMetadata();
                        if (metadata != null && metadata.containsKey("reviewData")) {
                            String reviewJson = (String) metadata.get("reviewData");
                            ReviewReport review = objectMapper.readValue(reviewJson, ReviewReport.class);
                            reviews.add(review);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse review from Weaviate result: {}", e.getMessage());
                    }
                }
            }
            
            log.info("Found {} similar reviews", reviews.size());
            
            return reviews;
            
        } catch (Exception e) {
            log.error("Failed to find similar reviews: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public ReviewStatistics getReviewStatistics(String category) {
        try {
            log.debug("Getting review statistics for category: {}", category);
            
            // Query Weaviate for reviews
            // This is a simplified implementation - could be enhanced with aggregation
            List<MemoryArtifact> results = weaviateService.multiVectorSearch(
                "category:" + category,
                100,
                AGENT_ID
            );
            
            if (results.isEmpty()) {
                return new ReviewStatistics(category, 0, 0.0, 0, new ArrayList<>());
            }
            
            // Calculate statistics
            int totalReviews = 0;
            double totalScore = 0.0;
            Map<String, Integer> issueTypeCounts = new HashMap<>();
            
            for (MemoryArtifact artifact : results) {
                if (ARTIFACT_TYPE.equals(artifact.getArtifactType())) {
                    Map<String, Object> metadata = artifact.getMetadata();
                    if (metadata != null) {
                        Object scoreObj = metadata.get("totalScore");
                        if (scoreObj instanceof Number) {
                            totalScore += ((Number) scoreObj).doubleValue();
                            totalReviews++;
                        }
                    }
                }
            }
            
            double averageScore = totalReviews > 0 ? totalScore / totalReviews : 0.0;
            
            List<String> topIssueTypes = issueTypeCounts.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();
            
            return new ReviewStatistics(
                category,
                totalReviews,
                averageScore,
                issueTypeCounts.values().stream().mapToInt(Integer::intValue).sum(),
                topIssueTypes
            );
            
        } catch (Exception e) {
            log.error("Failed to get review statistics: {}", e.getMessage(), e);
            return new ReviewStatistics(category, 0, 0.0, 0, new ArrayList<>());
        }
    }
    
    /**
     * Builds content for embedding from review report
     */
    private String buildReviewContent(ReviewReport reviewReport) {
        StringBuilder content = new StringBuilder();
        
        content.append("Project: ").append(reviewReport.getProjectName()).append("\n");
        content.append("Score: ").append(reviewReport.getOverallScore().getTotalScore())
               .append(" (").append(reviewReport.getOverallScore().getGrade()).append(")\n");
        content.append("Summary: ").append(reviewReport.getOverallScore().getSummary()).append("\n\n");
        
        // Add quality issues
        if (reviewReport.getQualityIssues() != null && !reviewReport.getQualityIssues().isEmpty()) {
            content.append("Quality Issues:\n");
            reviewReport.getQualityIssues().forEach(issue -> 
                content.append("- ").append(issue.getCategory()).append(": ")
                       .append(issue.getTitle()).append("\n")
            );
        }
        
        // Add security issues
        if (reviewReport.getSecurityIssues() != null && !reviewReport.getSecurityIssues().isEmpty()) {
            content.append("Security Issues:\n");
            reviewReport.getSecurityIssues().forEach(issue -> 
                content.append("- ").append(issue.getCategory()).append(": ")
                       .append(issue.getTitle()).append("\n")
            );
        }
        
        // Add best practice violations
        if (reviewReport.getBestPracticeViolations() != null && !reviewReport.getBestPracticeViolations().isEmpty()) {
            content.append("Best Practice Violations:\n");
            reviewReport.getBestPracticeViolations().stream()
                .limit(5)
                .forEach(violation -> 
                    content.append("- ").append(violation.getCategory()).append(": ")
                           .append(violation.getTitle()).append("\n")
                );
        }
        
        return content.toString();
    }
}
