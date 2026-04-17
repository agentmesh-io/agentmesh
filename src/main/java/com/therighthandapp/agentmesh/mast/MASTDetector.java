package com.therighthandapp.agentmesh.mast;

import com.therighthandapp.agentmesh.blackboard.BlackboardEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * MAST (Multi-Agent System Taxonomy) Failure Detector
 * Implements 14 failure mode detection algorithms to identify
 * agent collaboration issues in real-time.
 */
@Service
@Slf4j
public class MASTDetector {

    private final MASTViolationService violationService;
    private final Map<String, Instant> lastMemoryQuery = new HashMap<>();
    private final Map<String, BlackboardEntry> recentEntries = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, BlackboardEntry> eldest) {
            return size() > 100; // Keep last 100 entries
        }
    };
    // Track recent actions per agent for step repetition detection
    private final Map<String, List<String>> agentActionHistory = new HashMap<>();
    private static final int MAX_HISTORY_SIZE = 10;

    public MASTDetector(MASTViolationService violationService) {
        this.violationService = violationService;
    }

    /**
     * Track when an agent queries memory (for context loss detection)
     */
    public void trackMemoryQuery(String agentId) {
        lastMemoryQuery.put(agentId, Instant.now());
        log.debug("Tracked memory query for agent: {}", agentId);
    }

    /**
     * Analyze blackboard entry for potential violations
     */
    @Transactional
    public void analyzeBlackboardEntry(BlackboardEntry entry) {
        log.debug("Analyzing blackboard entry {} from agent {}", entry.getId(), entry.getAgentId());
        
        // Store entry for later analysis
        recentEntries.put(entry.getId() + "", entry);

        // FM-1.2: Role Violation Detection
        detectRoleViolation(entry);

        // FM-1.3: Step Repetition Detection
        detectStepRepetition(entry);

        // FM-2.1: Coordination Failure Detection
        detectCoordinationFailure(entry);

        // FM-2.3: Dependency Violation Detection
        detectDependencyViolation(entry);

        // FM-2.4: State Inconsistency Detection
        detectStateInconsistency(entry);

        // FM-3.3: Format Violation Detection
        detectFormatViolation(entry);

        // FM-3.4: Hallucination Detection
        detectHallucination(entry);

        // FM-3.5: Timeout Detection
        detectTimeout(entry);

        // FM-3.6: Tool Execution Failure Detection
        if ("TOOL_OUTPUT".equals(entry.getEntryType()) || "CODE".equals(entry.getEntryType())) {
            detectToolExecutionFailure(entry);
        }

        // FM-1.4: Context Loss Detection
        detectContextLoss(entry);

        // FM-1.1: Ambiguous Language Detection
        detectAmbiguousLanguage(entry);

        // FM-3.1: Test Coverage Below Threshold
        if ("TEST_RESULT".equals(entry.getEntryType())) {
            detectLowTestCoverage(entry);
        }

        // FM-2.2: Duplicate Work Detection
        detectDuplicateWork(entry);

        // FM-3.2: Unresolved Code Review Issues
        if ("REVIEW".equals(entry.getEntryType())) {
            detectUnresolvedReview(entry);
        }
    }

    /**
     * FM-1.4: Context Loss Detection
     * Agent posts code/decision without querying memory first
     */
    private void detectContextLoss(BlackboardEntry entry) {
        String agentId = entry.getAgentId();
        
        // Only check for CODE and TASK_BREAKDOWN entries
        if (!("CODE".equals(entry.getEntryType()) || "TASK_BREAKDOWN".equals(entry.getEntryType()))) {
            return;
        }

        Instant lastQuery = lastMemoryQuery.get(agentId);
        Instant entryTime = entry.getTimestamp();

        // Check if agent queried memory within last 60 seconds
        if (lastQuery == null || lastQuery.plusSeconds(60).isBefore(entryTime)) {
            String evidence = String.format(
                "Agent %s posted %s '%s' without querying memory for context. " +
                "Last memory query: %s. This may lead to context loss and inconsistent decisions.",
                agentId, entry.getEntryType(), entry.getTitle(),
                lastQuery != null ? lastQuery.toString() : "never"
            );
            
            MASTViolation violation = new MASTViolation(
                agentId,
                MASTFailureMode.FM_1_4_CONTEXT_LOSS,
                String.valueOf(entry.getId()),
                evidence
            );

            violationService.save(violation);
            log.warn("Detected FM-1.4 Context Loss: Agent {} posted without memory query", agentId);
        }
    }

    /**
     * FM-1.2: Role Violation Detection
     * Agent performs work outside its designated role
     */
    private void detectRoleViolation(BlackboardEntry entry) {
        String agentId = entry.getAgentId();
        String entryType = entry.getEntryType();
        String content = entry.getContent();
        
        if (content == null || agentId == null) {
            return;
        }

        // Define role expectations based on agent ID patterns
        AgentRole expectedRole = inferAgentRole(agentId);
        if (expectedRole == AgentRole.UNKNOWN) {
            return; // Can't validate unknown agents
        }

        // Check if entry type matches agent's role
        boolean roleViolation = false;
        String violationReason = null;

        switch (expectedRole) {
            case PLANNER:
                // Planner should only create SRS and TASK_BREAKDOWN
                if ("CODE".equals(entryType)) {
                    roleViolation = true;
                    violationReason = "Planner agent writing code instead of planning";
                } else if ("REVIEW".equals(entryType)) {
                    roleViolation = true;
                    violationReason = "Planner agent reviewing code instead of planning";
                } else if ("TEST_RESULT".equals(entryType)) {
                    roleViolation = true;
                    violationReason = "Planner agent running tests instead of planning";
                }
                break;

            case IMPLEMENTER:
                // Implementer should only create CODE entries
                if ("SRS".equals(entryType) || "TASK_BREAKDOWN".equals(entryType)) {
                    roleViolation = true;
                    violationReason = "Implementer agent planning instead of implementing";
                } else if ("REVIEW".equals(entryType)) {
                    roleViolation = true;
                    violationReason = "Implementer agent reviewing instead of implementing";
                } else if ("TEST_RESULT".equals(entryType)) {
                    roleViolation = true;
                    violationReason = "Implementer agent testing instead of implementing";
                }
                break;

            case REVIEWER:
                // Reviewer should only create REVIEW entries
                if ("CODE".equals(entryType)) {
                    roleViolation = true;
                    violationReason = "Reviewer agent writing code instead of reviewing";
                } else if ("SRS".equals(entryType) || "TASK_BREAKDOWN".equals(entryType)) {
                    roleViolation = true;
                    violationReason = "Reviewer agent planning instead of reviewing";
                } else if ("TEST_RESULT".equals(entryType)) {
                    roleViolation = true;
                    violationReason = "Reviewer agent testing instead of reviewing";
                }
                break;

            case TESTER:
                // Tester should only create TEST_RESULT entries
                if ("CODE".equals(entryType)) {
                    roleViolation = true;
                    violationReason = "Tester agent writing code instead of testing";
                } else if ("SRS".equals(entryType) || "TASK_BREAKDOWN".equals(entryType)) {
                    roleViolation = true;
                    violationReason = "Tester agent planning instead of testing";
                } else if ("REVIEW".equals(entryType)) {
                    roleViolation = true;
                    violationReason = "Tester agent reviewing instead of testing";
                }
                break;

            case UNKNOWN:
            default:
                // Can't validate unknown roles
                return;
        }

        if (roleViolation) {
            String evidence = String.format(
                "%s. Agent %s (role: %s) posted %s entry '%s'. " +
                "This violates role separation and may lead to confusion and quality issues.",
                violationReason, agentId, expectedRole, entryType, entry.getTitle()
            );
            
            MASTViolation violation = new MASTViolation(
                agentId,
                MASTFailureMode.FM_1_2_ROLE_VIOLATION,
                String.valueOf(entry.getId()),
                evidence
            );

            violationService.save(violation);
            log.warn("Detected FM-1.2 Role Violation: {} - {}", agentId, violationReason);
        }
    }

    /**
     * Infer agent role from agent ID
     */
    private AgentRole inferAgentRole(String agentId) {
        if (agentId == null) {
            return AgentRole.UNKNOWN;
        }
        
        String lowerAgentId = agentId.toLowerCase();
        if (lowerAgentId.contains("planner")) {
            return AgentRole.PLANNER;
        } else if (lowerAgentId.contains("implementer") || lowerAgentId.contains("developer")) {
            return AgentRole.IMPLEMENTER;
        } else if (lowerAgentId.contains("reviewer")) {
            return AgentRole.REVIEWER;
        } else if (lowerAgentId.contains("tester")) {
            return AgentRole.TESTER;
        }
        
        return AgentRole.UNKNOWN;
    }

    /**
     * Agent roles in the system
     */
    private enum AgentRole {
        PLANNER,      // Creates SRS, TASK_BREAKDOWN
        IMPLEMENTER,  // Creates CODE
        REVIEWER,     // Creates REVIEW
        TESTER,       // Creates TEST_RESULT
        UNKNOWN
    }

    /**
     * FM-1.3: Step Repetition Detection
     * Detect when agent repeats the same action multiple times (infinite loop)
     */
    private void detectStepRepetition(BlackboardEntry entry) {
        String agentId = entry.getAgentId();
        if (agentId == null) {
            return;
        }

        // Create an action signature: "entryType:title"
        String action = entry.getEntryType() + ":" + (entry.getTitle() != null ? entry.getTitle() : "");
        
        // Get or create action history for this agent
        List<String> history = agentActionHistory.computeIfAbsent(agentId, k -> new ArrayList<>());
        
        // Add current action
        history.add(action);
        
        // Keep only recent history
        if (history.size() > MAX_HISTORY_SIZE) {
            history.remove(0); // Remove oldest
        }
        
        // Count consecutive repetitions of current action
        int consecutiveCount = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).equals(action)) {
                consecutiveCount++;
            } else {
                break; // Stop at first different action
            }
        }
        
        // Also count total occurrences in recent history
        long totalOccurrences = history.stream()
                .filter(a -> a.equals(action))
                .count();
        
        // Detect violation if:
        // - 3+ consecutive identical actions, OR
        // - Same action appears 4+ times in recent history (even if not consecutive)
        boolean isConsecutiveLoop = consecutiveCount >= 3;
        boolean isFrequentRepetition = totalOccurrences >= 4;
        
        if (isConsecutiveLoop || isFrequentRepetition) {
            String evidence = String.format(
                "Agent %s is repeating action '%s'. " +
                "Consecutive repetitions: %d, Total in recent history: %d/%d. " +
                "This indicates a potential infinite loop or stuck state.",
                agentId, action, consecutiveCount, totalOccurrences, history.size()
            );
            
            MASTViolation violation = new MASTViolation(
                agentId,
                MASTFailureMode.FM_1_3_STEP_REPETITION,
                String.valueOf(entry.getId()),
                evidence
            );

            violationService.save(violation);
            log.warn("Detected FM-1.3 Step Repetition: Agent {} repeated '{}' {} times consecutively",
                agentId, action, consecutiveCount);
        }
    }

    /**
     * FM-2.1: Coordination Failure Detection
     * Detect conflicting decisions between agents on the same topic
     */
    private void detectCoordinationFailure(BlackboardEntry entry) {
        String content = entry.getContent();
        String entryType = entry.getEntryType();
        
        if (content == null || entryType == null) {
            return;
        }

        // Only check design/planning entries where conflicts are most critical
        if (!("SRS".equals(entryType) || "TASK_BREAKDOWN".equals(entryType) || "CODE".equals(entryType))) {
            return;
        }

        String lowerContent = content.toLowerCase();
        
        // Define conflict patterns - pairs of mutually exclusive decisions
        String[][] conflictPatterns = {
            // API Style conflicts
            {"rest", "graphql"},
            {"rest api", "grpc"},
            {"soap", "rest"},
            
            // Architecture conflicts
            {"microservices", "monolith"},
            {"serverless", "server-based"},
            {"event-driven", "request-response"},
            
            // Database conflicts
            {"sql", "nosql"},
            {"mysql", "postgresql"},
            {"mongodb", "cassandra"},
            
            // Frontend conflicts
            {"react", "angular"},
            {"vue", "react"},
            {"angular", "vue"},
            
            // Deployment conflicts
            {"kubernetes", "docker swarm"},
            {"aws", "azure"},
            {"on-premise", "cloud"}
        };

        // Check current entry for any conflicting patterns
        List<String> foundPatterns = new ArrayList<>();
        for (String[] pair : conflictPatterns) {
            if (lowerContent.contains(pair[0]) && lowerContent.contains(pair[1])) {
                foundPatterns.add(pair[0] + " vs " + pair[1]);
            }
        }

        // Also check against recent entries from OTHER agents on similar topics
        for (BlackboardEntry other : recentEntries.values()) {
            if (Objects.equals(other.getId(), entry.getId())) {
                continue; // Skip self
            }
            
            if (Objects.equals(other.getAgentId(), entry.getAgentId())) {
                continue; // Same agent is OK - single agent can explore options
            }

            if (!Objects.equals(other.getEntryType(), entryType)) {
                continue; // Different entry types
            }

            String otherContent = other.getContent();
            if (otherContent == null) {
                continue;
            }

            String otherLowerContent = otherContent.toLowerCase();
            
            // Check for conflicting patterns between this entry and other entries
            for (String[] pair : conflictPatterns) {
                boolean currentHasFirst = lowerContent.contains(pair[0]);
                boolean currentHasSecond = lowerContent.contains(pair[1]);
                boolean otherHasFirst = otherLowerContent.contains(pair[0]);
                boolean otherHasSecond = otherLowerContent.contains(pair[1]);
                
                // Conflict: current entry chooses one, other entry chooses the opposite
                if ((currentHasFirst && !currentHasSecond && otherHasSecond && !otherHasFirst) ||
                    (currentHasSecond && !currentHasFirst && otherHasFirst && !otherHasSecond)) {
                    
                    String conflict = currentHasFirst ? pair[0] + " vs " + pair[1] : pair[1] + " vs " + pair[0];
                    String evidence = String.format(
                        "Coordination failure detected: Agent %s chose '%s' in '%s', but agent %s chose '%s' in '%s'. " +
                        "Multiple agents making conflicting architectural decisions without coordination.",
                        entry.getAgentId(), 
                        currentHasFirst ? pair[0] : pair[1],
                        entry.getTitle(),
                        other.getAgentId(),
                        otherHasFirst ? pair[0] : pair[1],
                        other.getTitle()
                    );
                    
                    MASTViolation violation = new MASTViolation(
                        entry.getAgentId(),
                        MASTFailureMode.FM_2_1_COORDINATION_FAILURE,
                        String.valueOf(entry.getId()),
                        evidence
                    );

                    violationService.save(violation);
                    log.warn("Detected FM-2.1 Coordination Failure: {} - conflict on {}",
                        entry.getAgentId(), conflict);
                    return; // Only report one conflict per entry
                }
            }
        }

        // Report if single entry contains conflicting patterns (indecisive)
        if (!foundPatterns.isEmpty()) {
            String evidence = String.format(
                "Agent %s entry '%s' contains conflicting decisions: %s. " +
                "Single entry should not contain mutually exclusive options without clear justification.",
                entry.getAgentId(), entry.getTitle(), String.join(", ", foundPatterns)
            );
            
            MASTViolation violation = new MASTViolation(
                entry.getAgentId(),
                MASTFailureMode.FM_2_1_COORDINATION_FAILURE,
                String.valueOf(entry.getId()),
                evidence
            );

            violationService.save(violation);
            log.warn("Detected FM-2.1 Coordination Failure: {} contains conflicting patterns",
                entry.getAgentId());
        }
    }


    /**
     * FM-2.3: Dependency Violation Detection
     * Detects when agents execute tasks in wrong order, violating dependencies.
     * Expected workflow: Planner → Implementer → Reviewer → Tester
     * 
     * Violations include:
     * - Tester creating TEST_RESULT before Implementer creates CODE
     * - Reviewer creating REVIEW before Implementer creates CODE
     * - Implementer creating CODE before Planner creates SRS or TASK_BREAKDOWN
     * - Any agent working on a task before its dependencies are met
     */
    private void detectDependencyViolation(BlackboardEntry entry) {
        String agentId = entry.getAgentId();
        String entryType = entry.getEntryType();
        String tenantId = entry.getTenantId();
        
        if (agentId == null || entryType == null) {
            return;
        }
        
        AgentRole agentRole = inferAgentRole(agentId);
        
        // Check for dependency violations based on entry type and agent role
        switch (entryType) {
            case "CODE":
                // Implementer should only create CODE after planning is done
                if (agentRole == AgentRole.IMPLEMENTER) {
                    if (!hasPriorEntryForTenant("SRS", tenantId) && !hasPriorEntryForTenant("TASK_BREAKDOWN", tenantId)) {
                        String evidence = String.format(
                            "Implementer agent %s created CODE entry '%s' before any planning (SRS or TASK_BREAKDOWN) was done. " +
                            "Code implementation requires prior specification or task breakdown. " +
                            "Expected workflow: Planner creates SRS/TASK_BREAKDOWN → Implementer creates CODE.",
                            agentId, entry.getTitle()
                        );
                        
                        MASTViolation violation = new MASTViolation(
                            agentId,
                            MASTFailureMode.FM_2_3_DEPENDENCY_VIOLATION,
                            String.valueOf(entry.getId()),
                            evidence
                        );
                        
                        violationService.save(violation);
                        log.warn("Detected FM-2.3 Dependency Violation: {} created CODE before planning", agentId);
                    }
                }
                break;
                
            case "REVIEW":
                // Reviewer should only create REVIEW after CODE exists
                if (agentRole == AgentRole.REVIEWER) {
                    if (!hasPriorEntryForTenant("CODE", tenantId)) {
                        String evidence = String.format(
                            "Reviewer agent %s created REVIEW entry '%s' before any CODE entries exist. " +
                            "Code review requires code to review. " +
                            "Expected workflow: Implementer creates CODE → Reviewer creates REVIEW.",
                            agentId, entry.getTitle()
                        );
                        
                        MASTViolation violation = new MASTViolation(
                            agentId,
                            MASTFailureMode.FM_2_3_DEPENDENCY_VIOLATION,
                            String.valueOf(entry.getId()),
                            evidence
                        );
                        
                        violationService.save(violation);
                        log.warn("Detected FM-2.3 Dependency Violation: {} created REVIEW before CODE", agentId);
                    }
                }
                break;
                
            case "TEST_RESULT":
                // Tester should only create TEST_RESULT after CODE exists
                if (agentRole == AgentRole.TESTER) {
                    if (!hasPriorEntryForTenant("CODE", tenantId)) {
                        String evidence = String.format(
                            "Tester agent %s created TEST_RESULT entry '%s' before any CODE entries exist. " +
                            "Testing requires code to test. " +
                            "Expected workflow: Implementer creates CODE → Tester creates TEST_RESULT.",
                            agentId, entry.getTitle()
                        );
                        
                        MASTViolation violation = new MASTViolation(
                            agentId,
                            MASTFailureMode.FM_2_3_DEPENDENCY_VIOLATION,
                            String.valueOf(entry.getId()),
                            evidence
                        );
                        
                        violationService.save(violation);
                        log.warn("Detected FM-2.3 Dependency Violation: {} created TEST_RESULT before CODE", agentId);
                    }
                }
                break;
                
            case "TASK_BREAKDOWN":
                // Task breakdown should come after SRS (if SRS exists in workflow)
                if (agentRole == AgentRole.PLANNER) {
                    // Check if there's already a TASK_BREAKDOWN from another planner
                    if (hasPriorEntryFromDifferentAgentForTenant("TASK_BREAKDOWN", agentId, tenantId)) {
                        String evidence = String.format(
                            "Planner agent %s created duplicate TASK_BREAKDOWN entry '%s' when another planner already created one. " +
                            "This may indicate lack of coordination in the planning phase. " +
                            "Multiple task breakdowns should be consolidated.",
                            agentId, entry.getTitle()
                        );
                        
                        MASTViolation violation = new MASTViolation(
                            agentId,
                            MASTFailureMode.FM_2_3_DEPENDENCY_VIOLATION,
                            String.valueOf(entry.getId()),
                            evidence
                        );
                        
                        violationService.save(violation);
                        log.warn("Detected FM-2.3 Dependency Violation: {} created duplicate TASK_BREAKDOWN", agentId);
                    }
                }
                break;
        }
        
        // Additional check: Detect if Reviewer or Tester is working before Planner
        if ((agentRole == AgentRole.REVIEWER || agentRole == AgentRole.TESTER) && 
            !hasPriorEntryForTenant("SRS", tenantId) && !hasPriorEntryForTenant("TASK_BREAKDOWN", tenantId)) {
            
            String roleName = agentRole.toString().substring(0, 1) + 
                            agentRole.toString().substring(1).toLowerCase();
            
            String evidence = String.format(
                "Agent %s (role: %s) created entry '%s' before any planning phase (SRS or TASK_BREAKDOWN). " +
                "%s agents should not start work before planning is complete. " +
                "Expected workflow: Planner → Implementer → Reviewer → Tester.",
                agentId, roleName, entry.getTitle(), roleName
            );
            
            MASTViolation violation = new MASTViolation(
                agentId,
                MASTFailureMode.FM_2_3_DEPENDENCY_VIOLATION,
                String.valueOf(entry.getId()),
                evidence
            );
            
            violationService.save(violation);
            log.warn("Detected FM-2.3 Dependency Violation: {} started before planning phase", agentId);
        }
    }
    
    /**
     * FM-2.4: State Inconsistency Detection
     * Detects when different agents have conflicting views of the project state.
     * This can happen when:
     * - Multiple agents reference different versions of the same specification
     * - Agents work on conflicting assumptions about project structure
     * - Critical state changes are not propagated to all agents
     * 
     * Detection approach:
     * - Track key state indicators (tech stack, architecture decisions, API contracts)
     * - Compare mentions of same entities across different agents
     * - Flag when agents describe same concept differently
     */
    private void detectStateInconsistency(BlackboardEntry entry) {
        String content = entry.getContent();
        String tenantId = entry.getTenantId();
        String agentId = entry.getAgentId();
        
        if (content == null || tenantId == null) {
            return;
        }
        
        String lowerContent = content.toLowerCase();
        
        // Key state indicators to track
        String[][] stateIndicators = {
            // Technology stack conflicts
            {"java 11", "java 17", "java 21"},
            {"spring boot 2", "spring boot 3"},
            {"rest api", "graphql api", "grpc api"},
            
            // Architecture patterns
            {"microservices", "monolith", "serverless"},
            {"sql database", "nosql database"},
            {"synchronous", "asynchronous", "event-driven"},
            
            // Deployment targets
            {"kubernetes", "docker swarm", "ecs"},
            {"aws", "azure", "gcp"},
            
            // Framework versions
            {"react 17", "react 18", "react 19"},
            {"angular 15", "angular 16", "angular 17"},
            
            // API contracts
            {"version 1", "version 2", "version 3"},
            {"api v1", "api v2", "api v3"}
        };
        
        // Check current entry for state indicators
        List<String> currentIndicators = new ArrayList<>();
        for (String[] indicatorGroup : stateIndicators) {
            for (String indicator : indicatorGroup) {
                if (lowerContent.contains(indicator)) {
                    currentIndicators.add(indicator);
                }
            }
        }
        
        if (currentIndicators.isEmpty()) {
            return; // No state indicators in this entry
        }
        
        // Compare with recent entries from OTHER agents in SAME tenant
        for (BlackboardEntry other : recentEntries.values()) {
            // Skip same entry
            if (Objects.equals(other.getId(), entry.getId())) {
                continue;
            }
            
            // Only compare entries from same tenant
            if (!Objects.equals(tenantId, other.getTenantId())) {
                continue;
            }
            
            // Skip entries from same agent (agent can explore multiple options)
            if (Objects.equals(agentId, other.getAgentId())) {
                continue;
            }
            
            String otherContent = other.getContent();
            if (otherContent == null) {
                continue;
            }
            
            String otherLowerContent = otherContent.toLowerCase();
            
            // Check for conflicting state indicators
            for (String[] indicatorGroup : stateIndicators) {
                String currentChoice = null;
                String otherChoice = null;
                
                // Find what current entry chose from this group
                for (String indicator : indicatorGroup) {
                    if (lowerContent.contains(indicator)) {
                        currentChoice = indicator;
                        break;
                    }
                }
                
                // Find what other entry chose from this group
                for (String indicator : indicatorGroup) {
                    if (otherLowerContent.contains(indicator)) {
                        otherChoice = indicator;
                        break;
                    }
                }
                
                // If both chose from same group but made different choices, it's a conflict
                if (currentChoice != null && otherChoice != null && 
                    !currentChoice.equals(otherChoice)) {
                    
                    String evidence = String.format(
                        "State inconsistency detected: Agent %s mentions '%s' in '%s', " +
                        "but agent %s previously mentioned '%s' in '%s'. " +
                        "Multiple agents have conflicting views of the same state. " +
                        "This may lead to incompatible implementations.",
                        entry.getAgentId(), currentChoice, entry.getTitle(),
                        other.getAgentId(), otherChoice, other.getTitle()
                    );
                    
                    MASTViolation violation = new MASTViolation(
                        entry.getAgentId(),
                        MASTFailureMode.FM_2_4_STATE_INCONSISTENCY,
                        String.valueOf(entry.getId()),
                        evidence
                    );
                    
                    violationService.save(violation);
                    log.warn("Detected FM-2.4 State Inconsistency: {} has different view than {} - '{}' vs '{}'",
                        entry.getAgentId(), other.getAgentId(), currentChoice, otherChoice);
                    return; // Only report one inconsistency per entry
                }
            }
        }
    }
    
    /**
     * FM-3.3: Format Violation Detection
     * Detects when agent output doesn't conform to expected format:
     * - JSON parsing errors
     * - Missing required fields in structured data
     * - Invalid file extensions for code artifacts
     * - Malformed URLs or API endpoints
     * - Invalid version numbers or semantic versioning
     */
    private void detectFormatViolation(BlackboardEntry entry) {
        String content = entry.getContent();
        if (content == null || content.isBlank()) {
            return;
        }
        
        String lowerContent = content.toLowerCase();
        String entryType = entry.getEntryType();
        
        // Detect JSON format violations (common in CODE and CONFIG entries)
        if (entryType.equals("CODE") || entryType.equals("CONFIG") || entryType.equals("TOOL_OUTPUT")) {
            // Check for malformed JSON
            if (content.contains("{") && content.contains("}")) {
                // Look for common JSON syntax errors
                if (content.matches(".*\\{[^}]*\\{.*") && !content.matches(".*\\}.*\\}.*")) {
                    createFormatViolation(entry, "Unbalanced JSON braces - missing closing brace");
                    return;
                }
                
                // Check for missing quotes around keys
                if (content.matches(".*\\{\\s*[a-zA-Z_][a-zA-Z0-9_]*\\s*:.*")) {
                    if (!content.matches(".*\\{\\s*\"[a-zA-Z_][a-zA-Z0-9_]*\"\\s*:.*")) {
                        createFormatViolation(entry, "JSON keys must be quoted strings");
                        return;
                    }
                }
                
                // Check for trailing commas (common error)
                if (content.matches(".*,\\s*[}\\]].*")) {
                    createFormatViolation(entry, "Trailing comma in JSON object or array");
                    return;
                }
            }
        }
        
        // Detect missing required fields in structured outputs
        if (entryType.equals("SRS")) {
            // SRS should contain key sections
            boolean hasRequirements = lowerContent.contains("requirement") || 
                                     lowerContent.contains("functional") ||
                                     lowerContent.contains("non-functional");
            boolean hasObjectives = lowerContent.contains("objective") || 
                                   lowerContent.contains("goal") ||
                                   lowerContent.contains("purpose");
            
            if (!hasRequirements && !hasObjectives && content.length() > 50) {
                createFormatViolation(entry, "SRS missing required sections (requirements/objectives)");
                return;
            }
        }
        
        if (entryType.equals("TASK_BREAKDOWN")) {
            // Task breakdown should list tasks/steps
            boolean hasTasks = lowerContent.contains("task") || 
                              lowerContent.contains("step") ||
                              lowerContent.contains("phase") ||
                              content.matches(".*[1-9]\\.|\\d+\\).*"); // numbered lists
            
            if (!hasTasks && content.length() > 30) {
                createFormatViolation(entry, "Task breakdown missing task list or step structure");
                return;
            }
        }
        
        if (entryType.equals("CODE")) {
            // Check for invalid file extensions mentioned
            if (content.matches(".*\\.([a-zA-Z0-9]{6,}).*")) {
                createFormatViolation(entry, "Suspiciously long file extension detected");
                return;
            }
            
            // Check for malformed function/method signatures
            if (content.contains("public") || content.contains("private") || content.contains("function")) {
                // Look for functions with no parameters parentheses
                if (content.matches(".*\\b(function|def|fn)\\s+[a-zA-Z_][a-zA-Z0-9_]*[^\\(\\s].*")) {
                    createFormatViolation(entry, "Function declaration missing parentheses");
                    return;
                }
            }
        }
        
        // Detect malformed URLs (common in API documentation)
        if (lowerContent.contains("http") || lowerContent.contains("api")) {
            // Check for incomplete URLs
            if (content.matches(".*https?://[^\\s/]+\\s+\\S+.*") || 
                content.matches(".*https?:/[^/].*")) {
                createFormatViolation(entry, "Malformed URL format detected");
                return;
            }
        }
        
        // Detect invalid version numbers
        if (lowerContent.contains("version")) {
            // Check for nonsensical version numbers
            if (content.matches(".*version\\s+\\d{2,}\\.\\d{2,}\\.\\d{2,}.*")) {
                createFormatViolation(entry, "Suspiciously high version numbers (not semantic versioning)");
                return;
            }
            
            // Check for version without numbers
            if (content.matches("(?i).*version\\s+[a-z]+\\s.*") && 
                !lowerContent.contains("latest") && 
                !lowerContent.contains("current")) {
                createFormatViolation(entry, "Version specified without version number");
                return;
            }
        }
        
        // Detect incomplete code blocks (markdown or similar)
        if (content.contains("```")) {
            int openCount = content.split("```").length - 1;
            if (openCount % 2 != 0) {
                createFormatViolation(entry, "Unclosed code block (unbalanced backticks)");
                return;
            }
        }
        
        // Detect placeholder text that wasn't replaced
        String[] placeholders = {"TODO", "FIXME", "XXX", "PLACEHOLDER", "<INSERT", "FILL_IN", "TBD"};
        for (String placeholder : placeholders) {
            if (content.toUpperCase().contains(placeholder) && 
                (entryType.equals("CODE") || entryType.equals("CONFIG"))) {
                createFormatViolation(entry, "Output contains placeholder text: " + placeholder);
                return;
            }
        }
    }
    
    private void createFormatViolation(BlackboardEntry entry, String reason) {
        String evidence = String.format(
            "Agent %s produced output with format issues in entry '%s' of type %s. Issue: %s. " +
            "Content preview: %.200s%s",
            entry.getAgentId(),
            entry.getTitle(),
            entry.getEntryType(),
            reason,
            entry.getContent(),
            entry.getContent().length() > 200 ? "..." : ""
        );
        
        MASTViolation violation = new MASTViolation(
            entry.getAgentId(),
            MASTFailureMode.FM_3_3_FORMAT_VIOLATION,
            String.valueOf(entry.getId()),
            evidence
        );
        
        violationService.save(violation);
        log.warn("Detected FM-3.3 Format Violation: {} in entry {} - {}", 
            entry.getAgentId(), entry.getTitle(), reason);
    }
    
    /**
     * FM-3.4: Hallucination Detection
     * Detects when agents reference non-existent entities:
     * - Files that don't exist in referenced paths
     * - Classes, methods, variables not mentioned in prior CODE entries
     * - API endpoints not defined in prior SRS/CODE
     * - Dependencies not listed in prior entries
     * - Features/requirements not in the SRS
     */
    private void detectHallucination(BlackboardEntry entry) {
        String content = entry.getContent();
        if (content == null || content.isBlank()) {
            return;
        }
        
        String entryType = entry.getEntryType();
        String tenantId = entry.getTenantId();
        
        // Only check CODE, REVIEW, and TEST_RESULT entries for hallucinations
        if (!entryType.equals("CODE") && !entryType.equals("REVIEW") && !entryType.equals("TEST_RESULT")) {
            return;
        }
        
        // Detect references to non-existent files
        if (detectNonExistentFileReferences(entry, content, tenantId)) {
            return;
        }
        
        // Detect references to non-existent classes/methods
        if (detectNonExistentCodeReferences(entry, content, tenantId)) {
            return;
        }
        
        // Detect references to non-existent API endpoints
        if (detectNonExistentAPIReferences(entry, content, tenantId)) {
            return;
        }
        
        // Detect references to non-existent requirements
        if (detectNonExistentRequirements(entry, content, tenantId)) {
            return;
        }
        
        // Detect impossible version numbers or dates
        if (detectImpossibleReferences(entry, content)) {
            return;
        }
    }
    
    private boolean detectNonExistentFileReferences(BlackboardEntry entry, String content, String tenantId) {
        // Look for file path references (e.g., "src/main/java/...")
        String[] filePatterns = {
            "src/[a-zA-Z0-9_/.-]+\\.(java|py|js|ts|rb|go|rs|cpp|c|h)",
            "config/[a-zA-Z0-9_/.-]+\\.(yaml|yml|json|xml|properties)",
            "tests?/[a-zA-Z0-9_/.-]+\\.(java|py|js|ts)"
        };
        
        for (String pattern : filePatterns) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(content);
            
            while (m.find()) {
                String filePath = m.group();
                
                // Check if this file was mentioned in prior CODE entries
                boolean fileExists = false;
                for (BlackboardEntry priorEntry : recentEntries.values()) {
                    if (!Objects.equals(tenantId, priorEntry.getTenantId())) {
                        continue;
                    }
                    
                    if (priorEntry.getEntryType().equals("CODE") && 
                        priorEntry.getId() < entry.getId() &&
                        priorEntry.getContent() != null &&
                        (priorEntry.getContent().contains(filePath) || 
                         priorEntry.getTitle().contains(filePath))) {
                        fileExists = true;
                        break;
                    }
                }
                
                if (!fileExists) {
                    createHallucinationViolation(entry, 
                        "References non-existent file: " + filePath + " (not found in prior CODE entries)");
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private boolean detectNonExistentCodeReferences(BlackboardEntry entry, String content, String tenantId) {
        // Look for class references (e.g., "class UserService", "new OrderProcessor()")
        String[] classPatterns = {
            "class\\s+([A-Z][a-zA-Z0-9_]+)",
            "interface\\s+([A-Z][a-zA-Z0-9_]+)",
            "new\\s+([A-Z][a-zA-Z0-9_]+)\\s*\\(",
            "extends\\s+([A-Z][a-zA-Z0-9_]+)",
            "implements\\s+([A-Z][a-zA-Z0-9_]+)"
        };
        
        for (String pattern : classPatterns) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(content);
            
            while (m.find()) {
                String className = m.group(1);
                
                // Skip common/standard library classes
                if (isStandardLibraryClass(className)) {
                    continue;
                }
                
                // Check if this class was defined in prior CODE entries
                boolean classExists = false;
                for (BlackboardEntry priorEntry : recentEntries.values()) {
                    if (!Objects.equals(tenantId, priorEntry.getTenantId())) {
                        continue;
                    }
                    
                    if (priorEntry.getEntryType().equals("CODE") && 
                        priorEntry.getId() < entry.getId() &&
                        priorEntry.getContent() != null &&
                        (priorEntry.getContent().contains("class " + className) ||
                         priorEntry.getContent().contains("interface " + className) ||
                         priorEntry.getTitle().contains(className))) {
                        classExists = true;
                        break;
                    }
                }
                
                if (!classExists) {
                    createHallucinationViolation(entry, 
                        "References undefined class: " + className + " (not found in prior CODE entries)");
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private boolean detectNonExistentAPIReferences(BlackboardEntry entry, String content, String tenantId) {
        // Look for API endpoint references (e.g., "/api/users", "GET /orders")
        String[] apiPatterns = {
            "(GET|POST|PUT|DELETE|PATCH)\\s+(/[a-zA-Z0-9_/-]+)",
            "\"(/api/[a-zA-Z0-9_/-]+)\"",
            "'(/api/[a-zA-Z0-9_/-]+)'"
        };
        
        for (String pattern : apiPatterns) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(content);
            
            while (m.find()) {
                String endpoint = m.groupCount() > 1 ? m.group(2) : m.group(1);
                
                // Check if this endpoint was defined in prior SRS or CODE entries
                boolean endpointExists = false;
                for (BlackboardEntry priorEntry : recentEntries.values()) {
                    if (!Objects.equals(tenantId, priorEntry.getTenantId())) {
                        continue;
                    }
                    
                    if ((priorEntry.getEntryType().equals("SRS") || priorEntry.getEntryType().equals("CODE")) && 
                        priorEntry.getId() < entry.getId() &&
                        priorEntry.getContent() != null &&
                        priorEntry.getContent().contains(endpoint)) {
                        endpointExists = true;
                        break;
                    }
                }
                
                if (!endpointExists && endpoint.length() > 4) { // Only check meaningful endpoints
                    createHallucinationViolation(entry, 
                        "References undefined API endpoint: " + endpoint + " (not found in prior SRS/CODE)");
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private boolean detectNonExistentRequirements(BlackboardEntry entry, String content, String tenantId) {
        // Look for requirement references (e.g., "REQ-123", "requirement 5.2")
        String[] reqPatterns = {
            "REQ-\\d+",
            "requirement\\s+\\d+\\.\\d+",
            "feature\\s+[A-Z]+-\\d+"
        };
        
        for (String pattern : reqPatterns) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher m = p.matcher(content);
            
            while (m.find()) {
                String reqId = m.group();
                
                // Check if this requirement was defined in prior SRS entries
                boolean reqExists = false;
                for (BlackboardEntry priorEntry : recentEntries.values()) {
                    if (!Objects.equals(tenantId, priorEntry.getTenantId())) {
                        continue;
                    }
                    
                    if (priorEntry.getEntryType().equals("SRS") && 
                        priorEntry.getId() < entry.getId() &&
                        priorEntry.getContent() != null &&
                        priorEntry.getContent().toLowerCase().contains(reqId.toLowerCase())) {
                        reqExists = true;
                        break;
                    }
                }
                
                if (!reqExists) {
                    createHallucinationViolation(entry, 
                        "References undefined requirement: " + reqId + " (not found in prior SRS)");
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private boolean detectImpossibleReferences(BlackboardEntry entry, String content) {
        // Detect future dates (years > current year)
        int currentYear = java.time.Year.now().getValue();
        String yearPattern = "\\b(20\\d{2})\\b";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(yearPattern);
        java.util.regex.Matcher m = p.matcher(content);
        
        while (m.find()) {
            int year = Integer.parseInt(m.group(1));
            if (year > currentYear + 1) { // Allow next year for planning
                createHallucinationViolation(entry, 
                    "References future/impossible year: " + year + " (current year is " + currentYear + ")");
                return true;
            }
        }
        
        // Detect nonsensical numeric values
        if (content.matches(".*\\b\\d{10,}\\b.*")) { // 10+ digit numbers (likely nonsense)
            createHallucinationViolation(entry, 
                "Contains suspiciously large numeric values (possible hallucination)");
            return true;
        }
        
        return false;
    }
    
    private boolean isStandardLibraryClass(String className) {
        // Common standard library classes to skip
        String[] standardClasses = {
            "String", "Integer", "Long", "Double", "Float", "Boolean", "Character",
            "List", "ArrayList", "HashMap", "HashSet", "Map", "Set",
            "Optional", "Stream", "Collectors",
            "Object", "Class", "Exception", "RuntimeException",
            "Thread", "Runnable", "Callable",
            "Date", "Calendar", "LocalDate", "LocalDateTime", "Instant",
            "File", "Path", "Files",
            "Logger", "System", "Math", "Random"
        };
        
        for (String stdClass : standardClasses) {
            if (className.equals(stdClass)) {
                return true;
            }
        }
        
        return false;
    }
    
    private void createHallucinationViolation(BlackboardEntry entry, String reason) {
        String evidence = String.format(
            "Agent %s produced hallucinated content in entry '%s' of type %s. Issue: %s. " +
            "Content preview: %.200s%s",
            entry.getAgentId(),
            entry.getTitle(),
            entry.getEntryType(),
            reason,
            entry.getContent(),
            entry.getContent().length() > 200 ? "..." : ""
        );
        
        MASTViolation violation = new MASTViolation(
            entry.getAgentId(),
            MASTFailureMode.FM_3_4_HALLUCINATION,
            String.valueOf(entry.getId()),
            evidence
        );
        
        violationService.save(violation);
        log.warn("Detected FM-3.4 Hallucination: {} in entry {} - {}", 
            entry.getAgentId(), entry.getTitle(), reason);
    }
    
    /**
     * FM-3.5: Timeout Detection
     * Detects when agents take too long between entries or appear stalled:
     * - Long gaps between entries from the same agent (>30 minutes)
     * - Agent claims to be working but produces no output
     * - Repetitive "in progress" messages without actual deliverables
     * - Entry mentions "timeout", "stuck", or "hanging"
     */
    private void detectTimeout(BlackboardEntry entry) {
        String agentId = entry.getAgentId();
        String tenantId = entry.getTenantId();
        String content = entry.getContent();
        
        if (content == null || content.isBlank()) {
            return;
        }
        
        String lowerContent = content.toLowerCase();
        
        // Check for explicit timeout/stuck mentions
        String[] timeoutIndicators = {
            "timeout", "timed out", "time out",
            "stuck", "hanging", "frozen",
            "not responding", "unresponsive",
            "taking too long", "excessive time",
            "deadline exceeded", "exceeded limit"
        };
        
        for (String indicator : timeoutIndicators) {
            if (lowerContent.contains(indicator)) {
                createTimeoutViolation(entry, 
                    "Entry explicitly mentions timeout/stuck condition: '" + indicator + "'");
                return;
            }
        }
        
        // Check for repetitive "in progress" or "working on" without deliverables
        if (lowerContent.contains("in progress") || 
            lowerContent.contains("working on") ||
            lowerContent.contains("still processing")) {
            
            // Count how many times this agent has posted "in progress" recently
            int progressCount = 0;
            int deliverableCount = 0;
            
            for (BlackboardEntry recent : recentEntries.values()) {
                if (!Objects.equals(agentId, recent.getAgentId())) continue;
                if (!Objects.equals(tenantId, recent.getTenantId())) continue;
                
                String recentContent = recent.getContent();
                if (recentContent == null) continue;
                
                String recentLower = recentContent.toLowerCase();
                if (recentLower.contains("in progress") || 
                    recentLower.contains("working on") ||
                    recentLower.contains("still processing")) {
                    progressCount++;
                }
                
                // Check if they produced actual deliverables (CODE, SRS, etc.)
                String type = recent.getEntryType();
                if (type.equals("CODE") || type.equals("SRS") || 
                    type.equals("TASK_BREAKDOWN") || type.equals("TEST_RESULT")) {
                    deliverableCount++;
                }
            }
            
            // If 3+ "in progress" messages but few deliverables, likely stalled
            if (progressCount >= 3 && deliverableCount < 2) {
                createTimeoutViolation(entry,
                    String.format("Agent posted %d 'in progress' updates but only %d deliverables - appears stalled",
                        progressCount, deliverableCount));
                return;
            }
        }
        
        // Check for long gaps between entries from same agent
        Instant currentTime = entry.getTimestamp() != null ? entry.getTimestamp() : Instant.now();
        Instant lastEntryTime = null;
        
        for (BlackboardEntry recent : recentEntries.values()) {
            if (!Objects.equals(agentId, recent.getAgentId())) continue;
            if (!Objects.equals(tenantId, recent.getTenantId())) continue;
            if (Objects.equals(entry.getId(), recent.getId())) continue; // Skip current entry
            
            Instant recentTime = recent.getTimestamp() != null ? recent.getTimestamp() : Instant.now();
            if (lastEntryTime == null || recentTime.isAfter(lastEntryTime)) {
                lastEntryTime = recentTime;
            }
        }
        
        if (lastEntryTime != null) {
            long minutesBetween = java.time.Duration.between(lastEntryTime, currentTime).toMinutes();
            
            // If >30 minutes between entries, flag as potential timeout
            if (minutesBetween > 30) {
                createTimeoutViolation(entry,
                    String.format("Long gap detected: %d minutes since last entry from this agent", 
                        minutesBetween));
                return;
            }
        }
        
        // Check for error messages indicating timeouts
        if (lowerContent.contains("error") || lowerContent.contains("exception")) {
            String[] errorPatterns = {
                "timeout exception", "timeoutexception",
                "read timed out", "connection timeout",
                "execution timed out", "operation timeout",
                "request timeout", "socket timeout"
            };
            
            for (String pattern : errorPatterns) {
                if (lowerContent.contains(pattern)) {
                    createTimeoutViolation(entry,
                        "Entry contains timeout error message: '" + pattern + "'");
                    return;
                }
            }
        }
    }
    
    private void createTimeoutViolation(BlackboardEntry entry, String reason) {
        String evidence = String.format(
            "Agent %s appears to have timeout/stalled condition in entry '%s' of type %s. Issue: %s. " +
            "Content preview: %.200s%s",
            entry.getAgentId(),
            entry.getTitle(),
            entry.getEntryType(),
            reason,
            entry.getContent(),
            entry.getContent().length() > 200 ? "..." : ""
        );
        
        MASTViolation violation = new MASTViolation(
            entry.getAgentId(),
            MASTFailureMode.FM_3_5_TIMEOUT,
            String.valueOf(entry.getId()),
            evidence
        );
        
        violationService.save(violation);
        log.warn("Detected FM-3.5 Timeout: {} in entry {} - {}", 
            entry.getAgentId(), entry.getTitle(), reason);
    }
    
    /**
     * FM-3.6: Tool Execution Failure Detection
     * Detects when agents report failed tool executions or encounter errors:
     * - Compilation errors (javac, gcc, etc.)
     * - Test failures and assertion errors
     * - Build tool failures (Maven, Gradle, npm)
     * - Deployment failures
     * - Database connection errors
     * - API call failures (4xx, 5xx status codes)
     * - File I/O errors
     * - Permission denied errors
     * - Null pointer exceptions and runtime errors
     */
    private void detectToolExecutionFailure(BlackboardEntry entry) {
        String content = entry.getContent();
        if (content == null || content.isBlank()) {
            return;
        }
        
        String lowerContent = content.toLowerCase();
        
        // Check for explicit error indicators
        if (!lowerContent.contains("error") && 
            !lowerContent.contains("fail") && 
            !lowerContent.contains("exception") &&
            !lowerContent.contains("denied")) {
            return; // No error indicators found
        }
        
        // Compilation errors
        String[] compilationErrors = {
            "compilation failed", "compile error", "compilation error",
            "syntax error", "cannot find symbol", "undefined reference",
            "error: expected", "error: invalid", "parse error",
            "javac: error", "gcc: error", "tsc: error"
        };
        
        for (String error : compilationErrors) {
            if (lowerContent.contains(error)) {
                createToolFailureViolation(entry, 
                    "Compilation error detected: '" + error + "'");
                return;
            }
        }
        
        // Test failures
        String[] testFailures = {
            "test failed", "tests failed", "test failure",
            "assertion failed", "assertionerror", "expected but was",
            "junit", "testng", "mocha failed", "jest failed",
            "pytest failed", "test case failed", "0 passed"
        };
        
        for (String failure : testFailures) {
            if (lowerContent.contains(failure)) {
                createToolFailureViolation(entry,
                    "Test failure detected: '" + failure + "'");
                return;
            }
        }
        
        // Build tool failures
        String[] buildErrors = {
            "build failed", "build failure", "maven build failure",
            "gradle build failed", "npm err!", "yarn error",
            "[error]", "build error", "compilation failure",
            "resolution failed", "dependency not found"
        };
        
        for (String error : buildErrors) {
            if (lowerContent.contains(error)) {
                createToolFailureViolation(entry,
                    "Build tool failure: '" + error + "'");
                return;
            }
        }
        
        // Deployment failures
        String[] deploymentErrors = {
            "deployment failed", "deploy error", "deployment error",
            "failed to deploy", "rollback", "deployment unsuccessful",
            "container failed", "pod error", "service unavailable"
        };
        
        for (String error : deploymentErrors) {
            if (lowerContent.contains(error)) {
                createToolFailureViolation(entry,
                    "Deployment failure: '" + error + "'");
                return;
            }
        }
        
        // Database errors
        String[] databaseErrors = {
            "connection refused", "connection failed", "sql error",
            "database error", "query failed", "duplicate key",
            "constraint violation", "foreign key", "deadlock",
            "table does not exist", "column does not exist"
        };
        
        for (String error : databaseErrors) {
            if (lowerContent.contains(error)) {
                createToolFailureViolation(entry,
                    "Database error: '" + error + "'");
                return;
            }
        }
        
        // HTTP/API errors
        String[] apiErrors = {
            "http error", "status code 4", "status code 5",
            "400 bad request", "401 unauthorized", "403 forbidden",
            "404 not found", "500 internal server", "502 bad gateway",
            "503 service unavailable", "504 gateway timeout"
        };
        
        for (String error : apiErrors) {
            if (lowerContent.contains(error)) {
                createToolFailureViolation(entry,
                    "API/HTTP error: '" + error + "'");
                return;
            }
        }
        
        // File I/O errors
        String[] ioErrors = {
            "file not found", "filenotfoundexception", "no such file",
            "io error", "ioexception", "cannot read", "cannot write",
            "access denied", "permission denied", "disk full"
        };
        
        for (String error : ioErrors) {
            if (lowerContent.contains(error)) {
                createToolFailureViolation(entry,
                    "File I/O error: '" + error + "'");
                return;
            }
        }
        
        // Runtime exceptions
        String[] runtimeErrors = {
            "nullpointerexception", "null pointer", "npe",
            "classcastexception", "arrayindexoutofbounds",
            "illegalargumentexception", "illegalstateexception",
            "runtime error", "exception in thread", "stack trace"
        };
        
        for (String error : runtimeErrors) {
            if (lowerContent.contains(error)) {
                createToolFailureViolation(entry,
                    "Runtime error/exception: '" + error + "'");
                return;
            }
        }
        
        // Generic fatal errors
        String[] fatalErrors = {
            "fatal error", "critical error", "panic:",
            "segmentation fault", "core dumped", "killed",
            "aborted", "terminated unexpectedly"
        };
        
        for (String error : fatalErrors) {
            if (lowerContent.contains(error)) {
                createToolFailureViolation(entry,
                    "Fatal error: '" + error + "'");
                return;
            }
        }
        
        // Check for stack traces (strong indicator of errors)
        if (content.matches("(?s).*\\s+at\\s+[a-zA-Z0-9_.$]+\\([^)]*\\).*")) {
            createToolFailureViolation(entry,
                "Stack trace detected - indicates exception/error occurred");
            return;
        }
        
        // Check for exit codes indicating failure
        if (lowerContent.matches(".*exit code [1-9]\\d*.*") ||
            lowerContent.matches(".*returned [1-9]\\d*.*") ||
            lowerContent.matches(".*error code [1-9]\\d*.*")) {
            createToolFailureViolation(entry,
                "Non-zero exit code detected - command failed");
            return;
        }
    }
    
    private void createToolFailureViolation(BlackboardEntry entry, String reason) {
        String evidence = String.format(
            "Agent %s encountered tool execution failure in entry '%s' of type %s. Issue: %s. " +
            "Content preview: %.200s%s",
            entry.getAgentId(),
            entry.getTitle(),
            entry.getEntryType(),
            reason,
            entry.getContent(),
            entry.getContent().length() > 200 ? "..." : ""
        );
        
        MASTViolation violation = new MASTViolation(
            entry.getAgentId(),
            MASTFailureMode.FM_3_6_TOOL_INVOCATION_FAILURE,
            String.valueOf(entry.getId()),
            evidence
        );
        
        violationService.save(violation);
        log.warn("Detected FM-3.6 Tool Execution Failure: {} in entry {} - {}", 
            entry.getAgentId(), entry.getTitle(), reason);
    }
    
    /**
     * Check if there's a prior entry of given type in the workflow for the same tenant
     */
    private boolean hasPriorEntry(String entryType) {
        return hasPriorEntryForTenant(entryType, null);
    }
    
    /**
     * Check if there's a prior entry of given type for a specific tenant
     */
    private boolean hasPriorEntryForTenant(String entryType, String tenantId) {
        for (BlackboardEntry entry : recentEntries.values()) {
            // If tenantId is provided, filter by tenant
            if (tenantId != null && !tenantId.equals(entry.getTenantId())) {
                continue;
            }
            
            if (entryType.equals(entry.getEntryType())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if there's a prior entry of given type from a different agent (same tenant)
     */
    private boolean hasPriorEntryFromDifferentAgent(String entryType, String currentAgentId) {
        return hasPriorEntryFromDifferentAgentForTenant(entryType, currentAgentId, null);
    }
    
    /**
     * Check if there's a prior entry of given type from a different agent for a specific tenant
     */
    private boolean hasPriorEntryFromDifferentAgentForTenant(String entryType, String currentAgentId, String tenantId) {
        for (BlackboardEntry entry : recentEntries.values()) {
            // If tenantId is provided, filter by tenant
            if (tenantId != null && !tenantId.equals(entry.getTenantId())) {
                continue;
            }
            
            if (entryType.equals(entry.getEntryType()) && 
                !currentAgentId.equals(entry.getAgentId())) {
                return true;
            }
        }
        return false;
    }


    /**
     * FM-1.2: Ambiguous Language Detection
     * SRS or requirements contain ambiguous language
     */
    private void detectAmbiguousLanguage(BlackboardEntry entry) {
        // Only check SRS and TASK_BREAKDOWN entries
        if (!("SRS".equals(entry.getEntryType()) || "TASK_BREAKDOWN".equals(entry.getEntryType()))) {
            return;
        }

        String content = entry.getContent();
        if (content == null) {
            return;
        }

        // Ambiguous patterns
        String[] ambiguousPatterns = {
            "maybe", "possibly", "if needed", "TBD", "TODO", "probably",
            "might", "could be", "should be", "may be", "unclear",
            "not sure", "to be determined"
        };

        List<String> foundPatterns = new ArrayList<>();
        String lowerContent = content.toLowerCase();
        
        for (String pattern : ambiguousPatterns) {
            if (lowerContent.contains(pattern)) {
                foundPatterns.add(pattern);
            }
        }

        if (!foundPatterns.isEmpty()) {
            String evidence = String.format(
                "Entry '%s' contains ambiguous language: %s. " +
                "This may lead to misinterpretation and incorrect implementation.",
                entry.getTitle(), String.join(", ", foundPatterns)
            );
            
            MASTViolation violation = new MASTViolation(
                entry.getAgentId(),
                MASTFailureMode.FM_1_1_SPECIFICATION_VIOLATION,
                String.valueOf(entry.getId()),
                evidence
            );

            violationService.save(violation);
            log.warn("Detected FM-1.1 Ambiguous Language: Entry {} contains: {}", 
                entry.getTitle(), foundPatterns);
        }
    }

    /**
     * FM-3.1: Test Coverage Below Threshold
     * Parse test results and check if coverage < 80%
     */
    private void detectLowTestCoverage(BlackboardEntry entry) {
        String content = entry.getContent();
        if (content == null) {
            return;
        }

        // Parse coverage from content (assumes format: "Coverage: XX%")
        int coverage = parseCoverage(content);
        
        if (coverage >= 0 && coverage < 80) {
            String evidence = String.format(
                "Test coverage for '%s' is %d%%, below 80%% threshold. " +
                "Insufficient test coverage increases risk of bugs.",
                entry.getTitle(), coverage
            );
            
            MASTViolation violation = new MASTViolation(
                entry.getAgentId(),
                MASTFailureMode.FM_3_1_OUTPUT_QUALITY,
                String.valueOf(entry.getId()),
                evidence
            );

            violationService.save(violation);
            log.warn("Detected FM-3.1 Low Test Coverage: {} has {}% coverage", 
                entry.getTitle(), coverage);
        }
    }

    /**
     * FM-2.2: Duplicate Work Detection
     * Multiple agents working on similar tasks
     */
    private void detectDuplicateWork(BlackboardEntry entry) {
        if (!"CODE".equals(entry.getEntryType())) {
            return;
        }

        // Check recent entries for similar titles
        for (BlackboardEntry other : recentEntries.values()) {
            if (Objects.equals(other.getId(), entry.getId())) {
                continue; // Skip self
            }
            
            if (!"CODE".equals(other.getEntryType())) {
                continue;
            }

            if (Objects.equals(other.getAgentId(), entry.getAgentId())) {
                continue; // Same agent is OK
            }

            // Simple similarity: check if titles are very similar
            double similarity = calculateTitleSimilarity(entry.getTitle(), other.getTitle());
            
            if (similarity > 0.7) { // 70% similarity threshold
                String evidence = String.format(
                    "Agent %s is working on '%s' which is %.0f%% similar to '%s' by agent %s. " +
                    "This indicates potential duplicate work.",
                    entry.getAgentId(), entry.getTitle(),
                    similarity * 100,
                    other.getTitle(), other.getAgentId()
                );
                
                MASTViolation violation = new MASTViolation(
                    entry.getAgentId(),
                    MASTFailureMode.FM_2_2_COMMUNICATION_BREAKDOWN,
                    String.valueOf(entry.getId()),
                    evidence
                );

                violationService.save(violation);
                log.warn("Detected FM-2.2 Duplicate Work: '{}' similar to '{}'", 
                    entry.getTitle(), other.getTitle());
                break; // Only report one duplicate
            }
        }
    }

    /**
     * FM-3.2: Unresolved Code Review Issues
     * Reviewer marks code as REQUIRES_CHANGES but no follow-up
     */
    private void detectUnresolvedReview(BlackboardEntry review) {
        String content = review.getContent();
        if (content == null || !content.contains("REQUIRES_CHANGES")) {
            return; // Review passed or no action needed
        }

        // Schedule check for 30 minutes later (in real implementation, use scheduled task)
        // For now, just create a violation immediately
        String evidence = String.format(
            "Code review '%s' marked as REQUIRES_CHANGES. " +
            "Monitoring for follow-up action from original author.",
            review.getTitle()
        );
        
        MASTViolation violation = new MASTViolation(
            review.getAgentId(),
            MASTFailureMode.FM_3_2_INCOMPLETE_OUTPUT,
            String.valueOf(review.getId()),
            evidence
        );

        violationService.save(violation);
        log.info("Detected FM-3.2 Unresolved Review: Monitoring '{}'", review.getTitle());
    }

    // Helper methods

    private int parseCoverage(String content) {
        try {
            // Look for patterns like "Coverage: 72%" or "72% coverage"
            String pattern = "([0-9]+)\\s*%";
            java.util.regex.Pattern regex = java.util.regex.Pattern.compile(pattern, 
                java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher matcher = regex.matcher(content);
            
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        } catch (Exception e) {
            log.debug("Could not parse coverage from content", e);
        }
        return -1; // Not found
    }

    private double calculateTitleSimilarity(String title1, String title2) {
        if (title1 == null || title2 == null) {
            return 0.0;
        }

        // Simple Jaccard similarity on words
        Set<String> words1 = new HashSet<>(Arrays.asList(
            title1.toLowerCase().split("\\s+")));
        Set<String> words2 = new HashSet<>(Arrays.asList(
            title2.toLowerCase().split("\\s+")));

        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);

        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);

        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }
}
