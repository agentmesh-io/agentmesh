package com.therighthandapp.agentmesh.agents.tester.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.therighthandapp.agentmesh.agents.developer.domain.CodeArtifact;
import com.therighthandapp.agentmesh.agents.tester.domain.TestSuite;
import com.therighthandapp.agentmesh.agents.tester.ports.CodeRepository;
import com.therighthandapp.agentmesh.agents.tester.ports.TesterLLMService;
import com.therighthandapp.agentmesh.agents.tester.ports.TestMemoryService;
import com.therighthandapp.agentmesh.agents.tester.ports.TestSuiteRepository;
import com.therighthandapp.agentmesh.memory.MemoryArtifact;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Application Service: TesterAgent Service
 * 
 * Orchestrates the complete test suite generation workflow:
 * 1. Retrieve code artifact from Blackboard
 * 2. Find similar past test suites (context-aware)
 * 3. Convert code artifact to JSON
 * 4. Build comprehensive test prompt
 * 5. Generate tests using LLM
 * 6. Parse test suite from JSON
 * 7. Store test suite in Blackboard
 * 8. Store test suite in Weaviate (for future similarity search)
 * 9. Log test suite summary
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TesterAgentService {
    
    private final TestSuiteRepository testSuiteRepository;
    private final CodeRepository codeRepository;
    private final TesterLLMService llmService;
    private final TestMemoryService memoryService;
    private final TestPromptBuilder promptBuilder;
    private final TestParser testParser;
    private final ObjectMapper objectMapper;
    
    /**
     * Generates comprehensive test suite for code artifact
     * 
     * @param codeArtifactId The ID of the code artifact to test
     * @return The ID of the generated test suite
     */
    public String generateTestSuite(String codeArtifactId) {
        log.info("Starting test suite generation for code artifact: {}", codeArtifactId);
        
        // 1. Retrieve code artifact
        CodeArtifact codeArtifact = codeRepository.findById(codeArtifactId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Code artifact not found: " + codeArtifactId));
        
        log.info("Retrieved code artifact: {} (Project: {})", 
            codeArtifact.getArtifactId(), codeArtifact.getProjectTitle());
        
        // 2. Find similar test suites (context-aware)
        String codeDescription = buildCodeDescription(codeArtifact);
        List<TestSuite> similarTests = memoryService.findSimilarTestSuites(
            codeDescription, 3);
        
        log.info("Found {} similar test suites for context", similarTests.size());
        
        // 3. Convert code artifact to JSON
        String codeArtifactJson;
        try {
            codeArtifactJson = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(codeArtifact);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize code artifact", e);
            throw new RuntimeException("Failed to serialize code artifact", e);
        }
        
        // 4. Build comprehensive test prompt
        String similarTestsContext = buildSimilarTestsContext(similarTests);
        String prompt = promptBuilder.buildTestPrompt(codeArtifactJson, similarTestsContext);
        
        log.debug("Built test prompt (length: {} chars)", prompt.length());
        
        // 5. Generate tests using LLM
        String llmResponse = llmService.generateTests(prompt);
        
        log.info("Received test suite from LLM (length: {} chars)", llmResponse.length());
        
        // 6. Parse test suite from JSON
        TestSuite testSuite = testParser.parseTestSuite(
            llmResponse,
            codeArtifactId,
            codeArtifact.getProjectTitle(),
            "Test suite for: " + codeArtifact.getArtifactId()
        );
        
        log.info("Parsed test suite: {} total tests ({} unit, {} integration, {} E2E)",
            testSuite.getTotalTestCount(),
            testSuite.getUnitTests().size(),
            testSuite.getIntegrationTests().size(),
            testSuite.getE2eTests().size());
        
        // 7. Store test suite in Blackboard
        TestSuite savedTestSuite = testSuiteRepository.save(testSuite);
        String testSuiteId = savedTestSuite.getTestSuiteId();
        
        log.info("Stored test suite in Blackboard: {}", testSuiteId);
        
        // 8. Store test suite in Weaviate
        memoryService.storeTestSuite(testSuite);
        
        log.info("Stored test suite in Weaviate for future context");
        
        // 9. Log test suite summary
        logTestSuiteSummary(testSuite);
        
        return testSuiteId;
    }
    
    /**
     * Builds a description for Weaviate similarity search
     */
    private String buildCodeDescription(CodeArtifact artifact) {
        StringBuilder desc = new StringBuilder();
        
        desc.append("Project: ").append(artifact.getProjectTitle()).append("\n");
        desc.append("Code artifact with ")
            .append(artifact.getSourceFiles().size())
            .append(" source files\n");
        
        if (artifact.getQualityMetrics() != null) {
            var metrics = artifact.getQualityMetrics();
            desc.append("Code metrics: ")
                .append("LOC=").append(metrics.getTotalLines())
                .append(", Classes=").append(metrics.getClassCount())
                .append(", Methods=").append(metrics.getMethodCount())
                .append("\n");
        }
        
        return desc.toString();
    }
    
    /**
     * Builds context from similar test suites for LLM
     */
    private String buildSimilarTestsContext(List<TestSuite> similarTests) {
        if (similarTests == null || similarTests.isEmpty()) {
            return "";
        }
        
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < similarTests.size(); i++) {
            TestSuite suite = similarTests.get(i);
            context.append("## Similar Test Suite ").append(i + 1).append("\n");
            context.append("Project: ").append(suite.getProjectName()).append("\n");
            context.append("Total Tests: ").append(suite.getTotalTestCount()).append("\n");
            if (suite.getCoverage() != null) {
                context.append("Coverage: ").append(String.format("%.1f%%", suite.getCoverage().getOverallCoverage())).append("\n");
            }
            context.append("\n");
        }
        return context.toString();
    }
    
    /**
     * Logs comprehensive test suite summary
     */
    private void logTestSuiteSummary(TestSuite testSuite) {
        log.info("=== Test Suite Summary ===");
        log.info("Project: {}", testSuite.getProjectName());
        log.info("Test Suite ID: {}", testSuite.getTestSuiteId());
        log.info("Total Tests: {}", testSuite.getTotalTestCount());
        
        log.info("Test Breakdown:");
        log.info("  - Unit Tests: {}", testSuite.getUnitTests().size());
        log.info("  - Integration Tests: {}", testSuite.getIntegrationTests().size());
        log.info("  - E2E Tests: {}", testSuite.getE2eTests().size());
        
        if (testSuite.getCoverage() != null) {
            log.info("Coverage:");
            log.info("  - Overall: {}%", testSuite.getCoverage().getOverallCoverage());
            log.info("  - Line: {}%", testSuite.getCoverage().getLineCoverage());
            log.info("  - Branch: {}%", testSuite.getCoverage().getBranchCoverage());
            log.info("  - Method: {}%", testSuite.getCoverage().getMethodCoverage());
        }
        
        if (testSuite.getQualityMetrics() != null) {
            log.info("Quality Metrics:");
            log.info("  - Assertion Density: {}", testSuite.getQualityMetrics().getAssertionDensity());
            log.info("  - Mock Usage Ratio: {}", testSuite.getQualityMetrics().getMockUsageRatio());
            log.info("  - Quality Grade: {}", testSuite.getQualityMetrics().getQualityGrade());
        }
        
        log.info("Test Gaps: {} (Critical: {})", 
            testSuite.getTestGaps().size(),
            testSuite.getCriticalGapsCount());
        
        log.info("Quality Standards Met: {}", testSuite.meetsQualityStandards());
        log.info("========================");
    }
}
