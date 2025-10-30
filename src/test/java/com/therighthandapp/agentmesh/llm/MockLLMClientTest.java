package com.therighthandapp.agentmesh.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class MockLLMClientTest {

    private MockLLMClient client;

    @BeforeEach
    public void setUp() {
        client = new MockLLMClient();
        client.reset();
    }

    @Test
    public void testCompleteWithDefaultResponse() {
        Map<String, Object> params = new HashMap<>();
        params.put("temperature", 0.7);

        LLMResponse response = client.complete("Test prompt", params);

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getContent()).isEqualTo("Mock LLM response");
        assertThat(response.getModelName()).isEqualTo("mock-gpt-4");
        assertThat(client.getLastUsage()).isNotNull();
        assertThat(client.getLastUsage().getTotalTokens()).isGreaterThan(0);
    }

    @Test
    public void testCompleteWithConfiguredResponse() {
        String testPrompt = "Generate a hello world function";
        String expectedResponse = "def hello_world():\n    print('Hello, World!')";

        client.addResponse(testPrompt, expectedResponse);

        LLMResponse response = client.complete(testPrompt, null);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getContent()).isEqualTo(expectedResponse);
    }

    @Test
    public void testChat() {
        List<ChatMessage> messages = List.of(
                ChatMessage.system("You are a helpful assistant"),
                ChatMessage.user("What is 2+2?")
        );

        client.addResponse("What is 2+2?", "2 + 2 = 4");

        LLMResponse response = client.chat(messages, null);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getContent()).isEqualTo("2 + 2 = 4");
    }

    @Test
    public void testEmbed() {
        String text = "This is test text for embedding";

        float[] embedding = client.embed(text);

        assertThat(embedding).isNotNull();
        assertThat(embedding.length).isEqualTo(384);

        // Verify deterministic behavior (same text = same embedding)
        float[] embedding2 = client.embed(text);
        assertThat(embedding2).isEqualTo(embedding);
    }

    @Test
    public void testCallHistory() {
        client.complete("Prompt 1", null);
        client.complete("Prompt 2", null);
        client.embed("Text for embedding");

        List<MockLLMClient.CallRecord> history = client.getCallHistory();

        assertThat(history).hasSize(3);
        assertThat(history.get(0).getMethod()).isEqualTo("complete");
        assertThat(history.get(1).getMethod()).isEqualTo("complete");
        assertThat(history.get(2).getMethod()).isEqualTo("embed");
    }

    @Test
    public void testTokenCounting() {
        String shortPrompt = "Hi";
        String longPrompt = "This is a much longer prompt that should result in more tokens being counted";

        LLMResponse response1 = client.complete(shortPrompt, null);
        int shortTokens = client.getLastUsage().getPromptTokens();

        LLMResponse response2 = client.complete(longPrompt, null);
        int longTokens = client.getLastUsage().getPromptTokens();

        assertThat(longTokens).isGreaterThan(shortTokens);
    }

    @Test
    public void testCostEstimation() {
        LLMResponse response = client.complete("Calculate cost", null);

        assertThat(client.getLastUsage().getEstimatedCost()).isGreaterThan(0);
    }

    @Test
    public void testReset() {
        client.complete("Test", null);
        client.addResponse("key", "value");

        assertThat(client.getCallHistory()).isNotEmpty();

        client.reset();

        assertThat(client.getCallHistory()).isEmpty();
        assertThat(client.getLastUsage()).isNull();
    }

    @Test
    public void testCustomDefaultResponse() {
        String customDefault = "Custom default response";
        client.setDefaultResponse(customDefault);

        LLMResponse response = client.complete("Unmatched prompt", null);

        assertThat(response.getContent()).isEqualTo(customDefault);
    }
}

