package com.therighthandapp.agentmesh.agents.reviewer.infrastructure;

import com.therighthandapp.agentmesh.agents.reviewer.ports.ReviewerLLMService;
import com.therighthandapp.agentmesh.llm.ChatMessage;
import com.therighthandapp.agentmesh.llm.LLMClient;
import com.therighthandapp.agentmesh.llm.LLMResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Infrastructure Adapter: Reviewer LLM Service
 * 
 * Hexagonal Architecture: This is an adapter that implements the ReviewerLLMService port.
 * Delegates to the core LLM service for code review generation.
 */
@Slf4j
@RequiredArgsConstructor
public class ReviewerLLMServiceAdapter implements ReviewerLLMService {
    
    private final LLMClient llmClient;
    
    @Override
    public String generateReview(String prompt) {
        try {
            log.debug("Sending review generation request to LLM (prompt length: {} chars)", prompt.length());
            
            // Build messages for chat completion
            List<ChatMessage> messages = List.of(
                ChatMessage.system("You are an expert code reviewer with deep knowledge of software engineering."),
                ChatMessage.user(prompt)
            );
            
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("temperature", 0.3);
            parameters.put("max_tokens", 4000);
            
            LLMResponse response = llmClient.chat(messages, parameters);
            
            if (!response.isSuccess()) {
                throw new RuntimeException("LLM request failed: " + response.getErrorMessage());
            }
            
            String content = response.getContent();
            log.debug("Received LLM response (length: {} chars)", content.length());
            
            return content;
            
        } catch (Exception e) {
            log.error("Failed to generate review using LLM: {}", e.getMessage(), e);
            throw new RuntimeException("LLM service error during review generation", e);
        }
    }
    
    @Override
    public boolean isAvailable() {
        try {
            // Simple test: try to get a minimal response
            List<ChatMessage> testMessages = List.of(
                ChatMessage.user("test")
            );
            Map<String, Object> params = new HashMap<>();
            params.put("max_tokens", 1);
            
            LLMResponse response = llmClient.chat(testMessages, params);
            return response.isSuccess();
        } catch (Exception e) {
            log.warn("LLM service health check failed: {}", e.getMessage());
            return false;
        }
    }
}
