# Phase 5 Week 2 - Kickoff Plan

**Start Date**: November 6, 2025  
**Status**: 🟢 **READY TO BEGIN**  
**Prerequisites**: Week 1 Complete (100%, A grade)  
**Branch**: `phase5-week2-e2e-monitoring`

---

## 🎯 Week 2 Objectives

Focus on **End-to-End Workflows** and **Observability/Monitoring**:

1. **E2E Test Scenarios** - Validate complete agent collaboration workflows
2. **Prometheus Metrics** - Instrument all critical paths
3. **Grafana Dashboards** - Real-time monitoring and alerting
4. **Structured Logging** - Correlation tracking across agents
5. **Load Testing** - Validate performance under concurrent load

---

## 📋 Week 2 Task Breakdown

### Priority 1: E2E Test Scenarios (Day 1-3)

**Goal**: Prove that multiple agents can collaborate to complete a real-world SDLC task

#### Task 1.1: Design E2E Test Scenario ⏰ 2 hours
**Objective**: Design comprehensive workflow that exercises all agent types

**Scenario: "Build a User Management REST API"**
```
User Request: "Create a REST API for user management with CRUD operations"

Expected Flow:
1. Planner Agent:
   - Analyzes requirement
   - Creates task breakdown
   - Posts to blackboard: "Requirements Analysis Complete"
   - Stores SRS in memory

2. Implementer Agent:
   - Reads SRS from memory
   - Generates User.java entity
   - Generates UserController.java
   - Generates UserService.java
   - Posts to blackboard: "Implementation Complete"

3. Reviewer Agent:
   - Reads implementation from blackboard
   - Reviews code quality
   - Checks best practices
   - Posts to blackboard: "Code Review Complete - 2 issues found"

4. Tester Agent:
   - Reads implementation
   - Generates UserControllerTest.java
   - Creates integration tests
   - Posts to blackboard: "Tests Created - 95% coverage"

5. Final Validation:
   - All artifacts stored in memory
   - All blackboard posts verified
   - No MAST violations detected
   - Test coverage > 90%
```

**Success Criteria**:
- ✅ All 4 agents complete their tasks
- ✅ Blackboard shows 4 posts
- ✅ Memory contains 6 artifacts (SRS + 3 code files + 2 test files)
- ✅ Zero MAST violations
- ✅ Test coverage > 90%
- ✅ End-to-end time < 5 minutes

**Validation Points**:
1. After Planner: SRS exists in memory, blackboard has 1 post
2. After Implementer: 3 code files in memory, blackboard has 2 posts
3. After Reviewer: Review posted to blackboard (3 posts total)
4. After Tester: Test files in memory, blackboard has 4 posts
5. Final: All artifacts retrievable, no data loss

---

#### Task 1.2: Implement E2E Test Script ⏰ 4 hours
**File**: `test-scripts/10-e2e-rest-api-workflow.sh`

**Structure**:
```bash
#!/bin/bash
# Phase 5 Week 2: E2E Workflow Test

# Setup
create_tenant "e2e-test-org"
create_project "user-management-api"

# Step 1: Planner Agent
echo "Step 1: Planning Phase"
planner_output=$(curl -X POST /api/agents/planner/execute \
  -d '{"requirement": "Create REST API for user management"}')
validate_srs_in_memory
validate_blackboard_post "Requirements Analysis Complete"

# Step 2: Implementer Agent
echo "Step 2: Implementation Phase"
implementer_output=$(curl -X POST /api/agents/implementer/execute \
  -d '{"srs_id": "'$srs_id'"}')
validate_code_files_generated 3
validate_blackboard_post "Implementation Complete"

# Step 3: Reviewer Agent
echo "Step 3: Review Phase"
reviewer_output=$(curl -X POST /api/agents/reviewer/execute \
  -d '{"code_artifacts": "'$code_ids'"}')
validate_review_posted
validate_no_critical_issues

# Step 4: Tester Agent
echo "Step 4: Testing Phase"
tester_output=$(curl -X POST /api/agents/tester/execute \
  -d '{"code_artifacts": "'$code_ids'"}')
validate_test_files_generated 2
validate_coverage_threshold 90

# Final Validation
echo "Final Validation"
validate_total_memory_artifacts 6
validate_total_blackboard_posts 4
validate_zero_mast_violations
validate_e2e_time_under 300

echo "✅ E2E Test PASSED"
```

**Test Assertions**:
- `validate_srs_in_memory()`: Query memory for SRS artifact
- `validate_blackboard_post()`: Check blackboard contains expected message
- `validate_code_files_generated()`: Verify N code files exist
- `validate_test_files_generated()`: Verify N test files exist
- `validate_coverage_threshold()`: Check coverage > threshold
- `validate_zero_mast_violations()`: Query MAST detector
- `validate_e2e_time_under()`: Total time < N seconds

---

### Priority 2: Prometheus Metrics (Day 4)

#### Task 2.1: Setup Prometheus Integration ⏰ 3 hours

**Dependencies**:
```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

**Metrics to Instrument**:

1. **Agent Metrics**:
   ```java
   @Component
   public class AgentMetrics {
       // Task execution
       Counter agentTasksTotal = Counter.builder("agent.tasks.total")
           .tag("agent_type", agentType)
           .tag("status", "success/failure")
           .register(registry);
       
       Histogram agentTaskDuration = Histogram.builder("agent.task.duration")
           .tag("agent_type", agentType)
           .register(registry);
       
       // Health score
       Gauge agentHealthScore = Gauge.builder("agent.health.score", 
           () -> calculateHealthScore())
           .register(registry);
   }
   ```

2. **Blackboard Metrics**:
   ```java
   Counter blackboardPostsTotal;
   Histogram blackboardQueryDuration;
   Counter blackboardPostsByAgent;
   Gauge blackboardActiveMessages;
   ```

3. **Memory Metrics**:
   ```java
   Counter vectorSearchTotal;
   Histogram vectorSearchLatency;
   Histogram vectorSearchResultCount;
   Counter memoryStorageTotal;
   Gauge memoryArtifactCount;
   ```

4. **MAST Metrics**:
   ```java
   Counter mastViolationsTotal;
   Counter mastViolationsByFailureMode;
   Gauge mastViolationsByAgent;
   Histogram mastDetectionLatency;
   ```

**Configuration**:
```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  metrics:
    tags:
      application: agentmesh
      environment: development
```

**Prometheus Config**:
```yaml
# prometheus.yml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'agentmesh'
    static_configs:
      - targets: ['localhost:8080']
    metrics_path: '/actuator/prometheus'
```

---

### Priority 3: Grafana Dashboards (Day 5)

#### Task 3.1: Design 3 Core Dashboards ⏰ 4 hours

**Dashboard 1: Agent Performance**
- Panel 1: Task Completion Rate (tasks/minute by agent type)
- Panel 2: Task Duration (p50/p95/p99 by agent type)
- Panel 3: Agent Health Scores (gauge per agent)
- Panel 4: Task Success Rate (% by agent type)
- Panel 5: Active Agents (current count)

**Dashboard 2: System Health**
- Panel 1: API Request Rate (req/sec)
- Panel 2: API Latency (p50/p95/p99)
- Panel 3: Error Rate (errors/min)
- Panel 4: Memory Usage (JVM heap, native)
- Panel 5: Vector Search Performance (latency distribution)
- Panel 6: Blackboard Activity (posts/queries per min)

**Dashboard 3: MAST Violations**
- Panel 1: Total Violations Over Time (line chart)
- Panel 2: Violations by Failure Mode (bar chart)
- Panel 3: Violations by Agent (table)
- Panel 4: Recent Violations (logs table)
- Panel 5: Violation Rate (violations/hour)

**Alert Rules**:
```yaml
# alerts.yml
groups:
  - name: agentmesh
    rules:
      - alert: HighErrorRate
        expr: rate(http_requests_total{status="500"}[5m]) > 0.05
        annotations:
          summary: "Error rate > 5%"
      
      - alert: HighMastViolations
        expr: rate(mast_violations_total[5m]) > 0.1
        annotations:
          summary: "MAST violations increasing"
      
      - alert: SlowVectorSearch
        expr: histogram_quantile(0.95, vector_search_latency) > 0.100
        annotations:
          summary: "Vector search p95 > 100ms"
```

---

## 📊 Week 2 Success Metrics

### E2E Testing
- [ ] E2E test scenario designed and documented
- [ ] Test script implements full workflow
- [ ] All agents collaborate successfully
- [ ] Zero MAST violations in happy path
- [ ] E2E execution time < 5 minutes

### Monitoring & Observability
- [ ] Prometheus metrics instrumented (20+ metrics)
- [ ] Metrics endpoint operational (/actuator/prometheus)
- [ ] 3 Grafana dashboards created
- [ ] Alert rules configured
- [ ] Structured logging with correlation IDs

### Performance
- [ ] E2E workflow < 5 minutes
- [ ] API latency < 100ms p95
- [ ] Vector search < 100ms p95
- [ ] Zero errors under normal load

---

## 🚀 Getting Started (Right Now!)

### Step 1: Create Week 2 Branch
```bash
cd /Users/univers/projects/agentmesh/AgentMesh
git checkout -b phase5-week2-e2e-monitoring
```

### Step 2: Design E2E Test Scenario
Create detailed specification for "Build REST API" workflow:
- Define each agent's role
- Specify input/output for each step
- List validation points
- Document expected artifacts

### Step 3: Add Prometheus Dependencies
Update `pom.xml` with Micrometer and Actuator dependencies

### Step 4: Implement First Metrics
Start with simple counters for agent tasks and API calls

### Step 5: Run First E2E Test
Even if partial, get one agent working end-to-end

---

## 📁 Deliverables

### Code
- `test-scripts/10-e2e-rest-api-workflow.sh` - Comprehensive E2E test
- `src/main/java/com/therighthandapp/agentmesh/metrics/` - Metrics components
- `prometheus.yml` - Prometheus scrape configuration
- `grafana/dashboards/` - Dashboard JSON exports

### Documentation
- `E2E_TEST_GUIDE.md` - How to run E2E tests
- `MONITORING_GUIDE.md` - Metrics and dashboard usage
- `PHASE5_WEEK2_COMPLETE.md` - Completion report

### Tests
- 1 comprehensive E2E test (10+ validation points)
- Metrics validation (verify counts/gauges)
- Dashboard screenshot validation

---

## 🎯 This Week's Focus

**Day 1 (Today)**: E2E Test Design
- ✅ Create Week 2 kickoff plan (THIS FILE)
- 🟡 Design "Build REST API" E2E scenario
- 🟡 Document agent collaboration flow
- 🟡 Define validation assertions

**Day 2**: E2E Test Implementation
- Implement test script
- Create helper functions
- Add comprehensive assertions
- Test with real agents

**Day 3**: E2E Test Validation
- Run full E2E test
- Fix any failures
- Optimize performance
- Document results

**Day 4**: Prometheus Setup
- Add dependencies
- Implement metrics
- Configure endpoints
- Test metric collection

**Day 5**: Grafana Dashboards
- Create 3 dashboards
- Configure alerts
- Export JSON configs
- Document usage

---

## 🔄 Iteration Strategy

### Iteration 1: Basic E2E
- Single-agent workflow (Planner only)
- Simple validation (artifact exists)
- Basic metrics (task count)

### Iteration 2: Multi-Agent E2E
- 2 agents (Planner + Implementer)
- Blackboard validation
- More metrics (duration, success rate)

### Iteration 3: Full E2E
- 4 agents (full workflow)
- Comprehensive validation
- All metrics instrumented

### Iteration 4: Polish
- Error scenarios
- Performance tuning
- Dashboard refinement
- Documentation

---

## ⚠️ Known Challenges

1. **Agent Coordination**: Agents may not have full orchestration yet
   - **Solution**: May need to implement simple workflow engine
   
2. **Test Determinism**: AI agents may produce variable output
   - **Solution**: Focus on structural validation, not exact content
   
3. **Timing Issues**: Agent responses may be slow
   - **Solution**: Add configurable timeouts and retries
   
4. **MAST Coverage**: Not all failure modes implemented yet
   - **Solution**: Focus on implemented failure modes for Week 2

---

## 📚 References

- Phase 5 Plan: `PHASE5_PLAN.md`
- Week 1 Report: `PHASE5_WEEK1_FINAL_REPORT.md`
- Test Results: `PHASE5_WEEK1_TEST_RESULTS.md`
- Previous Tests: `test-scripts/01-05-*.sh`

---

**Ready to Start!** 🚀

First task: Design the E2E test scenario for "Build REST API" workflow.
