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
 * OpenAI API client implementation.
 * Enabled when agentmesh.llm.openai.enabled=true and API key is provided.
 */
@Component
@ConditionalOnProperty(name = "agentmesh.llm.openai.enabled", havingValue = "true")
public class OpenAIClient implements LLMClient {
    private static final Logger log = LoggerFactory.getLogger(OpenAIClient.class);
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String EMBEDDING_URL = "https://api.openai.com/v1/embeddings";

    @Value("${agentmesh.llm.openai.api-key}")
    private String apiKey;

    @Value("${agentmesh.llm.openai.model:gpt-4}")
    private String model;

    @Value("${agentmesh.llm.openai.embedding-model:text-embedding-ada-002}")
    private String embeddingModel;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private LLMUsage lastUsage;

    public OpenAIClient() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
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
            headers.setBearerAuth(apiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", convertMessages(messages));

            // Add optional parameters
            if (parameters != null) {
                requestBody.putAll(parameters);
            }

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            log.debug("Calling OpenAI API with model: {}", model);
            ResponseEntity<String> response = restTemplate.postForEntity(API_URL, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                return parseResponse(response.getBody());
            } else {
                log.error("OpenAI API error: {}", response.getStatusCode());
                return new LLMResponse("API error: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Error calling OpenAI API", e);
            return new LLMResponse("Error: " + e.getMessage());
        }
    }

    @Override
    public float[] embed(String text) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", embeddingModel);
            requestBody.put("input", text);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(EMBEDDING_URL, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                return parseEmbedding(response.getBody());
            } else {
                log.error("OpenAI embedding error: {}", response.getStatusCode());
                return new float[0];
            }

        } catch (Exception e) {
            log.error("Error getting embedding from OpenAI", e);
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

    private LLMResponse parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String content = root.path("choices").get(0).path("message").path("content").asText();

            JsonNode usage = root.path("usage");
            int promptTokens = usage.path("prompt_tokens").asInt();
            int completionTokens = usage.path("completion_tokens").asInt();

            // Rough cost estimate (GPT-4 pricing)
            double cost = (promptTokens * 0.00003) + (completionTokens * 0.00006);

            lastUsage = new LLMUsage(promptTokens, completionTokens, cost);

            log.info("OpenAI API call: {}", lastUsage);

            return new LLMResponse(content, model, lastUsage);

        } catch (Exception e) {
            log.error("Error parsing OpenAI response", e);
            return new LLMResponse("Parse error: " + e.getMessage());
        }
    }

    private float[] parseEmbedding(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode embeddingNode = root.path("data").get(0).path("embedding");

            float[] embedding = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                embedding[i] = (float) embeddingNode.get(i).asDouble();
            }

            return embedding;

        } catch (Exception e) {
            log.error("Error parsing embedding response", e);
            return new float[0];
        }
    }
}

