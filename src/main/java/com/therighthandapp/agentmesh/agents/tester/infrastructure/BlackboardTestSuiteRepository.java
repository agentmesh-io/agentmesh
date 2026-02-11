package com.therighthandapp.agentmesh.agents.tester.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.therighthandapp.agentmesh.agents.tester.domain.TestSuite;
import com.therighthandapp.agentmesh.agents.tester.ports.TestSuiteRepository;
import com.therighthandapp.agentmesh.blackboard.BlackboardEntry;
import com.therighthandapp.agentmesh.blackboard.BlackboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Infrastructure Adapter: Blackboard Test Suite Repository
 * 
 * Implements TestSuiteRepository port using Blackboard service.
 * Stores test suites as Blackboard entries with type="TEST"
 */
@Slf4j
@RequiredArgsConstructor
public class BlackboardTestSuiteRepository implements TestSuiteRepository {
    
    private static final String ENTRY_TYPE = "TEST";
    private static final String AGENT_ID = "tester-agent";
    
    private final BlackboardService blackboardService;
    private final ObjectMapper objectMapper;
    
    @Override
    public TestSuite save(TestSuite testSuite) {
        try {
            String jsonContent = objectMapper.writeValueAsString(testSuite);
            
            String title = String.format("Test Suite: %s (Coverage: %.1f%%)",
                testSuite.getProjectName(),
                testSuite.getCoverage() != null ? 
                    testSuite.getCoverage().getOverallCoverage() : 0.0);
            
            BlackboardEntry entry = blackboardService.post(
                AGENT_ID,
                ENTRY_TYPE,
                title,
                jsonContent
            );
            
            log.info("Saved test suite to Blackboard: {} (Project: {})",
                entry.getId(), testSuite.getProjectName());
            
            // Update testSuite with ID and return it
            return TestSuite.builder()
                .testSuiteId(String.valueOf(entry.getId()))
                .codeArtifactId(testSuite.getCodeArtifactId())
                .projectName(testSuite.getProjectName())
                .unitTests(testSuite.getUnitTests())
                .integrationTests(testSuite.getIntegrationTests())
                .e2eTests(testSuite.getE2eTests())
                .coverage(testSuite.getCoverage())
                .testGaps(testSuite.getTestGaps())
                .recommendations(testSuite.getRecommendations())
                .build();
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize test suite", e);
            throw new RuntimeException("Failed to save test suite to Blackboard", e);
        }
    }
    
    @Override
    public Optional<TestSuite> findById(String testSuiteId) {
        try {
            List<BlackboardEntry> entries = blackboardService.readByType(ENTRY_TYPE);
            
            for (BlackboardEntry entry : entries) {
                if (entry.getId().equals(testSuiteId)) {
                    TestSuite testSuite = objectMapper.readValue(
                        entry.getContent(),
                        TestSuite.class
                    );
                    return Optional.of(testSuite);
                }
            }
            
            log.warn("Test suite not found: {}", testSuiteId);
            return Optional.empty();
            
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize test suite: {}", testSuiteId, e);
            return Optional.empty();
        }
    }
    
    @Override
    public Optional<TestSuite> findByCodeArtifactId(String codeArtifactId) {
        try {
            List<BlackboardEntry> entries = blackboardService.readByType(ENTRY_TYPE);
            
            for (BlackboardEntry entry : entries) {
                TestSuite testSuite = objectMapper.readValue(
                    entry.getContent(),
                    TestSuite.class
                );
                
                if (testSuite.getCodeArtifactId().equals(codeArtifactId)) {
                    log.info("Found test suite for code artifact: {}", codeArtifactId);
                    return Optional.of(testSuite);
                }
            }
            
            log.warn("No test suite found for code artifact: {}", codeArtifactId);
            return Optional.empty();
            
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize test suites for search", e);
            return Optional.empty();
        }
    }
    
    @Override
    public void deleteById(String testSuiteId) {
        log.warn("Delete operation not supported - Blackboard maintains full history");
        throw new UnsupportedOperationException(
            "Blackboard does not support deletion - maintains full history");
    }
}
