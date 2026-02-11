package com.therighthandapp.agentmesh.agents.architect.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.therighthandapp.agentmesh.agents.architect.ports.ArchitectLLMService;
import com.therighthandapp.agentmesh.agents.architect.ports.ArchitectureMemoryService;
import com.therighthandapp.agentmesh.agents.architect.ports.ArchitectureRepository;
import com.therighthandapp.agentmesh.agents.architect.ports.PlanRepository;
import com.therighthandapp.agentmesh.agents.planner.ports.ExecutionPlanRepository;
import com.therighthandapp.agentmesh.blackboard.BlackboardService;
import com.therighthandapp.agentmesh.llm.LLMClient;
import com.therighthandapp.agentmesh.memory.WeaviateService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Infrastructure Configuration for Architect Agent
 * 
 * Wires up all infrastructure adapters implementing the architect's ports.
 * This configuration is in the infrastructure layer, keeping the domain clean.
 */
@Configuration
public class ArchitectInfrastructureConfig {
    
    /**
     * Architecture repository using Blackboard
     */
    @Bean
    public ArchitectureRepository architectureRepository(
            BlackboardService blackboardService,
            ObjectMapper objectMapper) {
        return new BlackboardArchitectureRepository(blackboardService, objectMapper);
    }
    
    /**
     * Plan repository adapter
     */
    @Bean
    public PlanRepository architectPlanRepository(ExecutionPlanRepository executionPlanRepository) {
        return new ArchitectPlanRepositoryAdapter(executionPlanRepository);
    }
    
    /**
     * LLM service for architecture generation
     */
    @Bean
    public ArchitectLLMService architectLLMService(LLMClient llmClient) {
        return new ArchitectLLMServiceAdapter(llmClient);
    }
    
    /**
     * Vector memory service for architecture patterns
     */
    @Bean
    public ArchitectureMemoryService architectureMemoryService(WeaviateService weaviateService) {
        return new WeaviateArchitectureMemoryService(weaviateService);
    }
}
