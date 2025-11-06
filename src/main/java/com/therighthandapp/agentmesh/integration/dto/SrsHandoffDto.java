package com.therighthandapp.agentmesh.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Data Transfer Object for handing off validated SRS from Auto-BADS to AgentMesh
 * This structured format ensures seamless communication between the two systems
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SrsHandoffDto {
    
    // Identification
    private UUID ideaId;
    private String ideaTitle;
    private LocalDateTime generatedAt;
    
    // Business Context
    private String businessCase;
    private String problemStatement;
    private String strategicAlignment;
    
    // Requirements
    private SoftwareRequirementsSpecification srs;
    private List<Feature> prioritizedBacklog;
    private List<String> technicalConstraints;
    private List<String> qualityAttributes;
    
    // Financial & Risk
    private FinancialProjections financials;
    private RiskAssessment riskAssessment;
    
    // Solution Recommendation
    private String recommendedSolutionType; // BUILD, BUY, HYBRID
    private Double recommendationScore;
    
    // Metadata
    private Map<String, Object> metadata;
    
    /**
     * Nested class for Software Requirements Specification
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SoftwareRequirementsSpecification {
        private String version;
        private List<FunctionalRequirement> functionalRequirements;
        private List<NonFunctionalRequirement> nonFunctionalRequirements;
        private SystemArchitecture architecture;
        private List<String> dependencies;
    }
    
    /**
     * Feature with priority and acceptance criteria
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Feature {
        private String id;
        private String name;
        private String description;
        private String priority; // HIGH, MEDIUM, LOW
        private List<String> acceptanceCriteria;
        private Integer estimatedEffort; // story points
    }
    
    /**
     * Functional requirement
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionalRequirement {
        private String id;
        private String requirement;
        private String rationale;
        private List<String> testCases;
    }
    
    /**
     * Non-functional requirement (performance, security, etc.)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NonFunctionalRequirement {
        private String category; // PERFORMANCE, SECURITY, SCALABILITY, etc.
        private String requirement;
        private String metric;
        private String targetValue;
    }
    
    /**
     * High-level system architecture
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SystemArchitecture {
        private String architectureStyle; // MICROSERVICES, MONOLITH, SERVERLESS, etc.
        private List<String> components;
        private List<String> integrationPoints;
        private String databaseStrategy;
    }
    
    /**
     * Financial projections from Auto-BADS analysis
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinancialProjections {
        private Double totalCostOfOwnership;
        private Double developmentCost;
        private Double operationalCostPerYear;
        private Double expectedRoi;
        private Integer breakEvenMonths;
    }
    
    /**
     * Risk assessment results
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskAssessment {
        private String overallRiskLevel; // LOW, MEDIUM, HIGH, CRITICAL
        private List<Risk> identifiedRisks;
    }
    
    /**
     * Individual risk item
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Risk {
        private String category; // TECHNICAL, OPERATIONAL, STRATEGIC
        private String description;
        private String severity; // LOW, MEDIUM, HIGH, CRITICAL
        private String likelihood; // LOW, MEDIUM, HIGH
        private List<String> mitigationStrategies;
    }
}
