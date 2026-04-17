# AgentMesh Test Scenarios

## Overview
This document provides comprehensive test scenarios to verify all implemented features of the AgentMesh system. The system is currently running with the following services:
- **AgentMesh API**: `http://localhost:8080`
- **Temporal UI**: `http://localhost:8082`
- **Weaviate**: `http://localhost:8081`
- **PostgreSQL**: `localhost:5432`

---

## Test Scenario 1: Multi-Tenancy & Organization Management

### Scenario 1.1: Create a New Tenant (Free Tier)
**Objective**: Verify that a new tenant can be created with default free tier limits.

```bash
# Create a new tenant
curl -X POST http://localhost:8080/api/tenants \
  -H "Content-Type: application/json" \
  -d '{
    "organizationId": "org-acme-001",
    "organizationName": "Acme Corporation",
    "tier": "FREE"
  }'
```

**Expected Result**:
- HTTP 201 Created
- Response contains tenant ID, organization details, and tier limits
- Verify limits: maxProjects=1, maxAgents=3, maxMonthlyTokens=100000

**Verification**:
```bash
# Get the created tenant
curl http://localhost:8080/api/tenants/{tenantId}

# Get tenant by organization
curl http://localhost:8080/api/tenants/org/org-acme-001
```

---

### Scenario 1.2: Upgrade Tenant to Professional Tier
**Objective**: Test tier upgrade functionality and verify new limits are applied.

```bash
# Upgrade tenant to PROFESSIONAL tier
curl -X PUT http://localhost:8080/api/tenants/{tenantId}/tier \
  -H "Content-Type: application/json" \
  -d '{
    "tier": "PROFESSIONAL"
  }'
```

**Expected Result**:
- HTTP 200 OK
- Updated limits: maxProjects=10, maxAgents=25, maxMonthlyTokens=10000000
- Usage metrics should be preserved

**Verification**:
```bash
# Verify tier upgrade
curl http://localhost:8080/api/tenants/{tenantId}
```

---

### Scenario 1.3: Create Multiple Projects Under Tenant
**Objective**: Test project creation and verify tenant project limits.

```bash
# Create first project
curl -X POST http://localhost:8080/api/tenants/{tenantId}/projects \
  -H "Content-Type: application/json" \
  -d '{
    "projectId": "proj-web-app",
    "name": "E-Commerce Web Application",
    "repository": "https://github.com/acme/ecommerce-web"
  }'

# Create second project (should fail if tenant is FREE tier)
curl -X POST http://localhost:8080/api/tenants/{tenantId}/projects \
  -H "Content-Type: application/json" \
  -d '{
    "projectId": "proj-mobile-app",
    "name": "E-Commerce Mobile App",
    "repository": "https://github.com/acme/ecommerce-mobile"
  }'
```

**Expected Result**:
- First project: HTTP 201 Created
- Second project: HTTP 400 Bad Request (if FREE tier), or 201 (if upgraded)
- Verify billing records are created for project creation

---

## Test Scenario 2: Agent Lifecycle Management

### Scenario 2.1: Create and Register Agents
**Objective**: Verify agent creation, registration, and state management.

```bash
# Create Planner Agent
curl -X POST "http://localhost:8080/api/agents?id=planner-001"

# Create Coder Agent
curl -X POST "http://localhost:8080/api/agents?id=coder-001"

# Create Reviewer Agent
curl -X POST "http://localhost:8080/api/agents?id=reviewer-001"

# Create Debugger Agent
curl -X POST "http://localhost:8080/api/agents?id=debugger-001"

# List all agents
curl http://localhost:8080/api/agents
```

**Expected Result**:
- Each agent creation returns HTTP 201
- Response contains agent ID and initial state (IDLE)
- List endpoint shows all 4 agents

---

### Scenario 2.2: Start and Stop Agents
**Objective**: Test agent lifecycle transitions.

```bash
# Start the Planner Agent
curl -X POST http://localhost:8080/api/agents/planner-001/start

# Start the Coder Agent
curl -X POST http://localhost:8080/api/agents/coder-001/start

# Verify agents are running
curl http://localhost:8080/api/agents

# Stop the Coder Agent
curl -X POST http://localhost:8080/api/agents/coder-001/stop
```

**Expected Result**:
- Start: HTTP 200, agent state changes to RUNNING
- Stop: HTTP 200, agent state changes to IDLE
- Non-existent agent: HTTP 404

---

### Scenario 2.3: Inter-Agent Communication
**Objective**: Test message passing between agents.

```bash
# Planner sends task to Coder
curl -X POST http://localhost:8080/api/agents/message \
  -H "Content-Type: application/json" \
  -d '{
    "senderId": "planner-001",
    "recipientId": "coder-001",
    "content": "Implement user authentication module with JWT tokens"
  }'

# Coder sends result to Reviewer
curl -X POST http://localhost:8080/api/agents/message \
  -H "Content-Type: application/json" \
  -d '{
    "senderId": "coder-001",
    "recipientId": "reviewer-001",
    "content": "Authentication module completed. Files: AuthController.java, JwtService.java, SecurityConfig.java"
  }'

# View message log
curl http://localhost:8080/api/agents/messages
```

**Expected Result**:
- HTTP 200 for successful messages
- Message log contains all sent messages with timestamps
- Messages appear in chronological order

---

## Test Scenario 3: Blackboard Architecture

### Scenario 3.1: Post Artifacts to Blackboard
**Objective**: Verify agents can post different types of artifacts to the shared Blackboard.

```bash
# Planner posts task breakdown
curl -X POST "http://localhost:8080/api/blackboard/entries?agentId=planner-001&entryType=TASK_BREAKDOWN&title=User%20Authentication%20Tasks" \
  -H "Content-Type: text/plain" \
  -d 'Tasks:
1. Design JWT token structure
2. Implement AuthController with login/logout endpoints
3. Create JwtService for token generation and validation
4. Configure Spring Security
5. Write unit tests'

# Coder posts generated code
curl -X POST "http://localhost:8080/api/blackboard/entries?agentId=coder-001&entryType=CODE&title=AuthController.java" \
  -H "Content-Type: text/plain" \
  -d '@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    private JwtService jwtService;
    
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        // Implementation
    }
}'

# Reviewer posts code review
curl -X POST "http://localhost:8080/api/blackboard/entries?agentId=reviewer-001&entryType=REVIEW&title=AuthController%20Review" \
  -H "Content-Type: text/plain" \
  -d 'Review findings:
- ✓ Code follows Spring best practices
- ✓ Proper error handling
- ⚠ Missing input validation
- ⚠ Consider rate limiting for login endpoint'
```

**Expected Result**:
- Each post returns HTTP 200 with entry ID
- Response includes timestamp and version number
- Entries are persisted to PostgreSQL

---

### Scenario 3.2: Query Blackboard Entries
**Objective**: Test various query patterns for retrieving Blackboard data.

```bash
# Get all entries
curl http://localhost:8080/api/blackboard/entries

# Get entries by type
curl http://localhost:8080/api/blackboard/entries/type/CODE

# Get entries by agent
curl http://localhost:8080/api/blackboard/entries/agent/coder-001

# Get specific entry
curl http://localhost:8080/api/blackboard/entries/{entryId}
```

**Expected Result**:
- All queries return HTTP 200
- Results are filtered correctly
- Entries include metadata (timestamp, agent, version)

---

### Scenario 3.3: Update and Snapshot Blackboard
**Objective**: Test entry updates and snapshot functionality.

```bash
# Update an existing entry
curl -X PUT http://localhost:8080/api/blackboard/entries/{entryId} \
  -H "Content-Type: text/plain" \
  -d 'Updated code with input validation and rate limiting'

# Create a snapshot (checkpoint)
curl -X POST http://localhost:8080/api/blackboard/snapshot
```

**Expected Result**:
- Update: HTTP 200, version number incremented
- Snapshot: HTTP 200 with snapshot ID and entry count
- Snapshot stored for potential rollback

---

## Test Scenario 4: Vector Database (Weaviate) - Long-Term Memory

### Scenario 4.1: Store Knowledge Artifacts
**Objective**: Test storing different types of artifacts in the vector database.

```bash
# Store SRS document
curl -X POST http://localhost:8080/api/memory/artifacts \
  -H "Content-Type: application/json" \
  -d '{
    "artifactType": "SRS",
    "title": "E-Commerce Platform Requirements",
    "content": "The system shall provide user authentication, product catalog management, shopping cart functionality, and payment processing. Security requirements include HTTPS, SQL injection prevention, and PCI compliance.",
    "metadata": {
      "project": "proj-web-app",
      "version": "1.0",
      "author": "planner-001"
    }
  }'

# Store code snippet
curl -X POST http://localhost:8080/api/memory/artifacts \
  -H "Content-Type: application/json" \
  -d '{
    "artifactType": "CODE_SNIPPET",
    "title": "JWT Token Generation Pattern",
    "content": "public String generateToken(UserDetails userDetails) { Map<String, Object> claims = new HashMap<>(); return Jwts.builder().setClaims(claims).setSubject(userDetails.getUsername()).setIssuedAt(new Date()).setExpiration(new Date(System.currentTimeMillis() + JWT_EXPIRATION)).signWith(getSigningKey(), SignatureAlgorithm.HS256).compact(); }",
    "metadata": {
      "language": "java",
      "pattern": "security"
    }
  }'

# Store failure lesson (MAST)
curl -X POST http://localhost:8080/api/memory/artifacts \
  -H "Content-Type: application/json" \
  -d '{
    "artifactType": "FAILURE_LESSON",
    "title": "Context Loss in Multi-Step Task",
    "content": "During implementation of authentication module, coder agent lost context of security requirements midway through implementation. Root cause: lack of explicit context retrieval before each coding subtask. Solution: implement mandatory RAG call before each code generation step.",
    "metadata": {
      "mastCode": "FM-1.4",
      "severity": "HIGH",
      "resolved": true
    }
  }'
```

**Expected Result**:
- Each store returns HTTP 200 with artifact ID
- Artifacts are vectorized and indexed in Weaviate
- Metadata is preserved

---

### Scenario 4.2: Semantic Search
**Objective**: Test semantic search capabilities for knowledge retrieval.

```bash
# Search for authentication-related knowledge
curl "http://localhost:8080/api/memory/search?query=user%20authentication%20security&limit=5"

# Search for JWT patterns
curl "http://localhost:8080/api/memory/search?query=JWT%20token%20generation&limit=3"

# Search for past failures
curl "http://localhost:8080/api/memory/search?query=context%20loss%20errors&limit=5"
```

**Expected Result**:
- HTTP 200 with relevant artifacts ranked by semantic similarity
- Results include similarity scores
- Most relevant artifacts appear first

---

### Scenario 4.3: Query by Artifact Type
**Objective**: Test filtering artifacts by type.

```bash
# Get all SRS documents
curl "http://localhost:8080/api/memory/artifacts/type/SRS?limit=10"

# Get all code snippets
curl "http://localhost:8080/api/memory/artifacts/type/CODE_SNIPPET?limit=10"

# Get all failure lessons
curl "http://localhost:8080/api/memory/artifacts/type/FAILURE_LESSON?limit=10"
```

**Expected Result**:
- HTTP 200 with filtered results
- Only artifacts of requested type are returned
- Results limited by the limit parameter

---

## Test Scenario 5: MAST (Multi-Agent System Failure Taxonomy)

### Scenario 5.1: Validate Agent Behavior Against MAST Rules
**Objective**: Test MAST violation detection for specification issues.

```bash
# Simulate a specification violation
# (This would typically be detected automatically by the system)

# Check for recent violations
curl http://localhost:8080/api/mast/violations/recent

# Check unresolved violations
curl http://localhost:8080/api/mast/violations/unresolved
```

**Expected Result**:
- HTTP 200 with list of violations
- Each violation includes: failure mode, severity, agent ID, timestamp

---

### Scenario 5.2: Monitor Agent Health Scores
**Objective**: Verify MAST health scoring system.

```bash
# Get health score for each agent
curl http://localhost:8080/api/mast/health/planner-001
curl http://localhost:8080/api/mast/health/coder-001
curl http://localhost:8080/api/mast/health/reviewer-001
curl http://localhost:8080/api/mast/health/debugger-001
```

**Expected Result**:
- HTTP 200 with health score (0-100)
- Score based on recent violations
- Agents with no violations have score near 100

---

### Scenario 5.3: Analyze Failure Mode Statistics
**Objective**: Test aggregate statistics for failure modes.

```bash
# Get failure mode statistics
curl http://localhost:8080/api/mast/statistics/failure-modes

# Get violations by specific agent
curl http://localhost:8080/api/mast/violations/agent/coder-001
```

**Expected Result**:
- HTTP 200 with statistics map
- Shows count for each MAST failure mode (FM-1.1, FM-1.2, etc.)
- Helps identify systemic issues

---

## Test Scenario 6: Billing and Token Tracking

### Scenario 6.1: Track Token Usage
**Objective**: Verify token consumption tracking per tenant.

```bash
# Get tenant billing info
curl http://localhost:8080/api/billing/tenants/{tenantId}

# Get detailed usage breakdown
curl http://localhost:8080/api/billing/tenants/{tenantId}/usage
```

**Expected Result**:
- HTTP 200 with current month's token usage
- Breakdown by project and agent
- Comparison against tier limits

---

### Scenario 6.2: Test Token Limit Enforcement
**Objective**: Verify that operations are blocked when limits are exceeded.

```bash
# Simulate high token usage
# (This would require multiple LLM calls that exceed tenant limits)

# Try to create a new agent after limit reached
curl -X POST "http://localhost:8080/api/agents?id=new-agent-001"
```

**Expected Result**:
- HTTP 429 Too Many Requests when limit exceeded
- Error message indicating token limit reached
- Suggestion to upgrade tier

---

## Test Scenario 7: Temporal Workflow Orchestration

### Scenario 7.1: View Temporal UI
**Objective**: Verify Temporal is running and accessible.

```bash
# Open Temporal UI in browser
open http://localhost:8082

# Or check via curl
curl http://localhost:8082/namespaces/default/workflows
```

**Expected Result**:
- Temporal UI loads successfully
- Shows AgentMesh namespace
- Can view workflow executions

---

### Scenario 7.2: Monitor Workflow Executions
**Objective**: Test workflow execution tracking.

**Steps**:
1. Open Temporal UI: `http://localhost:8082`
2. Navigate to Workflows
3. Look for AgentMesh workflows (if any are running)
4. Check workflow status, history, and event timeline

**Expected Result**:
- Workflows are listed with status
- Can drill down into workflow details
- Event history shows all workflow steps

---

## Test Scenario 8: GitHub Integration (if configured)

### Scenario 8.1: Webhook Integration Test
**Objective**: Test GitHub webhook handling (requires GitHub repository setup).

```bash
# Simulate GitHub issue webhook
curl -X POST http://localhost:8080/api/github/webhook \
  -H "Content-Type: application/json" \
  -H "X-GitHub-Event: issues" \
  -d '{
    "action": "opened",
    "issue": {
      "number": 42,
      "title": "Add user profile page",
      "body": "We need a user profile page showing user details, recent orders, and settings.",
      "user": {
        "login": "developer1"
      }
    },
    "repository": {
      "full_name": "acme/ecommerce-web"
    }
  }'
```

**Expected Result**:
- HTTP 200
- Webhook triggers agent workflow
- Issue is tracked in system
- Temporal workflow created for the task

---

## Test Scenario 9: Integration Flow - Complete Development Cycle

### Scenario 9.1: End-to-End Software Development Task
**Objective**: Test complete agent collaboration for a feature implementation.

**Setup**:
1. Create tenant and project (Scenarios 1.1, 1.3)
2. Create all agents (Scenario 2.1)
3. Start agents (Scenario 2.2)

**Execution Flow**:

```bash
# 1. Planner posts task breakdown to Blackboard
curl -X POST "http://localhost:8080/api/blackboard/entries?agentId=planner-001&entryType=TASK_BREAKDOWN&title=Implement%20User%20Profile%20Feature" \
  -H "Content-Type: text/plain" \
  -d 'Feature: User Profile Page
Tasks:
1. Create UserProfile entity and repository
2. Implement UserProfileController with GET/PUT endpoints
3. Add profile validation logic
4. Write unit tests (80% coverage minimum)
5. Code review and quality check'

# 2. Coder retrieves context from memory
curl "http://localhost:8080/api/memory/search?query=user%20profile%20REST%20controller%20pattern&limit=3"

# 3. Coder posts generated code to Blackboard
curl -X POST "http://localhost:8080/api/blackboard/entries?agentId=coder-001&entryType=CODE&title=UserProfileController.java" \
  -H "Content-Type: text/plain" \
  -d '@RestController
@RequestMapping("/api/users/profile")
public class UserProfileController {
    @GetMapping
    public ResponseEntity<UserProfile> getProfile() {
        // Implementation
    }
}'

# 4. Reviewer analyzes code from Blackboard
curl http://localhost:8080/api/blackboard/entries/type/CODE

# 5. Reviewer posts review to Blackboard
curl -X POST "http://localhost:8080/api/blackboard/entries?agentId=reviewer-001&entryType=REVIEW&title=UserProfileController%20Review" \
  -H "Content-Type: text/plain" \
  -d 'Code Review:
✓ Follows REST conventions
✗ Missing authentication check
✗ No input validation
Status: REQUIRES_CHANGES'

# 6. Store the lesson learned in memory
curl -X POST http://localhost:8080/api/memory/artifacts \
  -H "Content-Type: application/json" \
  -d '{
    "artifactType": "FAILURE_LESSON",
    "title": "Security checks in REST controllers",
    "content": "Always add @PreAuthorize annotation for protected endpoints and validate all user inputs",
    "metadata": {
      "project": "proj-web-app",
      "category": "security"
    }
  }'

# 7. Check MAST violations
curl http://localhost:8080/api/mast/violations/recent

# 8. Create checkpoint snapshot
curl -X POST http://localhost:8080/api/blackboard/snapshot

# 9. Check token usage
curl http://localhost:8080/api/billing/tenants/{tenantId}/usage
```

**Expected Result**:
- Complete workflow executes successfully
- All artifacts stored in Blackboard and Weaviate
- Code review identifies issues
- Lessons learned captured for future reference
- Token usage tracked and within limits
- Workflow visible in Temporal UI

---

## Test Scenario 10: Error Handling and Resilience

### Scenario 10.1: Handle Invalid Requests
**Objective**: Verify proper error handling for malformed requests.

```bash
# Try to create tenant without required fields
curl -X POST http://localhost:8080/api/tenants \
  -H "Content-Type: application/json" \
  -d '{}'

# Try to get non-existent tenant
curl http://localhost:8080/api/tenants/non-existent-id

# Try to send message from non-existent agent
curl -X POST http://localhost:8080/api/agents/message \
  -H "Content-Type: application/json" \
  -d '{
    "senderId": "non-existent",
    "recipientId": "coder-001",
    "content": "test"
  }'
```

**Expected Result**:
- Appropriate HTTP error codes (400, 404)
- Clear error messages
- System remains stable

---

### Scenario 10.2: Test Blackboard Rollback
**Objective**: Verify rollback capability using snapshots.

```bash
# 1. Create initial state
curl -X POST "http://localhost:8080/api/blackboard/entries?agentId=test&entryType=CODE&title=Test" \
  -H "Content-Type: text/plain" \
  -d 'Initial code'

# 2. Create snapshot
curl -X POST http://localhost:8080/api/blackboard/snapshot

# 3. Make changes
curl -X POST "http://localhost:8080/api/blackboard/entries?agentId=test&entryType=CODE&title=Modified" \
  -H "Content-Type: text/plain" \
  -d 'Modified code with bugs'

# 4. Rollback to snapshot (if implemented)
# curl -X POST http://localhost:8080/api/blackboard/rollback/{snapshotId}
```

**Expected Result**:
- Snapshot captures state before changes
- Rollback restores previous state
- Data consistency maintained

---

## Test Scenario 11: Performance and Load Testing

### Scenario 11.1: Concurrent Agent Operations
**Objective**: Test system under concurrent load.

```bash
# Run multiple operations in parallel
for i in {1..10}; do
  curl -X POST "http://localhost:8080/api/agents?id=agent-$i" &
done
wait

# Post multiple blackboard entries simultaneously
for i in {1..20}; do
  curl -X POST "http://localhost:8080/api/blackboard/entries?agentId=agent-1&entryType=TEST&title=Entry-$i" \
    -H "Content-Type: text/plain" \
    -d "Test entry $i" &
done
wait
```

**Expected Result**:
- All operations complete successfully
- No data corruption or race conditions
- Response times remain acceptable
- Database transactions handled correctly

---

## Success Criteria Summary

For each test scenario, verify:

✅ **Functional Correctness**: API returns expected results
✅ **Data Persistence**: Data is correctly stored and retrievable
✅ **Error Handling**: Appropriate error responses for invalid inputs
✅ **Multi-Tenancy**: Tenant isolation is maintained
✅ **Integration**: All services (PostgreSQL, Weaviate, Temporal) work together
✅ **Observability**: Operations are logged and traceable
✅ **Performance**: System handles concurrent operations without degradation
✅ **Resilience**: System recovers gracefully from errors

---

## Monitoring and Verification Tools

### Check System Health
```bash
# Check all containers
docker-compose ps

# Check AgentMesh logs
docker logs agentmesh-app -f

# Check Temporal logs
docker logs agentmesh-temporal -f

# Check Weaviate logs
docker logs agentmesh-weaviate -f

# Check PostgreSQL logs
docker logs agentmesh-postgres -f
```

### Database Verification
```bash
# Connect to PostgreSQL
docker exec -it agentmesh-postgres psql -U agentmesh -d agentmesh

# Example queries
\dt  # List tables
SELECT * FROM tenants;
SELECT * FROM projects;
SELECT * FROM blackboard_entries ORDER BY created_at DESC LIMIT 10;
SELECT * FROM mast_violations ORDER BY detected_at DESC LIMIT 10;
```

### Weaviate Verification
```bash
# Check Weaviate schema
curl http://localhost:8081/v1/schema

# Query Weaviate directly
curl http://localhost:8081/v1/objects
```

---

## Next Steps

After completing these test scenarios:

1. **Document Results**: Record which scenarios pass/fail
2. **Performance Baseline**: Measure response times for key operations
3. **Load Testing**: Use tools like Apache JMeter or k6 for stress testing
4. **Integration Tests**: Automate these scenarios as integration tests
5. **CI/CD Pipeline**: Add these tests to continuous integration
6. **Monitoring Setup**: Configure Prometheus/Grafana for production monitoring
7. **Security Testing**: Add authentication/authorization tests when implemented

---

## Additional Test Ideas

- **Concurrent Project Management**: Multiple projects operating simultaneously
- **Agent Failure Recovery**: What happens when an agent crashes?
- **Memory Pressure**: Test with large code artifacts and memory retrieval
- **Network Partition**: Simulate network issues between services
- **Database Failover**: Test PostgreSQL replication and failover
- **Temporal Workflow Retry**: Test workflow retry logic
- **Rate Limiting**: Test API rate limiting per tenant
- **Audit Trail**: Verify all operations are properly logged

