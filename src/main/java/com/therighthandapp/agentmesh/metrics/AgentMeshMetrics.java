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

    // Counters
    private final Counter llmCallsTotal;
    private final Counter llmTokensTotal;
    private final Counter selfCorrectionAttempts;
    private final Counter selfCorrectionSuccesses;
    private final Counter selfCorrectionFailures;

    // Timers
    private final Timer selfCorrectionDuration;
    private final Timer llmCallDuration;

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
}

