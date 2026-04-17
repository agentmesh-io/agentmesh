package com.therighthandapp.agentmesh.agents.reviewer.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.therighthandapp.agentmesh.agents.developer.domain.CodeArtifact;
import com.therighthandapp.agentmesh.agents.reviewer.domain.ReviewReport;
import com.therighthandapp.agentmesh.agents.reviewer.ports.CodeRepository;
import com.therighthandapp.agentmesh.agents.reviewer.ports.ReviewMemoryService;
import com.therighthandapp.agentmesh.agents.reviewer.ports.ReviewRepository;
import com.therighthandapp.agentmesh.agents.reviewer.ports.ReviewerLLMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Application Service: ReviewerAgent Service
 * 
 * Hexagonal Architecture: This is the core application service that orchestrates
 * the code review process. It uses ports to interact with external systems.
 * 
 * Workflow:
 * 1. Retrieve code artifact from Blackboard
 * 2. Find similar past reviews for context
 * 3. Build comprehensive review prompt
 * 4. Generate review using LLM
 * 5. Parse review report
 * 6. Store review in Blackboard
 * 7. Store review in Weaviate for future context
 * 8. Return review report ID
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewerAgentService {
    
    private final CodeRepository codeRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewerLLMService reviewerLLMService;
    private final ReviewMemoryService reviewMemoryService;
    private final ReviewPromptBuilder promptBuilder;
    private final ReviewParser reviewParser;
    private final ObjectMapper objectMapper;
    
    /**
     * Generates a comprehensive code review for a code artifact
     * 
     * @param codeArtifactId The ID of the code artifact to review
     * @return The ID of the generated review report
     */
    public String generateReview(String codeArtifactId) {
        log.info("Starting code review for artifact: {}", codeArtifactId);
        
        try {
            // Step 1: Retrieve code artifact from Blackboard
            log.debug("Step 1: Retrieving code artifact from Blackboard");
            CodeArtifact codeArtifact = codeRepository.findById(codeArtifactId)
                .orElseThrow(() -> new RuntimeException("Code artifact not found: " + codeArtifactId));
            
            log.info("Retrieved code artifact: {} with {} files", 
                    codeArtifact.getProjectTitle(), codeArtifact.getSourceFiles().size());
            
            // Step 2: Find similar past reviews for context
            log.debug("Step 2: Finding similar past reviews");
            String codeDescription = buildCodeDescription(codeArtifact);
            List<ReviewReport> similarReviews = reviewMemoryService.findSimilarReviews(codeDescription, 3);
            String similarReviewsContext = buildSimilarReviewsContext(similarReviews);
            
            log.info("Found {} similar past reviews for context", similarReviews.size());
            
            // Step 3: Convert code artifact to JSON for prompt
            log.debug("Step 3: Converting code artifact to JSON");
            String codeArtifactJson = objectMapper.writeValueAsString(codeArtifact);
            
            // Step 4: Build comprehensive review prompt
            log.debug("Step 4: Building review prompt");
            String prompt = promptBuilder.buildReviewPrompt(codeArtifactJson, similarReviewsContext);
            
            log.debug("Generated prompt with {} characters", prompt.length());
            
            // Step 5: Generate review using LLM
            log.debug("Step 5: Generating review using LLM");
            if (!reviewerLLMService.isAvailable()) {
                throw new RuntimeException("LLM service is not available");
            }
            
            String llmResponse = reviewerLLMService.generateReview(prompt);
            
            log.info("LLM generated review response with {} characters", llmResponse.length());
            
            // Step 6: Parse review report from LLM response
            log.debug("Step 6: Parsing review report");
            ReviewReport reviewReport = reviewParser.parseReviewReport(
                llmResponse,
                codeArtifactId,
                codeArtifact.getProjectTitle(),
                "Code artifact: " + codeArtifact.getArtifactId()
            );
            
            // Validate review report
            reviewReport.validate();
            
            log.info("Parsed review report with score: {} ({}), {} total issues", 
                    reviewReport.getOverallScore().getTotalScore(),
                    reviewReport.getOverallScore().getGrade(),
                    reviewReport.getTotalIssues());
            
            // Step 7: Store review in Blackboard
            log.debug("Step 7: Storing review in Blackboard");
            ReviewReport savedReview = reviewRepository.save(reviewReport);
            
            log.info("Saved review to Blackboard with ID: {}", savedReview.getReportId());
            
            // Step 8: Store review in Weaviate for future context
            log.debug("Step 8: Storing review in Weaviate");
            reviewMemoryService.storeReview(savedReview);
            
            log.info("Stored review in Weaviate for future similarity searches");
            
            // Step 9: Log review summary
            logReviewSummary(savedReview);
            
            log.info("Code review completed successfully for artifact: {}", codeArtifactId);
            
            return savedReview.getReportId();
            
        } catch (Exception e) {
            log.error("Failed to generate review for artifact {}: {}", codeArtifactId, e.getMessage(), e);
            throw new RuntimeException("Failed to generate code review", e);
        }
    }
    
    /**
     * Builds a description of the code artifact for similarity search
     */
    private String buildCodeDescription(CodeArtifact codeArtifact) {
        StringBuilder description = new StringBuilder();
        
        description.append("Project: ").append(codeArtifact.getProjectTitle()).append("\n");
        description.append("Artifact ID: ").append(codeArtifact.getArtifactId()).append("\n");
        description.append("Files: ").append(codeArtifact.getSourceFiles().size()).append("\n");
        description.append("Total LOC: ").append(codeArtifact.getTotalLinesOfCode()).append("\n");
        
        // Add quality metrics if available
        if (codeArtifact.getQualityMetrics() != null) {
            var metrics = codeArtifact.getQualityMetrics();
            description.append("Comment Ratio: ").append(metrics.getCommentRatio()).append("\n");
            description.append("Avg Method Length: ").append(metrics.getAverageMethodLength()).append("\n");
        }
        
        // Add key technologies from dependencies
        if (codeArtifact.getDependencies() != null && !codeArtifact.getDependencies().isEmpty()) {
            description.append("Technologies: ");
            codeArtifact.getDependencies().stream()
                .limit(5)
                .forEach(dep -> description.append(dep.getArtifactId()).append(", "));
            description.append("\n");
        }
        
        return description.toString();
    }
    
    /**
     * Builds context from similar past reviews
     */
    private String buildSimilarReviewsContext(List<ReviewReport> similarReviews) {
        if (similarReviews.isEmpty()) {
            return "";
        }
        
        StringBuilder context = new StringBuilder();
        context.append("Here are some patterns from similar past reviews:\n\n");
        
        for (int i = 0; i < similarReviews.size(); i++) {
            ReviewReport review = similarReviews.get(i);
            context.append(String.format("Review %d:\n", i + 1));
            context.append(String.format("- Score: %d (%s)\n", 
                review.getOverallScore().getTotalScore(),
                review.getOverallScore().getGrade()));
            context.append(String.format("- Total Issues: %d\n", review.getTotalIssues()));
            
            // Add top 3 quality issues if present
            if (review.getQualityIssues() != null && !review.getQualityIssues().isEmpty()) {
                context.append("- Common Quality Issues:\n");
                review.getQualityIssues().stream()
                    .limit(3)
                    .forEach(issue -> context.append(String.format("  * %s: %s\n", 
                        issue.getCategory(), issue.getTitle())));
            }
            
            // Add security issues if present
            if (review.getSecurityIssues() != null && !review.getSecurityIssues().isEmpty()) {
                context.append("- Security Issues:\n");
                review.getSecurityIssues().stream()
                    .limit(2)
                    .forEach(issue -> context.append(String.format("  * %s: %s\n", 
                        issue.getCategory(), issue.getTitle())));
            }
            
            context.append("\n");
        }
        
        return context.toString();
    }
    
    /**
     * Logs a summary of the review report
     */
    private void logReviewSummary(ReviewReport review) {
        log.info("=== REVIEW SUMMARY ===");
        log.info("Project: {}", review.getProjectName());
        log.info("Score: {} ({})", review.getOverallScore().getTotalScore(), 
                review.getOverallScore().getGrade());
        log.info("Quality Score: {}", review.getOverallScore().getQualityScore());
        log.info("Security Score: {}", review.getOverallScore().getSecurityScore());
        log.info("Maintainability Score: {}", review.getOverallScore().getMaintainabilityScore());
        log.info("Total Issues: {}", review.getTotalIssues());
        log.info("Critical Issues: {}", review.getCriticalIssueCount());
        log.info("Quality Issues: {}", review.getQualityIssues() != null ? review.getQualityIssues().size() : 0);
        log.info("Security Issues: {}", review.getSecurityIssues() != null ? review.getSecurityIssues().size() : 0);
        log.info("Best Practice Violations: {}", review.getBestPracticeViolations() != null ? 
                review.getBestPracticeViolations().size() : 0);
        log.info("Suggestions: {}", review.getSuggestions() != null ? review.getSuggestions().size() : 0);
        log.info("Passed: {}", review.isPassed() ? "YES" : "NO");
        log.info("=====================");
    }
}
