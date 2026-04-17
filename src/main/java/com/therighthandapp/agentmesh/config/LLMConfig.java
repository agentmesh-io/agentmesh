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
 * LLM Configuration with multi-provider support.
 *
 * Provider priority (set via agentmesh.llm.provider):
 * 1. "openai-compatible" → OpenAICompatibleClient (LMStudio, Ollama /v1, OpenAI, etc.)
 * 2. "ollama" → OllamaClient (Ollama native API)
 * 3. (none) → MockLLMClient (fallback for dev/test)
 *
 * Per Architect Protocol v7.8:
 *   - LMStudio is the primary local inference engine (http://localhost:1234/v1)
 *   - Use OpenAI-compatible SDKs for service portability
 *   - Never download models to project folders (use $LMSTUDIO_MODELS_PATH)
 */
@Configuration
public class LLMConfig {

    private static final Logger log = LoggerFactory.getLogger(LLMConfig.class);

    @Value("${agentmesh.llm.provider:}")
    private String provider;

    /**
     * Fallback LLM client when no provider is configured.
     * This ensures the application always has an LLM client available.
     */
    @Bean
    @ConditionalOnMissingBean(LLMClient.class)
    public LLMClient fallbackLLMClient() {
        log.warn("═══════════════════════════════════════════════════════");
        log.warn("  No LLM provider configured. Using MockLLMClient.");
        log.warn("  To use a real LLM, set one of:");
        log.warn("    agentmesh.llm.provider=openai-compatible  (LMStudio/OpenAI)");
        log.warn("    agentmesh.llm.provider=ollama             (Ollama native)");
        log.warn("  LMStudio default: http://localhost:1234/v1");
        log.warn("═══════════════════════════════════════════════════════");
        return new MockLLMClient();
    }
}

