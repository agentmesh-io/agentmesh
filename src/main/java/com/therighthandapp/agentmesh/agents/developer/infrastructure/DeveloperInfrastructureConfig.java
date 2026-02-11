package com.therighthandapp.agentmesh.agents.developer.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.therighthandapp.agentmesh.agents.developer.application.CodeParser;
import com.therighthandapp.agentmesh.agents.developer.application.CodePromptBuilder;
import com.therighthandapp.agentmesh.agents.developer.ports.*;
import com.therighthandapp.agentmesh.blackboard.BlackboardService;
import com.therighthandapp.agentmesh.llm.LLMClient;
import com.therighthandapp.agentmesh.memory.WeaviateService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Infrastructure Configuration for Developer Agent
 */
@Configuration
public class DeveloperInfrastructureConfig {
    
    @Bean
    public CodeRepository codeRepository(
            BlackboardService blackboardService,
            ObjectMapper objectMapper) {
        return new BlackboardCodeRepository(blackboardService, objectMapper);
    }
    
    @Bean
    public com.therighthandapp.agentmesh.agents.developer.ports.PlanRepository developerPlanRepository(
            com.therighthandapp.agentmesh.agents.planner.ports.ExecutionPlanRepository executionPlanRepository) {
        return new DeveloperPlanRepositoryAdapter(executionPlanRepository);
    }
    
    @Bean
    public com.therighthandapp.agentmesh.agents.developer.ports.ArchitectureRepository developerArchitectureRepository(
            com.therighthandapp.agentmesh.agents.architect.ports.ArchitectureRepository architectureRepository) {
        return new SystemArchitectureRepositoryAdapter(architectureRepository);
    }
    
    @Bean
    public DeveloperLLMService developerLLMService(LLMClient llmClient) {
        return new DeveloperLLMServiceAdapter(llmClient);
    }
    
    @Bean
    public CodeMemoryService codeMemoryService(WeaviateService weaviateService) {
        return new WeaviateCodeMemoryService(weaviateService);
    }
    
    @Bean
    public CodePromptBuilder codePromptBuilder() {
        return new CodePromptBuilder();
    }
    
    @Bean
    public CodeParser codeParser() {
        return new CodeParser();
    }
}
