package com.therighthandapp.agentmesh.agents.developer.infrastructure;

import com.therighthandapp.agentmesh.agents.developer.ports.DeveloperLLMService;
import com.therighthandapp.agentmesh.llm.ChatMessage;
import com.therighthandapp.agentmesh.llm.LLMClient;
import com.therighthandapp.agentmesh.llm.LLMResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Infrastructure Adapter: LLM Service for code generation
 */
@Slf4j
@RequiredArgsConstructor
public class DeveloperLLMServiceAdapter implements DeveloperLLMService {
    
    private final LLMClient llmClient;
    
    @Override
    public CodeResponse generateCode(String prompt, Map<String, Object> parameters) {
        log.debug("Calling LLM for code generation...");
        
        try {
            // Build messages for chat completion
            List<ChatMessage> messages = List.of(
                ChatMessage.system("You are an expert software developer generating production-ready code. " +
                    "You must respond ONLY with valid JSON, no markdown formatting or explanations."),
                ChatMessage.user(prompt)
            );
            
            // Call LLM
            LLMResponse llmResponse = llmClient.chat(messages, parameters);
            
            // Extract usage stats
            TokenUsage usage;
            if (llmResponse.getUsage() != null) {
                usage = new TokenUsage(
                    llmResponse.getUsage().getPromptTokens(),
                    llmResponse.getUsage().getCompletionTokens(),
                    llmResponse.getUsage().getPromptTokens() + llmResponse.getUsage().getCompletionTokens(),
                    llmResponse.getUsage().getEstimatedCost()
                );
            } else {
                usage = new TokenUsage(0, 0, 0, 0.0);
            }
            
            // Build response
            if (llmResponse.isSuccess()) {
                log.info("LLM code generation successful: {} tokens", usage.totalTokens());
                return new CodeResponse(
                    true,
                    llmResponse.getContent(),
                    null,
                    usage
                );
            } else {
                log.error("LLM code generation failed: {}", llmResponse.getErrorMessage());
                return new CodeResponse(
                    false,
                    null,
                    llmResponse.getErrorMessage(),
                    usage
                );
            }
            
        } catch (Exception e) {
            log.error("Error calling LLM for code generation", e);
            return new CodeResponse(
                false,
                null,
                "LLM call failed: " + e.getMessage(),
                new TokenUsage(0, 0, 0, 0.0)
            );
        }
    }
}
