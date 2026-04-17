package com.therighthandapp.agentmesh.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Ollama API client implementation for local LLM inference.
 * Enabled when agentmesh.llm.ollama.enabled=true.
 *
 * Ollama is a lightweight framework for running LLMs locally.
 * Supports models like: llama3, codellama, mistral, phi3, deepseek-coder
 *
 * @see <a href="https://ollama.ai">Ollama Documentation</a>
 */
@Component
@ConditionalOnProperty(name = "agentmesh.llm.ollama.enabled", havingValue = "true")
public class OllamaClient implements LLMClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    @Value("${agentmesh.llm.ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    @Value("${agentmesh.llm.ollama.model:codellama:13b}")
    private String model;

    @Value("${agentmesh.llm.ollama.embedding-model:nomic-embed-text}")
    private String embeddingModel;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private LLMUsage lastUsage;

    public OllamaClient() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        log.info("OllamaClient initialized - Local LLM enabled");
    }

    @Override
    public LLMResponse complete(String prompt, Map<String, Object> parameters) {
        List<ChatMessage> messages = List.of(ChatMessage.user(prompt));
        return chat(messages, parameters);
    }

    @Override
    public LLMResponse chat(List<ChatMessage> messages, Map<String, Object> parameters) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", convertMessages(messages));
            requestBody.put("stream", false);  // Disable streaming for simpler handling

            // Add optional parameters
            if (parameters != null) {
                Map<String, Object> options = new HashMap<>();
                if (parameters.containsKey("temperature")) {
                    options.put("temperature", parameters.get("temperature"));
                }
                if (parameters.containsKey("max_tokens")) {
                    options.put("num_predict", parameters.get("max_tokens"));
                }
                if (parameters.containsKey("top_p")) {
                    options.put("top_p", parameters.get("top_p"));
                }
                if (!options.isEmpty()) {
                    requestBody.put("options", options);
                }
            }

            String apiUrl = baseUrl + "/api/chat";
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            log.debug("Calling Ollama API at {} with model: {}", apiUrl, model);
            long startTime = System.currentTimeMillis();

            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, request, String.class);

            long duration = System.currentTimeMillis() - startTime;
            log.debug("Ollama response received in {}ms", duration);

            if (response.getStatusCode() == HttpStatus.OK) {
                return parseResponse(response.getBody(), duration);
            } else {
                log.error("Ollama API error: {}", response.getStatusCode());
                return new LLMResponse("API error: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Error calling Ollama API at {}: {}", baseUrl, e.getMessage());
            return new LLMResponse("Error: " + e.getMessage());
        }
    }

    @Override
    public float[] embed(String text) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", embeddingModel);
            requestBody.put("prompt", text);

            String apiUrl = baseUrl + "/api/embeddings";
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            log.debug("Calling Ollama embeddings API with model: {}", embeddingModel);
            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                return parseEmbedding(response.getBody());
            } else {
                log.error("Ollama embedding error: {}", response.getStatusCode());
                return new float[0];
            }

        } catch (Exception e) {
            log.error("Error getting embeddings from Ollama: {}", e.getMessage());
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

    private List<Map<String, String>> convertMessages(List<ChatMessage> messages) {
        List<Map<String, String>> converted = new ArrayList<>();
        for (ChatMessage msg : messages) {
            Map<String, String> m = new HashMap<>();
            m.put("role", msg.getRole());
            m.put("content", msg.getContent());
            converted.add(m);
        }
        return converted;
    }

    private LLMResponse parseResponse(String responseBody, long durationMs) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // Ollama response format:
            // { "model": "...", "message": { "role": "assistant", "content": "..." },
            //   "done": true, "total_duration": ..., "eval_count": ... }

            JsonNode messageNode = root.path("message");
            String content = messageNode.path("content").asText("");

            // Extract token counts for usage tracking
            int promptTokens = root.path("prompt_eval_count").asInt(0);
            int completionTokens = root.path("eval_count").asInt(0);

            this.lastUsage = new LLMUsage(promptTokens, completionTokens, promptTokens + completionTokens);

            log.debug("Ollama response: {} tokens (prompt: {}, completion: {})",
                     promptTokens + completionTokens, promptTokens, completionTokens);

            return new LLMResponse(content);

        } catch (Exception e) {
            log.error("Error parsing Ollama response", e);
            return new LLMResponse("Parse error: " + e.getMessage());
        }
    }

    private float[] parseEmbedding(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode embeddingNode = root.path("embedding");

            if (embeddingNode.isArray()) {
                float[] embedding = new float[embeddingNode.size()];
                for (int i = 0; i < embeddingNode.size(); i++) {
                    embedding[i] = (float) embeddingNode.get(i).asDouble();
                }
                return embedding;
            }

            return new float[0];

        } catch (Exception e) {
            log.error("Error parsing Ollama embedding response", e);
            return new float[0];
        }
    }
}
