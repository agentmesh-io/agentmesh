package com.therighthandapp.agentmesh.agents.reviewer.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.therighthandapp.agentmesh.agents.reviewer.domain.ReviewReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Application Service: Review Parser
 * 
 * Parses LLM-generated JSON responses into ReviewReport domain objects.
 * Handles validation, error recovery, and domain object construction.
 */
@Slf4j
@Component
public class ReviewParser {
    
    private final ObjectMapper objectMapper;
    
    public ReviewParser() {
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Parses LLM JSON response into ReviewReport
     * 
     * @param llmResponse The JSON response from LLM
     * @param codeArtifactId The code artifact ID being reviewed
     * @param projectName The project name
     * @param description Project description
     * @return Parsed ReviewReport
     */
    public ReviewReport parseReviewReport(String llmResponse, String codeArtifactId, 
                                         String projectName, String description) {
        try {
            // Extract JSON from response (handle markdown code blocks)
            String jsonContent = extractJson(llmResponse);
            
            JsonNode root = objectMapper.readTree(jsonContent);
            
            return ReviewReport.builder()
                .reportId(UUID.randomUUID().toString())
                .codeArtifactId(codeArtifactId)
                .projectName(projectName)
                .description(description)
                .overallScore(parseOverallScore(root.path("overallScore")))
                .qualityIssues(parseQualityIssues(root.path("qualityIssues")))
                .securityIssues(parseSecurityIssues(root.path("securityIssues")))
                .bestPracticeViolations(parseBestPracticeViolations(root.path("bestPracticeViolations")))
                .suggestions(parseSuggestions(root.path("suggestions")))
                .codeMetrics(parseCodeMetrics(root.path("codeMetrics")))
                .complexityAnalysis(parseComplexityAnalysis(root.path("complexityAnalysis")))
                .reviewedAt(LocalDateTime.now())
                .reviewerVersion("1.0.0")
                .build();
                
        } catch (Exception e) {
            log.error("Failed to parse review report: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to parse LLM response into ReviewReport", e);
        }
    }
    
    private String extractJson(String response) {
        // Remove markdown code blocks if present
        String cleaned = response.trim();
        
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        
        return cleaned.trim();
    }
    
    private ReviewReport.OverallScore parseOverallScore(JsonNode node) {
        if (node.isMissingNode()) {
            return ReviewReport.OverallScore.builder()
                .totalScore(0)
                .qualityScore(0)
                .securityScore(0)
                .maintainabilityScore(0)
                .grade("F")
                .summary("No assessment provided")
                .build();
        }
        
        return ReviewReport.OverallScore.builder()
            .totalScore(node.path("totalScore").asInt(0))
            .qualityScore(node.path("qualityScore").asInt(0))
            .securityScore(node.path("securityScore").asInt(0))
            .maintainabilityScore(node.path("maintainabilityScore").asInt(0))
            .grade(node.path("grade").asText("N/A"))
            .summary(node.path("summary").asText(""))
            .build();
    }
    
    private List<ReviewReport.QualityIssue> parseQualityIssues(JsonNode node) {
        List<ReviewReport.QualityIssue> issues = new ArrayList<>();
        
        if (node.isArray()) {
            for (JsonNode issueNode : node) {
                issues.add(ReviewReport.QualityIssue.builder()
                    .issueId(UUID.randomUUID().toString())
                    .severity(parseSeverity(issueNode.path("severity").asText("MEDIUM"), 
                                           ReviewReport.QualityIssue.Severity.class))
                    .category(issueNode.path("category").asText(""))
                    .title(issueNode.path("title").asText(""))
                    .description(issueNode.path("description").asText(""))
                    .filePath(issueNode.path("filePath").asText(""))
                    .lineNumber(issueNode.path("lineNumber").asInt(0))
                    .codeSnippet(issueNode.path("codeSnippet").asText(""))
                    .recommendation(issueNode.path("recommendation").asText(""))
                    .build());
            }
        }
        
        return issues;
    }
    
    private List<ReviewReport.SecurityIssue> parseSecurityIssues(JsonNode node) {
        List<ReviewReport.SecurityIssue> issues = new ArrayList<>();
        
        if (node.isArray()) {
            for (JsonNode issueNode : node) {
                issues.add(ReviewReport.SecurityIssue.builder()
                    .issueId(UUID.randomUUID().toString())
                    .severity(parseSeverity(issueNode.path("severity").asText("HIGH"), 
                                           ReviewReport.SecurityIssue.Severity.class))
                    .category(issueNode.path("category").asText(""))
                    .title(issueNode.path("title").asText(""))
                    .description(issueNode.path("description").asText(""))
                    .filePath(issueNode.path("filePath").asText(""))
                    .lineNumber(issueNode.path("lineNumber").asInt(0))
                    .codeSnippet(issueNode.path("codeSnippet").asText(""))
                    .cweId(issueNode.path("cweId").asText(""))
                    .remediation(issueNode.path("remediation").asText(""))
                    .build());
            }
        }
        
        return issues;
    }
    
    private List<ReviewReport.BestPracticeViolation> parseBestPracticeViolations(JsonNode node) {
        List<ReviewReport.BestPracticeViolation> violations = new ArrayList<>();
        
        if (node.isArray()) {
            for (JsonNode violationNode : node) {
                violations.add(ReviewReport.BestPracticeViolation.builder()
                    .violationId(UUID.randomUUID().toString())
                    .category(violationNode.path("category").asText(""))
                    .title(violationNode.path("title").asText(""))
                    .description(violationNode.path("description").asText(""))
                    .filePath(violationNode.path("filePath").asText(""))
                    .lineNumber(violationNode.path("lineNumber").asInt(0))
                    .codeSnippet(violationNode.path("codeSnippet").asText(""))
                    .recommendation(violationNode.path("recommendation").asText(""))
                    .reference(violationNode.path("reference").asText(""))
                    .build());
            }
        }
        
        return violations;
    }
    
    private List<ReviewReport.Suggestion> parseSuggestions(JsonNode node) {
        List<ReviewReport.Suggestion> suggestions = new ArrayList<>();
        
        if (node.isArray()) {
            for (JsonNode suggestionNode : node) {
                suggestions.add(ReviewReport.Suggestion.builder()
                    .suggestionId(UUID.randomUUID().toString())
                    .category(suggestionNode.path("category").asText(""))
                    .title(suggestionNode.path("title").asText(""))
                    .description(suggestionNode.path("description").asText(""))
                    .filePath(suggestionNode.path("filePath").asText(""))
                    .lineNumber(suggestionNode.path("lineNumber").asInt(0))
                    .currentCode(suggestionNode.path("currentCode").asText(""))
                    .suggestedCode(suggestionNode.path("suggestedCode").asText(""))
                    .benefit(suggestionNode.path("benefit").asText(""))
                    .impactScore(suggestionNode.path("impactScore").asInt(5))
                    .build());
            }
        }
        
        return suggestions;
    }
    
    private ReviewReport.CodeMetrics parseCodeMetrics(JsonNode node) {
        if (node.isMissingNode()) {
            return ReviewReport.CodeMetrics.builder()
                .totalFiles(0)
                .totalLines(0)
                .codeLines(0)
                .commentLines(0)
                .commentRatio(0.0)
                .duplicatedLines(0)
                .duplicationRatio(0.0)
                .languageDistribution(new HashMap<>())
                .build();
        }
        
        Map<String, Integer> langDist = new HashMap<>();
        JsonNode langNode = node.path("languageDistribution");
        if (langNode.isObject()) {
            langNode.fields().forEachRemaining(entry -> 
                langDist.put(entry.getKey(), entry.getValue().asInt(0)));
        }
        
        return ReviewReport.CodeMetrics.builder()
            .totalFiles(node.path("totalFiles").asInt(0))
            .totalLines(node.path("totalLines").asInt(0))
            .codeLines(node.path("codeLines").asInt(0))
            .commentLines(node.path("commentLines").asInt(0))
            .commentRatio(node.path("commentRatio").asDouble(0.0))
            .duplicatedLines(node.path("duplicatedLines").asInt(0))
            .duplicationRatio(node.path("duplicationRatio").asDouble(0.0))
            .languageDistribution(langDist)
            .build();
    }
    
    private ReviewReport.ComplexityAnalysis parseComplexityAnalysis(JsonNode node) {
        if (node.isMissingNode()) {
            return ReviewReport.ComplexityAnalysis.builder()
                .averageCyclomaticComplexity(0.0)
                .maxCyclomaticComplexity(0)
                .mostComplexMethod("")
                .mostComplexFile("")
                .complexMethods(new ArrayList<>())
                .cognitiveComplexity(0)
                .complexityGrade("N/A")
                .build();
        }
        
        List<String> complexMethods = new ArrayList<>();
        JsonNode methodsNode = node.path("complexMethods");
        if (methodsNode.isArray()) {
            methodsNode.forEach(method -> complexMethods.add(method.asText()));
        }
        
        return ReviewReport.ComplexityAnalysis.builder()
            .averageCyclomaticComplexity(node.path("averageCyclomaticComplexity").asDouble(0.0))
            .maxCyclomaticComplexity(node.path("maxCyclomaticComplexity").asInt(0))
            .mostComplexMethod(node.path("mostComplexMethod").asText(""))
            .mostComplexFile(node.path("mostComplexFile").asText(""))
            .complexMethods(complexMethods)
            .cognitiveComplexity(node.path("cognitiveComplexity").asInt(0))
            .complexityGrade(node.path("complexityGrade").asText("N/A"))
            .build();
    }
    
    private <T extends Enum<T>> T parseSeverity(String severityText, Class<T> enumClass) {
        try {
            return Enum.valueOf(enumClass, severityText.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid severity: {}, defaulting to MEDIUM", severityText);
            return Enum.valueOf(enumClass, "MEDIUM");
        }
    }
}
