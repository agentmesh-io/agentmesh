#!/bin/bash

# Test Script for FM-3.3: Format Violation Detection
# Tests detection of format issues in agent output

API_URL="http://localhost:8080"
TENANT_ID="test-tenant-fm-3-3-$(date +%s)"

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
        "SELECT COUNT(*) FROM mast_violations WHERE agent_id = '$agent_id' AND failure_mode = 'FM_3_3_FORMAT_VIOLATION';" \
        2>/dev/null | tr -d ' ')
    
    if [ "$count" = "$expected_violations" ]; then
        echo -e "${GREEN}✓ PASS${NC}: $test_name (expected $expected_violations, got $count)"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        echo -e "${RED}✗ FAIL${NC}: $test_name (expected $expected_violations, got $count)"
    fi
}

echo -e "${YELLOW}=== FM-3.3 Format Violation Detection Tests ===${NC}\n"

# Test 1: Malformed JSON - unbalanced braces (VIOLATION)
echo -e "\n${YELLOW}Test 1: JSON with missing closing brace${NC}"

post_to_blackboard "coder-json-bad-1" "CODE" "Config File" '{
  "database": {
    "host": "localhost",
    "port": 5432
  "security": {
    "enabled": true
  }
}'

check_test "Unbalanced JSON braces" "1" "coder-json-bad-1"

# Test 2: JSON with trailing comma (VIOLATION)
echo -e "\n${YELLOW}Test 2: JSON with trailing comma${NC}"

post_to_blackboard "coder-json-bad-2" "CODE" "API Response" '{
  "status": "success",
  "data": [1, 2, 3,],
  "count": 3
}'

check_test "Trailing comma in JSON" "1" "coder-json-bad-2"

# Test 3: SRS missing required sections (VIOLATION)
echo -e "\n${YELLOW}Test 3: SRS without requirements or objectives${NC}"

post_to_blackboard "analyst-bad-srs" "SRS" "Product Specification" "This document describes the system. It will have many features and be very useful for users."

check_test "SRS missing required sections" "1" "analyst-bad-srs"

# Test 4: Valid SRS with requirements (NO VIOLATION)
echo -e "\n${YELLOW}Test 4: Proper SRS with requirements${NC}"

TENANT_ID_2="test-tenant-fm-3-3-good-srs-$(date +%s)"

post_to_blackboard "analyst-good-srs" "SRS" "Complete Specification" "The system has the following functional requirements: 1. User authentication 2. Data processing. Objectives: Improve efficiency and reduce errors." "$TENANT_ID_2"

check_test "Valid SRS format" "0" "analyst-good-srs"

# Test 5: Task breakdown without tasks (VIOLATION)
echo -e "\n${YELLOW}Test 5: Task breakdown missing task structure${NC}"

post_to_blackboard "planner-bad" "TASK_BREAKDOWN" "Project Plan" "We need to build the system carefully and test it thoroughly."

check_test "Task breakdown without tasks" "1" "planner-bad"

# Test 6: Proper task breakdown (NO VIOLATION)
echo -e "\n${YELLOW}Test 6: Valid task breakdown with numbered steps${NC}"

TENANT_ID_3="test-tenant-fm-3-3-good-tasks-$(date +%s)"

post_to_blackboard "planner-good" "TASK_BREAKDOWN" "Implementation Tasks" "1. Set up database schema. 2. Implement API endpoints. 3. Create frontend components. 4. Write integration tests." "$TENANT_ID_3"

check_test "Valid task breakdown" "0" "planner-good"

# Test 7: Malformed URL (VIOLATION)
echo -e "\n${YELLOW}Test 7: Code with malformed URL${NC}"

post_to_blackboard "coder-bad-url" "CODE" "API Client" "Connect to the API at https:/api.example.com/data (note: missing slash after protocol)"

check_test "Malformed URL format" "1" "coder-bad-url"

# Test 8: Invalid version numbers (VIOLATION)
echo -e "\n${YELLOW}Test 8: Suspiciously high version numbers${NC}"

post_to_blackboard "coder-bad-version" "CODE" "Dependencies" "Using library version 123.456.789 for the project."

check_test "Invalid version numbers" "1" "coder-bad-version"

# Test 9: Placeholder text in code (VIOLATION)
echo -e "\n${YELLOW}Test 9: Code with TODO placeholders${NC}"

post_to_blackboard "coder-placeholder" "CODE" "Authentication Module" "public class AuthService {
    public boolean authenticate(String user, String pass) {
        // TODO: Implement actual authentication logic
        return false;
    }
}"

check_test "Placeholder text in code" "1" "coder-placeholder"

# Test 10: Unclosed code block (VIOLATION)
echo -e "\n${YELLOW}Test 10: Markdown with unclosed code block${NC}"

post_to_blackboard "documenter-bad" "CODE" "API Documentation" "Here is the API usage:

\`\`\`java
public void callApi() {
    // Call the API
}

The method returns void."

check_test "Unclosed code block" "1" "documenter-bad"

# Test 11: Function without parentheses (VIOLATION)
echo -e "\n${YELLOW}Test 11: Function declaration missing parentheses${NC}"

post_to_blackboard "coder-bad-func" "CODE" "Utils Module" "public static String calculateHash {
    return hash;
}"

check_test "Function missing parentheses" "1" "coder-bad-func"

# Test 12: Well-formatted code (NO VIOLATION)
echo -e "\n${YELLOW}Test 12: Properly formatted code${NC}"

TENANT_ID_4="test-tenant-fm-3-3-good-code-$(date +%s)"

post_to_blackboard "coder-good" "CODE" "Service Implementation" "public class UserService {
    public User getUser(Long id) {
        return userRepository.findById(id).orElse(null);
    }
}" "$TENANT_ID_4"

check_test "Well-formatted code" "0" "coder-good"

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
