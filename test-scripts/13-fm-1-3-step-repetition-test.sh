#!/bin/bash

# ====================================================================
# Test Script: FM-1.3 Step Repetition Detection
# ====================================================================
# Tests MAST failure mode FM-1.3 which detects when agents repeat
# the same action multiple times (infinite loops).
#
# Detection Criteria:
# - 3+ consecutive identical actions, OR
# - Same action appears 4+ times in recent history (10 actions)
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

# Function to create blackboard entry
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
    local count=$(docker exec agentmesh-postgres psql -U agentmesh -d agentmesh -t -c "SELECT COUNT(*) FROM mast_violations WHERE agent_id = '$agent_id' AND failure_mode = 'FM_1_3_STEP_REPETITION';" 2>/dev/null | tr -d ' ')
    
    if [ "$count" -gt 0 ]; then
        echo "FOUND"
    else
        echo "NOT_FOUND"
    fi
}

echo "======================================================================"
echo "  FM-1.3: Step Repetition Detection Test Suite"
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
# Test 1: No Repetition - Varied Actions (NO VIOLATION)
# ====================================================================
echo -e "${YELLOW}Test 1: Agent Performing Varied Actions (Expected: NO VIOLATION)${NC}"
post_to_blackboard "agent-varied-1" "SRS" "Requirement 1" "Content 1" > /dev/null
post_to_blackboard "agent-varied-1" "SRS" "Requirement 2" "Content 2" > /dev/null
post_to_blackboard "agent-varied-1" "TASK_BREAKDOWN" "Task A" "Content A" > /dev/null
violation=$(check_violations "agent-varied-1")

if [ "$violation" = "NOT_FOUND" ]; then
    print_result "Varied actions" "PASS" "No violation (correct behavior)"
else
    print_result "Varied actions" "FAIL" "False positive - violation detected incorrectly"
fi
echo ""

# ====================================================================
# Test 2: 3 Consecutive Identical Actions (VIOLATION)
# ====================================================================
echo -e "${YELLOW}Test 2: Agent Repeating Same Action 3 Times Consecutively (Expected: VIOLATION)${NC}"
post_to_blackboard "agent-loop-1" "CODE" "UserService" "class UserService {}" > /dev/null
post_to_blackboard "agent-loop-1" "CODE" "UserService" "class UserService {}" > /dev/null
post_to_blackboard "agent-loop-1" "CODE" "UserService" "class UserService {}" > /dev/null
violation=$(check_violations "agent-loop-1")

if [ "$violation" = "FOUND" ]; then
    print_result "3 consecutive repetitions" "PASS" "Violation correctly detected"
else
    print_result "3 consecutive repetitions" "FAIL" "Violation should be detected but wasn't"
fi
echo ""

# ====================================================================
# Test 3: 4 Total Repetitions (Non-consecutive) (VIOLATION)
# ====================================================================
echo -e "${YELLOW}Test 3: Same Action 4 Times in Recent History (Expected: VIOLATION)${NC}"
post_to_blackboard "agent-loop-2" "SRS" "Requirements" "Req 1" > /dev/null
post_to_blackboard "agent-loop-2" "TASK_BREAKDOWN" "Tasks" "Task 1" > /dev/null
post_to_blackboard "agent-loop-2" "SRS" "Requirements" "Req 2" > /dev/null
post_to_blackboard "agent-loop-2" "TASK_BREAKDOWN" "Tasks" "Task 2" > /dev/null
post_to_blackboard "agent-loop-2" "SRS" "Requirements" "Req 3" > /dev/null
post_to_blackboard "agent-loop-2" "SRS" "Requirements" "Req 4" > /dev/null
violation=$(check_violations "agent-loop-2")

if [ "$violation" = "FOUND" ]; then
    print_result "4 total repetitions" "PASS" "Violation correctly detected"
else
    print_result "4 total repetitions" "FAIL" "Violation should be detected but wasn't"
fi
echo ""

# ====================================================================
# Test 4: 2 Repetitions Only (NO VIOLATION)
# ====================================================================
echo -e "${YELLOW}Test 4: Only 2 Repetitions (Expected: NO VIOLATION)${NC}"
post_to_blackboard "agent-ok-1" "CODE" "Service" "class Service {}" > /dev/null
post_to_blackboard "agent-ok-1" "CODE" "Service" "class Service {}" > /dev/null
violation=$(check_violations "agent-ok-1")

if [ "$violation" = "NOT_FOUND" ]; then
    print_result "2 repetitions only" "PASS" "No violation (under threshold)"
else
    print_result "2 repetitions only" "FAIL" "False positive - under threshold"
fi
echo ""

# ====================================================================
# Test 5: 5 Consecutive Repetitions (VIOLATION)
# ====================================================================
echo -e "${YELLOW}Test 5: Agent Stuck in Loop - 5 Repetitions (Expected: VIOLATION)${NC}"
for i in {1..5}; do
    post_to_blackboard "agent-stuck-1" "REVIEW" "Code Review" "REQUIRES_CHANGES" > /dev/null
done
violation=$(check_violations "agent-stuck-1")

if [ "$violation" = "FOUND" ]; then
    print_result "5 consecutive repetitions" "PASS" "Violation correctly detected"
else
    print_result "5 consecutive repetitions" "FAIL" "Violation should be detected but wasn't"
fi
echo ""

# ====================================================================
# Test 6: Different Entry Types, Same Title (NO VIOLATION)
# ====================================================================
echo -e "${YELLOW}Test 6: Different Entry Types (Expected: NO VIOLATION)${NC}"
post_to_blackboard "agent-ok-2" "SRS" "Authentication" "Requirements" > /dev/null
post_to_blackboard "agent-ok-2" "CODE" "Authentication" "class Auth {}" > /dev/null
post_to_blackboard "agent-ok-2" "TEST_RESULT" "Authentication" "Tests passed" > /dev/null
violation=$(check_violations "agent-ok-2")

if [ "$violation" = "NOT_FOUND" ]; then
    print_result "Different entry types" "PASS" "No violation (different types)"
else
    print_result "Different entry types" "FAIL" "False positive - different types"
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
