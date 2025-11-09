#!/bin/bash

# Test Script for FM-3.4: Hallucination Detection
# Tests detection of references to non-existent entities

API_URL="http://localhost:8080"
TENANT_ID="test-tenant-fm-3-4-$(date +%s)"

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
        "SELECT COUNT(*) FROM mast_violations WHERE agent_id = '$agent_id' AND failure_mode = 'FM_3_4_HALLUCINATION';" \
        2>/dev/null | tr -d ' ')
    
    if [ "$count" = "$expected_violations" ]; then
        echo -e "${GREEN}✓ PASS${NC}: $test_name (expected $expected_violations, got $count)"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        echo -e "${RED}✗ FAIL${NC}: $test_name (expected $expected_violations, got $count)"
    fi
}

echo -e "${YELLOW}=== FM-3.4 Hallucination Detection Tests ===${NC}\n"

# Test 1: Reference to non-existent file (VIOLATION)
echo -e "\n${YELLOW}Test 1: Code references non-existent file${NC}"

post_to_blackboard "coder-hallucinate-file" "CODE" "Import Statement" "import { UserService } from 'src/services/UserService.ts';
// This file was never created in prior entries"

check_test "Non-existent file reference" "1" "coder-hallucinate-file"

# Test 2: Reference to existing file (NO VIOLATION)
echo -e "\n${YELLOW}Test 2: Code references previously defined file${NC}"

TENANT_ID_2="test-tenant-fm-3-4-existing-file-$(date +%s)"

# First, create the file
post_to_blackboard "coder-create-file" "CODE" "User Service" "// File: src/services/AuthService.java
public class AuthService {
    public boolean authenticate(String user, String pass) {
        return true;
    }
}" "$TENANT_ID_2"

sleep 1

# Then reference it
post_to_blackboard "coder-use-file" "CODE" "Import Auth" "import src/services/AuthService.java;
// Using the previously created file" "$TENANT_ID_2"

check_test "Existing file reference (no violation)" "0" "coder-use-file"

# Test 3: Reference to non-existent class (VIOLATION)
echo -e "\n${YELLOW}Test 3: Code uses undefined class${NC}"

post_to_blackboard "coder-hallucinate-class" "CODE" "Service Usage" "public class OrderController {
    private OrderProcessor processor = new OrderProcessor();
    // OrderProcessor was never defined
}"

check_test "Non-existent class reference" "1" "coder-hallucinate-class"

# Test 4: Reference to existing class (NO VIOLATION)
echo -e "\n${YELLOW}Test 4: Code uses previously defined class${NC}"

TENANT_ID_3="test-tenant-fm-3-4-existing-class-$(date +%s)"

# Define the class
post_to_blackboard "coder-define-class" "CODE" "Payment Processor" "public class PaymentProcessor {
    public void processPayment(double amount) {
        System.out.println(\"Processing: \" + amount);
    }
}" "$TENANT_ID_3"

sleep 1

# Use it
post_to_blackboard "coder-use-class" "CODE" "Payment Service" "public class PaymentService {
    private PaymentProcessor processor = new PaymentProcessor();
}" "$TENANT_ID_3"

check_test "Existing class reference (no violation)" "0" "coder-use-class"

# Test 5: Reference to non-existent API endpoint (VIOLATION)
echo -e "\n${YELLOW}Test 5: Code references undefined API endpoint${NC}"

post_to_blackboard "coder-hallucinate-api" "CODE" "API Client" "fetch('/api/users/premium/settings')
    .then(response => response.json());
// This endpoint was never defined in SRS"

check_test "Non-existent API endpoint" "1" "coder-hallucinate-api"

# Test 6: Reference to defined API endpoint (NO VIOLATION)
echo -e "\n${YELLOW}Test 6: Code uses API endpoint from SRS${NC}"

TENANT_ID_4="test-tenant-fm-3-4-existing-api-$(date +%s)"

# Define API in SRS
post_to_blackboard "analyst-define-api" "SRS" "API Specification" "The system exposes the following REST API:
- GET /api/products - List all products
- POST /api/products - Create new product
- DELETE /api/products/{id} - Delete product" "$TENANT_ID_4"

sleep 1

# Use it in code
post_to_blackboard "coder-use-api" "CODE" "Product Client" "async function getProducts() {
    const response = await fetch('/api/products');
    return response.json();
}" "$TENANT_ID_4"

check_test "Existing API endpoint (no violation)" "0" "coder-use-api"

# Test 7: Reference to non-existent requirement (VIOLATION)
echo -e "\n${YELLOW}Test 7: Code implements non-existent requirement${NC}"

post_to_blackboard "coder-hallucinate-req" "CODE" "Feature Implementation" "// Implementing REQ-999: Advanced AI-powered analytics
public void advancedAnalytics() {
    // This requirement doesn't exist in SRS
}"

check_test "Non-existent requirement reference" "1" "coder-hallucinate-req"

# Test 8: Reference to defined requirement (NO VIOLATION)
echo -e "\n${YELLOW}Test 8: Code implements requirement from SRS${NC}"

TENANT_ID_5="test-tenant-fm-3-4-existing-req-$(date +%s)"

# Define requirement
post_to_blackboard "analyst-define-req" "SRS" "Requirements" "REQ-101: User authentication with JWT tokens
REQ-102: Password reset functionality
REQ-103: Session timeout after 30 minutes" "$TENANT_ID_5"

sleep 1

# Implement it
post_to_blackboard "coder-impl-req" "CODE" "Auth Implementation" "// Implementing REQ-101: User authentication with JWT
public String authenticate(String username, String password) {
    return jwtService.generateToken(username);
}" "$TENANT_ID_5"

check_test "Existing requirement (no violation)" "0" "coder-impl-req"

# Test 9: Future date reference (VIOLATION)
echo -e "\n${YELLOW}Test 9: Code mentions impossible future year${NC}"

post_to_blackboard "coder-future-date" "CODE" "Release Notes" "// This feature will be released in 2030
// Planned for version 2030.1.0"

check_test "Future year reference" "1" "coder-future-date"

# Test 10: Large nonsensical number (VIOLATION)
echo -e "\n${YELLOW}Test 10: Code contains suspiciously large number${NC}"

post_to_blackboard "coder-large-number" "CODE" "Configuration" "public static final int MAX_CONNECTIONS = 9999999999;
// This is likely a hallucinated value"

check_test "Nonsensical large number" "1" "coder-large-number"

# Test 11: Standard library class (NO VIOLATION)
echo -e "\n${YELLOW}Test 11: Code uses standard library classes${NC}"

TENANT_ID_6="test-tenant-fm-3-4-stdlib-$(date +%s)"

post_to_blackboard "coder-stdlib" "CODE" "Utils" "import java.util.ArrayList;
import java.util.HashMap;

public class DataProcessor {
    private List<String> items = new ArrayList<>();
    private Map<String, Integer> counts = new HashMap<>();
}" "$TENANT_ID_6"

check_test "Standard library classes (no violation)" "0" "coder-stdlib"

# Test 12: Reviewing non-existent code (VIOLATION)
echo -e "\n${YELLOW}Test 12: Review references non-existent code${NC}"

post_to_blackboard "reviewer-hallucinate" "REVIEW" "Code Review" "The implementation of the DatabaseConnectionPool class looks good.
Consider adding connection timeout handling.
// DatabaseConnectionPool was never created"

check_test "Review of non-existent code" "1" "reviewer-hallucinate"

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
