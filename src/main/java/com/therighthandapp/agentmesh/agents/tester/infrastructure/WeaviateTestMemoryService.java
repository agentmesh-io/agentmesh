package com.therighthandapp.agentmesh.agents.tester.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.therighthandapp.agentmesh.agents.tester.domain.TestSuite;
import com.therighthandapp.agentmesh.agents.tester.ports.TestMemoryService;
import com.therighthandapp.agentmesh.memory.MemoryArtifact;
import com.therighthandapp.agentmesh.memory.WeaviateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Infrastructure Adapter: Weaviate Test Memory Service
 * 
 * Implements TestMemoryService port using Weaviate for vector storage.
 * Stores test suites with embeddings for context-aware similarity search.
 */
@Slf4j
@RequiredArgsConstructor
public class WeaviateTestMemoryService implements TestMemoryService {
    
    private static final String AGENT_ID = "tester-agent";
    private static final String ARTIFACT_TYPE = "TEST";
    
    private final WeaviateService weaviateService;
    private final ObjectMapper objectMapper;
    
    @Override
    public void storeTestSuite(TestSuite testSuite) {
        try {
            MemoryArtifact artifact = new MemoryArtifact();
            artifact.setAgentId(AGENT_ID);
            artifact.setArtifactType(ARTIFACT_TYPE);
            artifact.setContent(buildTestSuiteContent(testSuite));
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("testSuiteId", testSuite.getTestSuiteId());
            metadata.put("codeArtifactId", testSuite.getCodeArtifactId());
            metadata.put("projectName", testSuite.getProjectName());
            metadata.put("totalTests", testSuite.getTotalTestCount());
            metadata.put("unitTests", testSuite.getUnitTests().size());
            metadata.put("integrationTests", testSuite.getIntegrationTests().size());
            metadata.put("e2eTests", testSuite.getE2eTests().size());
            
            if (testSuite.getCoverage() != null) {
                metadata.put("overallCoverage", testSuite.getCoverage().getOverallCoverage());
                metadata.put("lineCoverage", testSuite.getCoverage().getLineCoverage());
                metadata.put("branchCoverage", testSuite.getCoverage().getBranchCoverage());
            }
            
            if (testSuite.getQualityMetrics() != null) {
                metadata.put("qualityGrade", testSuite.getQualityMetrics().getQualityGrade());
                metadata.put("assertionDensity", testSuite.getQualityMetrics().getAssertionDensity());
            }
            
            metadata.put("testGapsCount", testSuite.getTestGaps().size());
            metadata.put("criticalGapsCount", testSuite.getCriticalGapsCount());
            metadata.put("meetsQualityStandards", testSuite.meetsQualityStandards());
            
            // Store complete test suite as JSON
            String testSuiteJson = objectMapper.writeValueAsString(testSuite);
            metadata.put("testSuiteData", testSuiteJson);
            
            artifact.setMetadata(metadata);
            
            String storedId = weaviateService.store(artifact);
            
            log.info("Stored test suite in Weaviate: {} (Project: {}, ID: {})",
                storedId, testSuite.getProjectName(), testSuite.getTestSuiteId());
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize test suite for Weaviate", e);
            throw new RuntimeException("Failed to store test suite in memory", e);
        }
    }
    
    @Override
    public List<TestSuite> findSimilarTestSuites(String codeDescription, int limit) {
        try {
            List<MemoryArtifact> results = weaviateService.multiVectorSearch(
                codeDescription,
                limit,
                AGENT_ID
            );
            
            log.info("Found {} similar test suites for: '{}'",
                results.size(), codeDescription.substring(0, Math.min(50, codeDescription.length())));
            
            // Convert MemoryArtifact to TestSuite
            return results.stream()
                .map(artifact -> {
                    try {
                        return objectMapper.readValue(artifact.getContent(), TestSuite.class);
                    } catch (JsonProcessingException e) {
                        log.error("Failed to deserialize test suite from memory", e);
                        return null;
                    }
                })
                .filter(suite -> suite != null)
                .toList();
            
        } catch (Exception e) {
            log.error("Failed to search for similar test suites", e);
            return Collections.emptyList();
        }
    }
    
    @Override
    public TestStatistics getTestStatistics(String category) {
        try {
            // Search for all test suites
            List<MemoryArtifact> allTests = weaviateService.multiVectorSearch(
                category,
                100,
                AGENT_ID
            );
            
            if (allTests.isEmpty()) {
                return new TestStatistics(
                    category,
                    0,
                    0.0,
                    0,
                    Collections.emptyList()
                );
            }
            
            // Aggregate statistics
            int totalTestSuites = allTests.size();
            double totalCoverage = 0.0;
            int totalTests = 0;
            Map<String, Integer> patternCounts = new HashMap<>();
            
            for (MemoryArtifact artifact : allTests) {
                Map<String, Object> metadata = artifact.getMetadata();
                
                if (metadata.containsKey("overallCoverage")) {
                    totalCoverage += ((Number) metadata.get("overallCoverage")).doubleValue();
                }
                
                if (metadata.containsKey("totalTests")) {
                    totalTests += ((Number) metadata.get("totalTests")).intValue();
                }
                
                // Track quality patterns
                if (metadata.containsKey("qualityGrade")) {
                    String grade = metadata.get("qualityGrade").toString();
                    patternCounts.merge(grade, 1, Integer::sum);
                }
            }
            
            double averageCoverage = totalCoverage / totalTestSuites;
            
            List<String> commonPatterns = patternCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();
            
            return new TestStatistics(
                category,
                totalTestSuites,
                averageCoverage,
                totalTests,
                commonPatterns
            );
            
        } catch (Exception e) {
            log.error("Failed to retrieve test statistics", e);
            return new TestStatistics(
                category,
                0,
                0.0,
                0,
                Collections.emptyList()
            );
        }
    }
    
    /**
     * Builds content for Weaviate embedding
     */
    private String buildTestSuiteContent(TestSuite testSuite) {
        StringBuilder content = new StringBuilder();
        
        content.append("Project: ").append(testSuite.getProjectName()).append("\n");
        content.append("Description: ").append(testSuite.getDescription()).append("\n\n");
        
        content.append("Test Suite Summary:\n");
        content.append("Total Tests: ").append(testSuite.getTotalTestCount()).append("\n");
        content.append("Unit Tests: ").append(testSuite.getUnitTests().size()).append("\n");
        content.append("Integration Tests: ").append(testSuite.getIntegrationTests().size()).append("\n");
        content.append("E2E Tests: ").append(testSuite.getE2eTests().size()).append("\n\n");
        
        if (testSuite.getCoverage() != null) {
            content.append("Coverage: ").append(testSuite.getCoverage().getOverallCoverage())
                .append("% overall\n\n");
        }
        
        // Sample unit tests (first 3)
        if (!testSuite.getUnitTests().isEmpty()) {
            content.append("Unit Test Examples:\n");
            testSuite.getUnitTests().stream()
                .limit(3)
                .forEach(test -> {
                    content.append("- ").append(test.getTestName())
                        .append(" (").append(test.getType()).append(")\n");
                    content.append("  ").append(test.getDescription()).append("\n");
                });
            content.append("\n");
        }
        
        // Integration test examples
        if (!testSuite.getIntegrationTests().isEmpty()) {
            content.append("Integration Test Examples:\n");
            testSuite.getIntegrationTests().stream()
                .limit(2)
                .forEach(test -> {
                    content.append("- ").append(test.getTestName()).append("\n");
                    content.append("  Components: ")
                        .append(String.join(", ", test.getComponentsUnderTest()))
                        .append("\n");
                });
            content.append("\n");
        }
        
        // Test gaps
        if (!testSuite.getTestGaps().isEmpty()) {
            content.append("Test Gaps (").append(testSuite.getTestGaps().size()).append("):\n");
            testSuite.getTestGaps().stream()
                .limit(3)
                .forEach(gap -> {
                    content.append("- ").append(gap.getType())
                        .append(" (").append(gap.getSeverity()).append("): ")
                        .append(gap.getDescription()).append("\n");
                });
        }
        
        return content.toString();
    }
}
