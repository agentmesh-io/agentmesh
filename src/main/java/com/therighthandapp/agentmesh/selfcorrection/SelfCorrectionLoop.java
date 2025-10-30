package com.therighthandapp.agentmesh.selfcorrection;

import com.therighthandapp.agentmesh.blackboard.BlackboardService;
import com.therighthandapp.agentmesh.llm.ChatMessage;
import com.therighthandapp.agentmesh.llm.LLMClient;
import com.therighthandapp.agentmesh.llm.LLMResponse;
import com.therighthandapp.agentmesh.mast.MASTFailureMode;
import com.therighthandapp.agentmesh.mast.MASTValidator;
import com.therighthandapp.agentmesh.metrics.AgentMeshMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implements the self-correction loop: Generate → Test → Reflect → Correct
 * This is the core mechanism for achieving production-ready code quality.
 */
@Service
public class SelfCorrectionLoop {
    private static final Logger log = LoggerFactory.getLogger(SelfCorrectionLoop.class);

    @Value("${agentmesh.selfcorrection.max-iterations:5}")
    private int maxIterations;

    @Value("${agentmesh.selfcorrection.timeout-seconds:300}")
    private long timeoutSeconds;

    private final LLMClient llmClient;
    private final BlackboardService blackboard;
    private final MASTValidator mastValidator;

    @Autowired(required = false)
    private AgentMeshMetrics metrics;

    public SelfCorrectionLoop(LLMClient llmClient, BlackboardService blackboard, MASTValidator mastValidator) {
        this.llmClient = llmClient;
        this.blackboard = blackboard;
        this.mastValidator = mastValidator;
    }

    /**
     * Execute self-correction loop for code generation
     *
     * @param agentId Agent performing the task
     * @param taskDescription What to generate
     * @param requirements Quality requirements/acceptance criteria
     * @return Final corrected output or failure
     */
    public CorrectionResult correctUntilValid(String agentId, String taskDescription,
                                              List<String> requirements) {
        log.info("Starting self-correction loop for agent {} on task: {}", agentId, taskDescription);

        if (metrics != null) {
            metrics.recordSelfCorrectionAttempt();
        }

        Instant startTime = Instant.now();
        String currentOutput = null;
        List<String> conversationHistory = new ArrayList<>();

        for (int iteration = 1; iteration <= maxIterations; iteration++) {
            log.info("Self-correction iteration {}/{}", iteration, maxIterations);

            // Check timeout
            if (mastValidator.detectTimeout(agentId, taskDescription, startTime, timeoutSeconds)) {
                if (metrics != null) {
                    Duration duration = Duration.between(startTime, Instant.now());
                    metrics.recordSelfCorrectionFailure(duration, iteration);
                }
                return CorrectionResult.failure("Timeout after " + iteration + " iterations", iteration);
            }

            // Step 1: Generate (or regenerate based on feedback)
            currentOutput = generate(agentId, taskDescription, conversationHistory);
            conversationHistory.add("Generated output: " + truncate(currentOutput, 100));

            // Step 2: Test & Validate
            ValidationResult validation = validate(currentOutput, requirements);

            if (validation.isValid()) {
                log.info("Self-correction succeeded after {} iterations", iteration);
                blackboard.post(agentId, "CODE_FINAL", taskDescription, currentOutput);

                if (metrics != null) {
                    Duration duration = Duration.between(startTime, Instant.now());
                    metrics.recordSelfCorrectionSuccess(duration, iteration);
                }

                return CorrectionResult.success(currentOutput, iteration);
            }

            // Step 3: Reflect - Get critique from reviewer
            String critique = reflect(currentOutput, validation.getErrors());
            conversationHistory.add("Critique: " + critique);

            // Step 4: Prepare for correction
            conversationHistory.add("Errors to fix: " + String.join(", ", validation.getErrors()));

            // Check for loop
            if (mastValidator.detectLoop(agentId, currentOutput,
                    conversationHistory.subList(Math.max(0, conversationHistory.size() - 10),
                                              conversationHistory.size()))) {
                if (metrics != null) {
                    Duration duration = Duration.between(startTime, Instant.now());
                    metrics.recordSelfCorrectionFailure(duration, iteration);
                }
                return CorrectionResult.failure("Loop detected - agent is stuck", iteration);
            }
        }

        log.warn("Self-correction failed after {} iterations", maxIterations);
        mastValidator.recordViolation(agentId, MASTFailureMode.FM_3_1_OUTPUT_QUALITY,
                taskDescription, "Failed to produce valid output after " + maxIterations + " iterations");

        if (metrics != null) {
            Duration duration = Duration.between(startTime, Instant.now());
            metrics.recordSelfCorrectionFailure(duration, maxIterations);
        }

        return CorrectionResult.failure("Max iterations reached", maxIterations);
    }

    /**
     * Generate code using LLM
     */
    private String generate(String agentId, String taskDescription, List<String> history) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system("You are an expert software engineer. Generate high-quality, production-ready code."));
        messages.add(ChatMessage.user("Task: " + taskDescription));

        // Add history for context
        if (!history.isEmpty()) {
            String contextHistory = "Previous attempts and feedback:\n" +
                    String.join("\n", history.subList(Math.max(0, history.size() - 5), history.size()));
            messages.add(ChatMessage.assistant(contextHistory));
            messages.add(ChatMessage.user("Please correct the issues and generate improved code."));
        }

        Map<String, Object> params = new HashMap<>();
        params.put("temperature", 0.3);
        params.put("max_tokens", 2000);

        LLMResponse response = llmClient.chat(messages, params);
        log.info("Generation LLM usage: {}", llmClient.getLastUsage());

        return response.isSuccess() ? response.getContent() : "";
    }

    /**
     * Validate generated code
     */
    private ValidationResult validate(String code, List<String> requirements) {
        List<String> errors = new ArrayList<>();

        // Basic checks
        if (code == null || code.trim().isEmpty()) {
            errors.add("Output is empty");
            return new ValidationResult(false, errors);
        }

        // Check requirements
        for (String req : requirements) {
            if (!code.toLowerCase().contains(req.toLowerCase())) {
                errors.add("Missing requirement: " + req);
            }
        }

        // Syntax check (simplified - in production, use real parser)
        if (!code.contains("public") && !code.contains("class") && !code.contains("function")) {
            errors.add("Code appears malformed or incomplete");
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    /**
     * Get critique/reflection from reviewer agent
     */
    private String reflect(String code, List<String> errors) {
        List<ChatMessage> messages = List.of(
                ChatMessage.system("You are a senior code reviewer. Provide constructive feedback."),
                ChatMessage.user("Review this code and suggest improvements:\n\nCode:\n" + code +
                               "\n\nKnown issues:\n" + String.join("\n", errors))
        );

        Map<String, Object> params = new HashMap<>();
        params.put("temperature", 0.5);
        params.put("max_tokens", 500);

        LLMResponse response = llmClient.chat(messages, params);
        log.info("Reflection LLM usage: {}", llmClient.getLastUsage());

        return response.isSuccess() ? response.getContent() : "No feedback available";
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }

    /**
     * Internal class for validation results
     */
    private static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;

        ValidationResult(boolean valid, List<String> errors) {
            this.valid = valid;
            this.errors = errors;
        }

        boolean isValid() {
            return valid;
        }

        List<String> getErrors() {
            return errors;
        }
    }
}

