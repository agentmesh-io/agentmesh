#!/bin/bash

# Test Script for FM-2.3: Dependency Violation Detection
# Tests wrong execution order detection (e.g., Tester before Implementer)

API_URL="http://localhost:8080"
TENANT_ID="test-tenant-fm-2-3-$(date +%s)"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test counters
TESTS_RUN=0
TESTS_PASSED=0

# Helper function to post to blackboard
post_to_blackboard() {
    local agent_id="$1"
    local entry_type="$2"
    local title="$3"
    local content="$4"
    local tenant_override="${5:-$TENANT_ID}"  # Use 5th parameter if provided, otherwise use global TENANT_ID
    
    curl -s -X POST "$API_URL/api/blackboard/entries?agentId=$agent_id&entryType=$entry_type&title=$(echo "$title" | jq -sRr @uri)" \
        -H "Content-Type: text/plain" \
        -H "X-Tenant-ID: $tenant_override" \
        --data-raw "$content"
}

# Helper function to check test result
check_test() {
    local test_name="$1"
    local expected_violations="$2"
    local agent_id="$3"
    
    TESTS_RUN=$((TESTS_RUN + 1))
    
    # Wait for violation to be processed
    sleep 2
    
    # Check database for violations
    local count=$(docker exec agentmesh-postgres psql -U agentmesh -d agentmesh -t -c \
        "SELECT COUNT(*) FROM mast_violations WHERE agent_id = '$agent_id' AND failure_mode = 'FM_2_3_DEPENDENCY_VIOLATION';" \
        2>/dev/null | tr -d ' ')
    
    if [ "$count" = "$expected_violations" ]; then
        echo -e "${GREEN}✓ PASS${NC}: $test_name (expected $expected_violations, got $count)"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        echo -e "${RED}✗ FAIL${NC}: $test_name (expected $expected_violations, got $count)"
    fi
}

echo -e "${YELLOW}=== FM-2.3 Dependency Violation Detection Tests ===${NC}\n"

# Test 1: Tester before Implementer (VIOLATION)
echo -e "\n${YELLOW}Test 1: Tester creates TEST_RESULT before any CODE exists${NC}"
post_to_blackboard "tester-early-1" "TEST_RESULT" "Unit Tests for Login API" "Running tests on non-existent code. Coverage: 0%"

check_test "Tester before CODE" "1" "tester-early-1"

# Test 2: Reviewer before Implementer (VIOLATION)
echo -e "\n${YELLOW}Test 2: Reviewer creates REVIEW before any CODE exists${NC}"
post_to_blackboard "reviewer-early-1" "REVIEW" "Code Review for Authentication Module" "Reviewing code that does not exist yet."

check_test "Reviewer before CODE" "1" "reviewer-early-1"

# Test 3: Implementer before Planner (VIOLATION)
echo -e "\n${YELLOW}Test 3: Implementer creates CODE before any planning (SRS/TASK_BREAKDOWN)${NC}"
post_to_blackboard "implementer-eager-1" "CODE" "Login Implementation" "public class Login { /* Implementation without specs */ }"

check_test "Implementer before planning" "1" "implementer-eager-1"

# Test 4: Correct workflow - Planner → Implementer → Reviewer → Tester (NO VIOLATION)
echo -e "\n${YELLOW}Test 4: Correct workflow - Planner → Implementer → Reviewer → Tester${NC}"

# Planner creates SRS
post_to_blackboard "planner-correct-1" "SRS" "Authentication System Requirements" "The system shall provide user authentication with username and password."

sleep 1

# Implementer creates CODE
post_to_blackboard "implementer-correct-1" "CODE" "Authentication Implementation" "public class AuthService { /* Proper implementation */ }"

sleep 1

# Reviewer creates REVIEW
post_to_blackboard "reviewer-correct-1" "REVIEW" "Code Review for AuthService" "LGTM - Code follows best practices."

sleep 1

# Tester creates TEST_RESULT
post_to_blackboard "tester-correct-1" "TEST_RESULT" "Auth Tests" "All tests passed. Coverage: 95%"

check_test "Correct workflow (Implementer)" "0" "implementer-correct-1"
check_test "Correct workflow (Reviewer)" "0" "reviewer-correct-1"
check_test "Correct workflow (Tester)" "0" "tester-correct-1"

# Test 5: Reviewer working before planning phase (VIOLATION)
echo -e "\n${YELLOW}Test 5: Reviewer starts before any planning (SRS/TASK_BREAKDOWN)${NC}"

# Use new tenant ID for clean slate
TENANT_ID_2="test-tenant-fm-2-3-clean-$(date +%s)"

post_to_blackboard "reviewer-no-planning-1" "REVIEW" "Review of Unknown Project" "Reviewing without any planning or code." "$TENANT_ID_2"

check_test "Reviewer before planning" "1" "reviewer-no-planning-1"

# Test 6: Duplicate TASK_BREAKDOWN from different planners (VIOLATION)
echo -e "\n${YELLOW}Test 6: Multiple planners creating duplicate TASK_BREAKDOWN${NC}"

# First planner creates TASK_BREAKDOWN
post_to_blackboard "planner-first" "TASK_BREAKDOWN" "Project Task Breakdown" "Task 1: Setup, Task 2: Implementation, Task 3: Testing" "$TENANT_ID_2"

sleep 1

# Second planner creates duplicate TASK_BREAKDOWN
post_to_blackboard "planner-second" "TASK_BREAKDOWN" "Alternative Task Breakdown" "Task A: Design, Task B: Coding, Task C: QA" "$TENANT_ID_2"

check_test "Duplicate TASK_BREAKDOWN" "1" "planner-second"

# Test 7: Planner → Implementer (partial workflow, NO VIOLATION)
echo -e "\n${YELLOW}Test 7: Partial workflow - Planner followed by Implementer${NC}"

TENANT_ID_3="test-tenant-fm-2-3-partial-$(date +%s)"

# Planner creates TASK_BREAKDOWN
post_to_blackboard "planner-partial" "TASK_BREAKDOWN" "Feature Implementation Plan" "Break down feature into 5 tasks" "$TENANT_ID_3"

sleep 1

# Implementer creates CODE
post_to_blackboard "implementer-partial" "CODE" "Feature Implementation" "public class Feature { /* Implementation */ }" "$TENANT_ID_3"

check_test "Partial workflow (Implementer)" "0" "implementer-partial"

# Test 8: Tester working before planning phase (VIOLATION)
echo -e "\n${YELLOW}Test 8: Tester starts before any planning phase${NC}"

TENANT_ID_4="test-tenant-fm-2-3-tester-early-$(date +%s)"

post_to_blackboard "tester-no-plan" "TEST_RESULT" "Testing Without Plan" "Running tests without any planning or code. Coverage: 0%" "$TENANT_ID_4"

check_test "Tester before planning" "1" "tester-no-plan"

# Summary
echo -e "\n${YELLOW}=== Test Summary ===${NC}"
echo -e "Tests run: $TESTS_RUN"
echo -e "Tests passed: ${GREEN}$TESTS_PASSED${NC}"
echo -e "Tests failed: ${RED}$((TESTS_RUN - TESTS_PASSED))${NC}"

if [ $TESTS_PASSED -eq $TESTS_RUN ]; then
    echo -e "\n${GREEN}All tests passed!${NC}"
    exit 0
else
    echo -e "\n${RED}Some tests failed!${NC}"
    exit 1
fi
