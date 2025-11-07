#!/bin/bash

# ====================================================================
# Test Script: FM-1.2 Role Violation Detection
# ====================================================================
# Tests MAST failure mode FM-1.2 which detects when agents perform
# work outside their designated roles.
#
# Role Expectations:
# - Planner: Creates SRS and TASK_BREAKDOWN
# - Implementer: Creates CODE
# - Reviewer: Creates REVIEW
# - Tester: Creates TEST_RESULT
# ====================================================================

BASE_URL="http://localhost:8080"
TENANT_ID="test-tenant-$(date +%s)"
PROJECT_ID="test-project-$(date +%s)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test counters
TESTS_RUN=0
TESTS_PASSED=0
TESTS_FAILED=0

# Function to print test result
print_result() {
    local test_name="$1"
    local status="$2"
    local message="$3"
    
    TESTS_RUN=$((TESTS_RUN + 1))
    
    if [ "$status" = "PASS" ]; then
        echo -e "${GREEN}✓ PASS${NC}: $test_name"
        [ -n "$message" ] && echo -e "  ${BLUE}→${NC} $message"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        echo -e "${RED}✗ FAIL${NC}: $test_name"
        [ -n "$message" ] && echo -e "  ${RED}→${NC} $message"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

# Function to create blackboard entry directly
post_to_blackboard() {
    local agent_id="$1"
    local entry_type="$2"
    local title="$3"
    local content="$4"
    
    curl -s -X POST "$BASE_URL/api/blackboard/entries?agentId=$agent_id&entryType=$entry_type&title=$(echo "$title" | jq -sRr @uri)" \
        -H "Content-Type: text/plain" \
        -H "X-Tenant-ID: $TENANT_ID" \
        --data-raw "$content"
}

# Function to check for violations
check_violations() {
    local agent_id="$1"
    
    # Wait a bit for processing
    sleep 0.5
    
    # Check database directly for violations
    local count=$(docker exec agentmesh-postgres psql -U agentmesh -d agentmesh -t -c "SELECT COUNT(*) FROM mast_violations WHERE agent_id = '$agent_id' AND failure_mode = 'FM_1_2_ROLE_VIOLATION';" 2>/dev/null | tr -d ' ')
    
    if [ "$count" -gt 0 ]; then
        echo "FOUND"
    else
        echo "NOT_FOUND"
    fi
}

echo "======================================================================"
echo "  FM-1.2: Role Violation Detection Test Suite"
echo "======================================================================"
echo ""
echo -e "${BLUE}Configuration:${NC}"
echo "  Base URL: $BASE_URL"
echo "  Tenant ID: $TENANT_ID"
echo "  Project ID: $PROJECT_ID"
echo ""

# Check if AgentMesh is running
if ! curl -s "$BASE_URL/actuator/health" > /dev/null; then
    echo -e "${RED}ERROR: AgentMesh is not running at $BASE_URL${NC}"
    exit 1
fi

echo -e "${GREEN}✓ AgentMesh is running${NC}"
echo ""

# ====================================================================
# Test 1: Planner writing CODE (VIOLATION)
# ====================================================================
echo -e "${YELLOW}Test 1: Planner Agent Writing Code (Expected: VIOLATION)${NC}"
response=$(post_to_blackboard "planner-agent-1" "CODE" "User Authentication Service" "class AuthService { ... }")
violation=$(check_violations "planner-agent-1")

if [ "$violation" = "FOUND" ]; then
    print_result "Planner writing code" "PASS" "Violation correctly detected"
else
    print_result "Planner writing code" "FAIL" "Violation should be detected but wasn't"
fi
echo ""

# ====================================================================
# Test 2: Planner creating SRS (NO VIOLATION)
# ====================================================================
echo -e "${YELLOW}Test 2: Planner Agent Creating SRS (Expected: NO VIOLATION)${NC}"
response=$(post_to_blackboard "planner-agent-2" "SRS" "User Management System Requirements" "Requirements: 1. User login 2. Profile management")
violation=$(check_violations "planner-agent-2")

if [ "$violation" = "NOT_FOUND" ]; then
    print_result "Planner creating SRS" "PASS" "No violation (correct behavior)"
else
    print_result "Planner creating SRS" "FAIL" "False positive - violation detected incorrectly"
fi
echo ""

# ====================================================================
# Test 3: Implementer creating SRS (VIOLATION)
# ====================================================================
echo -e "${YELLOW}Test 3: Implementer Agent Creating SRS (Expected: VIOLATION)${NC}"
response=$(post_to_blackboard "implementer-agent-1" "SRS" "API Requirements" "Requirements for API design")
violation=$(check_violations "implementer-agent-1")

if [ "$violation" = "FOUND" ]; then
    print_result "Implementer creating SRS" "PASS" "Violation correctly detected"
else
    print_result "Implementer creating SRS" "FAIL" "Violation should be detected but wasn't"
fi
echo ""

# ====================================================================
# Test 4: Implementer writing CODE (NO VIOLATION)
# ====================================================================
echo -e "${YELLOW}Test 4: Implementer Agent Writing Code (Expected: NO VIOLATION)${NC}"
response=$(post_to_blackboard "implementer-agent-2" "CODE" "User Service Implementation" "class UserService { ... }")
violation=$(check_violations "implementer-agent-2")

if [ "$violation" = "NOT_FOUND" ]; then
    print_result "Implementer writing code" "PASS" "No violation (correct behavior)"
else
    print_result "Implementer writing code" "FAIL" "False positive - violation detected incorrectly"
fi
echo ""

# ====================================================================
# Test 5: Reviewer writing CODE (VIOLATION)
# ====================================================================
echo -e "${YELLOW}Test 5: Reviewer Agent Writing Code (Expected: VIOLATION)${NC}"
response=$(post_to_blackboard "reviewer-agent-1" "CODE" "Fixed Bug in AuthService" "class AuthService { /* bug fix */ }")
violation=$(check_violations "reviewer-agent-1")

if [ "$violation" = "FOUND" ]; then
    print_result "Reviewer writing code" "PASS" "Violation correctly detected"
else
    print_result "Reviewer writing code" "FAIL" "Violation should be detected but wasn't"
fi
echo ""

# ====================================================================
# Test 6: Reviewer creating REVIEW (NO VIOLATION)
# ====================================================================
echo -e "${YELLOW}Test 6: Reviewer Agent Creating Review (Expected: NO VIOLATION)${NC}"
response=$(post_to_blackboard "reviewer-agent-2" "REVIEW" "Code Review: UserService" "APPROVED - Good implementation")
violation=$(check_violations "reviewer-agent-2")

if [ "$violation" = "NOT_FOUND" ]; then
    print_result "Reviewer creating review" "PASS" "No violation (correct behavior)"
else
    print_result "Reviewer creating review" "FAIL" "False positive - violation detected incorrectly"
fi
echo ""

# ====================================================================
# Test 7: Tester writing CODE (VIOLATION)
# ====================================================================
echo -e "${YELLOW}Test 7: Tester Agent Writing Code (Expected: VIOLATION)${NC}"
response=$(post_to_blackboard "tester-agent-1" "CODE" "Test Helper Methods" "class TestHelper { ... }")
violation=$(check_violations "tester-agent-1")

if [ "$violation" = "FOUND" ]; then
    print_result "Tester writing code" "PASS" "Violation correctly detected"
else
    print_result "Tester writing code" "FAIL" "Violation should be detected but wasn't"
fi
echo ""

# ====================================================================
# Test 8: Tester creating TEST_RESULT (NO VIOLATION)
# ====================================================================
echo -e "${YELLOW}Test 8: Tester Agent Creating Test Result (Expected: NO VIOLATION)${NC}"
response=$(post_to_blackboard "tester-agent-2" "TEST_RESULT" "Unit Test Results" "All tests passed. Coverage: 85%")
violation=$(check_violations "tester-agent-2")

if [ "$violation" = "NOT_FOUND" ]; then
    print_result "Tester creating test result" "PASS" "No violation (correct behavior)"
else
    print_result "Tester creating test result" "FAIL" "False positive - violation detected incorrectly"
fi
echo ""

# ====================================================================
# Test 9: Planner creating REVIEW (VIOLATION)
# ====================================================================
echo -e "${YELLOW}Test 9: Planner Agent Creating Review (Expected: VIOLATION)${NC}"
response=$(post_to_blackboard "planner-agent-3" "REVIEW" "Architecture Review" "REQUIRES_CHANGES")
violation=$(check_violations "planner-agent-3")

if [ "$violation" = "FOUND" ]; then
    print_result "Planner creating review" "PASS" "Violation correctly detected"
else
    print_result "Planner creating review" "FAIL" "Violation should be detected but wasn't"
fi
echo ""

# ====================================================================
# Test 10: Tester creating TASK_BREAKDOWN (VIOLATION)
# ====================================================================
echo -e "${YELLOW}Test 10: Tester Agent Creating Task Breakdown (Expected: VIOLATION)${NC}"
response=$(post_to_blackboard "tester-agent-3" "TASK_BREAKDOWN" "Test Tasks" "1. Unit tests 2. Integration tests")
violation=$(check_violations "tester-agent-3")

if [ "$violation" = "FOUND" ]; then
    print_result "Tester creating task breakdown" "PASS" "Violation correctly detected"
else
    print_result "Tester creating task breakdown" "FAIL" "Violation should be detected but wasn't"
fi
echo ""

# ====================================================================
# Summary
# ====================================================================
echo "======================================================================"
echo "  Test Summary"
echo "======================================================================"
echo -e "Total Tests: ${BLUE}$TESTS_RUN${NC}"
echo -e "Passed: ${GREEN}$TESTS_PASSED${NC}"
echo -e "Failed: ${RED}$TESTS_FAILED${NC}"

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "\n${GREEN}✓ ALL TESTS PASSED!${NC}"
    echo ""
    exit 0
else
    PASS_RATE=$(echo "scale=2; $TESTS_PASSED * 100 / $TESTS_RUN" | bc)
    echo -e "\n${YELLOW}⚠ Pass Rate: $PASS_RATE%${NC}"
    echo ""
    exit 1
fi
