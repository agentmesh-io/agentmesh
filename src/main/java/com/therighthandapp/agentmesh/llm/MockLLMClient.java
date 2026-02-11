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
        String combined = lowerSystem + " " + lowerUser;
        
        // Execution Plan generation request - check this FIRST (most specific)
        if (combined.contains("execution plan") || combined.contains("expert software architect") ||
            combined.contains("implementation plan") || combined.contains("task breakdown")) {
            return generateMockExecutionPlan(userMessage);
        }
        
        // Review request - check BEFORE test (review prompts may contain "test" in metrics)
        if (lowerSystem.contains("code review") || lowerSystem.contains("reviewer") || 
            combined.contains("review report") || combined.contains("code reviewer")) {
            return generateMockReview(userMessage);
        }
        
        // Test generation request - check for test engineer or test generation keywords
        if ((combined.contains("test") && (combined.contains("engineer") || combined.contains("generat"))) 
            || (lowerUser.contains("generate") && lowerUser.contains("test"))) {
            return generateMockTestCode(userMessage);
        }
        
        // Code generation request (including hello world)
        if (lowerSystem.contains("code") || lowerSystem.contains("implement") || lowerUser.contains("implement") ||
            lowerUser.contains("hello world") || lowerUser.contains("function")) {
            return generateMockCode(userMessage);
        }
        
        // Review request (fallback with more generic matching)
        if (lowerSystem.contains("review") || lowerSystem.contains("quality") || lowerUser.contains("review")) {
            return generateMockReview(userMessage);
        }
        
        // SRS/Requirements generation
        if (lowerSystem.contains("srs") || lowerUser.contains("create an srs")) {
            return generateMockSRS(userMessage);
        }
        
        // Default response
        return defaultResponse;
    }
    
    private String generateMockExecutionPlan(String userMessage) {
        String projectName = extractProjectName(userMessage);
        String planId = java.util.UUID.randomUUID().toString();
        String timestamp = java.time.LocalDateTime.now().toString();
        
        // Generate JSON matching ExecutionPlan domain class exactly:
        // - FileDefinition: path, purpose, type (enum), dependencies, requirements
        // - Module: name, description, priority, techStack, files, dependencies, configuration
        // - FileStructure: rootDirectory, directories (Map)
        // - TestingStrategy: targetCoveragePercent, testingFrameworks, testCategories, criticalPaths
        // - TechStack: primaryLanguages, frameworks, libraries, databases, infrastructure
        return """
               {
                 "planId": "%s",
                 "srsId": "mock-srs-001",
                 "srsUrl": "http://mock-auto-bads/srs/mock-srs-001",
                 "projectTitle": "%s",
                 "generatedAt": "%s",
                 "modules": [
                   {
                     "name": "core",
                     "description": "Core business logic module",
                     "priority": "HIGH",
                     "techStack": ["Java", "Spring Boot"],
                     "dependencies": [],
                     "configuration": {},
                     "files": [
                       {
                         "path": "src/main/java/com/example/Application.java",
                         "purpose": "Main application entry point",
                         "type": "SOURCE_CODE",
                         "dependencies": ["org.springframework.boot.SpringApplication"],
                         "requirements": ["FR-001"]
                       },
                       {
                         "path": "src/main/java/com/example/service/MainService.java",
                         "purpose": "Main service implementation",
                         "type": "SOURCE_CODE",
                         "dependencies": ["org.springframework.stereotype.Service"],
                         "requirements": ["FR-002"]
                       },
                       {
                         "path": "src/main/java/com/example/controller/ApiController.java",
                         "purpose": "REST API controller",
                         "type": "SOURCE_CODE",
                         "dependencies": ["org.springframework.web.bind.annotation.RestController"],
                         "requirements": ["FR-003"]
                       }
                     ]
                   },
                   {
                     "name": "api",
                     "description": "REST API module",
                     "priority": "MEDIUM",
                     "techStack": ["Java", "Spring Web"],
                     "dependencies": ["core"],
                     "configuration": {},
                     "files": [
                       {
                         "path": "src/main/java/com/example/api/RestEndpoint.java",
                         "purpose": "REST endpoint definitions",
                         "type": "SOURCE_CODE",
                         "dependencies": ["org.springframework.web.bind.annotation.RequestMapping"],
                         "requirements": ["FR-004"]
                       }
                     ]
                   }
                 ],
                 "fileStructure": {
                   "rootDirectory": "src/main/java",
                   "directories": {
                     "com": {
                       "name": "com",
                       "purpose": "Root package",
                       "files": [],
                       "subdirectories": {
                         "example": {
                           "name": "example",
                           "purpose": "Main package",
                           "files": ["Application.java"],
                           "subdirectories": {}
                         }
                       }
                     }
                   }
                 },
                 "testingStrategy": {
                   "targetCoveragePercent": 80,
                   "testingFrameworks": ["JUnit5", "Mockito", "SpringBootTest"],
                   "testCategories": [
                     {
                       "name": "Unit Tests",
                       "description": "Tests for individual components",
                       "estimatedTestCount": 10
                     },
                     {
                       "name": "Integration Tests",
                       "description": "Tests for component interactions",
                       "estimatedTestCount": 5
                     }
                   ],
                   "criticalPaths": ["API endpoints", "Service layer"]
                 },
                 "techStack": {
                   "primaryLanguages": ["Java"],
                   "frameworks": ["Spring Boot 3.2"],
                   "libraries": ["Lombok", "Jackson"],
                   "databases": ["PostgreSQL"],
                   "infrastructure": ["Docker", "Maven"]
                 },
                 "effortEstimate": {
                   "totalHours": 40,
                   "hoursByModule": {
                     "core": 20,
                     "api": 20
                   },
                   "estimatedLinesOfCode": 500,
                   "phases": [
                     {
                       "name": "Setup",
                       "durationDays": 1,
                       "tasks": ["Project setup", "Configure dependencies"]
                     },
                     {
                       "name": "Development",
                       "durationDays": 3,
                       "tasks": ["Implement core", "Implement API"]
                     },
                     {
                       "name": "Testing",
                       "durationDays": 1,
                       "tasks": ["Unit tests", "Integration tests"]
                     }
                   ]
                 },
                 "metadata": {
                   "generatorVersion": "1.0.0",
                   "mockGenerated": "true"
                 }
               }
               """.formatted(planId, projectName, timestamp);
    }
    
    private String extractProjectName(String userMessage) {
        // Try to extract project name from user message
        if (userMessage.contains("projectName")) {
            int start = userMessage.indexOf("projectName");
            int colonIndex = userMessage.indexOf(":", start);
            if (colonIndex > 0) {
                int endQuote = userMessage.indexOf("\"", colonIndex + 2);
                if (endQuote > colonIndex) {
                    return userMessage.substring(colonIndex + 2, endQuote);
                }
            }
        }
        return "MockProject";
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
        // Return JSON matching what ReviewParser expects
        return """
               {
                 "overallScore": 8,
                 "qualityIssues": [
                   {
                     "severity": "MEDIUM",
                     "category": "NULL_CHECK",
                     "description": "Missing null checks in service methods",
                     "location": "MainService.java:15",
                     "suggestion": "Add null validation for input parameters"
                   }
                 ],
                 "securityIssues": [],
                 "bestPracticeViolations": [
                   {
                     "rule": "SOLID_PRINCIPLES",
                     "description": "Consider extracting business logic into separate service",
                     "severity": "LOW",
                     "location": "ApiController.java:30"
                   }
                 ],
                 "suggestions": [
                   {
                     "type": "PERFORMANCE",
                     "description": "Consider using batch operations for database queries",
                     "priority": "MEDIUM",
                     "estimatedImpact": "Improve response time by 20%"
                   }
                 ],
                 "codeMetrics": {
                   "linesOfCode": 150,
                   "cyclomaticComplexity": 5,
                   "testCoverage": 75.0,
                   "duplicatePercentage": 2.0
                 },
                 "complexityAnalysis": {
                   "overallComplexity": "LOW",
                   "hotspots": [],
                   "recommendations": ["Code structure is clean and maintainable"]
                 },
                 "approved": true,
                 "summary": "Code quality is good with minor improvements needed. Approved for deployment."
               }
               """;
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

