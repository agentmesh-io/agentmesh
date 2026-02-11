package com.therighthandapp.agentmesh.agents.tester.ports;

import com.therighthandapp.agentmesh.agents.developer.domain.CodeArtifact;

import java.util.Optional;

/**
 * Port Interface: Code Repository (for TesterAgent)
 * 
 * Hexagonal Architecture: This is an output port that abstracts code artifact retrieval.
 * The implementation will fetch code artifacts from the Blackboard.
 */
public interface CodeRepository {
    
    /**
     * Finds a code artifact by its ID
     * 
     * @param codeArtifactId The code artifact ID
     * @return The code artifact if found
     */
    Optional<CodeArtifact> findById(String codeArtifactId);
}
