package com.therighthandapp.agentmesh.orchestration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.therighthandapp.agentmesh.agents.architect.application.ArchitectAgentService;
import com.therighthandapp.agentmesh.agents.architect.domain.SystemArchitecture;
import com.therighthandapp.agentmesh.agents.developer.application.DeveloperAgentService;
import com.therighthandapp.agentmesh.agents.developer.domain.CodeArtifact;
import com.therighthandapp.agentmesh.agents.planner.application.PlannerAgentService;
import com.therighthandapp.agentmesh.agents.planner.domain.ExecutionPlan;
import com.therighthandapp.agentmesh.agents.reviewer.application.ReviewerAgentService;
import com.therighthandapp.agentmesh.blackboard.BlackboardEntry;
import com.therighthandapp.agentmesh.blackboard.BlackboardService;
import com.therighthandapp.agentmesh.llm.ChatMessage;
import com.therighthandapp.agentmesh.llm.LLMClient;
import com.therighthandapp.agentmesh.llm.LLMResponse;
import com.therighthandapp.agentmesh.selfcorrection.CorrectionResult;
import com.therighthandapp.agentmesh.selfcorrection.SelfCorrectionLoop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implementation of Temporal activities for agent tasks.
 * This connects the Temporal workflow engine to the ASEM Blackboard and LLM agents.
 */
@Component
public class AgentActivityImpl implements AgentActivity {
    private static final Logger log = LoggerFactory.getLogger(AgentActivityImpl.class);

    private final BlackboardService blackboard;
    private final LLMClient llmClient;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private SelfCorrectionLoop selfCorrectionLoop;
    
    @Autowired(required = false)
    private PlannerAgentService plannerAgentService;
    
    @Autowired(required = false)
    private ArchitectAgentService architectAgentService;
    
    @Autowired(required = false)
    private DeveloperAgentService developerAgentService;
    
    @Autowired(required = false)
    private ReviewerAgentService reviewerAgentService;
    
    @Autowired(required = false)
    private com.therighthandapp.agentmesh.agents.tester.application.TesterAgentService testerAgentService;

    public AgentActivityImpl(BlackboardService blackboard, LLMClient llmClient, ObjectMapper objectMapper) {
        this.blackboard = blackboard;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String executePlanning(String srsContent) {
        log.info("Executing planning activity for SRS");

        // Use PlannerAgentService if available
        if (plannerAgentService != null) {
            try {
                // Try to parse SRS ID from content
                // If content looks like an SRS ID (srs-X or just a number), use it
                // Otherwise, treat it as a project description and skip Auto-BADS lookup
                String srsId = null;
                if (srsContent.matches("^(srs-)?\\d+$")) {
                    // Extract numeric ID
                    srsId = srsContent.replaceFirst("^srs-", "");
                    log.info("Attempting to fetch SRS from Auto-BADS: {}", srsId);
                    ExecutionPlan plan = plannerAgentService.generateExecutionPlan(srsId);
                    log.info("Execution plan generated successfully from Auto-BADS SRS: {}", plan.getPlanId());
                    return plan.getPlanId();
                } else {
                    // Content is a project description, not an SRS ID
                    log.info("Input is a project description, generating plan without Auto-BADS");
                    // For now, fall through to basic planning
                    // In the future, PlannerAgentService could have a generateFromDescription() method
                }
            } catch (Exception e) {
                log.error("PlannerAgentService failed, falling back to basic planning: {}", e.getMessage());
                // Fall through to basic planning
            }
        }

        // Fallback: Basic planning without structured output
        log.info("Using basic planning (no PlannerAgentService)");
        
        // Create planner prompt
        String prompt = buildPlannerPrompt(srsContent);

        // Call LLM
        Map<String, Object> params = new HashMap<>();
        params.put("temperature", 0.7);
        params.put("max_tokens", 2000);

        LLMResponse response = llmClient.complete(prompt, params);
        log.info("Planner LLM usage: {}", llmClient.getLastUsage());

        String content = response.isSuccess() ? response.getContent() : "Planning failed: " + response.getErrorMessage();
        
        // Post planning result to Blackboard
        var entry = blackboard.post("planner-agent", "PLAN", "Execution Plan", content);

        // Always return the blackboard entry ID as a numeric string
        // The executeArchitecture method knows how to handle numeric IDs
        // by falling back to basic LLM-based architecture generation
        String planId = entry.getId().toString();
        log.info("Basic planning complete, returning blackboard entry ID: {}", planId);

        return planId;
    }

    @Override
    public String executeArchitecture(String planId) {
        log.info("Executing architecture design for plan: {}", planId);
        
        // If planId is a simple numeric ID (blackboard entry), skip the ArchitectAgentService
        // and use basic LLM-based architecture generation
        boolean isBlackboardEntryId = planId != null && planId.matches("^\\d+$");

        if (architectAgentService != null && !isBlackboardEntryId) {
            try {
                SystemArchitecture architecture = architectAgentService.generateArchitecture(planId);
                log.info("Architecture generated successfully via ArchitectAgentService: {}", architecture.getArchitectureId());
                return architecture.getArchitectureId();
            } catch (Exception e) {
                log.warn("ArchitectAgentService failed, falling back to basic architecture generation: {}", e.getMessage());
                // Fall through to basic architecture generation
            }
        }

        // Fallback: Basic architecture generation using LLM directly
        log.info("Using basic LLM architecture generation for planId: {}", planId);

        // Retrieve plan content from blackboard if possible
        String planContent = "";
        try {
            if (isBlackboardEntryId) {
                var entryOpt = blackboard.getById(Long.parseLong(planId));
                if (entryOpt.isPresent()) {
                    planContent = entryOpt.get().getContent();
                }
            }
        } catch (Exception e) {
            log.warn("Could not retrieve plan from blackboard: {}", e.getMessage());
        }

        String prompt = buildArchitecturePrompt(planContent);

        Map<String, Object> params = new HashMap<>();
        params.put("temperature", 0.7);
        params.put("max_tokens", 3000);

        LLMResponse response = llmClient.complete(prompt, params);
        log.info("Architecture LLM usage: {}", llmClient.getLastUsage());

        String content = response.isSuccess() ? response.getContent() : "Architecture generation failed: " + response.getErrorMessage();

        // Post architecture result to Blackboard
        var entry = blackboard.post("architect-agent", "ARCHITECTURE", "System Architecture", content);

        return entry.getId().toString();
    }

    private String buildArchitecturePrompt(String planContent) {
        return """
            You are a senior software architect. Based on the following execution plan, design a comprehensive system architecture.
            
            EXECUTION PLAN:
            %s
            
            Generate a system architecture that includes:
            1. High-level system components and their responsibilities
            2. Component interactions and data flows
            3. Technology stack recommendations
            4. Database schema considerations
            5. API design patterns
            6. Security considerations
            7. Scalability approach
            
            Format your response as a detailed architectural document.
            """.formatted(planContent.isEmpty() ? "No specific plan provided - create a general microservices architecture" : planContent);
    }

    @Override
    public String executeDevelopment(String planId, String architectureId) {
        log.info("Executing code development for plan: {}, architecture: {}", planId, architectureId);
        
        if (developerAgentService == null) {
            log.warn("DeveloperAgentService not available, skipping code generation");
            return "SKIPPED";
        }
        
        try {
            CodeArtifact codeArtifact = developerAgentService.generateCode(planId, architectureId);
            log.info("Code generated successfully: {} files, {} LOC", 
                     codeArtifact.getSourceFiles().size(),
                     codeArtifact.getTotalLinesOfCode());
            return codeArtifact.getArtifactId();
        } catch (Exception e) {
            log.error("Code generation failed for plan: {}, architecture: {}", planId, architectureId, e);
            throw new RuntimeException("Code generation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String executeCodeGeneration(String planId, String taskDescription) {
        log.info("Executing code generation for task: {}, planId: {}", taskDescription, planId);

        // Use DeveloperAgentService if available (preferred path)
        if (developerAgentService != null) {
            try {
                log.info("Using DeveloperAgentService for code generation");
                // DeveloperAgentService expects both planId and architectureId
                // Try to use it, but if it fails (e.g., plan not found), fall back
                CodeArtifact codeArtifact = developerAgentService.generateCode(planId, planId);
                log.info("Code artifact generated successfully: {}", codeArtifact.getArtifactId());
                return codeArtifact.getArtifactId();
            } catch (Exception e) {
                log.warn("DeveloperAgentService failed (likely plan not in expected format), falling back to basic generation: {}", e.getMessage());
                // Fall through to basic generation
            }
        }

        // Fallback: Basic generation without DeveloperAgentService
        log.info("Using basic code generation (no DeveloperAgentService or it failed)");

        // Retrieve plan from Blackboard by ID
        String planContent;
        try {
            // Try to parse as Long (Blackboard entry ID)
            Long entryId = Long.parseLong(planId);
            planContent = blackboard.getById(entryId)
                    .map(BlackboardEntry::getContent)
                    .orElse("No plan found for ID: " + planId);
            log.debug("Retrieved plan content from Blackboard entry {}", entryId);
        } catch (NumberFormatException e) {
            // Not a Long - might be a UUID, try to find it in PLAN entries
            log.debug("Searching for plan by planId: {}", planId);
            
            var planEntries = blackboard.readByType("PLAN");
            planContent = null;
            
            for (Object obj : planEntries) {
                try {
                    // Handle both BlackboardEntry objects and cached LinkedHashMaps
                    String content;
                    if (obj instanceof BlackboardEntry) {
                        content = ((BlackboardEntry) obj).getContent();
                    } else if (obj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> map = (Map<String, Object>) obj;
                        content = (String) map.get("content");
                    } else {
                        log.warn("Unexpected object type in PLAN entries: {}", obj.getClass());
                        continue;
                    }
                    
                    // Try to parse as ExecutionPlan
                    ExecutionPlan plan = objectMapper.readValue(content, ExecutionPlan.class);
                    if (planId.equals(plan.getPlanId())) {
                        planContent = content;
                        log.debug("Found plan: {}", planId);
                        break;
                    }
                } catch (Exception ex) {
                    log.debug("Failed to parse entry as ExecutionPlan: {}", ex.getMessage());
                }
            }
            
            if (planContent == null) {
                planContent = "No plan found for planId: " + planId;
                log.warn("Plan not found: {}", planId);
            }
        }

        // Create coder prompt with context
        List<ChatMessage> messages = List.of(
                ChatMessage.system("You are an expert software engineer. Generate production-ready code based on the plan."),
                ChatMessage.user("Plan:\n" + planContent + "\n\nTask: " + taskDescription)
        );

        Map<String, Object> params = new HashMap<>();
        params.put("temperature", 0.3);
        params.put("max_tokens", 3000);

        LLMResponse response = llmClient.chat(messages, params);
        log.info("Developer LLM usage: {}", llmClient.getLastUsage());

        // Generate a code artifact with UUID
        String codeArtifactId = UUID.randomUUID().toString();
        String codeContent = response.isSuccess() ? response.getContent() : "Code generation failed";
        
        // Create a proper CodeArtifact structure matching the domain model
        String codeJson;
        try {
            // Create a properly structured CodeArtifact JSON that can be deserialized
            Map<String, Object> artifactMap = new HashMap<>();
            artifactMap.put("artifactId", codeArtifactId);
            artifactMap.put("planId", planId);
            artifactMap.put("architectureId", null);
            artifactMap.put("projectTitle", taskDescription);
            artifactMap.put("createdAt", java.time.LocalDateTime.now().toString());
            
            // Create a single source file with the generated code
            Map<String, Object> sourceFile = new HashMap<>();
            sourceFile.put("filePath", "src/main/java/GeneratedCode.java");
            sourceFile.put("fileName", "GeneratedCode.java");
            sourceFile.put("language", "java");
            sourceFile.put("content", codeContent);
            sourceFile.put("imports", java.util.Collections.emptyList());
            sourceFile.put("lineCount", codeContent.split("\n").length);
            
            artifactMap.put("sourceFiles", java.util.Collections.singletonList(sourceFile));
            artifactMap.put("dependencies", java.util.Collections.emptyList());
            artifactMap.put("buildConfig", null);
            artifactMap.put("qualityMetrics", null);
            artifactMap.put("metadata", java.util.Collections.emptyMap());
            
            codeJson = objectMapper.writeValueAsString(artifactMap);
            log.info("Created properly structured CodeArtifact JSON with length: {} chars", codeJson.length());
            log.debug("CodeArtifact JSON preview: {}", codeJson.substring(0, Math.min(200, codeJson.length())));
        } catch (Exception e) {
            log.error("Failed to create code artifact JSON", e);
            codeJson = codeContent;  // Fall back to raw content
        }

        log.info("About to post code to blackboard - content length: {} chars", codeJson.length());
        var entry = blackboard.post("developer-agent", "CODE", taskDescription, codeJson);
        log.info("Code posted to Blackboard: entryId={}, artifactId={}, saved content length in DB: {}", 
            entry.getId(), codeArtifactId, entry.getContent().length());

        // Return the artifactId, not the entry ID
        return codeArtifactId;
    }

    @Override
    public String executeCodeReview(String codeId) {
        log.info("Executing code review for code artifact: {}", codeId);
        
        // Use new ReviewerAgentService if available and codeId is a UUID
        boolean isUUID = codeId != null && codeId.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

        if (reviewerAgentService != null && isUUID) {
            try {
                String reviewReportId = reviewerAgentService.generateReview(codeId);
                log.info("Review report generated successfully: {}", reviewReportId);
                return reviewReportId;
            } catch (Exception e) {
                log.warn("ReviewerAgentService failed, falling back to LLM review: {}", e.getMessage());
            }
        }
        
        // Fallback to LLM-based review
        log.info("Using LLM-based code review for codeId: {}", codeId);

        // Retrieve code from Blackboard
        String codeContent = "";
        try {
            if (codeId.matches("^\\d+$")) {
                // Numeric ID - blackboard entry
                codeContent = blackboard.getById(Long.parseLong(codeId))
                        .map(e -> e.getContent())
                        .orElse("No code found");
            } else {
                // UUID - search CODE entries
                var codeEntries = blackboard.readByType("CODE");
                for (Object obj : codeEntries) {
                    if (obj instanceof BlackboardEntry entry) {
                        if (entry.getContent().contains(codeId)) {
                            codeContent = entry.getContent();
                            break;
                        }
                    }
                }
                if (codeContent.isEmpty()) {
                    codeContent = "Code artifact not found for ID: " + codeId;
                }
            }
        } catch (Exception e) {
            log.warn("Could not retrieve code content: {}", e.getMessage());
            codeContent = "Code retrieval failed";
        }

        // Create reviewer prompt
        List<ChatMessage> messages = List.of(
                ChatMessage.system("You are a senior code reviewer. Analyze the code for quality, security, and best practices."),
                ChatMessage.user("Review this code:\n\n" + codeContent)
        );

        Map<String, Object> params = new HashMap<>();
        params.put("temperature", 0.5);
        params.put("max_tokens", 1500);

        LLMResponse response = llmClient.chat(messages, params);
        log.info("Reviewer LLM usage: {}", llmClient.getLastUsage());

        var entry = blackboard.post("reviewer-agent", "REVIEW", "Code Review Result",
                response.isSuccess() ? response.getContent() : "Review failed");

        return entry.getId().toString();
    }

    @Override
    public String executeDebug(String testFailureId) {
        log.info("Executing debug for test failure: {}", testFailureId);

        // Retrieve test failure from Blackboard
        String failureInfo = blackboard.getById(Long.parseLong(testFailureId))
                .map(e -> e.getContent())
                .orElse("No failure info found");

        // Create debugger prompt
        List<ChatMessage> messages = List.of(
                ChatMessage.system("You are an expert debugger. Analyze test failures and suggest fixes."),
                ChatMessage.user("Test failure:\n" + failureInfo + "\n\nProvide root cause analysis and fix suggestions.")
        );

        Map<String, Object> params = new HashMap<>();
        params.put("temperature", 0.4);
        params.put("max_tokens", 2000);

        LLMResponse response = llmClient.chat(messages, params);
        log.info("Debugger LLM usage: {}", llmClient.getLastUsage());

        var entry = blackboard.post("debugger-agent", "DEBUG", "Debug Analysis",
                response.isSuccess() ? response.getContent() : "Debug failed");

        return entry.getId().toString();
    }

    @Override
    public String executeTestGeneration(String codeId) {
        log.info("Executing test generation for code: {}", codeId);
        
        // Try using TesterAgentService if available
        if (testerAgentService != null) {
            try {
                log.info("Using TesterAgentService for test generation");
                String testSuiteId = testerAgentService.generateTestSuite(codeId);
                log.info("TesterAgent completed: test suite ID = {}", testSuiteId);
                return testSuiteId;
            } catch (Exception e) {
                log.error("TesterAgentService failed, falling back to legacy test generation", e);
            }
        } else {
            log.warn("TesterAgentService not available, using legacy test generation");
        }

        // Fallback: Legacy test generation
        String codeContent;
        try {
            // Try to parse as Long (Blackboard entry ID)
            Long entryId = Long.parseLong(codeId);
            codeContent = blackboard.getById(entryId)
                    .map(BlackboardEntry::getContent)
                    .orElse("No code found for ID: " + codeId);
            log.debug("Retrieved code from Blackboard entry {}", entryId);
        } catch (NumberFormatException e) {
            // Not a Long - it's a UUID (artifactId), need to find it in CODE entries
            log.debug("Searching for code artifact by artifactId: {}", codeId);
            
            var codeEntries = blackboard.readByType("CODE");
            codeContent = null;
            
            for (Object obj : codeEntries) {
                try {
                    // Handle both BlackboardEntry objects and cached LinkedHashMaps
                    String content;
                    if (obj instanceof BlackboardEntry) {
                        content = ((BlackboardEntry) obj).getContent();
                    } else if (obj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> map = (Map<String, Object>) obj;
                        content = (String) map.get("content");
                    } else {
                        log.warn("Unexpected object type in CODE entries: {}", obj.getClass());
                        continue;
                    }
                    
                    // Try to parse as CodeArtifact JSON
                    var jsonNode = objectMapper.readTree(content);
                    if (jsonNode.has("artifactId") && codeId.equals(jsonNode.get("artifactId").asText())) {
                        codeContent = content;
                        log.debug("Found code artifact: {}", codeId);
                        break;
                    }
                } catch (Exception ex) {
                    log.debug("Failed to parse entry as CodeArtifact: {}", ex.getMessage());
                }
            }
            
            if (codeContent == null) {
                codeContent = "No code found for artifactId: " + codeId;
                log.warn("Code artifact not found: {}", codeId);
            }
        }

        List<ChatMessage> messages = List.of(
                ChatMessage.system("You are a test automation expert. Generate comprehensive unit tests."),
                ChatMessage.user("Generate unit tests for this code:\n\n" + codeContent)
        );

        Map<String, Object> params = new HashMap<>();
        params.put("temperature", 0.4);
        params.put("max_tokens", 2500);

        LLMResponse response = llmClient.chat(messages, params);
        log.info("Test Agent LLM usage: {}", llmClient.getLastUsage());

        var entry = blackboard.post("test-agent", "TEST", "Generated Tests",
                response.isSuccess() ? response.getContent() : "Test generation failed");

        return entry.getId().toString();
    }

    private String buildPlannerPrompt(String srsContent) {
        return """
                You are an expert software architect. Given the following Software Requirements Specification (SRS),
                create a detailed implementation plan with:
                1. Task breakdown
                2. Dependencies between tasks
                3. Estimated complexity
                4. Key technical decisions
                
                SRS:
                %s
                
                Provide a structured plan that can be executed by a development team.
                """.formatted(srsContent);
    }
}

