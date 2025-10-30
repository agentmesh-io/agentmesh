package com.therighthandapp.agentmesh.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock LLM implementation for deterministic testing.
 * Records calls and returns pre-configured responses.
 */
@Component
public class MockLLMClient implements LLMClient {
    private static final Logger log = LoggerFactory.getLogger(MockLLMClient.class);

    private final Map<String, String> responseMap = new ConcurrentHashMap<>();
    private final List<CallRecord> callHistory = new ArrayList<>();
    private LLMUsage lastUsage;
    private String defaultResponse = "Mock LLM response";

    @Override
    public LLMResponse complete(String prompt, Map<String, Object> parameters) {
        log.debug("MockLLM.complete called with prompt length: {}", prompt.length());

        CallRecord record = new CallRecord("complete", prompt, parameters);
        callHistory.add(record);

        // Check if we have a pre-configured response for this prompt
        String response = responseMap.getOrDefault(prompt, defaultResponse);

        // Mock token counting (rough estimate)
        int promptTokens = estimateTokens(prompt);
        int completionTokens = estimateTokens(response);
        double cost = (promptTokens * 0.00001) + (completionTokens * 0.00002); // Mock pricing

        lastUsage = new LLMUsage(promptTokens, completionTokens, cost);

        return new LLMResponse(response, "mock-gpt-4", lastUsage);
    }

    @Override
    public LLMResponse chat(List<ChatMessage> messages, Map<String, Object> parameters) {
        log.debug("MockLLM.chat called with {} messages", messages.size());

        StringBuilder combinedPrompt = new StringBuilder();
        for (ChatMessage msg : messages) {
            combinedPrompt.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
        }

        CallRecord record = new CallRecord("chat", combinedPrompt.toString(), parameters);
        callHistory.add(record);

        // Get the last user message as the key
        String lastUserMessage = messages.stream()
                .filter(m -> "user".equals(m.getRole()))
                .reduce((first, second) -> second)
                .map(ChatMessage::getContent)
                .orElse("");

        String response = responseMap.getOrDefault(lastUserMessage, defaultResponse);

        int promptTokens = estimateTokens(combinedPrompt.toString());
        int completionTokens = estimateTokens(response);
        double cost = (promptTokens * 0.00001) + (completionTokens * 0.00002);

        lastUsage = new LLMUsage(promptTokens, completionTokens, cost);

        return new LLMResponse(response, "mock-gpt-4", lastUsage);
    }

    @Override
    public float[] embed(String text) {
        log.debug("MockLLM.embed called for text length: {}", text.length());

        CallRecord record = new CallRecord("embed", text, null);
        callHistory.add(record);

        // Return a mock embedding vector (384 dimensions like some real models)
        float[] embedding = new float[384];
        Random random = new Random(text.hashCode()); // Deterministic based on text
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = random.nextFloat();
        }

        return embedding;
    }

    @Override
    public String getModelName() {
        return "mock-gpt-4";
    }

    @Override
    public LLMUsage getLastUsage() {
        return lastUsage;
    }

    // Test utility methods

    /**
     * Configure a response for a specific prompt (for deterministic testing)
     */
    public void addResponse(String prompt, String response) {
        responseMap.put(prompt, response);
    }

    /**
     * Set the default response for unmatched prompts
     */
    public void setDefaultResponse(String response) {
        this.defaultResponse = response;
    }

    /**
     * Get call history for test assertions
     */
    public List<CallRecord> getCallHistory() {
        return new ArrayList<>(callHistory);
    }

    /**
     * Clear all recorded calls and responses
     */
    public void reset() {
        callHistory.clear();
        responseMap.clear();
        lastUsage = null;
    }

    /**
     * Rough token estimation (4 chars per token average)
     */
    private int estimateTokens(String text) {
        return Math.max(1, text.length() / 4);
    }

    /**
     * Record of an LLM call for testing/debugging
     */
    public static class CallRecord {
        private final String method;
        private final String input;
        private final Map<String, Object> parameters;
        private final long timestamp;

        public CallRecord(String method, String input, Map<String, Object> parameters) {
            this.method = method;
            this.input = input;
            this.parameters = parameters;
            this.timestamp = System.currentTimeMillis();
        }

        public String getMethod() {
            return method;
        }

        public String getInput() {
            return input;
        }

        public Map<String, Object> getParameters() {
            return parameters;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}

