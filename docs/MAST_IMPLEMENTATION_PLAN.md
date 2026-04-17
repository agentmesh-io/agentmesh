# MAST Failure Detection Implementation Plan

## Overview
Implement the 14 failure mode detection algorithms defined in the MAST (Multi-Agent System Taxonomy) paper to automatically detect and track agent collaboration failures in real-time.

## Failure Mode Categories

### 1. Specification Issues (FM-1.x)
**FM-1.1: Incomplete SRS**
- Detection: Check if SRS in Weaviate contains required sections
- Trigger: When SRS document is stored
- Required sections: Requirements, Architecture, Security, Data Model

**FM-1.2: Ambiguous Requirements**  
- Detection: Analyze SRS for ambiguous language patterns
- Patterns: "maybe", "possibly", "if needed", "TBD", "TODO"
- Severity: MEDIUM

**FM-1.3: Context Loss**
- Detection: Agent starts task without retrieving relevant context from memory
- Trigger: Agent posts code/decision without prior RAG query
- Time window: 60 seconds

**FM-1.4: Missing Requirements**
- Detection: Generated code references features not in SRS
- Trigger: Code posted to blackboard
- Check: Compare code entities against SRS features

### 2. Inter-Agent Misalignment (FM-2.x)
**FM-2.1: Contradictory Decisions**
- Detection: Two agents make conflicting decisions on same entity
- Example: Planner says "use REST", Architect says "use GraphQL"
- Severity: HIGH

**FM-2.2: Duplicate Work**
- Detection: Multiple agents implement same feature
- Trigger: Similar blackboard entries within time window
- Similarity threshold: 80%

**FM-2.3: Communication Breakdown**
- Detection: Agent doesn't respond to direct message
- Timeout: 5 minutes
- Check: Message sent but no blackboard entry referencing it

### 3. Task Verification Issues (FM-3.x)
**FM-3.1: Test Coverage Below Threshold**
- Detection: Coder posts code but test coverage < 80%
- Trigger: TEST_RESULT entry posted
- Parse coverage from test results

**FM-3.2: Unresolved Code Review Issues**
- Detection: Reviewer marks code as REQUIRES_CHANGES but coder doesn't revise
- Time window: 30 minutes
- Check: No updated code entry after review

## Implementation Strategy

### Phase 1: Detection Infrastructure
1. Create `MASTDetector` service with violation detection logic
2. Add event listeners for blackboard posts
3. Implement pattern matching and analysis algorithms
4. Store violations in database

### Phase 2: Specification Checks (FM-1.x)
1. SRS completeness checker
2. Ambiguous language detector (regex patterns)
3. Context loss detector (RAG query tracking)
4. Requirements coverage analyzer

### Phase 3: Inter-Agent Checks (FM-2.x)
1. Contradiction detector (semantic analysis)
2. Duplicate work detector (similarity scoring)
3. Communication timeout tracker

### Phase 4: Verification Checks (FM-3.x)
1. Test coverage parser
2. Code review follow-up tracker

## Data Model

```java
@Entity
public class MASTViolation {
    @Id
    private String id;
    private String failureMode; // FM-1.1, FM-1.2, etc.
    private ViolationSeverity severity; // LOW, MEDIUM, HIGH, CRITICAL
    private String agentId;
    private String description;
    private LocalDateTime detectedAt;
    private String context; // JSON with additional data
    private boolean resolved;
    private LocalDateTime resolvedAt;
}
```

## Detection Hooks

### Blackboard Event Listener
- Listen to POST /api/blackboard
- Analyze entry type and content
- Trigger relevant detection algorithms
- Store violations if detected

### Agent Message Listener
- Listen to POST /api/agents/{from}/send/{to}
- Track message responses
- Detect communication breakdowns

### Memory Store Listener  
- Listen to POST /api/memory/store
- Check SRS completeness
- Detect ambiguous requirements

## API Extensions

**GET /api/mast/violations/agent/{agentId}** - Already exists ✓
**GET /api/mast/violations/recent** - Already exists ✓
**GET /api/mast/violations/unresolved** - Already exists ✓
**GET /api/mast/agents/{agentId}/health** - Already exists ✓
**GET /api/mast/statistics** - Already exists ✓

**POST /api/mast/violations/{id}/resolve** - New: Mark violation as resolved
**GET /api/mast/violations/by-mode/{failureMode}** - New: Filter by FM-1.1, FM-2.2, etc.

## Testing Strategy

1. Unit tests for each detection algorithm
2. Integration tests with blackboard posts
3. End-to-end workflow test triggering violations
4. Verification tests ensuring violations are detected

## Success Criteria

- ✅ At least 5 failure modes implemented (FM-1.1, FM-1.2, FM-1.3, FM-2.1, FM-3.1)
- ✅ Violations automatically detected during agent operations
- ✅ Health scores reflect violation counts
- ✅ Test suite generates and detects violations
- ✅ All MAST tests passing with real data

## Implementation Order

1. **First**: FM-1.3 (Context Loss) - Easiest to detect
2. **Second**: FM-3.1 (Test Coverage) - Parse test results
3. **Third**: FM-1.2 (Ambiguous Requirements) - Regex patterns
4. **Fourth**: FM-2.2 (Duplicate Work) - Similarity matching
5. **Fifth**: FM-2.3 (Communication Breakdown) - Timeout tracking

---

**Status**: Ready to implement  
**Next Step**: Create MASTDetector service and implement FM-1.3
