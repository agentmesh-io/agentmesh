package com.therighthandapp.agentmesh.agents.tester.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.therighthandapp.agentmesh.agents.tester.ports.CodeRepository;
import com.therighthandapp.agentmesh.agents.tester.ports.TesterLLMService;
import com.therighthandapp.agentmesh.agents.tester.ports.TestMemoryService;
import com.therighthandapp.agentmesh.agents.tester.ports.TestSuiteRepository;
import com.therighthandapp.agentmesh.blackboard.BlackboardService;
import com.therighthandapp.agentmesh.llm.LLMClient;
import com.therighthandapp.agentmesh.memory.WeaviateService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Infrastructure Configuration: TesterAgent
 * 
 * Spring configuration for wiring TesterAgent infrastructure adapters to ports.
 */
@Configuration
public class TesterInfrastructureConfig {
    
    @Bean
    public TestSuiteRepository testSuiteRepository(
            BlackboardService blackboardService,
            ObjectMapper objectMapper) {
        return new BlackboardTestSuiteRepository(blackboardService, objectMapper);
    }
    
    @Bean
    @Qualifier("testerCodeRepository")
    public CodeRepository testerCodeRepository(
            BlackboardService blackboardService,
            ObjectMapper objectMapper) {
        return new CodeArtifactRepositoryAdapter(blackboardService, objectMapper);
    }
    
    @Bean
    public TesterLLMService testerLLMService(LLMClient llmClient) {
        return new TesterLLMServiceAdapter(llmClient);
    }
    
    @Bean
    public TestMemoryService testMemoryService(
            WeaviateService weaviateService,
            ObjectMapper objectMapper) {
        return new WeaviateTestMemoryService(weaviateService, objectMapper);
    }
}
