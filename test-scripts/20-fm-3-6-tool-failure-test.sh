#!/bin/bash

# Test Script for FM-3.6: Tool Execution Failure Detection
# Tests detection of tool/command execution failures

API_URL="http://localhost:8080"
TENANT_ID="test-tenant-fm-3-6-$(date +%s)"

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
        "SELECT COUNT(*) FROM mast_violations WHERE agent_id = '$agent_id' AND failure_mode = 'FM_3_6_TOOL_INVOCATION_FAILURE';" \
        2>/dev/null | tr -d ' ')
    
    if [ "$count" = "$expected_violations" ]; then
        echo -e "${GREEN}✓ PASS${NC}: $test_name (expected $expected_violations, got $count)"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        echo -e "${RED}✗ FAIL${NC}: $test_name (expected $expected_violations, got $count)"
    fi
}

echo -e "${YELLOW}=== FM-3.6 Tool Execution Failure Detection Tests ===${NC}\n"

# Test 1: Compilation error (VIOLATION)
echo -e "\n${YELLOW}Test 1: Compilation failure in build output${NC}"

post_to_blackboard "compiler-fail" "TOOL_OUTPUT" "Build Log" "Compilation failed with errors:
src/main/java/User.java:15: error: cannot find symbol
    String name = getName();
                  ^
  symbol:   method getName()
  location: class User
1 error"

check_test "Compilation error" "1" "compiler-fail"

# Test 2: Test failure (VIOLATION)
echo -e "\n${YELLOW}Test 2: Unit test failures${NC}"

post_to_blackboard "tester-fail" "TOOL_OUTPUT" "Test Results" "Tests run: 10, Failures: 3, Errors: 1
Test failed: testUserAuthentication
AssertionError: Expected <true> but was <false>
    at org.junit.Assert.fail(Assert.java:88)"

check_test "Test failure" "1" "tester-fail"

# Test 3: Maven build failure (VIOLATION)
echo -e "\n${YELLOW}Test 3: Maven build error${NC}"

post_to_blackboard "maven-fail" "TOOL_OUTPUT" "Maven Build" "[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:3.8.1:compile
[ERROR] Build failed with compilation errors
[ERROR] -> [Help 1]"

check_test "Maven build failure" "1" "maven-fail"

# Test 4: Successful build (NO VIOLATION)
echo -e "\n${YELLOW}Test 4: Successful compilation and build${NC}"

TENANT_ID_2="test-tenant-fm-3-6-success-$(date +%s)"

post_to_blackboard "builder-success" "TOOL_OUTPUT" "Build Success" "BUILD SUCCESS
Total time: 5.123 s
Finished at: 2025-11-09T10:00:00
All tests passed: 25/25" "$TENANT_ID_2"

check_test "Successful build (no violation)" "0" "builder-success"

# Test 5: Database connection error (VIOLATION)
echo -e "\n${YELLOW}Test 5: Database connection failure${NC}"

post_to_blackboard "db-connector-fail" "TOOL_OUTPUT" "DB Connection" "Database connection failed:
Connection refused: connect
java.sql.SQLException: Communications link failure
The last packet sent successfully to the server was 0 milliseconds ago."

check_test "Database connection error" "1" "db-connector-fail"

# Test 6: API HTTP error (VIOLATION)
echo -e "\n${YELLOW}Test 6: HTTP 500 error from API call${NC}"

post_to_blackboard "api-caller-fail" "TOOL_OUTPUT" "API Response" "API call failed with status code 500 Internal Server Error
Response: {\"error\": \"Database query timeout\"}"

check_test "HTTP error" "1" "api-caller-fail"

# Test 7: File not found error (VIOLATION)
echo -e "\n${YELLOW}Test 7: File I/O error${NC}"

post_to_blackboard "file-reader-fail" "TOOL_OUTPUT" "File Operation" "Failed to read configuration file:
java.io.FileNotFoundException: /config/app.properties (No such file or directory)
    at java.io.FileInputStream.open(Native Method)"

check_test "File not found error" "1" "file-reader-fail"

# Test 8: NullPointerException (VIOLATION)
echo -e "\n${YELLOW}Test 8: Runtime NullPointerException${NC}"

post_to_blackboard "runtime-fail" "CODE" "Error Log" "Application crashed with error:
Exception in thread \"main\" java.lang.NullPointerException
    at com.example.UserService.processUser(UserService.java:45)
    at com.example.Main.main(Main.java:12)"

check_test "NullPointerException" "1" "runtime-fail"

# Test 9: Deployment failure (VIOLATION)
echo -e "\n${YELLOW}Test 9: Kubernetes deployment failed${NC}"

post_to_blackboard "deployer-fail" "TOOL_OUTPUT" "K8s Deploy" "Deployment failed: Pod error
Error from server: container \"app\" in pod \"myapp-7d8f9c\" is waiting to start: 
ImagePullBackOff - Failed to pull image \"myapp:latest\""

check_test "Deployment failure" "1" "deployer-fail"

# Test 10: Permission denied (VIOLATION)
echo -e "\n${YELLOW}Test 10: Permission denied error${NC}"

post_to_blackboard "writer-fail" "TOOL_OUTPUT" "Script Output" "Failed to write to log file:
/var/log/app/application.log: Permission denied
Error code 13"

check_test "Permission denied" "1" "writer-fail"

# Test 11: Non-zero exit code (VIOLATION)
echo -e "\n${YELLOW}Test 11: Command failed with exit code${NC}"

post_to_blackboard "cmd-fail" "TOOL_OUTPUT" "Command Result" "Command execution failed
Process exited with error code 1
Returned status: 1"

check_test "Non-zero exit code" "1" "cmd-fail"

# Test 12: npm install failure (VIOLATION)
echo -e "\n${YELLOW}Test 12: npm dependency installation error${NC}"

post_to_blackboard "npm-fail" "TOOL_OUTPUT" "npm Install" "npm ERR! code ERESOLVE
npm ERR! ERESOLVE unable to resolve dependency tree
npm ERR! npm install failed with errors"

check_test "npm error" "1" "npm-fail"

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
