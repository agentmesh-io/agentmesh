package com.therighthandapp.agentmesh.agents.tester.ports;

import com.therighthandapp.agentmesh.agents.tester.domain.TestSuite;

import java.util.Optional;

/**
 * Port Interface: Test Suite Repository
 * 
 * Hexagonal Architecture: This is an output port that abstracts test suite storage.
 * The implementation will store test suites in the Blackboard.
 */
public interface TestSuiteRepository {
    
    /**
     * Saves a test suite to the Blackboard
     * 
     * @param testSuite The test suite to save
     * @return The saved test suite with generated ID
     */
    TestSuite save(TestSuite testSuite);
    
    /**
     * Finds a test suite by its ID
     * 
     * @param testSuiteId The test suite ID
     * @return The test suite if found
     */
    Optional<TestSuite> findById(String testSuiteId);
    
    /**
     * Finds a test suite by code artifact ID
     * 
     * @param codeArtifactId The code artifact ID
     * @return The test suite if found
     */
    Optional<TestSuite> findByCodeArtifactId(String codeArtifactId);
    
    /**
     * Deletes a test suite
     * 
     * @param testSuiteId The test suite ID to delete
     */
    void deleteById(String testSuiteId);
}
