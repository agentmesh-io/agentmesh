package com.therighthandapp.agentmesh.mast;

/**
 * Multi-Agent System Testing (MAST) Failure Modes
 * Based on the MAST taxonomy from the ASEM specification.
 *
 * These represent the 14 unique failure modes identified in MAS frameworks
 * categorized into three types: Specification Issues, Inter-Agent Misalignment,
 * and Task Verification failures.
 */
public enum MASTFailureMode {

    // Category 1: Specification Issues (FM-1.x)
    FM_1_1_SPECIFICATION_VIOLATION(
        "FM-1.1",
        "Specification Violation",
        "Agent fails to follow task requirements or specifications",
        "Validate output against SRS requirements and acceptance criteria"
    ),

    FM_1_2_ROLE_VIOLATION(
        "FM-1.2",
        "Role Violation",
        "Agent acts outside its designated role or responsibilities",
        "Check that agent actions align with assigned role definition"
    ),

    FM_1_3_STEP_REPETITION(
        "FM-1.3",
        "Step Repetition / Looping",
        "Agent repeats the same steps or gets stuck in an infinite loop",
        "Monitor for duplicate actions and enforce timeout limits"
    ),

    FM_1_4_CONTEXT_LOSS(
        "FM-1.4",
        "Context Loss",
        "Agent loses critical context from previous interactions",
        "Verify that agent retrieves and uses historical context from Blackboard"
    ),

    // Category 2: Inter-Agent Misalignment (FM-2.x)
    FM_2_1_COORDINATION_FAILURE(
        "FM-2.1",
        "Coordination Failure",
        "Agents fail to coordinate actions, leading to conflicts",
        "Verify that agents synchronize via Blackboard events"
    ),

    FM_2_2_COMMUNICATION_BREAKDOWN(
        "FM-2.2",
        "Communication Breakdown",
        "Messages between agents are lost, malformed, or misinterpreted",
        "Validate message format and ensure delivery confirmation"
    ),

    FM_2_3_DEPENDENCY_VIOLATION(
        "FM-2.3",
        "Dependency Violation",
        "Agent executes before dependencies are satisfied",
        "Check that all prerequisite tasks are completed before execution"
    ),

    FM_2_4_STATE_INCONSISTENCY(
        "FM-2.4",
        "State Inconsistency",
        "Agents operate on stale or inconsistent state",
        "Verify that agents read the latest version from Blackboard"
    ),

    // Category 3: Task Verification (FM-3.x)
    FM_3_1_OUTPUT_QUALITY(
        "FM-3.1",
        "Poor Output Quality",
        "Agent produces low-quality or incorrect results",
        "Run quality checks: code review, linting, test coverage"
    ),

    FM_3_2_INCOMPLETE_OUTPUT(
        "FM-3.2",
        "Incomplete Output",
        "Agent produces partial results or stops prematurely",
        "Verify that all required artifacts are generated"
    ),

    FM_3_3_FORMAT_VIOLATION(
        "FM-3.3",
        "Format Violation",
        "Agent output does not conform to expected format",
        "Validate output against schema/structure requirements"
    ),

    FM_3_4_HALLUCINATION(
        "FM-3.4",
        "Hallucination / Factual Error",
        "Agent generates false or nonsensical information",
        "Cross-check facts against knowledge base and external sources"
    ),

    FM_3_5_TIMEOUT(
        "FM-3.5",
        "Timeout / Unresponsiveness",
        "Agent fails to complete task within time limit",
        "Enforce timeout and provide fallback strategy"
    ),

    FM_3_6_TOOL_INVOCATION_FAILURE(
        "FM-3.6",
        "Tool Invocation Failure",
        "Agent fails to correctly use external tools or APIs",
        "Validate tool parameters and handle errors gracefully"
    );

    private final String code;
    private final String name;
    private final String description;
    private final String mitigationStrategy;

    MASTFailureMode(String code, String name, String description, String mitigationStrategy) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.mitigationStrategy = mitigationStrategy;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getMitigationStrategy() {
        return mitigationStrategy;
    }

    public Category getCategory() {
        if (code.startsWith("FM-1")) return Category.SPECIFICATION_ISSUES;
        if (code.startsWith("FM-2")) return Category.INTER_AGENT_MISALIGNMENT;
        if (code.startsWith("FM-3")) return Category.TASK_VERIFICATION;
        return Category.UNKNOWN;
    }

    public enum Category {
        SPECIFICATION_ISSUES,
        INTER_AGENT_MISALIGNMENT,
        TASK_VERIFICATION,
        UNKNOWN
    }

    @Override
    public String toString() {
        return String.format("%s: %s - %s", code, name, description);
    }
}

