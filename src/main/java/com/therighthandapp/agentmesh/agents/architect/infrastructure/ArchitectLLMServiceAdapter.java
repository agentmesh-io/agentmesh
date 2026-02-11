package com.therighthandapp.agentmesh.agents.architect.infrastructure;

import com.therighthandapp.agentmesh.agents.architect.ports.ArchitectLLMService;
import com.therighthandapp.agentmesh.agents.architect.ports.ArchitectLLMService.ArchitectureResponse;
import com.therighthandapp.agentmesh.agents.architect.ports.ArchitectLLMService.TokenUsage;
import com.therighthandapp.agentmesh.llm.ChatMessage;
import com.therighthandapp.agentmesh.llm.LLMClient;
import com.therighthandapp.agentmesh.llm.LLMResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Infrastructure Adapter: LLM Service for Architecture Generation
 * 
 * Adapts the existing LLMClient to the architect's ArchitectLLMService port.
 */
@Slf4j
@RequiredArgsConstructor
public class ArchitectLLMServiceAdapter implements ArchitectLLMService {
    
    private final LLMClient llmClient;
    
    @Override
    public ArchitectureResponse generateArchitecture(String prompt, Map<String, Object> parameters) {
        log.debug("Calling LLM for architecture generation...");
        
        try {
            // Build messages for chat completion
            List<ChatMessage> messages = List.of(
                ChatMessage.system("You are an expert system architect designing production-ready architectures. " +
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
                // Fallback if usage not available
                usage = new TokenUsage(0, 0, 0, 0.0);
            }
            
            // Build response
            if (llmResponse.isSuccess()) {
                log.info("LLM architecture generation successful: {} tokens", usage.totalTokens());
                return new ArchitectureResponse(
                    true,
                    llmResponse.getContent(),
                    null,
                    usage
                );
            } else {
                log.error("LLM architecture generation failed: {}", llmResponse.getErrorMessage());
                return new ArchitectureResponse(
                    false,
                    null,
                    llmResponse.getErrorMessage(),
                    usage
                );
            }
            
        } catch (Exception e) {
            log.error("Error calling LLM for architecture generation", e);
            return new ArchitectureResponse(
                false,
                null,
                "LLM call failed: " + e.getMessage(),
                new TokenUsage(0, 0, 0, 0.0)
            );
        }
    }
}
