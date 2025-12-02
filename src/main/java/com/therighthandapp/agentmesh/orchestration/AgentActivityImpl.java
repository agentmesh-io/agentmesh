package com.therighthandapp.agentmesh.orchestration;

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

/**
 * Implementation of Temporal activities for agent tasks.
 * This connects the Temporal workflow engine to the ASEM Blackboard and LLM agents.
 */
@Component
public class AgentActivityImpl implements AgentActivity {
    private static final Logger log = LoggerFactory.getLogger(AgentActivityImpl.class);

    private final BlackboardService blackboard;
    private final LLMClient llmClient;

    @Autowired(required = false)
    private SelfCorrectionLoop selfCorrectionLoop;

    public AgentActivityImpl(BlackboardService blackboard, LLMClient llmClient) {
        this.blackboard = blackboard;
        this.llmClient = llmClient;
    }

    @Override
    public String executePlanning(String srsContent) {
        log.info("Executing planning activity for SRS");

        // Create planner prompt
        String prompt = buildPlannerPrompt(srsContent);

        // Call LLM
        Map<String, Object> params = new HashMap<>();
        params.put("temperature", 0.7);
        params.put("max_tokens", 2000);

        LLMResponse response = llmClient.complete(prompt, params);
        log.info("Planner LLM usage: {}", llmClient.getLastUsage());

        // Post planning result to Blackboard
        var entry = blackboard.post("planner-agent", "PLAN", "Execution Plan",
                response.isSuccess() ? response.getContent() : "Planning failed: " + response.getErrorMessage());

        return entry.getId().toString();
    }

    @Override
    public String executeCodeGeneration(String planId, String taskDescription) {
        log.info("Executing code generation for task: {}", taskDescription);

        // Use self-correction loop if available
        if (selfCorrectionLoop != null) {
            log.info("Using self-correction loop for code generation");

            // Define quality requirements
            List<String> requirements = Arrays.asList("class", "public", "method");

            CorrectionResult result = selfCorrectionLoop.correctUntilValid(
                    "coder-agent",
                    taskDescription,
                    requirements
            );

            if (result.isSuccess()) {
                log.info("Self-correction succeeded after {} iterations", result.getIterationCount());
                // Result already posted to Blackboard by SelfCorrectionLoop
                var entries = blackboard.readByType("CODE_FINAL");
                if (!entries.isEmpty()) {
                    return entries.get(entries.size() - 1).getId().toString();
                }
            } else {
                log.warn("Self-correction failed: {}", result.getFailureReason());
                // Fall through to basic generation
            }
        }

        // Fallback: Basic generation without self-correction
        log.info("Using basic code generation (no self-correction)");

        // Retrieve plan from Blackboard
        String planContent = blackboard.getById(Long.parseLong(planId))
                .map(e -> e.getContent())
                .orElse("No plan found");

        // Create coder prompt with context
        List<ChatMessage> messages = List.of(
                ChatMessage.system("You are an expert software engineer. Generate production-ready code based on the plan."),
                ChatMessage.user("Plan:\n" + planContent + "\n\nTask: " + taskDescription)
        );

        Map<String, Object> params = new HashMap<>();
        params.put("temperature", 0.3);
        params.put("max_tokens", 3000);

        LLMResponse response = llmClient.chat(messages, params);
        log.info("Coder LLM usage: {}", llmClient.getLastUsage());

        var entry = blackboard.post("coder-agent", "CODE", taskDescription,
                response.isSuccess() ? response.getContent() : "Code generation failed");

        return entry.getId().toString();
    }

    @Override
    public String executeCodeReview(String codeId) {
        log.info("Executing code review for code: {}", codeId);

        // Retrieve code from Blackboard
        String codeContent = blackboard.getById(Long.parseLong(codeId))
                .map(e -> e.getContent())
                .orElse("No code found");

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

        // Retrieve code from Blackboard
        String codeContent = blackboard.getById(Long.parseLong(codeId))
                .map(e -> e.getContent())
                .orElse("No code found");

        // Create test agent prompt
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

