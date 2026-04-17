# E2E Test Scenario: Build User Management REST API

**Test ID**: E2E-001  
**Test Type**: End-to-End Multi-Agent Collaboration  
**Estimated Duration**: 3-5 minutes  
**Phase**: 5 Week 2 Day 1  

---

## 🎯 Test Objective

**Primary Goal**: Validate that multiple specialized agents can collaborate to complete a real-world SDLC task

**Secondary Goals**:
- Verify blackboard communication between agents
- Validate memory usage for context sharing
- Ensure artifacts are properly stored and retrievable
- Detect MAST violations in agent behavior
- Measure end-to-end workflow performance

---

## 📋 Test Scenario

### User Request
```
"Create a REST API for user management with CRUD operations. 
Include endpoints for:
- GET /users - List all users
- GET /users/{id} - Get user by ID
- POST /users - Create new user
- PUT /users/{id} - Update user
- DELETE /users/{id} - Delete user

Include proper error handling and validation."
```

### Expected Outcome
A complete REST API implementation with:
- Entity class (User.java)
- Controller class (UserController.java)
- Service layer (UserService.java)
- Unit tests (UserControllerTest.java)
- Integration tests (UserServiceIntegrationTest.java)
- All code reviewed for quality
- Test coverage > 90%

---

## 🔄 Workflow Steps

### Step 1: Planner Agent (Requirements Analysis)

**Input**:
```json
{
  "tenantId": "e2e-test-org",
  "projectId": "user-management-api",
  "userRequest": "Create REST API for user management with CRUD operations...",
  "agentType": "planner"
}
```

**Expected Behavior**:
1. Analyzes user request
2. Breaks down into technical requirements
3. Creates Software Requirements Specification (SRS)
4. Identifies components needed:
   - Entity layer (User.java)
   - Service layer (UserService.java)
   - Controller layer (UserController.java)
   - Exception handling
   - Validation
5. Stores SRS in memory with metadata:
   - artifactType: "SRS"
   - projectId: "user-management-api"
   - agentId: "planner-001"
6. Posts to blackboard: "Requirements Analysis Complete"

**Output Artifacts**:
```json
{
  "memory_artifacts": [
    {
      "id": "artifact-srs-001",
      "type": "SRS",
      "title": "User Management REST API Requirements",
      "content": "## Requirements\n1. Entity Layer...",
      "agentId": "planner-001",
      "projectId": "user-management-api"
    }
  ],
  "blackboard_posts": [
    {
      "id": "post-001",
      "message": "Requirements Analysis Complete",
      "agentId": "planner-001",
      "metadata": {
        "phase": "planning",
        "artifacts": ["artifact-srs-001"]
      }
    }
  ]
}
```

**Validation Assertions**:
- ✅ SRS artifact exists in memory
- ✅ SRS contains all 5 required components
- ✅ Blackboard has 1 post from planner
- ✅ Memory query returns SRS by projectId
- ✅ No MAST violations detected

---

### Step 2: Implementer Agent (Code Generation)

**Input**:
```json
{
  "tenantId": "e2e-test-org",
  "projectId": "user-management-api",
  "srs_artifact_id": "artifact-srs-001",
  "agentType": "implementer"
}
```

**Expected Behavior**:
1. Reads SRS from memory using artifact_id
2. Generates User.java entity:
   ```java
   @Entity
   public class User {
       @Id @GeneratedValue
       private Long id;
       @NotBlank private String username;
       @Email private String email;
       // getters/setters
   }
   ```
3. Generates UserService.java:
   ```java
   @Service
   public class UserService {
       public List<User> findAll() { ... }
       public User findById(Long id) { ... }
       public User save(User user) { ... }
       public void deleteById(Long id) { ... }
   }
   ```
4. Generates UserController.java:
   ```java
   @RestController @RequestMapping("/users")
   public class UserController {
       @GetMapping public List<User> getAll() { ... }
       @GetMapping("/{id}") public User getById(@PathVariable Long id) { ... }
       @PostMapping public User create(@RequestBody User user) { ... }
       @PutMapping("/{id}") public User update(@PathVariable Long id, @RequestBody User user) { ... }
       @DeleteMapping("/{id}") public void delete(@PathVariable Long id) { ... }
   }
   ```
5. Stores each file as separate memory artifact
6. Posts to blackboard: "Implementation Complete - 3 files generated"

**Output Artifacts**:
```json
{
  "memory_artifacts": [
    {
      "id": "artifact-user-entity",
      "type": "CODE",
      "title": "User.java",
      "content": "@Entity\npublic class User { ... }",
      "agentId": "implementer-001",
      "projectId": "user-management-api",
      "metadata": {
        "language": "java",
        "component": "entity"
      }
    },
    {
      "id": "artifact-user-service",
      "type": "CODE",
      "title": "UserService.java",
      "content": "@Service\npublic class UserService { ... }",
      "agentId": "implementer-001"
    },
    {
      "id": "artifact-user-controller",
      "type": "CODE",
      "title": "UserController.java",
      "content": "@RestController\npublic class UserController { ... }",
      "agentId": "implementer-001"
    }
  ],
  "blackboard_posts": [
    {
      "id": "post-002",
      "message": "Implementation Complete - 3 files generated",
      "agentId": "implementer-001",
      "metadata": {
        "phase": "implementation",
        "artifacts": [
          "artifact-user-entity",
          "artifact-user-service",
          "artifact-user-controller"
        ]
      }
    }
  ]
}
```

**Validation Assertions**:
- ✅ 3 code artifacts exist in memory
- ✅ Each artifact has correct artifactType: "CODE"
- ✅ User.java contains @Entity annotation
- ✅ UserController contains @RestController
- ✅ All 5 CRUD endpoints present
- ✅ Blackboard has 2 posts (planner + implementer)
- ✅ Memory query by artifactType="CODE" returns 3 results
- ✅ No MAST violations detected

---

### Step 3: Reviewer Agent (Code Review)

**Input**:
```json
{
  "tenantId": "e2e-test-org",
  "projectId": "user-management-api",
  "code_artifact_ids": [
    "artifact-user-entity",
    "artifact-user-service",
    "artifact-user-controller"
  ],
  "agentType": "reviewer"
}
```

**Expected Behavior**:
1. Reads all 3 code artifacts from memory
2. Performs code review checks:
   - ✅ Proper annotations present (@Entity, @Service, @RestController)
   - ✅ Exception handling implemented
   - ✅ Validation annotations used (@NotBlank, @Email)
   - ⚠️ Missing: JavaDoc comments
   - ⚠️ Missing: Logging statements
3. Generates review report with 2 issues found
4. Stores review report in memory
5. Posts to blackboard: "Code Review Complete - 2 issues found"

**Output Artifacts**:
```json
{
  "memory_artifacts": [
    {
      "id": "artifact-review-report",
      "type": "REVIEW",
      "title": "Code Review Report - User Management API",
      "content": "## Review Summary\n**Status**: APPROVED with recommendations\n\n**Issues Found**:\n1. Missing JavaDoc comments on public methods\n2. No logging for error conditions\n\n**Recommendations**:\n- Add @Slf4j and log.error() for exceptions\n- Document API endpoints with JavaDoc\n\n**Overall Score**: 8/10",
      "agentId": "reviewer-001",
      "projectId": "user-management-api",
      "metadata": {
        "status": "approved_with_recommendations",
        "issuesCount": 2,
        "score": 8
      }
    }
  ],
  "blackboard_posts": [
    {
      "id": "post-003",
      "message": "Code Review Complete - 2 issues found",
      "agentId": "reviewer-001",
      "metadata": {
        "phase": "review",
        "status": "approved_with_recommendations",
        "artifacts": ["artifact-review-report"]
      }
    }
  ]
}
```

**Validation Assertions**:
- ✅ Review report artifact exists in memory
- ✅ Review contains issue count (2 issues)
- ✅ Review status is "approved_with_recommendations"
- ✅ Blackboard has 3 posts
- ✅ Memory query for artifactType="REVIEW" returns 1 result
- ✅ No MAST violations detected

---

### Step 4: Tester Agent (Test Generation)

**Input**:
```json
{
  "tenantId": "e2e-test-org",
  "projectId": "user-management-api",
  "code_artifact_ids": [
    "artifact-user-entity",
    "artifact-user-service",
    "artifact-user-controller"
  ],
  "agentType": "tester"
}
```

**Expected Behavior**:
1. Reads code artifacts from memory
2. Generates UserControllerTest.java:
   ```java
   @WebMvcTest(UserController.class)
   public class UserControllerTest {
       @Test void testGetAll() { ... }
       @Test void testGetById() { ... }
       @Test void testCreate() { ... }
       @Test void testUpdate() { ... }
       @Test void testDelete() { ... }
       @Test void testGetById_NotFound() { ... }
   }
   ```
3. Generates UserServiceIntegrationTest.java:
   ```java
   @SpringBootTest
   public class UserServiceIntegrationTest {
       @Test void testFindAll() { ... }
       @Test void testSaveAndFind() { ... }
       @Test void testDelete() { ... }
   }
   ```
4. Calculates test coverage: 95% (19/20 methods covered)
5. Stores test files in memory
6. Posts to blackboard: "Tests Created - 95% coverage achieved"

**Output Artifacts**:
```json
{
  "memory_artifacts": [
    {
      "id": "artifact-controller-test",
      "type": "TEST",
      "title": "UserControllerTest.java",
      "content": "@WebMvcTest\npublic class UserControllerTest { ... }",
      "agentId": "tester-001",
      "projectId": "user-management-api",
      "metadata": {
        "testType": "unit",
        "coverage": 95,
        "testCount": 6
      }
    },
    {
      "id": "artifact-service-integration-test",
      "type": "TEST",
      "title": "UserServiceIntegrationTest.java",
      "content": "@SpringBootTest\npublic class UserServiceIntegrationTest { ... }",
      "agentId": "tester-001",
      "metadata": {
        "testType": "integration",
        "testCount": 3
      }
    }
  ],
  "blackboard_posts": [
    {
      "id": "post-004",
      "message": "Tests Created - 95% coverage achieved",
      "agentId": "tester-001",
      "metadata": {
        "phase": "testing",
        "coverage": 95,
        "testCount": 9,
        "artifacts": [
          "artifact-controller-test",
          "artifact-service-integration-test"
        ]
      }
    }
  ]
}
```

**Validation Assertions**:
- ✅ 2 test artifacts exist in memory
- ✅ Test coverage ≥ 90%
- ✅ UserControllerTest contains 6 test methods
- ✅ Integration test exists
- ✅ Blackboard has 4 posts (complete workflow)
- ✅ Memory query for artifactType="TEST" returns 2 results
- ✅ No MAST violations detected

---

### Step 5: Final Validation (System Check)

**Expected State**:
```json
{
  "total_memory_artifacts": 7,
  "artifacts_by_type": {
    "SRS": 1,
    "CODE": 3,
    "REVIEW": 1,
    "TEST": 2
  },
  "total_blackboard_posts": 4,
  "agents_involved": 4,
  "mast_violations": 0,
  "test_coverage": 95,
  "e2e_duration_seconds": 180
}
```

**Validation Assertions**:
- ✅ Total artifacts in memory: 7
- ✅ Artifact breakdown: 1 SRS, 3 CODE, 1 REVIEW, 2 TEST
- ✅ Total blackboard posts: 4
- ✅ All posts from different agents
- ✅ No MAST violations detected
- ✅ Test coverage > 90%
- ✅ E2E duration < 5 minutes
- ✅ All artifacts retrievable by projectId
- ✅ Blackboard timeline shows correct order

---

## 🧪 Test Implementation Plan

### Test Script Structure
```bash
#!/bin/bash
# test-scripts/10-e2e-rest-api-workflow.sh

# Configuration
BASE_URL="http://localhost:8080/api"
TENANT_ID="e2e-test-org"
PROJECT_ID="user-management-api"

# Helper Functions
create_tenant() { ... }
create_project() { ... }
execute_agent() { ... }
validate_memory_artifact() { ... }
validate_blackboard_post() { ... }
query_memory_by_type() { ... }
check_mast_violations() { ... }
calculate_test_coverage() { ... }

# Test Execution
echo "🚀 Starting E2E Test: Build User Management REST API"

# Setup
create_tenant "$TENANT_ID"
create_project "$TENANT_ID" "$PROJECT_ID"

# Step 1: Planner Agent
echo "Step 1/4: Planning Phase"
planner_response=$(execute_agent "planner" "$PROJECT_ID" "Create REST API...")
srs_id=$(extract_artifact_id "$planner_response")
validate_memory_artifact "$srs_id" "SRS"
validate_blackboard_post "Requirements Analysis Complete"

# Step 2: Implementer Agent
echo "Step 2/4: Implementation Phase"
implementer_response=$(execute_agent "implementer" "$PROJECT_ID" "$srs_id")
code_ids=$(extract_artifact_ids "$implementer_response")
validate_code_files 3
validate_blackboard_post "Implementation Complete"

# Step 3: Reviewer Agent
echo "Step 3/4: Review Phase"
reviewer_response=$(execute_agent "reviewer" "$PROJECT_ID" "$code_ids")
review_id=$(extract_artifact_id "$reviewer_response")
validate_review_status "approved_with_recommendations"
validate_blackboard_post "Code Review Complete"

# Step 4: Tester Agent
echo "Step 4/4: Testing Phase"
tester_response=$(execute_agent "tester" "$PROJECT_ID" "$code_ids")
test_ids=$(extract_artifact_ids "$tester_response")
coverage=$(calculate_test_coverage "$tester_response")
validate_coverage_threshold "$coverage" 90
validate_blackboard_post "Tests Created"

# Final Validation
echo "Final Validation"
total_artifacts=$(count_memory_artifacts "$PROJECT_ID")
assert_equals "$total_artifacts" 7 "Total artifacts"

total_posts=$(count_blackboard_posts "$PROJECT_ID")
assert_equals "$total_posts" 4 "Total blackboard posts"

violations=$(check_mast_violations "$PROJECT_ID")
assert_equals "$violations" 0 "MAST violations"

echo "✅ E2E Test PASSED - All assertions successful"
```

---

## 📊 Success Criteria

### Functional Requirements (Must Pass)
- [x] All 4 agents execute successfully
- [x] 7 artifacts stored in memory
- [x] 4 blackboard posts created
- [x] All artifacts retrievable by projectId
- [x] Blackboard shows correct chronological order
- [x] Test coverage ≥ 90%
- [x] Zero MAST violations

### Performance Requirements (Should Pass)
- [x] E2E execution time < 5 minutes
- [x] Each agent response time < 60 seconds
- [x] Memory queries < 100ms
- [x] Blackboard queries < 50ms

### Quality Requirements (Nice to Have)
- [x] Generated code compiles
- [x] Tests are runnable
- [x] Review provides actionable feedback
- [x] SRS is well-structured

---

## 🔍 Validation Functions

### validate_memory_artifact(artifact_id, expected_type)
```bash
validate_memory_artifact() {
    local artifact_id=$1
    local expected_type=$2
    
    response=$(curl -s "$BASE_URL/memory/$artifact_id")
    actual_type=$(echo "$response" | jq -r '.artifactType')
    
    if [ "$actual_type" == "$expected_type" ]; then
        echo "✅ Memory artifact validated: $artifact_id ($expected_type)"
        return 0
    else
        echo "❌ Memory artifact validation failed"
        return 1
    fi
}
```

### validate_blackboard_post(expected_message)
```bash
validate_blackboard_post() {
    local expected_message=$1
    
    response=$(curl -s "$BASE_URL/blackboard/posts?projectId=$PROJECT_ID")
    posts=$(echo "$response" | jq -r '.posts[] | .message')
    
    if echo "$posts" | grep -q "$expected_message"; then
        echo "✅ Blackboard post validated: $expected_message"
        return 0
    else
        echo "❌ Blackboard post not found"
        return 1
    fi
}
```

### check_mast_violations(project_id)
```bash
check_mast_violations() {
    local project_id=$1
    
    response=$(curl -s "$BASE_URL/mast/violations?projectId=$project_id")
    count=$(echo "$response" | jq -r '.violations | length')
    
    echo "$count"
}
```

---

## 🐛 Error Scenarios

### Scenario 1: Planner Agent Fails
**Trigger**: Invalid user request (empty string)  
**Expected**: 400 Bad Request, no memory artifacts created  
**Assertion**: Memory query returns 0 artifacts

### Scenario 2: Implementer Can't Find SRS
**Trigger**: Invalid SRS artifact ID  
**Expected**: 404 Not Found, no code generated  
**Assertion**: Memory query for CODE returns 0 artifacts

### Scenario 3: Reviewer Finds Critical Issues
**Trigger**: Code with security vulnerabilities  
**Expected**: Review status "rejected", workflow stops  
**Assertion**: Tester agent never executes

### Scenario 4: Test Coverage Below Threshold
**Trigger**: Complex code, simple tests  
**Expected**: Warning message, workflow continues  
**Assertion**: Coverage < 90% but artifact still created

---

## 📈 Metrics to Collect

### Timing Metrics
- Planner execution time
- Implementer execution time
- Reviewer execution time
- Tester execution time
- Total E2E duration
- Memory storage latency
- Blackboard post latency

### Quality Metrics
- Lines of code generated
- Number of methods created
- Test coverage percentage
- Review score (1-10)
- MAST violations count

### System Metrics
- Memory artifact count
- Blackboard post count
- Active agent count
- API request count

---

## 🎯 Next Steps

1. **Implement Test Script** (4 hours)
   - Create bash script with all helper functions
   - Implement validation assertions
   - Add error handling and retry logic

2. **Mock Agent Responses** (2 hours)
   - If real agents not ready, create mock endpoints
   - Return realistic agent responses
   - Simulate latency (2-10 seconds per agent)

3. **Run Test** (1 hour)
   - Execute test script
   - Capture console output
   - Save metrics to results file

4. **Document Results** (1 hour)
   - Create test report
   - Include pass/fail for each assertion
   - Add performance metrics
   - Document any issues found

---

**Test Design Complete!** ✅

This design provides a comprehensive blueprint for implementing the E2E test. Next step: Implement the test script in `test-scripts/10-e2e-rest-api-workflow.sh`.
