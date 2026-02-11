package com.therighthandapp.agentmesh.config;

import com.therighthandapp.agentmesh.llm.LLMClient;
import com.therighthandapp.agentmesh.llm.MockLLMClient;
import com.therighthandapp.agentmesh.llm.OllamaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * LLM Configuration with smart fallback.
 *
 * Priority:
 * 1. OllamaClient (if agentmesh.llm.ollama.enabled=true and Ollama is reachable)
 * 2. OpenAIClient (if agentmesh.llm.openai.enabled=true)
 * 3. MockLLMClient (fallback for development/testing)
 */
@Configuration
public class LLMConfig {

    private static final Logger log = LoggerFactory.getLogger(LLMConfig.class);

    @Value("${agentmesh.llm.ollama.enabled:false}")
    private boolean ollamaEnabled;

    @Value("${agentmesh.llm.openai.enabled:false}")
    private boolean openaiEnabled;

    /**
     * Fallback LLM client when no other client is configured.
     * This ensures the application always has an LLM client available.
     */
    @Bean
    @ConditionalOnMissingBean(LLMClient.class)
    public LLMClient fallbackLLMClient() {
        log.warn("No LLM client configured. Using MockLLMClient as fallback.");
        log.warn("To use a real LLM, enable either:");
        log.warn("  - agentmesh.llm.ollama.enabled=true (for local Ollama)");
        log.warn("  - agentmesh.llm.openai.enabled=true (for OpenAI)");
        return new MockLLMClient();
    }
}

