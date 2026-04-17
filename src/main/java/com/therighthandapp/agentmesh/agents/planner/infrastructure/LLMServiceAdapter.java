package com.therighthandapp.agentmesh.agents.planner.infrastructure;

import com.therighthandapp.agentmesh.agents.planner.ports.LLMService;
import com.therighthandapp.agentmesh.llm.ChatMessage;
import com.therighthandapp.agentmesh.llm.LLMClient;
import com.therighthandapp.agentmesh.llm.LLMResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Infrastructure Adapter: LLM Service Implementation
 * 
 * Adapts the existing LLMClient to the planner's LLMService port.
 * This isolates the planner domain from LLM implementation details.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LLMServiceAdapter implements LLMService {
    
    private final LLMClient llmClient;
    
    @Override
    public LLMPlanResponse generatePlan(String prompt, Map<String, Object> parameters) {
        log.debug("Calling LLM for plan generation...");
        
        try {
            // Build messages for chat completion
            List<ChatMessage> messages = List.of(
                ChatMessage.system("You are an expert software architect creating detailed execution plans. " +
                    "You must respond ONLY with valid JSON, no markdown formatting or explanations."),
                ChatMessage.user(prompt)
            );
            
            // Call LLM
            LLMResponse llmResponse = llmClient.chat(messages, parameters);
            
            // Extract usage stats
            UsageStats usage = null;
            if (llmResponse.getUsage() != null) {
                usage = new UsageStats(
                    llmResponse.getUsage().getPromptTokens(),
                    llmResponse.getUsage().getCompletionTokens(),
                    llmResponse.getUsage().getPromptTokens() + llmResponse.getUsage().getCompletionTokens(),
                    llmResponse.getUsage().getEstimatedCost()
                );
            } else {
                // Fallback if usage not available
                usage = new UsageStats(0, 0, 0, 0.0);
            }
            
            // Build response
            if (llmResponse.isSuccess()) {
                log.info("LLM plan generation successful: {} tokens", usage.totalTokens());
                return new LLMPlanResponse(
                    llmResponse.getContent(),
                    true,
                    null,
                    usage
                );
            } else {
                log.error("LLM plan generation failed: {}", llmResponse.getErrorMessage());
                return new LLMPlanResponse(
                    null,
                    false,
                    llmResponse.getErrorMessage(),
                    usage
                );
            }
            
        } catch (Exception e) {
            log.error("Error calling LLM for plan generation", e);
            return new LLMPlanResponse(
                null,
                false,
                "LLM call failed: " + e.getMessage(),
                new UsageStats(0, 0, 0, 0.0)
            );
        }
    }
}
