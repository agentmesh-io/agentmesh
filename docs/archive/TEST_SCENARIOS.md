# AgentMesh Test Scenarios & Demo Use Cases

## 🎯 Real-World Test Scenarios

### Scenario 1: Simple Feature Development (End-to-End)
**Goal:** Generate a REST API endpoint for user management

**Steps:**
```bash
# 1. Post SRS to Blackboard
curl -X POST 'http://localhost:8080/api/blackboard/entries?agentId=user&entryType=SRS&title=User Management API' \
  -H 'Content-Type: text/plain' \
  -d 'Create a REST API for user CRUD operations with:
- GET /api/users - List all users
- POST /api/users - Create user
- PUT /api/users/{id} - Update user
- DELETE /api/users/{id} - Delete user
Include validation and error handling.'

# 2. Trigger Planner Agent
curl -X POST 'http://localhost:8080/api/agents?id=planner-agent'
curl -X POST 'http://localhost:8080/api/agents/planner-agent/start'

# 3. Check Blackboard for Plan
curl 'http://localhost:8080/api/blackboard/entries/type/PLAN'

# 4. Trigger Coder Agent (with self-correction)
curl -X POST 'http://localhost:8080/api/agents?id=coder-agent'
curl -X POST 'http://localhost:8080/api/agents/coder-agent/start'

# 5. Check Generated Code
curl 'http://localhost:8080/api/blackboard/entries/type/CODE_FINAL'

# 6. Trigger Reviewer Agent
curl -X POST 'http://localhost:8080/api/agents?id=reviewer-agent'
curl -X POST 'http://localhost:8080/api/agents/reviewer-agent/start'

# 7. Check Review Results
curl 'http://localhost:8080/api/blackboard/entries/type/REVIEW'

# 8. Check Agent Health
curl 'http://localhost:8080/api/mast/health/coder-agent'

# 9. Check MAST Violations
curl 'http://localhost:8080/api/mast/violations/recent'

# 10. Check Metrics
curl 'http://localhost:8080/actuator/prometheus' | grep agentmesh
```

**Expected Outcome:**
- Plan generated with task breakdown
- Code generated after 1-3 self-correction iterations
- Review identifies improvements or approves
- Agent health score: 80-100
- Metrics show successful self-correction

---

### Scenario 2: Bug Fix with Self-Correction
**Goal:** Fix a failing test with iterative debugging

**Steps:**
```bash
# 1. Post Bug Report
curl -X POST 'http://localhost:8080/api/blackboard/entries?agentId=qa&entryType=BUG&title=NPE in UserService' \
  -H 'Content-Type: text/plain' \
  -d 'NullPointerException in UserService.getUser() when user not found.
Stack trace: [...]
Expected: Return 404
Actual: NPE thrown'

# 2. Post Failing Test
curl -X POST 'http://localhost:8080/api/blackboard/entries?agentId=test-agent&entryType=TEST_FAILURE&title=testGetUserNotFound' \
  -H 'Content-Type: text/plain' \
  -d 'Test: testGetUserNotFound
Status: FAILED
Error: NullPointerException at line 42'

# 3. Trigger Debugger Agent
curl -X POST 'http://localhost:8080/api/agents/debugger-agent/start'

# 4. Check Debug Analysis
curl 'http://localhost:8080/api/blackboard/entries/type/DEBUG'

# 5. Trigger Coder for Fix (with self-correction)
curl -X POST 'http://localhost:8080/api/agents/coder-agent/start'

# 6. Monitor Self-Correction
curl 'http://localhost:8080/actuator/prometheus' | grep selfcorrection

# 7. Verify Fix
curl 'http://localhost:8080/api/blackboard/entries/type/CODE_FINAL'
```

**Expected Outcome:**
- Debugger identifies missing null check
- Coder generates fix with validation
- Self-correction ensures quality
- MAST violations: 0 (if successful)

---

### Scenario 3: MAST Violation Detection
**Goal:** Demonstrate automatic quality assurance

**Test Case A: Loop Detection (FM-1.3)**
```bash
# Generate code with infinite loop
curl -X POST 'http://localhost:8080/api/blackboard/entries?agentId=coder&entryType=CODE&title=BadCode' \
  -H 'Content-Type: text/plain' \
  -d 'while(true) { generate(); }'

# Self-correction will detect loop after 3 iterations
curl 'http://localhost:8080/api/mast/violations/recent'

# Expected: FM-1.3 violation recorded
```

**Test Case B: Timeout (FM-3.5)**
```bash
# Configure short timeout
# Edit application.yml: agentmesh.selfcorrection.timeout-seconds: 10

# Trigger complex task
curl -X POST 'http://localhost:8080/api/blackboard/entries?agentId=coder&entryType=TASK&title=Complex' \
  -H 'Content-Type: text/plain' \
  -d 'Generate a full microservices architecture with 10 services'

# Check for timeout violation
curl 'http://localhost:8080/api/mast/violations/recent' | grep FM-3.5
```

**Test Case C: Quality Issues (FM-3.1)**
```bash
# Mock LLM to return low-quality code
# Self-correction will fail after max iterations

curl 'http://localhost:8080/api/mast/violations/recent' | grep FM-3.1
curl 'http://localhost:8080/api/mast/health/coder-agent'
# Expected: Health score < 90
```

---

### Scenario 4: Multi-Agent Coordination
**Goal:** Test Blackboard-based collaboration

**Steps:**
```bash
# 1. Planner posts plan
curl -X POST 'http://localhost:8080/api/blackboard/entries?agentId=planner&entryType=PLAN&title=Feature Plan' \
  -H 'Content-Type: text/plain' \
  -d 'Task 1: Create User model
Task 2: Create UserRepository
Task 3: Create UserService
Task 4: Create UserController'

# 2. Coder picks up Task 1
curl 'http://localhost:8080/api/blackboard/entries/type/PLAN'
# Note the entry ID

curl -X POST 'http://localhost:8080/api/blackboard/entries?agentId=coder&entryType=CODE&title=User Model' \
  -H 'Content-Type: text/plain' \
  -d 'public class User { ... }'

# 3. Test agent generates tests
curl 'http://localhost:8080/api/blackboard/entries/type/CODE'
# Get code entry ID

curl -X POST 'http://localhost:8080/api/blackboard/entries?agentId=test-agent&entryType=TEST&title=User Tests' \
  -H 'Content-Type: text/plain' \
  -d '@Test public void testUser() { ... }'

# 4. Reviewer reviews all
curl 'http://localhost:8080/api/blackboard/entries'
# Check coordination

# 5. Create Snapshot
curl -X POST 'http://localhost:8080/api/blackboard/snapshot'

# 6. Check MAST for coordination issues
curl 'http://localhost:8080/api/mast/violations/recent' | grep FM-2
```

**Expected Outcome:**
- All agents see shared state via Blackboard
- No FM-2.x violations (coordination/communication)
- Snapshot created successfully

---

### Scenario 5: Cost & Performance Monitoring
**Goal:** Track token usage and optimize costs

**Steps:**
```bash
# 1. Start monitoring
watch -n 5 'curl -s http://localhost:8080/actuator/prometheus | grep agentmesh_llm'

# 2. Run multiple tasks
for i in {1..10}; do
  curl -X POST "http://localhost:8080/api/blackboard/entries?agentId=coder&entryType=TASK&title=Task$i" \
    -H 'Content-Type: text/plain' \
    -d "Generate function $i"
done

# 3. Check total token consumption
curl 'http://localhost:8080/actuator/prometheus' | grep agentmesh_llm_tokens_total

# 4. Calculate cost
# tokens * $0.00003 (GPT-4 input) + tokens * $0.00006 (output)

# 5. Check per-agent metrics
curl 'http://localhost:8080/api/mast/statistics/failure-modes'
```

**Metrics to Monitor:**
- `agentmesh_llm_calls_total` - Call count
- `agentmesh_llm_tokens_total` - Token usage
- `agentmesh_selfcorrection_attempts_total` - Correction attempts
- `agentmesh_selfcorrection_duration` - Time spent

---

### Scenario 6: Load Testing
**Goal:** Test system under concurrent load

**Setup:**
```bash
# Install Apache Bench
brew install apache2  # macOS
# or apt-get install apache2-utils  # Linux

# Test concurrent agents
ab -n 100 -c 10 -p task.json -T application/json \
  http://localhost:8080/api/blackboard/entries?agentId=coder&entryType=TASK&title=LoadTest

# Monitor during load
watch -n 1 'curl -s http://localhost:8080/api/mast/violations/unresolved | jq length'
watch -n 1 'curl -s http://localhost:8080/actuator/health'
```

**Expected:**
- System remains healthy under load
- No deadlocks (optimistic locking works)
- Metrics continue updating
- Response time < 5s per request

---

### Scenario 7: Failure Recovery
**Goal:** Test resilience and error handling

**Test Case A: LLM Failure**
```bash
# Stop OpenAI (or block network)
# System falls back to MockLLM

curl 'http://localhost:8080/api/mast/violations/recent' | grep FM-3.6
# Should see tool invocation failure
```

**Test Case B: Database Failure**
```bash
# Stop H2 (for production: stop PostgreSQL)
docker-compose stop postgres

# System should:
# - Return 503 on /actuator/health
# - Log errors
# - Attempt reconnection

docker-compose start postgres
# System should recover
```

**Test Case C: Self-Correction Loop**
```bash
# Configure max iterations = 2
# Trigger difficult task

curl 'http://localhost:8080/api/mast/violations/recent'
# Should see FM-3.1 after 2 failed iterations

curl 'http://localhost:8080/api/mast/health/coder-agent'
# Health score should decrease
```

---

## 🧪 Automated Test Suite

### Integration Test Script
```bash
#!/bin/bash
# test-suite.sh

BASE_URL="http://localhost:8080"

echo "🧪 AgentMesh Integration Test Suite"
echo "===================================="

# Test 1: Health Check
echo "Test 1: Health Check"
curl -f $BASE_URL/actuator/health || exit 1
echo "✅ Pass"

# Test 2: Create Agent
echo "Test 2: Create Agent"
curl -f -X POST "$BASE_URL/api/agents?id=test-agent" || exit 1
echo "✅ Pass"

# Test 3: Post to Blackboard
echo "Test 3: Post to Blackboard"
curl -f -X POST "$BASE_URL/api/blackboard/entries?agentId=test&entryType=TEST&title=Test" \
  -H 'Content-Type: text/plain' -d 'Test content' || exit 1
echo "✅ Pass"

# Test 4: Read from Blackboard
echo "Test 4: Read from Blackboard"
curl -f "$BASE_URL/api/blackboard/entries" || exit 1
echo "✅ Pass"

# Test 5: Check MAST Health
echo "Test 5: Check MAST Health"
curl -f "$BASE_URL/api/mast/health/test-agent" || exit 1
echo "✅ Pass"

# Test 6: Get Metrics
echo "Test 6: Get Metrics"
curl -f "$BASE_URL/actuator/prometheus" | grep agentmesh || exit 1
echo "✅ Pass"

echo ""
echo "✅ All tests passed!"
```

**Run:**
```bash
chmod +x test-suite.sh
./test-suite.sh
```

---

## 📊 Performance Benchmarks

### Expected Performance (MockLLM)
- **Blackboard Read:** < 10ms
- **Blackboard Write:** < 50ms
- **Self-Correction (1 iter):** < 100ms
- **Health Check:** < 5ms
- **Metrics Export:** < 20ms

### Expected Performance (Real LLM)
- **Planning:** 2-4s
- **Code Generation:** 3-6s (per iteration)
- **Self-Correction (3 iters):** 10-20s
- **Code Review:** 2-3s

---

## 🎬 Demo Script

### 5-Minute Demo
```bash
# Terminal 1: Start AgentMesh
mvn spring-boot:run

# Terminal 2: Monitor Metrics
watch -n 2 'curl -s http://localhost:8080/actuator/prometheus | grep agentmesh | head -20'

# Terminal 3: Execute Demo
echo "📝 Creating User Management Feature"
curl -X POST 'http://localhost:8080/api/blackboard/entries?agentId=demo&entryType=SRS&title=User API' \
  -H 'Content-Type: text/plain' -d 'Create REST API for user management'

sleep 2

echo "🤖 Checking Agent Health"
curl http://localhost:8080/api/mast/health/coder-agent | jq

sleep 2

echo "📊 Checking Metrics"
curl http://localhost:8080/actuator/prometheus | grep agentmesh_selfcorrection

echo "✅ Demo Complete!"
```

---

## 🔍 Debugging Scenarios

### Debug Self-Correction Failures
```bash
# Enable debug logging
# Edit application.yml: logging.level.com.therighthandapp.agentmesh: DEBUG

# Run task
curl -X POST 'http://localhost:8080/api/blackboard/entries?agentId=coder&entryType=TASK&title=Debug' \
  -H 'Content-Type: text/plain' -d 'Generate complex code'

# Check logs
tail -f logs/spring.log | grep SelfCorrectionLoop

# Check violations
curl http://localhost:8080/api/mast/violations/recent | jq
```

### Debug MAST Violations
```bash
# Get all violations
curl http://localhost:8080/api/mast/violations/recent | jq

# Filter by agent
curl 'http://localhost:8080/api/mast/violations/agent/coder-agent' | jq

# Get statistics
curl http://localhost:8080/api/mast/statistics/failure-modes | jq

# Resolve violation
curl -X POST 'http://localhost:8080/api/mast/violations/1/resolve' \
  -H 'Content-Type: application/json' \
  -d '{"resolution": "Fixed by adjusting prompt"}'
```

---

## ✅ Success Criteria

### Per Scenario
- [ ] All API calls return 200 OK
- [ ] Metrics are updating
- [ ] No unhandled exceptions
- [ ] Agent health > 70
- [ ] Self-correction succeeds in < 5 iterations
- [ ] Response time within SLA

### System-Wide
- [ ] 56/56 tests passing
- [ ] No memory leaks (monitor heap)
- [ ] Database connections properly closed
- [ ] Prometheus scraping successful
- [ ] All MAST violations resolved or acknowledged

---

## 📝 Test Data Templates

### task.json (for load testing)
```json
{
  "description": "Generate a simple calculator class with add, subtract, multiply, divide methods"
}
```

### srs-complex.txt
```
Software Requirements Specification: E-Commerce Platform

Functional Requirements:
1. User authentication and authorization
2. Product catalog with search and filters
3. Shopping cart functionality
4. Payment processing integration
5. Order management system
6. Admin dashboard for inventory

Non-Functional Requirements:
- Performance: < 2s page load time
- Security: HTTPS, JWT, encryption
- Scalability: Support 10k concurrent users
- Availability: 99.9% uptime
```

---

## 🎯 Recommended Test Order

1. **Smoke Tests** (5 min) - Health checks, basic CRUD
2. **Simple Feature** (10 min) - End-to-end single feature
3. **Self-Correction** (15 min) - Test quality loop
4. **MAST Detection** (10 min) - Trigger violations
5. **Multi-Agent** (15 min) - Coordination test
6. **Load Test** (10 min) - Performance under stress
7. **Failure Recovery** (10 min) - Resilience testing

**Total:** ~75 minutes for complete validation


