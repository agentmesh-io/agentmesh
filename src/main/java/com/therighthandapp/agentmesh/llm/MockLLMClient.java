package com.therighthandapp.agentmesh.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock LLM implementation for deterministic testing.
 * Records calls and returns pre-configured responses.
 */
@Component
@Primary
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
        String response = responseMap.get(prompt);
        
        // If no pre-configured response, check if a default response was explicitly set
        if (response == null && !defaultResponse.equals("Mock LLM response")) {
            response = defaultResponse;
        }
        
        // If still no response, generate contextual response
        if (response == null) {
            response = generateContextualResponse("", prompt);
        }

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

        // Get system message to understand context
        String systemMessage = messages.stream()
                .filter(m -> "system".equals(m.getRole()))
                .findFirst()
                .map(ChatMessage::getContent)
                .orElse("");

        // Check for pre-configured response first
        String response = responseMap.get(lastUserMessage);
        
        // If no pre-configured response, check if a default response was explicitly set
        if (response == null && !defaultResponse.equals("Mock LLM response")) {
            response = defaultResponse;
        }
        
        // If still no response, generate contextual mock response
        if (response == null) {
            response = generateContextualResponse(systemMessage, lastUserMessage);
        }

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

    /**
     * Generate contextual mock responses based on the request type
     */
    private String generateContextualResponse(String systemMessage, String userMessage) {
        String lowerSystem = systemMessage.toLowerCase();
        String lowerUser = userMessage.toLowerCase();
        
        // Test generation request - check for test engineer or test generation keywords
        if ((lowerSystem.contains("test") && (lowerSystem.contains("engineer") || lowerSystem.contains("generat"))) 
            || lowerUser.contains("generate") && lowerUser.contains("test")) {
            return generateMockTestCode(userMessage);
        }
        
        // Code generation request (including hello world)
        if (lowerSystem.contains("code") || lowerSystem.contains("implement") || lowerUser.contains("implement") ||
            lowerUser.contains("hello world") || lowerUser.contains("function")) {
            return generateMockCode(userMessage);
        }
        
        // Review request
        if (lowerSystem.contains("review") || lowerSystem.contains("quality") || lowerUser.contains("review")) {
            return generateMockReview(userMessage);
        }
        
        // SRS/Requirements generation
        if (lowerSystem.contains("architect") || lowerSystem.contains("srs") || lowerUser.contains("create an srs")) {
            return generateMockSRS(userMessage);
        }
        
        // Default response
        return defaultResponse;
    }
    
    private String generateMockSRS(String userMessage) {
        return "# Software Requirements Specification\n\n" +
               "## 1. Introduction\n" +
               "Mock SRS for: " + userMessage.substring(0, Math.min(50, userMessage.length())) + "\n\n" +
               "## 2. Functional Requirements\n" +
               "- FR1: System shall provide CRUD operations\n" +
               "- FR2: System shall validate input data\n" +
               "- FR3: System shall handle errors gracefully\n\n" +
               "## 3. Non-Functional Requirements\n" +
               "- NFR1: Response time < 200ms\n" +
               "- NFR2: 99.9% availability\n" +
               "- NFR3: Support 1000 concurrent users";
    }
    
    private String generateMockCode(String userMessage) {
        // Check for hello world request
        if (userMessage.toLowerCase().contains("hello")) {
            return """
                   public void helloWorld() {
                       System.out.println("Hello, World!");
                   }\
                   """;
        }
        
        return "public class MockGeneratedCode {\n" +
               "    // Generated code for: " + userMessage.substring(0, Math.min(40, userMessage.length())) + "\n" +
               "    \n" +
               "    private final Repository repository;\n" +
               "    \n" +
               "    public MockGeneratedCode(Repository repository) {\n" +
               "        this.repository = repository;\n" +
               "    }\n" +
               "    \n" +
               "    public void execute() {\n" +
               "        // Mock implementation\n" +
               "        repository.save(new Entity());\n" +
               "    }\n" +
               "}";
    }
    
    private String generateMockReview(String userMessage) {
        return """
               # Code Review Report
               
               ## Status: APPROVED
               
               ## Issues Found: 2
               
               ### Issue 1: Missing null checks
               - Severity: MEDIUM
               - Location: Line 15
               - Recommendation: Add null validation
               
               ### Issue 2: Optimize database queries
               - Severity: LOW
               - Location: Line 42
               - Recommendation: Use batch operations
               
               ## Score: 8/10
               Overall code quality is good with minor improvements needed.""";
    }
    
    private String generateMockTestCode(String userMessage) {
        return """
               @Test
               public void testCrudOperations() {
                   // Arrange
                   Entity entity = new Entity();
                   entity.setId(1L);
                   entity.setName("Test");
                  \s
                   // Act
                   repository.save(entity);
                   Entity result = repository.findById(1L).get();
                  \s
                   // Assert
                   assertNotNull(result);
                   assertEquals("Test", result.getName());
                  \s
                   // Update test
                   result.setName("Updated");
                   repository.save(result);
                   Entity updated = repository.findById(1L).get();
                   assertEquals("Updated", updated.getName());
                  \s
                   // Delete test
                   repository.deleteById(1L);
                   assertFalse(repository.findById(1L).isPresent());
               }
               
               @Test
               public void testValidation() {
                   // Test input validation
                   Entity invalid = new Entity();
                   assertThrows(ValidationException.class, () -> {
                       repository.save(invalid);
                   });
               }
               
               @Test
               public void testErrorHandling() {
                   // Test error scenarios
                   assertThrows(NotFoundException.class, () -> {
                       repository.findById(999L).orElseThrow();
                   });
               }
               
               @Test
               public void testConcurrency() {
                   // Test concurrent access
                   Entity entity = new Entity();
                   entity.setId(2L);
                   repository.save(entity);
                  \s
                   CountDownLatch latch = new CountDownLatch(10);
                   for (int i = 0; i < 10; i++) {
                       new Thread(() -> {
                           repository.findById(2L);
                           latch.countDown();
                       }).start();
                   }
                   latch.await();
               }\
               """;
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

