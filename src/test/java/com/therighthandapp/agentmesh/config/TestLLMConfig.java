package com.therighthandapp.agentmesh.config;

import com.therighthandapp.agentmesh.llm.LLMClient;
import com.therighthandapp.agentmesh.llm.MockLLMClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Test configuration for LLM client.
 * Provides MockLLMClient for tests to avoid needing real API keys.
 */
@TestConfiguration
@Profile("test")
public class TestLLMConfig {

    @Bean
    @Primary
    LLMClient llmClient() {
        return new MockLLMClient();
    }
}
