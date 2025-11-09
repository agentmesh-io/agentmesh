#!/bin/bash

# Test Script for FM-2.4: State Inconsistency Detection
# Tests detection of conflicting state views across agents

API_URL="http://localhost:8080"
TENANT_ID="test-tenant-fm-2-4-$(date +%s)"

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
        "SELECT COUNT(*) FROM mast_violations WHERE agent_id = '$agent_id' AND failure_mode = 'FM_2_4_STATE_INCONSISTENCY';" \
        2>/dev/null | tr -d ' ')
    
    if [ "$count" = "$expected_violations" ]; then
        echo -e "${GREEN}✓ PASS${NC}: $test_name (expected $expected_violations, got $count)"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        echo -e "${RED}✗ FAIL${NC}: $test_name (expected $expected_violations, got $count)"
    fi
}

echo -e "${YELLOW}=== FM-2.4 State Inconsistency Detection Tests ===${NC}\n"

# Test 1: Conflicting Java versions (VIOLATION)
echo -e "\n${YELLOW}Test 1: Different agents mention conflicting Java versions${NC}"

post_to_blackboard "planner-java-11" "SRS" "Backend Requirements" "The backend system will use Java 11 for compatibility with existing infrastructure."

sleep 1

post_to_blackboard "implementer-java-17" "CODE" "Backend Implementation" "Implementing the service using Java 17 features including records and sealed classes."

check_test "Conflicting Java versions" "1" "implementer-java-17"

# Test 2: Conflicting Spring Boot versions (VIOLATION)
echo -e "\n${YELLOW}Test 2: Different Spring Boot versions mentioned${NC}"

post_to_blackboard "architect-sb2" "SRS" "Framework Selection" "We will use Spring Boot 2 for stability and long-term support."

sleep 1

post_to_blackboard "developer-sb3" "CODE" "Spring Configuration" "Configuring Spring Boot 3 application with native compilation support."

check_test "Conflicting Spring Boot versions" "1" "developer-sb3"

# Test 3: Conflicting API styles (VIOLATION)
echo -e "\n${YELLOW}Test 3: Conflicting API implementation approaches${NC}"

post_to_blackboard "api-designer-rest" "SRS" "API Design" "The system will expose a REST API for all client integrations."

sleep 1

post_to_blackboard "api-impl-graphql" "CODE" "API Implementation" "Implementing the GraphQL API with Apollo Server for flexible querying."

check_test "Conflicting API styles" "1" "api-impl-graphql"

# Test 4: Consistent technology choices (NO VIOLATION)
echo -e "\n${YELLOW}Test 4: Agents agreeing on same technology stack${NC}"

TENANT_ID_2="test-tenant-fm-2-4-consistent-$(date +%s)"

post_to_blackboard "planner-consistent" "SRS" "Tech Stack" "The project will use Java 17, Spring Boot 3, and REST API." "$TENANT_ID_2"

sleep 1

post_to_blackboard "implementer-consistent" "CODE" "Implementation" "Building the REST API with Java 17 and Spring Boot 3 as specified." "$TENANT_ID_2"

check_test "Consistent tech stack" "0" "implementer-consistent"

# Test 5: Conflicting architecture patterns (VIOLATION)
echo -e "\n${YELLOW}Test 5: Conflicting architecture decisions${NC}"

post_to_blackboard "architect-microservices" "SRS" "Architecture" "We will adopt a microservices architecture for scalability and independent deployment."

sleep 1

post_to_blackboard "lead-monolith" "TASK_BREAKDOWN" "Implementation Plan" "Breaking down the monolith application into modules for easier development."

check_test "Conflicting architectures" "1" "lead-monolith"

# Test 6: Conflicting database choices (VIOLATION)
echo -e "\n${YELLOW}Test 6: Different database technology mentioned${NC}"

post_to_blackboard "db-designer-sql" "SRS" "Data Layer" "The application will use SQL database (PostgreSQL) for ACID compliance."

sleep 1

post_to_blackboard "db-impl-nosql" "CODE" "Database Setup" "Setting up NoSQL database (MongoDB) for flexible schema and horizontal scaling."

check_test "Conflicting databases" "1" "db-impl-nosql"

# Test 7: Same agent exploring options (NO VIOLATION)
echo -e "\n${YELLOW}Test 7: Single agent exploring multiple options${NC}"

TENANT_ID_3="test-tenant-fm-2-4-exploration-$(date +%s)"

post_to_blackboard "researcher-options" "SRS" "Option 1" "Could use microservices for better scalability." "$TENANT_ID_3"

sleep 1

post_to_blackboard "researcher-options" "SRS" "Option 2" "Alternative monolith approach would simplify deployment." "$TENANT_ID_3"

check_test "Same agent exploring (no conflict)" "0" "researcher-options"

# Test 8: Conflicting deployment targets (VIOLATION)
echo -e "\n${YELLOW}Test 8: Different cloud providers mentioned${NC}"

post_to_blackboard "devops-aws" "SRS" "Infrastructure" "We will deploy on AWS using EKS for Kubernetes orchestration."

sleep 1

post_to_blackboard "devops-azure" "CODE" "Deployment Config" "Configuring Azure AKS for container deployment."

check_test "Conflicting cloud providers" "1" "devops-azure"

# Test 9: Conflicting synchronization patterns (VIOLATION)
echo -e "\n${YELLOW}Test 9: Different communication patterns${NC}"

post_to_blackboard "architect-sync" "SRS" "Communication" "Services will communicate using synchronous REST calls."

sleep 1

post_to_blackboard "impl-async" "CODE" "Event Handler" "Implementing asynchronous event-driven communication between services."

check_test "Conflicting communication patterns" "1" "impl-async"

# Test 10: Conflicting React versions (VIOLATION)
echo -e "\n${YELLOW}Test 10: Different frontend framework versions${NC}"

post_to_blackboard "frontend-lead-17" "SRS" "Frontend Stack" "The UI will be built with React 17 for stability."

sleep 1

post_to_blackboard "ui-dev-18" "CODE" "Component Library" "Creating components using React 18 concurrent rendering features."

check_test "Conflicting React versions" "1" "ui-dev-18"

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
