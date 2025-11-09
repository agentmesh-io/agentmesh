#!/bin/bash

# Test Script for FM-3.5: Timeout Detection
# Tests detection of timeout/stalled conditions in agent execution

API_URL="http://localhost:8080"
TENANT_ID="test-tenant-fm-3-5-$(date +%s)"

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
    local tenant_override="${5:-$TENANT_ID}"
    
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
        "SELECT COUNT(*) FROM mast_violations WHERE agent_id = '$agent_id' AND failure_mode = 'FM_3_5_TIMEOUT';" \
        2>/dev/null | tr -d ' ')
    
    if [ "$count" = "$expected_violations" ]; then
        echo -e "${GREEN}✓ PASS${NC}: $test_name (expected $expected_violations, got $count)"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        echo -e "${RED}✗ FAIL${NC}: $test_name (expected $expected_violations, got $count)"
    fi
}

echo -e "${YELLOW}=== FM-3.5 Timeout Detection Tests ===${NC}\n"

# Test 1: Explicit timeout mention (VIOLATION)
echo -e "\n${YELLOW}Test 1: Entry explicitly mentions timeout${NC}"

post_to_blackboard "worker-timeout-1" "TOOL_OUTPUT" "Build Failed" "Build failed with error: Connection timeout while downloading dependencies from Maven Central."

check_test "Explicit timeout mention" "1" "worker-timeout-1"

# Test 2: Stuck/hanging indication (VIOLATION)
echo -e "\n${YELLOW}Test 2: Entry mentions agent is stuck${NC}"

post_to_blackboard "worker-stuck" "STATUS" "Current State" "The process appears to be stuck at the database migration step. It's been hanging for over 20 minutes."

check_test "Stuck/hanging indication" "1" "worker-stuck"

# Test 3: Multiple "in progress" without deliverables (VIOLATION)
echo -e "\n${YELLOW}Test 3: Repeated 'in progress' messages without actual work${NC}"

# Post several "in progress" messages
post_to_blackboard "worker-stalled" "STATUS" "Progress Update 1" "Still working on the authentication module, in progress."
sleep 1
post_to_blackboard "worker-stalled" "STATUS" "Progress Update 2" "The authentication work is in progress, making progress."
sleep 1
post_to_blackboard "worker-stalled" "STATUS" "Progress Update 3" "Continuing to work on authentication, still processing the requirements."
sleep 1
post_to_blackboard "worker-stalled" "STATUS" "Progress Update 4" "Authentication module work is in progress."

check_test "Multiple in-progress without deliverables" "1" "worker-stalled"

# Test 4: In progress with actual deliverables (NO VIOLATION)
echo -e "\n${YELLOW}Test 4: In progress messages with actual code deliverables${NC}"

TENANT_ID_2="test-tenant-fm-3-5-good-$(date +%s)"

post_to_blackboard "worker-productive" "STATUS" "Starting Work" "Working on user service implementation." "$TENANT_ID_2"
sleep 1
post_to_blackboard "worker-productive" "CODE" "User Service" "public class UserService { /* implementation */ }" "$TENANT_ID_2"
sleep 1
post_to_blackboard "worker-productive" "STATUS" "Progress" "Still working on the service, in progress." "$TENANT_ID_2"
sleep 1
post_to_blackboard "worker-productive" "CODE" "User Repository" "public class UserRepository { /* implementation */ }" "$TENANT_ID_2"

check_test "In-progress with deliverables (no violation)" "0" "worker-productive"

# Test 5: Timeout exception in error message (VIOLATION)
echo -e "\n${YELLOW}Test 5: Timeout exception in code output${NC}"

post_to_blackboard "coder-timeout" "TOOL_OUTPUT" "Test Results" "Test execution failed:
java.net.SocketTimeoutException: Read timed out
    at java.net.SocketInputStream.read()
    at java.net.HttpURLConnection.getInputStream()"

check_test "Timeout exception in output" "1" "coder-timeout"

# Test 6: Unresponsive/not responding mention (VIOLATION)
echo -e "\n${YELLOW}Test 6: Entry mentions unresponsive system${NC}"

post_to_blackboard "tester-timeout" "TEST_RESULT" "Integration Test" "The API endpoint is not responding. Request has been waiting for 5 minutes with no response."

check_test "Unresponsive system mention" "1" "tester-timeout"

# Test 7: Deadline exceeded (VIOLATION)
echo -e "\n${YELLOW}Test 7: Entry mentions deadline exceeded${NC}"

post_to_blackboard "planner-late" "STATUS" "Sprint Update" "Task completion deadline exceeded. The module was supposed to be done 2 hours ago but still not finished."

check_test "Deadline exceeded" "1" "planner-late"

# Test 8: Normal progress updates (NO VIOLATION)
echo -e "\n${YELLOW}Test 8: Normal progress updates without timeout indicators${NC}"

TENANT_ID_3="test-tenant-fm-3-5-normal-$(date +%s)"

post_to_blackboard "worker-normal" "STATUS" "Update" "Completed the authentication module. Moving on to authorization next." "$TENANT_ID_3"
sleep 1
post_to_blackboard "worker-normal" "CODE" "Auth Module" "public class AuthModule { /* completed code */ }" "$TENANT_ID_3"

check_test "Normal progress (no violation)" "0" "worker-normal"

# Test 9: Connection timeout error (VIOLATION)
echo -e "\n${YELLOW}Test 9: Connection timeout in error logs${NC}"

post_to_blackboard "deployer-timeout" "TOOL_OUTPUT" "Deployment Log" "ERROR: Deployment failed - connection timeout while connecting to production database server after 30 seconds."

check_test "Connection timeout error" "1" "deployer-timeout"

# Test 10: Execution timed out (VIOLATION)
echo -e "\n${YELLOW}Test 10: Execution timeout in tool output${NC}"

post_to_blackboard "runner-timeout" "TOOL_OUTPUT" "Script Output" "Script execution timed out after 300 seconds. The long-running query did not complete in the allowed time."

check_test "Execution timed out" "1" "runner-timeout"

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
