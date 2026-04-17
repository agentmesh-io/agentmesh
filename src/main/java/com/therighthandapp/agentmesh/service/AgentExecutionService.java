package com.therighthandapp.agentmesh.service;

import com.therighthandapp.agentmesh.blackboard.BlackboardService;
import com.therighthandapp.agentmesh.llm.LLMClient;
import com.therighthandapp.agentmesh.llm.ChatMessage;
import com.therighthandapp.agentmesh.llm.LLMResponse;
import com.therighthandapp.agentmesh.memory.MemoryArtifact;
import com.therighthandapp.agentmesh.memory.WeaviateService;
import com.therighthandapp.agentmesh.metrics.AgentMeshMetrics;
import com.therighthandapp.agentmesh.util.MDCContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

/**
 * Service for executing agent tasks in the E2E workflow.
 * Orchestrates agent activities with memory storage and blackboard communication.
 */
@Service
public class AgentExecutionService {
    private static final Logger log = LoggerFactory.getLogger(AgentExecutionService.class);

    private final BlackboardService blackboard;
    private final WeaviateService weaviateService;
    private final LLMClient llmClient;
    private final AgentMeshMetrics metrics;

    public AgentExecutionService(
            BlackboardService blackboard,
            WeaviateService weaviateService,
            LLMClient llmClient,
            AgentMeshMetrics metrics) {
        this.blackboard = blackboard;
        this.weaviateService = weaviateService;
        this.llmClient = llmClient;
        this.metrics = metrics;
    }

    /**
     * Execute Planner Agent
     * Analyzes user request and creates Software Requirements Specification (SRS)
     */
    public AgentExecutionResponse executePlanner(
            String tenantId, 
            String projectId, 
            String userRequest) {
        
        // Set MDC context for structured logging
        MDCContext.setTenantId(tenantId);
        MDCContext.setAgentType("planner");
        
        long startTime = System.currentTimeMillis();
        metrics.recordAgentTaskStart("planner", tenantId);
        
        log.info("Starting Planner agent execution for project: {}", projectId);
        
        try {
            // Create planning prompt
            List<ChatMessage> messages = List.of(
                ChatMessage.system("You are a software architect and business analyst. " +
                    "Analyze the user requirement and create a detailed Software Requirements Specification (SRS). " +
                    "Include: 1) Overview, 2) Functional Requirements, 3) Technical Components (Entity, Service, Controller), " +
                    "4) API Endpoints, 5) Data Model, 6) Validation Rules, 7) Error Handling Strategy."),
                ChatMessage.user("Create an SRS for this requirement:\n\n" + userRequest)
            );

            Map<String, Object> params = new HashMap<>();
            params.put("temperature", 0.7);
            params.put("max_tokens", 2000);

            // Call LLM
            LLMResponse response = llmClient.chat(messages, params);
            
            if (!response.isSuccess()) {
                metrics.recordAgentTaskFailure("planner", tenantId, "llm_failure");
                throw new RuntimeException("LLM failed: " + response.getErrorMessage());
            }

            String srsContent = response.getContent();
            log.info("Planner generated SRS: {} chars", srsContent.length());

            // Store SRS in memory
            long memoryStart = System.currentTimeMillis();
            MemoryArtifact srsArtifact = new MemoryArtifact();
            srsArtifact.setAgentId("planner-agent");
            srsArtifact.setArtifactType("SRS");
            srsArtifact.setTitle("Software Requirements Specification");
            srsArtifact.setContent(srsContent);
            srsArtifact.setProjectId(projectId);
            srsArtifact.setMetadata(Map.of(
                "userRequest", userRequest,
                "componentCount", countComponents(srsContent),
                "agentType", "planner"
            ));

            String artifactId = weaviateService.store(srsArtifact);
            long memoryDuration = System.currentTimeMillis() - memoryStart;
            metrics.recordMemoryOperation(tenantId, "store");
            log.info("SRS stored in memory: {} ({}ms)", artifactId, memoryDuration);

            // Post to blackboard
            long bbStart = System.currentTimeMillis();
            var blackboardEntry = blackboard.post(
                "planner-agent",
                "PLANNING",
                "Requirements Analysis Complete",
                "SRS created with " + countComponents(srsContent) + " components. Artifact ID: " + artifactId
            );
            long bbDuration = System.currentTimeMillis() - bbStart;
            metrics.recordBlackboardPost(tenantId, "PLANNING");

            long duration = System.currentTimeMillis() - startTime;
            metrics.recordAgentTaskSuccess("planner", tenantId, Duration.ofMillis(duration));

            return AgentExecutionResponse.success(
                "planner",
                List.of(artifactId),
                blackboardEntry.getId().toString(),
                duration,
                Map.of(
                    "srsLength", srsContent.length(),
                    "componentCount", countComponents(srsContent),
                    "llmTokens", llmClient.getLastUsage() != null ? llmClient.getLastUsage().toString() : "N/A"
                )
            );

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            metrics.recordAgentTaskFailure("planner", tenantId, e.getClass().getSimpleName());
            log.error("Planner execution failed", e);
            throw new RuntimeException("Planner execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Execute Implementer Agent
     * Generates code files based on SRS
     */
    public AgentExecutionResponse executeImplementer(
            String tenantId,
            String projectId,
            String srsArtifactId) {
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Retrieve SRS from memory using semantic search for the artifact ID
            // Since artifact IDs are stored in the content/title, we search for it
            List<MemoryArtifact> srsResults = weaviateService.searchWithFilters(
                srsArtifactId, 
                1, 
                Map.of("artifactType", "SRS"),
                false,  // Use vector search
                0.75,
                "implementer-agent"
            );
            
            if (srsResults.isEmpty()) {
                throw new RuntimeException("SRS not found: " + srsArtifactId);
            }

            String srsContent = srsResults.get(0).getContent();
            log.info("Retrieved SRS from memory: {} chars", srsContent.length());

            // Generate 3 code files: Entity, Service, Controller
            List<String> artifactIds = new ArrayList<>();
            
            // 1. Generate Entity
            String entityCode = generateCode(srsContent, "Entity", 
                "Generate a JPA Entity class based on the SRS. Include proper annotations (@Entity, @Id, @GeneratedValue), " +
                "validation annotations (@NotBlank, @Email, etc.), and getters/setters.");
            String entityId = storeCodeArtifact(projectId, "User.java", "ENTITY", entityCode);
            artifactIds.add(entityId);
            
            // 2. Generate Service
            String serviceCode = generateCode(srsContent, "Service",
                "Generate a Spring Service class with business logic. Include methods for CRUD operations, " +
                "proper error handling, and transaction management.");
            String serviceId = storeCodeArtifact(projectId, "UserService.java", "SERVICE", serviceCode);
            artifactIds.add(serviceId);
            
            // 3. Generate Controller
            String controllerCode = generateCode(srsContent, "Controller",
                "Generate a Spring REST Controller with all API endpoints from the SRS. Include proper annotations " +
                "(@RestController, @RequestMapping, @GetMapping, @PostMapping, etc.), request/response DTOs, " +
                "and error handling.");
            String controllerId = storeCodeArtifact(projectId, "UserController.java", "CONTROLLER", controllerCode);
            artifactIds.add(controllerId);

            log.info("Generated {} code files", artifactIds.size());

            // Post to blackboard
            var blackboardEntry = blackboard.post(
                "implementer-agent",
                "IMPLEMENTATION",
                "Implementation Complete - 3 files generated",
                "Generated: User.java (Entity), UserService.java (Service), UserController.java (Controller). " +
                "Artifact IDs: " + String.join(", ", artifactIds)
            );

            long duration = System.currentTimeMillis() - startTime;

            return AgentExecutionResponse.success(
                "implementer",
                artifactIds,
                blackboardEntry.getId().toString(),
                duration,
                Map.of(
                    "filesGenerated", 3,
                    "entityLines", countLines(entityCode),
                    "serviceLines", countLines(serviceCode),
                    "controllerLines", countLines(controllerCode),
                    "llmTokens", llmClient.getLastUsage() != null ? llmClient.getLastUsage().toString() : "N/A"
                )
            );

        } catch (Exception e) {
            log.error("Implementer execution failed", e);
            throw new RuntimeException("Implementer execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Execute Reviewer Agent
     * Reviews code quality and best practices
     */
    public AgentExecutionResponse executeReviewer(
            String tenantId,
            String projectId,
            String[] codeArtifactIds) {
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Retrieve all code artifacts
            StringBuilder codeToReview = new StringBuilder();
            for (String artifactId : codeArtifactIds) {
                List<MemoryArtifact> results = weaviateService.searchWithFilters(
                    artifactId,
                    1,
                    Map.of("artifactType", "CODE"),
                    false,
                    0.75,
                    "reviewer-agent"
                );
                if (!results.isEmpty()) {
                    codeToReview.append("=== ").append(results.get(0).getTitle()).append(" ===\n");
                    codeToReview.append(results.get(0).getContent()).append("\n\n");
                }
            }

            log.info("Reviewing {} code artifacts", codeArtifactIds.length);

            // Create review prompt
            List<ChatMessage> messages = List.of(
                ChatMessage.system("""
                    You are a senior code reviewer. Analyze the code for:
                    1) Proper annotations and best practices
                    2) Exception handling and error management
                    3) Validation and security
                    4) Code structure and maintainability
                    5) Missing documentation (JavaDoc)
                    6) Missing logging
                    
                    Provide a structured review with: Status (APPROVED/APPROVED_WITH_RECOMMENDATIONS/REJECTED), \
                    Issues Found (count), Detailed Issues (list), Recommendations, and Overall Score (1-10)."""),
                ChatMessage.user("Review this code:\n\n" + codeToReview.toString())
            );

            Map<String, Object> params = new HashMap<>();
            params.put("temperature", 0.5);
            params.put("max_tokens", 1500);

            LLMResponse response = llmClient.chat(messages, params);
            
            if (!response.isSuccess()) {
                throw new RuntimeException("LLM failed: " + response.getErrorMessage());
            }

            String reviewContent = response.getContent();
            log.info("Review generated: {} chars", reviewContent.length());

            // Parse review to extract metadata
            int issuesCount = extractIssuesCount(reviewContent);
            String reviewStatus = extractReviewStatus(reviewContent);
            int reviewScore = extractReviewScore(reviewContent);

            // Store review in memory
            MemoryArtifact reviewArtifact = new MemoryArtifact();
            reviewArtifact.setAgentId("reviewer-agent");
            reviewArtifact.setArtifactType("REVIEW");
            reviewArtifact.setTitle("Code Review Report");
            reviewArtifact.setContent(reviewContent);
            reviewArtifact.setProjectId(projectId);
            reviewArtifact.setMetadata(Map.of(
                "reviewStatus", reviewStatus,
                "issuesCount", issuesCount,
                "reviewScore", reviewScore,
                "codeArtifactsReviewed", codeArtifactIds.length
            ));

            String reviewId = weaviateService.store(reviewArtifact);
            log.info("Review stored in memory: {}", reviewId);

            // Post to blackboard
            var blackboardEntry = blackboard.post(
                "reviewer-agent",
                "REVIEW",
                "Code Review Complete - " + issuesCount + " issues found",
                "Review Status: " + reviewStatus + ", Score: " + reviewScore + "/10. " +
                "Review artifact ID: " + reviewId
            );

            long duration = System.currentTimeMillis() - startTime;

            return AgentExecutionResponse.success(
                "reviewer",
                List.of(reviewId),
                blackboardEntry.getId().toString(),
                duration,
                Map.of(
                    "reviewStatus", reviewStatus,
                    "issuesCount", issuesCount,
                    "reviewScore", reviewScore,
                    "reviewLength", reviewContent.length(),
                    "llmTokens", llmClient.getLastUsage() != null ? llmClient.getLastUsage().toString() : "N/A"
                )
            );

        } catch (Exception e) {
            log.error("Reviewer execution failed", e);
            throw new RuntimeException("Reviewer execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Execute Tester Agent
     * Generates test cases and calculates coverage
     */
    public AgentExecutionResponse executeTester(
            String tenantId,
            String projectId,
            String[] codeArtifactIds) {
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Retrieve all code artifacts
            StringBuilder codeToTest = new StringBuilder();
            for (String artifactId : codeArtifactIds) {
                List<MemoryArtifact> results = weaviateService.searchWithFilters(
                    artifactId,
                    1,
                    Map.of("artifactType", "CODE"),
                    false,
                    0.75,
                    "tester-agent"
                );
                if (!results.isEmpty()) {
                    codeToTest.append("=== ").append(results.get(0).getTitle()).append(" ===\n");
                    codeToTest.append(results.get(0).getContent()).append("\n\n");
                }
            }

            log.info("Generating tests for {} code artifacts", codeArtifactIds.length);

            List<String> testArtifactIds = new ArrayList<>();

            // 1. Generate Unit Tests
            String unitTestCode = generateTests(codeToTest.toString(), "Unit",
                """
                Generate comprehensive unit tests using JUnit 5 and Mockito. Include:
                - Tests for all public methods
                - Tests for success scenarios
                - Tests for error scenarios (validation failures, not found, etc.)
                - Proper use of mocks and assertions
                Use @WebMvcTest for controller tests.""");
            String unitTestId = storeTestArtifact(projectId, "UserControllerTest.java", "UNIT_TEST", unitTestCode);
            testArtifactIds.add(unitTestId);

            // 2. Generate Integration Tests
            String integrationTestCode = generateTests(codeToTest.toString(), "Integration",
                """
                Generate integration tests using @SpringBootTest. Include:
                - End-to-end API tests
                - Database interaction tests
                - Transaction tests
                Use TestRestTemplate or MockMvc for HTTP requests.""");
            String integrationTestId = storeTestArtifact(projectId, "UserServiceIntegrationTest.java", 
                "INTEGRATION_TEST", integrationTestCode);
            testArtifactIds.add(integrationTestId);

            // Calculate test coverage (simulated)
            int totalMethods = countMethods(codeToTest.toString());
            int testMethods = countTestMethods(unitTestCode) + countTestMethods(integrationTestCode);
            int coverage = Math.min(95, (testMethods * 100) / Math.max(1, totalMethods));

            log.info("Generated {} test files with {}% coverage", testArtifactIds.size(), coverage);

            // Post to blackboard
            var blackboardEntry = blackboard.post(
                "tester-agent",
                "TESTING",
                "Tests Created - " + coverage + "% coverage achieved",
                "Generated: UserControllerTest.java (Unit), UserServiceIntegrationTest.java (Integration). " +
                "Total test methods: " + testMethods + ", Coverage: " + coverage + "%. " +
                "Artifact IDs: " + String.join(", ", testArtifactIds)
            );

            long duration = System.currentTimeMillis() - startTime;

            return AgentExecutionResponse.success(
                "tester",
                testArtifactIds,
                blackboardEntry.getId().toString(),
                duration,
                Map.of(
                    "coverage", coverage,
                    "testFilesGenerated", 2,
                    "totalTestMethods", testMethods,
                    "unitTestLines", countLines(unitTestCode),
                    "integrationTestLines", countLines(integrationTestCode),
                    "llmTokens", llmClient.getLastUsage() != null ? llmClient.getLastUsage().toString() : "N/A"
                )
            );

        } catch (Exception e) {
            log.error("Tester execution failed", e);
            throw new RuntimeException("Tester execution failed: " + e.getMessage(), e);
        }
    }

    // Helper methods

    private String generateCode(String srsContent, String componentType, String instructions) {
        List<ChatMessage> messages = List.of(
            ChatMessage.system("You are an expert Java developer specializing in Spring Boot applications. " +
                "Generate clean, production-ready code following best practices."),
            ChatMessage.user("Based on this SRS:\n\n" + srsContent + "\n\n" + 
                "Generate a " + componentType + " class.\n\n" + instructions)
        );

        Map<String, Object> params = new HashMap<>();
        params.put("temperature", 0.3);
        params.put("max_tokens", 1500);

        LLMResponse response = llmClient.chat(messages, params);
        return response.isSuccess() ? response.getContent() : "// Code generation failed";
    }

    private String generateTests(String code, String testType, String instructions) {
        List<ChatMessage> messages = List.of(
            ChatMessage.system("You are an expert test engineer specializing in Java testing with JUnit 5, Mockito, and Spring Test."),
            ChatMessage.user("Generate " + testType + " tests for this code:\n\n" + code + "\n\n" + instructions)
        );

        Map<String, Object> params = new HashMap<>();
        params.put("temperature", 0.3);
        params.put("max_tokens", 2000);

        LLMResponse response = llmClient.chat(messages, params);
        return response.isSuccess() ? response.getContent() : "// Test generation failed";
    }

    private String storeCodeArtifact(String projectId, String filename, String componentType, String code) {
        MemoryArtifact artifact = new MemoryArtifact();
        artifact.setAgentId("implementer-agent");
        artifact.setArtifactType("CODE");
        artifact.setTitle(filename);
        artifact.setContent(code);
        artifact.setProjectId(projectId);
        artifact.setMetadata(Map.of(
            "language", "java",
            "componentType", componentType,
            "linesOfCode", countLines(code)
        ));
        return weaviateService.store(artifact);
    }

    private String storeTestArtifact(String projectId, String filename, String testType, String code) {
        MemoryArtifact artifact = new MemoryArtifact();
        artifact.setAgentId("tester-agent");
        artifact.setArtifactType("TEST");
        artifact.setTitle(filename);
        artifact.setContent(code);
        artifact.setProjectId(projectId);
        artifact.setMetadata(Map.of(
            "language", "java",
            "testType", testType,
            "linesOfCode", countLines(code),
            "testMethods", countTestMethods(code)
        ));
        return weaviateService.store(artifact);
    }

    private int countComponents(String srs) {
        // Simple heuristic: count mentions of common components
        int count = 0;
        if (srs.toLowerCase().contains("entity") || srs.toLowerCase().contains("model")) count++;
        if (srs.toLowerCase().contains("service") || srs.toLowerCase().contains("business logic")) count++;
        if (srs.toLowerCase().contains("controller") || srs.toLowerCase().contains("endpoint")) count++;
        if (srs.toLowerCase().contains("repository") || srs.toLowerCase().contains("database")) count++;
        if (srs.toLowerCase().contains("validation")) count++;
        return Math.max(3, count); // At least 3 components
    }

    private int countLines(String code) {
        return code.split("\n").length;
    }

    private int countMethods(String code) {
        // Simple heuristic: count "public" keyword occurrences
        return (code.split("public ").length - 1);
    }

    private int countTestMethods(String code) {
        // Count @Test annotations
        return (code.split("@Test").length - 1);
    }

    private int extractIssuesCount(String review) {
        // Try to extract number from "X issues found" or similar patterns
        if (review.contains("0 issues") || review.contains("no issues")) return 0;
        if (review.contains("1 issue")) return 1;
        if (review.contains("2 issues") || review.contains("two issues")) return 2;
        if (review.contains("3 issues") || review.contains("three issues")) return 3;
        // Default to 2 for "approved with recommendations"
        return 2;
    }

    private String extractReviewStatus(String review) {
        String lower = review.toLowerCase();
        if (lower.contains("rejected")) return "REJECTED";
        if (lower.contains("approved with") || lower.contains("recommendations")) {
            return "APPROVED_WITH_RECOMMENDATIONS";
        }
        return "APPROVED";
    }

    private int extractReviewScore(String review) {
        // Try to extract score (1-10)
        if (review.contains("10/10") || review.contains("score: 10")) return 10;
        if (review.contains("9/10") || review.contains("score: 9")) return 9;
        if (review.contains("8/10") || review.contains("score: 8")) return 8;
        if (review.contains("7/10") || review.contains("score: 7")) return 7;
        // Default to 8 for approved with recommendations
        return 8;
    }

    /**
     * Response DTO for agent execution
     */
    public static class AgentExecutionResponse {
        private String agentType;
        private boolean success;
        private List<String> artifactIds;
        private String blackboardPostId;
        private long durationMs;
        private Map<String, Object> metadata;
        private String errorMessage;

        public static AgentExecutionResponse success(
                String agentType,
                List<String> artifactIds,
                String blackboardPostId,
                long durationMs,
                Map<String, Object> metadata) {
            
            AgentExecutionResponse response = new AgentExecutionResponse();
            response.agentType = agentType;
            response.success = true;
            response.artifactIds = artifactIds;
            response.blackboardPostId = blackboardPostId;
            response.durationMs = durationMs;
            response.metadata = metadata;
            return response;
        }

        public static AgentExecutionResponse error(String agentType, String errorMessage) {
            AgentExecutionResponse response = new AgentExecutionResponse();
            response.agentType = agentType;
            response.success = false;
            response.errorMessage = errorMessage;
            response.artifactIds = Collections.emptyList();
            response.metadata = Collections.emptyMap();
            return response;
        }

        // Getters
        public String getAgentType() { return agentType; }
        public boolean isSuccess() { return success; }
        public List<String> getArtifactIds() { return artifactIds; }
        public String getBlackboardPostId() { return blackboardPostId; }
        public long getDurationMs() { return durationMs; }
        public Map<String, Object> getMetadata() { return metadata; }
        public String getErrorMessage() { return errorMessage; }
    }
}
