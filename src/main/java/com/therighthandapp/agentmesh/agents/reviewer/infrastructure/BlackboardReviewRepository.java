package com.therighthandapp.agentmesh.agents.reviewer.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.therighthandapp.agentmesh.agents.reviewer.domain.ReviewReport;
import com.therighthandapp.agentmesh.agents.reviewer.ports.ReviewRepository;
import com.therighthandapp.agentmesh.blackboard.BlackboardEntry;
import com.therighthandapp.agentmesh.blackboard.BlackboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Infrastructure Adapter: Blackboard Review Repository
 * 
 * Hexagonal Architecture: This is an adapter that implements the ReviewRepository port.
 * Stores review reports in the Blackboard with type="REVIEW".
 */
@Slf4j
@RequiredArgsConstructor
public class BlackboardReviewRepository implements ReviewRepository {
    
    private final BlackboardService blackboardService;
    private final ObjectMapper objectMapper;
    
    private static final String ENTRY_TYPE = "REVIEW";
    private static final String AGENT_ID = "reviewer-agent";
    
    @Override
    public ReviewReport save(ReviewReport reviewReport) {
        try {
            log.debug("Saving review report to Blackboard: {}", reviewReport.getReportId());
            
            String jsonContent = objectMapper.writeValueAsString(reviewReport);
            
            BlackboardEntry entry = blackboardService.post(
                AGENT_ID,
                ENTRY_TYPE,
                "Review Report: " + reviewReport.getProjectName(),
                jsonContent
            );
            
            log.info("Successfully saved review report to Blackboard: reportId={}, entryId={}",
                    reviewReport.getReportId(), entry.getId());
            
            return reviewReport;
            
        } catch (Exception e) {
            log.error("Failed to save review report to Blackboard: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save review report", e);
        }
    }
    
    @Override
    public Optional<ReviewReport> findById(String reportId) {
        try {
            log.debug("Finding review report by ID: {}", reportId);
            
            List<BlackboardEntry> entries = blackboardService.readByType(ENTRY_TYPE);
            
            for (BlackboardEntry entry : entries) {
                try {
                    ReviewReport reviewReport = objectMapper.readValue(
                        entry.getContent(),
                        ReviewReport.class
                    );
                    
                    if (reportId.equals(reviewReport.getReportId())) {
                        log.debug("Found review report: {}", reportId);
                        return Optional.of(reviewReport);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse Blackboard entry {} as ReviewReport: {}", 
                            entry.getId(), e.getMessage());
                }
            }
            
            log.debug("Review report not found: {}", reportId);
            return Optional.empty();
            
        } catch (Exception e) {
            log.error("Failed to find review report {}: {}", reportId, e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    @Override
    public Optional<ReviewReport> findByCodeArtifactId(String codeArtifactId) {
        try {
            log.debug("Finding review report by code artifact ID: {}", codeArtifactId);
            
            List<BlackboardEntry> entries = blackboardService.readByType(ENTRY_TYPE);
            
            for (BlackboardEntry entry : entries) {
                try {
                    ReviewReport reviewReport = objectMapper.readValue(
                        entry.getContent(),
                        ReviewReport.class
                    );
                    
                    if (codeArtifactId.equals(reviewReport.getCodeArtifactId())) {
                        log.debug("Found review report for code artifact: {}", codeArtifactId);
                        return Optional.of(reviewReport);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse Blackboard entry {} as ReviewReport: {}", 
                            entry.getId(), e.getMessage());
                }
            }
            
            log.debug("No review report found for code artifact: {}", codeArtifactId);
            return Optional.empty();
            
        } catch (Exception e) {
            log.error("Failed to find review by code artifact {}: {}", codeArtifactId, e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    @Override
    public void deleteById(String reportId) {
        log.warn("Delete operation not supported in BlackboardService - reportId: {}", reportId);
        // BlackboardService doesn't provide a delete method
        // This is intentional as Blackboard maintains full history
    }
}
