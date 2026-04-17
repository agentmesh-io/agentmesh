package com.therighthandapp.agentmesh.agents.tester.infrastructure;

import com.therighthandapp.agentmesh.agents.tester.ports.TesterLLMService;
import com.therighthandapp.agentmesh.llm.ChatMessage;
import com.therighthandapp.agentmesh.llm.LLMClient;
import com.therighthandapp.agentmesh.llm.LLMResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Infrastructure Adapter: Tester LLM Service
 * 
 * Implements TesterLLMService port using LLMClient.
 * Delegates test generation to configured LLM provider.
 */
@Slf4j
@RequiredArgsConstructor
public class TesterLLMServiceAdapter implements TesterLLMService {
    
    private final LLMClient llmClient;
    
    @Override
    public String generateTests(String prompt) {
        List<ChatMessage> messages = List.of(
            ChatMessage.system("You are an expert test engineer specializing in comprehensive test suite generation."),
            ChatMessage.user(prompt)
        );
        
        Map<String, Object> parameters = Map.of(
            "temperature", 0.3,
            "max_tokens", 4000
        );
        
        try {
            LLMResponse response = llmClient.chat(messages, parameters);
            
            if (!response.isSuccess()) {
                log.error("LLM test generation failed: {}", response.getErrorMessage());
                throw new RuntimeException("Failed to generate tests: " + response.getErrorMessage());
            }
            
            String content = response.getContent();
            log.debug("Generated test suite (length: {} chars)", content.length());
            
            return content;
            
        } catch (Exception e) {
            log.error("Failed to generate tests using LLM", e);
            throw new RuntimeException("Failed to generate tests", e);
        }
    }
    
    @Override
    public boolean isAvailable() {
        try {
            List<ChatMessage> messages = List.of(
                ChatMessage.user("test")
            );
            
            Map<String, Object> parameters = Map.of(
                "max_tokens", 1
            );
            
            LLMResponse response = llmClient.chat(messages, parameters);
            return response.isSuccess();
            
        } catch (Exception e) {
            log.warn("LLM availability check failed", e);
            return false;
        }
    }
}
