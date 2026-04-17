package com.therighthandapp.agentmesh.api;

import com.therighthandapp.agentmesh.agents.architect.application.ArchitectAgentService;
import com.therighthandapp.agentmesh.agents.developer.application.DeveloperAgentService;
import com.therighthandapp.agentmesh.agents.planner.application.PlannerAgentService;
import com.therighthandapp.agentmesh.agents.reviewer.application.ReviewerAgentService;
import com.therighthandapp.agentmesh.agents.tester.application.TesterAgentService;
import com.therighthandapp.agentmesh.llm.LLMClient;
import com.therighthandapp.agentmesh.memory.WeaviateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Diagnostic API for checking system health and agent availability.
 * Provides detailed status of all components for debugging.
 */
@RestController
@RequestMapping("/api/diagnostics")
@Slf4j
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001", "http://localhost:13001"})
public class DiagnosticsController {

    @Autowired(required = false)
    private PlannerAgentService plannerAgentService;

    @Autowired(required = false)
    private ArchitectAgentService architectAgentService;

    @Autowired(required = false)
    private DeveloperAgentService developerAgentService;

    @Autowired(required = false)
    private TesterAgentService testerAgentService;

    @Autowired(required = false)
    private ReviewerAgentService reviewerAgentService;

    @Autowired(required = false)
    private LLMClient llmClient;

    @Autowired(required = false)
    private WeaviateService weaviateService;

    @Value("${agentmesh.llm.ollama.enabled:false}")
    private boolean ollamaEnabled;

    @Value("${agentmesh.llm.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${agentmesh.llm.ollama.model:codellama:latest}")
    private String ollamaModel;

    @Value("${agentmesh.weaviate.enabled:false}")
    private boolean weaviateEnabled;

    @Value("${agentmesh.temporal.enabled:false}")
    private boolean temporalEnabled;

    /**
     * Get comprehensive system diagnostics
     * GET /api/diagnostics
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getDiagnostics() {
        log.info("GET /api/diagnostics - Running system diagnostics");

        Map<String, Object> diagnostics = new LinkedHashMap<>();

        // System info
        diagnostics.put("timestamp", java.time.Instant.now().toString());
        diagnostics.put("javaVersion", System.getProperty("java.version"));

        // Agent availability
        Map<String, Object> agents = new LinkedHashMap<>();
        agents.put("planner", Map.of(
            "available", plannerAgentService != null,
            "class", plannerAgentService != null ? plannerAgentService.getClass().getSimpleName() : "N/A"
        ));
        agents.put("architect", Map.of(
            "available", architectAgentService != null,
            "class", architectAgentService != null ? architectAgentService.getClass().getSimpleName() : "N/A"
        ));
        agents.put("developer", Map.of(
            "available", developerAgentService != null,
            "class", developerAgentService != null ? developerAgentService.getClass().getSimpleName() : "N/A"
        ));
        agents.put("tester", Map.of(
            "available", testerAgentService != null,
            "class", testerAgentService != null ? testerAgentService.getClass().getSimpleName() : "N/A"
        ));
        agents.put("reviewer", Map.of(
            "available", reviewerAgentService != null,
            "class", reviewerAgentService != null ? reviewerAgentService.getClass().getSimpleName() : "N/A"
        ));
        diagnostics.put("agents", agents);

        // LLM status
        Map<String, Object> llm = new LinkedHashMap<>();
        llm.put("available", llmClient != null);
        llm.put("clientClass", llmClient != null ? llmClient.getClass().getSimpleName() : "N/A");
        llm.put("modelName", llmClient != null ? llmClient.getModelName() : "N/A");
        llm.put("ollamaEnabled", ollamaEnabled);
        llm.put("ollamaBaseUrl", ollamaBaseUrl);
        llm.put("ollamaModel", ollamaModel);
        diagnostics.put("llm", llm);

        // External services
        Map<String, Object> services = new LinkedHashMap<>();
        services.put("weaviate", Map.of(
            "enabled", weaviateEnabled,
            "available", weaviateService != null
        ));
        services.put("temporal", Map.of(
            "enabled", temporalEnabled
        ));
        diagnostics.put("services", services);

        // Configuration summary
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("ollamaEnabled", ollamaEnabled);
        config.put("weaviateEnabled", weaviateEnabled);
        config.put("temporalEnabled", temporalEnabled);
        diagnostics.put("config", config);

        return ResponseEntity.ok(diagnostics);
    }

    /**
     * Test LLM connectivity
     * POST /api/diagnostics/llm/test
     */
    @PostMapping("/llm/test")
    public ResponseEntity<Map<String, Object>> testLLM(@RequestBody(required = false) Map<String, String> request) {
        log.info("POST /api/diagnostics/llm/test - Testing LLM connectivity");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", java.time.Instant.now().toString());

        if (llmClient == null) {
            result.put("status", "ERROR");
            result.put("error", "No LLM client available");
            return ResponseEntity.ok(result);
        }

        result.put("clientClass", llmClient.getClass().getSimpleName());
        result.put("modelName", llmClient.getModelName());

        try {
            String testPrompt = request != null && request.containsKey("prompt")
                ? request.get("prompt")
                : "Say 'Hello, AgentMesh!' in one short sentence.";

            long startTime = System.currentTimeMillis();
            var response = llmClient.complete(testPrompt, Map.of("max_tokens", 50));
            long duration = System.currentTimeMillis() - startTime;

            result.put("status", response.isSuccess() ? "SUCCESS" : "ERROR");
            result.put("responseTime", duration + "ms");
            result.put("response", response.isSuccess() ? response.getContent() : response.getErrorMessage());

            if (llmClient.getLastUsage() != null) {
                result.put("usage", Map.of(
                    "promptTokens", llmClient.getLastUsage().getPromptTokens(),
                    "completionTokens", llmClient.getLastUsage().getCompletionTokens(),
                    "totalTokens", llmClient.getLastUsage().getTotalTokens()
                ));
            }

        } catch (Exception e) {
            log.error("LLM test failed", e);
            result.put("status", "ERROR");
            result.put("error", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Get agent capabilities
     * GET /api/diagnostics/agents
     */
    @GetMapping("/agents")
    public ResponseEntity<Map<String, Object>> getAgentCapabilities() {
        log.info("GET /api/diagnostics/agents - Getting agent capabilities");

        Map<String, Object> capabilities = new LinkedHashMap<>();

        // Count available agents
        int availableCount = 0;
        if (plannerAgentService != null) availableCount++;
        if (architectAgentService != null) availableCount++;
        if (developerAgentService != null) availableCount++;
        if (testerAgentService != null) availableCount++;
        if (reviewerAgentService != null) availableCount++;

        capabilities.put("totalAgents", 5);
        capabilities.put("availableAgents", availableCount);
        capabilities.put("completeness", (availableCount * 100 / 5) + "%");

        // Workflow readiness
        boolean canRunWorkflow = plannerAgentService != null && llmClient != null;
        capabilities.put("workflowReady", canRunWorkflow);

        if (!canRunWorkflow) {
            StringBuilder missing = new StringBuilder();
            if (plannerAgentService == null) missing.append("PlannerAgent, ");
            if (llmClient == null) missing.append("LLMClient, ");
            capabilities.put("missingForWorkflow", missing.toString().replaceAll(", $", ""));
        }

        return ResponseEntity.ok(capabilities);
    }
}

