package com.therighthandapp.agentmesh.agents.reviewer.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.therighthandapp.agentmesh.agents.reviewer.ports.CodeRepository;
import com.therighthandapp.agentmesh.agents.reviewer.ports.ReviewMemoryService;
import com.therighthandapp.agentmesh.agents.reviewer.ports.ReviewRepository;
import com.therighthandapp.agentmesh.agents.reviewer.ports.ReviewerLLMService;
import com.therighthandapp.agentmesh.blackboard.BlackboardService;
import com.therighthandapp.agentmesh.llm.LLMClient;
import com.therighthandapp.agentmesh.memory.WeaviateService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Infrastructure Configuration: ReviewerAgent
 * 
 * Spring configuration that wires infrastructure adapters to ports.
 * Enables dependency injection for hexagonal architecture.
 */
@Configuration
public class ReviewerInfrastructureConfig {
    
    @Bean
    public ReviewRepository reviewRepository(
            BlackboardService blackboardService,
            ObjectMapper objectMapper) {
        return new BlackboardReviewRepository(blackboardService, objectMapper);
    }
    
    @Bean
    @Qualifier("reviewerCodeRepository")
    public CodeRepository reviewerCodeRepository(
            BlackboardService blackboardService,
            ObjectMapper objectMapper) {
        return new CodeArtifactRepositoryAdapter(blackboardService, objectMapper);
    }
    
    @Bean
    public ReviewerLLMService reviewerLLMService(LLMClient llmClient) {
        return new ReviewerLLMServiceAdapter(llmClient);
    }
    
    @Bean
    public ReviewMemoryService reviewMemoryService(
            WeaviateService weaviateService,
            ObjectMapper objectMapper) {
        return new WeaviateReviewMemoryService(weaviateService, objectMapper);
    }
}
