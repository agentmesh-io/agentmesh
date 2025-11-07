package com.therighthandapp.agentmesh.metrics;

import com.therighthandapp.agentmesh.mast.MASTFailureMode;
import com.therighthandapp.agentmesh.mast.MASTValidator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Metrics instrumentation for AgentMesh observability.
 * Exposes metrics via Micrometer for Prometheus/Grafana.
 */
@Component
public class AgentMeshMetrics {

    private final MeterRegistry meterRegistry;
    private final MASTValidator mastValidator;

    // LLM Counters
    private final Counter llmCallsTotal;
    private final Counter llmTokensTotal;
    private final Counter selfCorrectionAttempts;
    private final Counter selfCorrectionSuccesses;
    private final Counter selfCorrectionFailures;

    // Agent Execution Counters
    private final Counter agentTasksTotal;
    private final Counter agentTasksSuccess;
    private final Counter agentTasksFailure;
    
    // Blackboard Counters
    private final Counter blackboardPostsTotal;
    private final Counter blackboardQueriesTotal;
    
    // Memory Counters
    private final Counter memoryOperationsTotal;
    private final Counter memoryHybridSearchTotal;

    // Timers
    private final Timer selfCorrectionDuration;
    private final Timer llmCallDuration;
    private final Timer agentExecutionDuration;
    private final Timer blackboardQueryDuration;
    private final Timer memorySearchDuration;

    // MAST violation counters per failure mode
    private final Map<MASTFailureMode, Counter> mastViolationCounters = new ConcurrentHashMap<>();

    public AgentMeshMetrics(MeterRegistry meterRegistry, MASTValidator mastValidator) {
        this.meterRegistry = meterRegistry;
        this.mastValidator = mastValidator;

        // Initialize counters
        this.llmCallsTotal = Counter.builder("agentmesh.llm.calls.total")
                .description("Total number of LLM API calls")
                .register(meterRegistry);

        this.llmTokensTotal = Counter.builder("agentmesh.llm.tokens.total")
                .description("Total number of tokens consumed")
                .register(meterRegistry);

        this.selfCorrectionAttempts = Counter.builder("agentmesh.selfcorrection.attempts.total")
                .description("Total self-correction attempts")
                .register(meterRegistry);

        this.selfCorrectionSuccesses = Counter.builder("agentmesh.selfcorrection.successes.total")
                .description("Successful self-corrections")
                .register(meterRegistry);

        this.selfCorrectionFailures = Counter.builder("agentmesh.selfcorrection.failures.total")
                .description("Failed self-corrections")
                .register(meterRegistry);

        // Initialize timers
        this.selfCorrectionDuration = Timer.builder("agentmesh.selfcorrection.duration")
                .description("Duration of self-correction cycles")
                .register(meterRegistry);

        this.llmCallDuration = Timer.builder("agentmesh.llm.call.duration")
                .description("Duration of LLM API calls")
                .register(meterRegistry);

        // Initialize agent execution metrics
        this.agentTasksTotal = Counter.builder("agentmesh.agent.tasks.total")
                .description("Total number of agent tasks executed")
                .register(meterRegistry);

        this.agentTasksSuccess = Counter.builder("agentmesh.agent.tasks.success")
                .description("Number of successful agent tasks")
                .register(meterRegistry);

        this.agentTasksFailure = Counter.builder("agentmesh.agent.tasks.failure")
                .description("Number of failed agent tasks")
                .register(meterRegistry);

        this.agentExecutionDuration = Timer.builder("agentmesh.agent.execution.duration")
                .description("Agent task execution duration")
                .register(meterRegistry);

        // Initialize blackboard metrics
        this.blackboardPostsTotal = Counter.builder("agentmesh.blackboard.posts.total")
                .description("Total number of blackboard posts")
                .register(meterRegistry);

        this.blackboardQueriesTotal = Counter.builder("agentmesh.blackboard.queries.total")
                .description("Total number of blackboard queries")
                .register(meterRegistry);

        this.blackboardQueryDuration = Timer.builder("agentmesh.blackboard.query.duration")
                .description("Blackboard query duration")
                .register(meterRegistry);

        // Initialize memory metrics
        this.memoryOperationsTotal = Counter.builder("agentmesh.memory.operations.total")
                .description("Total number of memory operations")
                .register(meterRegistry);

        this.memoryHybridSearchTotal = Counter.builder("agentmesh.memory.hybrid_search.total")
                .description("Total number of hybrid search operations")
                .register(meterRegistry);

        this.memorySearchDuration = Timer.builder("agentmesh.memory.search.duration")
                .description("Memory search duration")
                .register(meterRegistry);

        // Initialize MAST violation counters for each failure mode
        for (MASTFailureMode mode : MASTFailureMode.values()) {
            Counter counter = Counter.builder("agentmesh.mast.violations")
                    .tag("failure_mode", mode.getCode())
                    .tag("category", mode.getCategory().name())
                    .description("MAST violations detected")
                    .register(meterRegistry);
            mastViolationCounters.put(mode, counter);
        }

        // Register gauges for agent health
        Gauge.builder("agentmesh.mast.unresolved.violations", mastValidator,
                v -> v.getUnresolvedViolations().size())
                .description("Number of unresolved MAST violations")
                .register(meterRegistry);
    }

    /**
     * Record an LLM call
     */
    public void recordLLMCall(String model, int tokens, Duration duration) {
        llmCallsTotal.increment();
        llmTokensTotal.increment(tokens);
        llmCallDuration.record(duration);
    }

    /**
     * Record self-correction attempt
     */
    public void recordSelfCorrectionAttempt() {
        selfCorrectionAttempts.increment();
    }

    /**
     * Record self-correction success
     */
    public void recordSelfCorrectionSuccess(Duration duration, int iterations) {
        selfCorrectionSuccesses.increment();
        selfCorrectionDuration.record(duration);
    }

    /**
     * Record self-correction failure
     */
    public void recordSelfCorrectionFailure(Duration duration, int iterations) {
        selfCorrectionFailures.increment();
        selfCorrectionDuration.record(duration);
    }

    /**
     * Record MAST violation
     */
    public void recordMASTViolation(MASTFailureMode failureMode) {
        Counter counter = mastViolationCounters.get(failureMode);
        if (counter != null) {
            counter.increment();
        }
    }

    /**
     * Register agent health gauge
     */
    public void registerAgentHealthGauge(String agentId) {
        Gauge.builder("agentmesh.agent.health", mastValidator,
                v -> v.getAgentHealthScore(agentId))
                .tag("agent_id", agentId)
                .description("Health score for agent (0-100)")
                .register(meterRegistry);
    }

    // ==================== Agent Execution Metrics ====================

    /**
     * Record agent task start
     */
    public void recordAgentTaskStart(String agentType, String tenantId) {
        agentTasksTotal.increment();
        Counter.builder("agentmesh.agent.tasks.started")
                .tag("agent_type", agentType)
                .tag("tenant_id", tenantId)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record successful agent task completion
     */
    public void recordAgentTaskSuccess(String agentType, String tenantId, Duration duration) {
        agentTasksSuccess.increment();
        Timer.builder("agentmesh.agent.execution.duration")
                .tag("agent_type", agentType)
                .tag("tenant_id", tenantId)
                .tag("status", "success")
                .register(meterRegistry)
                .record(duration);
    }

    /**
     * Record failed agent task
     */
    public void recordAgentTaskFailure(String agentType, String tenantId, String errorType) {
        agentTasksFailure.increment();
        Counter.builder("agentmesh.agent.tasks.failed")
                .tag("agent_type", agentType)
                .tag("tenant_id", tenantId)
                .tag("error_type", errorType)
                .register(meterRegistry)
                .increment();
    }

    // ==================== Blackboard Metrics ====================

    /**
     * Record blackboard post
     */
    public void recordBlackboardPost(String tenantId, String postType) {
        blackboardPostsTotal.increment();
        Counter.builder("agentmesh.blackboard.posts.by_type")
                .tag("tenant_id", tenantId)
                .tag("post_type", postType)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record blackboard query
     */
    public void recordBlackboardQuery(String tenantId, Duration duration) {
        blackboardQueriesTotal.increment();
        Timer.builder("agentmesh.blackboard.query.duration")
                .tag("tenant_id", tenantId)
                .register(meterRegistry)
                .record(duration);
    }

    // ==================== Memory Metrics ====================

    /**
     * Record memory operation
     */
    public void recordMemoryOperation(String tenantId, String operationType) {
        memoryOperationsTotal.increment();
        Counter.builder("agentmesh.memory.operations.by_type")
                .tag("tenant_id", tenantId)
                .tag("operation_type", operationType)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record hybrid search operation
     */
    public void recordHybridSearch(String tenantId, Duration duration, int resultsCount) {
        memoryHybridSearchTotal.increment();
        Timer.builder("agentmesh.memory.search.duration")
                .tag("tenant_id", tenantId)
                .register(meterRegistry)
                .record(duration);
        
        meterRegistry.summary("agentmesh.memory.search.results", "tenant_id", tenantId)
                .record(resultsCount);
    }
}
