package com.therighthandapp.agentmesh.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * OpenAI-compatible LLM client.
 *
 * Works with any provider exposing the OpenAI /v1/chat/completions API:
 *   - LMStudio (default: http://localhost:1234/v1) — primary local inference
 *   - Ollama (http://localhost:11434/v1)
 *   - OpenAI (https://api.openai.com/v1)
 *   - Azure OpenAI, Together.ai, Groq, etc.
 *
 * Per Architect Protocol v7.8: LMStudio is the primary local inference engine.
 * Uses OpenAI-compatible SDK to ensure service portability.
 */
@Component
@Primary
@ConditionalOnProperty(name = "agentmesh.llm.provider", havingValue = "openai-compatible", matchIfMissing = false)
public class OpenAICompatibleClient implements LLMClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAICompatibleClient.class);

    @Value("${agentmesh.llm.base-url:http://localhost:1234/v1}")
    private String baseUrl;

    @Value("${agentmesh.llm.api-key:}")
    private String apiKey;

    @Value("${agentmesh.llm.model:default}")
    private String model;

    @Value("${agentmesh.llm.embedding-model:text-embedding-nomic-embed-text-v1.5}")
    private String embeddingModel;

    @Value("${agentmesh.llm.temperature:0.7}")
    private double defaultTemperature;

    @Value("${agentmesh.llm.max-tokens:4096}")
    private int defaultMaxTokens;

    @Value("${agentmesh.llm.timeout:120000}")
    private int timeoutMs;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private LLMUsage lastUsage;

    public OpenAICompatibleClient() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        log.info("OpenAI-compatible LLM client initialized");
    }

    @Override
    public LLMResponse complete(String prompt, Map<String, Object> parameters) {
        List<ChatMessage> messages = List.of(ChatMessage.user(prompt));
        return chat(messages, parameters);
    }

    @Override
    public LLMResponse chat(List<ChatMessage> messages, Map<String, Object> parameters) {
        try {
            HttpHeaders headers = buildHeaders();

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", convertMessages(messages));
            requestBody.put("stream", false);

            // Apply parameters with defaults
            double temperature = parameters != null && parameters.containsKey("temperature")
                    ? ((Number) parameters.get("temperature")).doubleValue()
                    : defaultTemperature;
            int maxTokens = parameters != null && parameters.containsKey("max_tokens")
                    ? ((Number) parameters.get("max_tokens")).intValue()
                    : defaultMaxTokens;

            requestBody.put("temperature", temperature);
            requestBody.put("max_tokens", maxTokens);

            if (parameters != null && parameters.containsKey("top_p")) {
                requestBody.put("top_p", parameters.get("top_p"));
            }

            String apiUrl = baseUrl + "/chat/completions";
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            log.debug("Calling LLM API at {} with model: {}", apiUrl, model);
            long startTime = System.currentTimeMillis();

            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, request, String.class);

            long duration = System.currentTimeMillis() - startTime;
            log.debug("LLM response received in {}ms", duration);

            if (response.getStatusCode().is2xxSuccessful()) {
                return parseResponse(response.getBody(), duration);
            } else {
                log.error("LLM API error: {}", response.getStatusCode());
                return new LLMResponse("API error: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Error calling LLM API at {}: {}", baseUrl, e.getMessage());
            return new LLMResponse("Error: " + e.getMessage());
        }
    }

    @Override
    public float[] embed(String text) {
        try {
            HttpHeaders headers = buildHeaders();

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", embeddingModel);
            requestBody.put("input", text);

            String apiUrl = baseUrl + "/embeddings";
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            log.debug("Calling embeddings API with model: {}", embeddingModel);
            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                return parseEmbedding(response.getBody());
            } else {
                log.error("Embedding API error: {}", response.getStatusCode());
                return new float[0];
            }

        } catch (Exception e) {
            log.error("Error getting embeddings: {}", e.getMessage());
            return new float[0];
        }
    }

    @Override
    public String getModelName() {
        return model;
    }

    @Override
    public LLMUsage getLastUsage() {
        return lastUsage;
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) {
            headers.setBearerAuth(apiKey);
        }
        return headers;
    }

    private List<Map<String, String>> convertMessages(List<ChatMessage> messages) {
        return messages.stream()
                .map(msg -> Map.of("role", msg.getRole(), "content", msg.getContent()))
                .toList();
    }

    /**
     * Parse OpenAI-compatible response format:
     * { "choices": [{ "message": { "content": "..." } }], "usage": { ... } }
     */
    private LLMResponse parseResponse(String responseBody, long durationMs) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // Extract content from choices[0].message.content
            JsonNode choices = root.path("choices");
            String content = "";
            if (choices.isArray() && !choices.isEmpty()) {
                content = choices.get(0).path("message").path("content").asText("");
            }

            // Extract usage
            JsonNode usageNode = root.path("usage");
            int promptTokens = usageNode.path("prompt_tokens").asInt(0);
            int completionTokens = usageNode.path("completion_tokens").asInt(0);

            // Estimate cost (local = $0, OpenAI varies by model)
            double cost = estimateCost(promptTokens, completionTokens);
            this.lastUsage = new LLMUsage(promptTokens, completionTokens, cost);

            String responseModel = root.path("model").asText(model);
            log.debug("LLM response: model={}, {} tokens (prompt: {}, completion: {}), cost=${}",
                    responseModel, promptTokens + completionTokens, promptTokens, completionTokens,
                    String.format("%.4f", cost));

            return new LLMResponse(content, responseModel, lastUsage);

        } catch (Exception e) {
            log.error("Error parsing LLM response", e);
            return new LLMResponse("Parse error: " + e.getMessage());
        }
    }

    private float[] parseEmbedding(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root.path("data");

            if (data.isArray() && !data.isEmpty()) {
                JsonNode embeddingNode = data.get(0).path("embedding");
                if (embeddingNode.isArray()) {
                    float[] embedding = new float[embeddingNode.size()];
                    for (int i = 0; i < embeddingNode.size(); i++) {
                        embedding[i] = (float) embeddingNode.get(i).asDouble();
                    }
                    return embedding;
                }
            }

            return new float[0];

        } catch (Exception e) {
            log.error("Error parsing embedding response", e);
            return new float[0];
        }
    }

    /**
     * Estimate cost based on provider.
     * Local inference (LMStudio/Ollama) = $0.
     */
    private double estimateCost(int promptTokens, int completionTokens) {
        if (baseUrl.contains("localhost") || baseUrl.contains("127.0.0.1")) {
            return 0.0; // Local inference is free
        }
        // OpenAI GPT-4o pricing (approximate)
        return (promptTokens * 0.0000025) + (completionTokens * 0.00001);
    }
}

