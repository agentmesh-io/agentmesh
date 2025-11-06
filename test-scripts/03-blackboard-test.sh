#!/bin/bash

# Test Script 3: Blackboard Architecture Tests
# This script tests the shared blackboard for agent collaboration

set -e  # Exit on error

BASE_URL="http://localhost:8080"
ENTRY_ID=""

echo "=================================="
echo "Test 3: Blackboard Architecture"
echo "=================================="
echo ""

# Test 3.1: Post task breakdown
echo "→ Test 3.1: Planner posts task breakdown..."
RESPONSE=$(curl -s -X POST "${BASE_URL}/api/blackboard/entries?agentId=planner-test-001&entryType=TASK_BREAKDOWN&title=User%20Authentication%20Feature" \
  -H "Content-Type: text/plain" \
  -d 'Feature: User Authentication
Tasks:
1. Design JWT token structure
2. Implement AuthController with login/logout endpoints
3. Create JwtService for token generation and validation
4. Configure Spring Security
5. Write unit tests (80% coverage minimum)')

echo "Response: $RESPONSE"
ENTRY_ID=$(echo $RESPONSE | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')

if [ ! -z "$ENTRY_ID" ]; then
  echo "✓ PASSED: Task breakdown posted (Entry ID: $ENTRY_ID)"
else
  echo "✗ FAILED: Could not post task breakdown"
  exit 1
fi
echo ""

# Test 3.2: Post code artifact
echo "→ Test 3.2: Coder posts generated code..."
RESPONSE=$(curl -s -X POST "${BASE_URL}/api/blackboard/entries?agentId=coder-test-001&entryType=CODE&title=AuthController.java" \
  -H "Content-Type: text/plain" \
  --data-binary @- <<'EOF'
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    private JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        String token = jwtService.generateToken(request.getUsername());
        return ResponseEntity.ok(new LoginResponse(token));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.ok().build();
    }
}
EOF
)

echo "Response: $RESPONSE"

if echo "$RESPONSE" | grep -q "AuthController.java"; then
  echo "✓ PASSED: Code artifact posted"
else
  echo "✗ FAILED: Could not post code artifact"
  exit 1
fi
echo ""

# Test 3.3: Post code review
echo "→ Test 3.3: Reviewer posts code review..."
RESPONSE=$(curl -s -X POST "${BASE_URL}/api/blackboard/entries?agentId=reviewer-test-001&entryType=REVIEW&title=AuthController%20Review" \
  -H "Content-Type: text/plain" \
  -d 'Code Review Results:
✓ Code follows Spring Boot conventions
✓ Proper REST endpoint structure
✓ Clean separation of concerns
⚠ Missing: Input validation for LoginRequest
⚠ Missing: Rate limiting annotation
⚠ Missing: Proper exception handling
✗ Security: No @PreAuthorize annotations
✗ Missing: Audit logging

Overall: REQUIRES_CHANGES
Priority: HIGH (Security issues)')

echo "Response: $RESPONSE"

if echo "$RESPONSE" | grep -q "AuthController Review"; then
  echo "✓ PASSED: Code review posted"
else
  echo "✗ FAILED: Could not post code review"
  exit 1
fi
echo ""

# Test 3.4: Post test results
echo "→ Test 3.4: Debugger posts test results..."
RESPONSE=$(curl -s -X POST "${BASE_URL}/api/blackboard/entries?agentId=debugger-test-001&entryType=TEST_RESULT&title=AuthController%20Unit%20Tests" \
  -H "Content-Type: text/plain" \
  -d 'Unit Test Execution Results:
Total Tests: 8
Passed: 6
Failed: 2
Coverage: 72%

Failed Tests:
1. testLoginWithInvalidCredentials - Expected 401, got 500
2. testLogoutWithExpiredToken - NullPointerException

Action Required: Fix exception handling')

echo "Response: $RESPONSE"

if echo "$RESPONSE" | grep -q "Unit Test Execution"; then
  echo "✓ PASSED: Test results posted"
else
  echo "✗ FAILED: Could not post test results"
  exit 1
fi
echo ""

# Test 3.5: Query all entries
echo "→ Test 3.5: Retrieving all blackboard entries..."
RESPONSE=$(curl -s ${BASE_URL}/api/blackboard/entries)
ENTRY_COUNT=$(echo "$RESPONSE" | grep -o '"id":' | wc -l)

if [ "$ENTRY_COUNT" -ge 4 ]; then
  echo "✓ PASSED: Retrieved all entries (found $ENTRY_COUNT entries)"
else
  echo "✗ FAILED: Expected at least 4 entries, found $ENTRY_COUNT"
  exit 1
fi
echo ""

# Test 3.6: Query entries by type
echo "→ Test 3.6: Querying entries by type (CODE)..."
RESPONSE=$(curl -s ${BASE_URL}/api/blackboard/entries/type/CODE)
echo "Response: $RESPONSE"

if echo "$RESPONSE" | grep -q "AuthController.java"; then
  echo "✓ PASSED: Retrieved CODE entries"
else
  echo "✗ FAILED: Could not retrieve CODE entries"
  exit 1
fi
echo ""

# Test 3.7: Query entries by agent
echo "→ Test 3.7: Querying entries by agent (coder-test-001)..."
RESPONSE=$(curl -s ${BASE_URL}/api/blackboard/entries/agent/coder-test-001)
echo "Response: $RESPONSE"

if echo "$RESPONSE" | grep -q "coder-test-001"; then
  echo "✓ PASSED: Retrieved entries by agent"
else
  echo "✗ FAILED: Could not retrieve entries by agent"
  exit 1
fi
echo ""

# Test 3.8: Get specific entry
echo "→ Test 3.8: Retrieving specific entry by ID..."
RESPONSE=$(curl -s ${BASE_URL}/api/blackboard/entries/${ENTRY_ID})
echo "Response: $RESPONSE"

if echo "$RESPONSE" | grep -q "\"id\":$ENTRY_ID"; then
  echo "✓ PASSED: Retrieved specific entry"
else
  echo "✗ FAILED: Could not retrieve specific entry"
  exit 1
fi
echo ""

# Test 3.9: Update an entry
echo "→ Test 3.9: Updating blackboard entry..."
RESPONSE=$(curl -s -X PUT ${BASE_URL}/api/blackboard/entries/${ENTRY_ID} \
  -H "Content-Type: text/plain" \
  -d 'Feature: User Authentication (UPDATED)
Tasks:
1. Design JWT token structure - COMPLETED
2. Implement AuthController - IN_PROGRESS
3. Create JwtService - PENDING
4. Configure Spring Security - PENDING
5. Write unit tests - PENDING')

echo "Response: $RESPONSE"

if echo "$RESPONSE" | grep -q "UPDATED"; then
  echo "✓ PASSED: Entry updated successfully"
else
  echo "✗ FAILED: Could not update entry"
  exit 1
fi
echo ""

# Test 3.10: Create snapshot
echo "→ Test 3.10: Creating blackboard snapshot..."
RESPONSE=$(curl -s -X POST ${BASE_URL}/api/blackboard/snapshot)
echo "Response: $RESPONSE"

if echo "$RESPONSE" | grep -q "timestamp"; then
  echo "✓ PASSED: Snapshot created successfully"
else
  echo "✗ FAILED: Could not create snapshot"
  exit 1
fi
echo ""

# Test 3.11: Post concurrent entries (stress test)
echo "→ Test 3.11: Testing concurrent entry posting..."
for i in {1..5}; do
  curl -s -X POST "${BASE_URL}/api/blackboard/entries?agentId=stress-test&entryType=TEST&title=Concurrent%20Entry%20$i" \
    -H "Content-Type: text/plain" \
    -d "Concurrent test entry number $i" > /dev/null &
done
wait

sleep 1  # Allow time for processing

RESPONSE=$(curl -s ${BASE_URL}/api/blackboard/entries/type/TEST)
CONCURRENT_COUNT=$(echo "$RESPONSE" | grep -o "Concurrent Entry" | wc -l)

if [ "$CONCURRENT_COUNT" -ge 5 ]; then
  echo "✓ PASSED: Concurrent entries posted successfully (found $CONCURRENT_COUNT)"
else
  echo "✗ FAILED: Some concurrent entries were lost (found $CONCURRENT_COUNT/5)"
  exit 1
fi
echo ""

echo "=================================="
echo "All Blackboard Tests PASSED"
echo "=================================="
echo ""

