package com.therighthandapp.agentmesh.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for OllamaClient.
 * These tests require a running Ollama server at localhost:11434.
 *
 * Run with: mvn test -Dtest=OllamaClientIntegrationTest
 *
 * To enable: set environment variable OLLAMA_ENABLED=true
 */
@EnabledIfEnvironmentVariable(named = "OLLAMA_ENABLED", matches = "true")
public class OllamaClientIntegrationTest {

    private OllamaClient createClient() {
        OllamaClient client = new OllamaClient();
        // Use reflection to set the required fields since @Value won't work in tests
        try {
            var baseUrlField = OllamaClient.class.getDeclaredField("baseUrl");
            baseUrlField.setAccessible(true);
            baseUrlField.set(client, "http://localhost:11434");

            var modelField = OllamaClient.class.getDeclaredField("model");
            modelField.setAccessible(true);
            modelField.set(client, System.getenv().getOrDefault("OLLAMA_MODEL", "codellama:13b"));

            var embeddingModelField = OllamaClient.class.getDeclaredField("embeddingModel");
            embeddingModelField.setAccessible(true);
            embeddingModelField.set(client, "nomic-embed-text");
        } catch (Exception e) {
            throw new RuntimeException("Failed to configure OllamaClient for testing", e);
        }
        return client;
    }

    @Test
    void testCompleteGeneratesCode() {
        OllamaClient client = createClient();

        String prompt = "Write a simple Java method that returns 'Hello World'";
        Map<String, Object> params = new HashMap<>();
        params.put("temperature", 0.3);
        params.put("max_tokens", 200);

        LLMResponse response = client.complete(prompt, params);

        assertNotNull(response);
        assertNotNull(response.getContent());
        assertFalse(response.getContent().isEmpty());
        // Should contain Java code
        assertTrue(response.getContent().contains("Hello") || response.getContent().contains("return"));

        System.out.println("Generated code:\n" + response.getContent());
    }

    @Test
    void testChatWithMessages() {
        OllamaClient client = createClient();

        List<ChatMessage> messages = List.of(
            ChatMessage.system("You are a helpful Java programming assistant. Be concise."),
            ChatMessage.user("Write a one-line Java method signature for adding two integers")
        );

        Map<String, Object> params = new HashMap<>();
        params.put("temperature", 0.2);

        LLMResponse response = client.chat(messages, params);

        assertNotNull(response);
        assertNotNull(response.getContent());
        assertFalse(response.getContent().isEmpty());

        System.out.println("Chat response:\n" + response.getContent());
    }

    @Test
    void testEmbedGeneratesVector() {
        OllamaClient client = createClient();

        float[] embedding = client.embed("public class HelloWorld { }");

        assertNotNull(embedding);
        assertTrue(embedding.length > 0);

        System.out.println("Embedding dimensions: " + embedding.length);
    }

    @Test
    void testGetModelName() {
        OllamaClient client = createClient();

        String modelName = client.getModelName();

        assertNotNull(modelName);
        assertFalse(modelName.isEmpty());

        System.out.println("Model name: " + modelName);
    }

    @Test
    void testUsageTracking() {
        OllamaClient client = createClient();

        client.complete("Say hello", new HashMap<>());

        LLMUsage usage = client.getLastUsage();

        assertNotNull(usage);
        assertTrue(usage.getTotalTokens() >= 0);

        System.out.println("Usage - Prompt: " + usage.getPromptTokens() +
                          ", Completion: " + usage.getCompletionTokens() +
                          ", Total: " + usage.getTotalTokens());
    }
}
