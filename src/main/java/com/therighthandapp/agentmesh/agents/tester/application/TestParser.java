package com.therighthandapp.agentmesh.agents.tester.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.therighthandapp.agentmesh.agents.tester.domain.TestSuite;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Application Service: Test Parser
 * 
 * Parses LLM-generated JSON responses into TestSuite domain objects.
 * Handles validation, error recovery, and domain object construction.
 */
@Slf4j
@Component
public class TestParser {
    
    private final ObjectMapper objectMapper;
    
    public TestParser() {
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Parses LLM JSON response into TestSuite
     * 
     * @param llmResponse The JSON response from LLM
     * @param codeArtifactId The code artifact ID being tested
     * @param projectName The project name
     * @param description Project description
     * @return Parsed TestSuite
     */
    public TestSuite parseTestSuite(String llmResponse, String codeArtifactId,
                                   String projectName, String description) {
        try {
            // Extract JSON from response (handle markdown code blocks)
            String jsonContent = extractJson(llmResponse);
            
            JsonNode root = objectMapper.readTree(jsonContent);
            
            return TestSuite.builder()
                .testSuiteId(UUID.randomUUID().toString())
                .codeArtifactId(codeArtifactId)
                .projectName(projectName)
                .description(description)
                .unitTests(parseUnitTests(root.path("unitTests")))
                .integrationTests(parseIntegrationTests(root.path("integrationTests")))
                .e2eTests(parseE2ETests(root.path("e2eTests")))
                .coverage(parseCoverageReport(root.path("coverage")))
                .qualityMetrics(parseQualityMetrics(root.path("qualityMetrics")))
                .testGaps(parseTestGaps(root.path("testGaps")))
                .recommendations(parseRecommendations(root.path("recommendations")))
                .generatedAt(LocalDateTime.now())
                .testerVersion("1.0.0")
                .build();
                
        } catch (Exception e) {
            log.error("Failed to parse test suite: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to parse LLM response into TestSuite", e);
        }
    }
    
    private String extractJson(String response) {
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
    
    private List<TestSuite.UnitTest> parseUnitTests(JsonNode node) {
        List<TestSuite.UnitTest> tests = new ArrayList<>();
        
        if (node.isArray()) {
            for (JsonNode testNode : node) {
                tests.add(TestSuite.UnitTest.builder()
                    .testId(UUID.randomUUID().toString())
                    .className(testNode.path("className").asText(""))
                    .methodName(testNode.path("methodName").asText(""))
                    .testName(testNode.path("testName").asText(""))
                    .description(testNode.path("description").asText(""))
                    .type(parseTestType(testNode.path("type").asText("POSITIVE")))
                    .testCases(parseStringList(testNode.path("testCases")))
                    .testCode(testNode.path("testCode").asText(""))
                    .assertions(parseStringList(testNode.path("assertions")))
                    .mockedDependencies(parseStringList(testNode.path("mockedDependencies")))
                    .filePath(testNode.path("filePath").asText(""))
                    .build());
            }
        }
        
        return tests;
    }
    
    private List<TestSuite.IntegrationTest> parseIntegrationTests(JsonNode node) {
        List<TestSuite.IntegrationTest> tests = new ArrayList<>();
        
        if (node.isArray()) {
            for (JsonNode testNode : node) {
                tests.add(TestSuite.IntegrationTest.builder()
                    .testId(UUID.randomUUID().toString())
                    .testName(testNode.path("testName").asText(""))
                    .description(testNode.path("description").asText(""))
                    .componentsUnderTest(parseStringList(testNode.path("componentsUnderTest")))
                    .testCode(testNode.path("testCode").asText(""))
                    .dependencies(parseStringList(testNode.path("dependencies")))
                    .setupCode(testNode.path("setupCode").asText(""))
                    .teardownCode(testNode.path("teardownCode").asText(""))
                    .filePath(testNode.path("filePath").asText(""))
                    .build());
            }
        }
        
        return tests;
    }
    
    private List<TestSuite.E2ETest> parseE2ETests(JsonNode node) {
        List<TestSuite.E2ETest> tests = new ArrayList<>();
        
        if (node.isArray()) {
            for (JsonNode testNode : node) {
                tests.add(TestSuite.E2ETest.builder()
                    .testId(UUID.randomUUID().toString())
                    .testName(testNode.path("testName").asText(""))
                    .description(testNode.path("description").asText(""))
                    .scenario(testNode.path("scenario").asText(""))
                    .steps(parseStringList(testNode.path("steps")))
                    .testCode(testNode.path("testCode").asText(""))
                    .expectedOutcome(testNode.path("expectedOutcome").asText(""))
                    .filePath(testNode.path("filePath").asText(""))
                    .build());
            }
        }
        
        return tests;
    }
    
    private TestSuite.CoverageReport parseCoverageReport(JsonNode node) {
        if (node.isMissingNode()) {
            return TestSuite.CoverageReport.builder()
                .overallCoverage(0.0)
                .lineCoverage(0.0)
                .branchCoverage(0.0)
                .methodCoverage(0.0)
                .totalLines(0)
                .coveredLines(0)
                .totalBranches(0)
                .coveredBranches(0)
                .totalMethods(0)
                .coveredMethods(0)
                .fileCoverage(new HashMap<>())
                .build();
        }
        
        Map<String, TestSuite.CoverageReport.FileCoverage> fileCoverage = new HashMap<>();
        JsonNode fileCoverageNode = node.path("fileCoverage");
        if (fileCoverageNode.isObject()) {
            fileCoverageNode.fields().forEachRemaining(entry -> {
                JsonNode fileNode = entry.getValue();
                fileCoverage.put(entry.getKey(),
                    TestSuite.CoverageReport.FileCoverage.builder()
                        .filePath(fileNode.path("filePath").asText(""))
                        .coverage(fileNode.path("coverage").asDouble(0.0))
                        .totalLines(fileNode.path("totalLines").asInt(0))
                        .coveredLines(fileNode.path("coveredLines").asInt(0))
                        .uncoveredLines(parseIntList(fileNode.path("uncoveredLines")))
                        .build());
            });
        }
        
        return TestSuite.CoverageReport.builder()
            .overallCoverage(node.path("overallCoverage").asDouble(0.0))
            .lineCoverage(node.path("lineCoverage").asDouble(0.0))
            .branchCoverage(node.path("branchCoverage").asDouble(0.0))
            .methodCoverage(node.path("methodCoverage").asDouble(0.0))
            .totalLines(node.path("totalLines").asInt(0))
            .coveredLines(node.path("coveredLines").asInt(0))
            .totalBranches(node.path("totalBranches").asInt(0))
            .coveredBranches(node.path("coveredBranches").asInt(0))
            .totalMethods(node.path("totalMethods").asInt(0))
            .coveredMethods(node.path("coveredMethods").asInt(0))
            .fileCoverage(fileCoverage)
            .build();
    }
    
    private TestSuite.TestQualityMetrics parseQualityMetrics(JsonNode node) {
        if (node.isMissingNode()) {
            return TestSuite.TestQualityMetrics.builder()
                .totalTests(0)
                .unitTestCount(0)
                .integrationTestCount(0)
                .e2eTestCount(0)
                .assertionDensity(0.0)
                .mockUsageRatio(0.0)
                .testComplexity(0.0)
                .qualityGrade("N/A")
                .build();
        }
        
        return TestSuite.TestQualityMetrics.builder()
            .totalTests(node.path("totalTests").asInt(0))
            .unitTestCount(node.path("unitTestCount").asInt(0))
            .integrationTestCount(node.path("integrationTestCount").asInt(0))
            .e2eTestCount(node.path("e2eTestCount").asInt(0))
            .assertionDensity(node.path("assertionDensity").asDouble(0.0))
            .mockUsageRatio(node.path("mockUsageRatio").asDouble(0.0))
            .testComplexity(node.path("testComplexity").asDouble(0.0))
            .qualityGrade(node.path("qualityGrade").asText("N/A"))
            .build();
    }
    
    private List<TestSuite.TestGap> parseTestGaps(JsonNode node) {
        List<TestSuite.TestGap> gaps = new ArrayList<>();
        
        if (node.isArray()) {
            for (JsonNode gapNode : node) {
                gaps.add(TestSuite.TestGap.builder()
                    .gapId(UUID.randomUUID().toString())
                    .type(parseGapType(gapNode.path("type").asText("MISSING_UNIT_TEST")))
                    .description(gapNode.path("description").asText(""))
                    .component(gapNode.path("component").asText(""))
                    .filePath(gapNode.path("filePath").asText(""))
                    .methodName(gapNode.path("methodName").asText(""))
                    .severity(parseGapSeverity(gapNode.path("severity").asText("MEDIUM")))
                    .recommendation(gapNode.path("recommendation").asText(""))
                    .build());
            }
        }
        
        return gaps;
    }
    
    private List<TestSuite.TestRecommendation> parseRecommendations(JsonNode node) {
        List<TestSuite.TestRecommendation> recommendations = new ArrayList<>();
        
        if (node.isArray()) {
            for (JsonNode recNode : node) {
                recommendations.add(TestSuite.TestRecommendation.builder()
                    .recommendationId(UUID.randomUUID().toString())
                    .category(recNode.path("category").asText(""))
                    .title(recNode.path("title").asText(""))
                    .description(recNode.path("description").asText(""))
                    .rationale(recNode.path("rationale").asText(""))
                    .impactScore(recNode.path("impactScore").asInt(5))
                    .suggestedTestCode(recNode.path("suggestedTestCode").asText(""))
                    .build());
            }
        }
        
        return recommendations;
    }
    
    private List<String> parseStringList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(item -> list.add(item.asText()));
        }
        return list;
    }
    
    private List<Integer> parseIntList(JsonNode node) {
        List<Integer> list = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(item -> list.add(item.asInt()));
        }
        return list;
    }
    
    private TestSuite.UnitTest.TestType parseTestType(String typeText) {
        try {
            return TestSuite.UnitTest.TestType.valueOf(typeText.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid test type: {}, defaulting to POSITIVE", typeText);
            return TestSuite.UnitTest.TestType.POSITIVE;
        }
    }
    
    private TestSuite.TestGap.GapType parseGapType(String typeText) {
        try {
            return TestSuite.TestGap.GapType.valueOf(typeText.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid gap type: {}, defaulting to MISSING_UNIT_TEST", typeText);
            return TestSuite.TestGap.GapType.MISSING_UNIT_TEST;
        }
    }
    
    private TestSuite.TestGap.Severity parseGapSeverity(String severityText) {
        try {
            return TestSuite.TestGap.Severity.valueOf(severityText.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid severity: {}, defaulting to MEDIUM", severityText);
            return TestSuite.TestGap.Severity.MEDIUM;
        }
    }
}
