package com.therighthandapp.agentmesh.agents.tester.ports;

import com.therighthandapp.agentmesh.agents.tester.domain.TestSuite;

import java.util.List;

/**
 * Port Interface: Test Memory Service
 * 
 * Hexagonal Architecture: This is an output port that abstracts test pattern storage in Weaviate.
 * Enables context-aware test generation by retrieving similar past test suites.
 */
public interface TestMemoryService {
    
    /**
     * Stores a test suite in the vector database (Weaviate)
     * 
     * @param testSuite The test suite to store
     */
    void storeTestSuite(TestSuite testSuite);
    
    /**
     * Finds similar test suites based on code characteristics
     * 
     * @param codeDescription Description of the code being tested
     * @param limit Maximum number of similar test suites to return
     * @return List of similar test suites
     */
    List<TestSuite> findSimilarTestSuites(String codeDescription, int limit);
    
    /**
     * Gets test statistics for a specific category
     * 
     * @param category The category to analyze (e.g., "Unit", "Integration")
     * @return Statistics about test suites in this category
     */
    TestStatistics getTestStatistics(String category);
    
    /**
     * Value Object: Test Statistics
     */
    record TestStatistics(
        String category,
        Integer totalTestSuites,
        Double averageCoverage,
        Integer totalTests,
        List<String> commonPatterns
    ) {}
}
