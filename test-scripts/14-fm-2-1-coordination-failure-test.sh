#!/bin/bash

# ====================================================================
# Test Script: FM-2.1 Coordination Failure Detection
# ====================================================================
# Tests MAST failure mode FM-2.1 which detects conflicting decisions
# between agents (e.g., one chooses REST, another chooses GraphQL).
#
# Detection Criteria:
# - Conflicting technology choices between agents
# - Mutually exclusive architectural decisions
# - Single entry containing conflicting patterns
# ====================================================================

BASE_URL="http://localhost:8080"
TENANT_ID="test-tenant-$(date +%s)"
PROJECT_ID="test-project-$(date +%s)"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

TESTS_RUN=0
TESTS_PASSED=0
TESTS_FAILED=0

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

check_violations() {
    local agent_id="$1"
    sleep 0.5
    local count=$(docker exec agentmesh-postgres psql -U agentmesh -d agentmesh -t -c "SELECT COUNT(*) FROM mast_violations WHERE agent_id = '$agent_id' AND failure_mode = 'FM_2_1_COORDINATION_FAILURE';" 2>/dev/null | tr -d ' ')
    
    if [ "$count" -gt 0 ]; then
        echo "FOUND"
    else
        echo "NOT_FOUND"
    fi
}

echo "======================================================================"
echo "  FM-2.1: Coordination Failure Detection Test Suite"
echo "======================================================================"
echo ""
echo -e "${BLUE}Configuration:${NC}"
echo "  Base URL: $BASE_URL"
echo "  Tenant ID: $TENANT_ID"
echo ""

if ! curl -s "$BASE_URL/actuator/health" > /dev/null; then
    echo -e "${RED}ERROR: AgentMesh is not running${NC}"
    exit 1
fi

echo -e "${GREEN}✓ AgentMesh is running${NC}"
echo ""

# ====================================================================
# Test 1: Conflicting API Choices - REST vs GraphQL (VIOLATION)
# ====================================================================
echo -e "${YELLOW}Test 1: Two Agents Choose Different APIs (Expected: VIOLATION)${NC}"
post_to_blackboard "planner-agent-api-1" "SRS" "API Design" "We will use REST API for all endpoints" > /dev/null
post_to_blackboard "planner-agent-api-2" "SRS" "API Design" "We will implement GraphQL for better querying" > /dev/null
violation=$(check_violations "planner-agent-api-2")

if [ "$violation" = "FOUND" ]; then
    print_result "REST vs GraphQL conflict" "PASS" "Conflict correctly detected"
else
    print_result "REST vs GraphQL conflict" "FAIL" "Conflict should be detected"
fi
echo ""

# ====================================================================
# Test 2: Consistent Technology Choice (NO VIOLATION)
# ====================================================================
echo -e "${YELLOW}Test 2: Multiple Agents Agree on Technology (Expected: NO VIOLATION)${NC}"
post_to_blackboard "planner-consistent-1" "SRS" "Backend Design" "Using REST API for backend services" > /dev/null
post_to_blackboard "planner-consistent-2" "SRS" "Frontend Design" "Frontend will consume REST API" > /dev/null
violation=$(check_violations "planner-consistent-2")

if [ "$violation" = "NOT_FOUND" ]; then
    print_result "Consistent REST choice" "PASS" "No conflict (correct)"
else
    print_result "Consistent REST choice" "FAIL" "False positive"
fi
echo ""

# ====================================================================
# Test 3: Microservices vs Monolith Conflict (VIOLATION)
# ====================================================================
echo -e "${YELLOW}Test 3: Architecture Conflict - Microservices vs Monolith (Expected: VIOLATION)${NC}"
post_to_blackboard "architect-1" "TASK_BREAKDOWN" "System Architecture" "Design microservices architecture for scalability" > /dev/null
post_to_blackboard "architect-2" "TASK_BREAKDOWN" "System Architecture" "Build monolith application for simplicity" > /dev/null
violation=$(check_violations "architect-2")

if [ "$violation" = "FOUND" ]; then
    print_result "Microservices vs Monolith" "PASS" "Conflict correctly detected"
else
    print_result "Microservices vs Monolith" "FAIL" "Conflict should be detected"
fi
echo ""

# ====================================================================
# Test 4: Database Conflict - SQL vs NoSQL (VIOLATION)
# ====================================================================
echo -e "${YELLOW}Test 4: Database Technology Conflict (Expected: VIOLATION)${NC}"
post_to_blackboard "db-designer-1" "SRS" "Database Requirements" "Use SQL database for ACID compliance" > /dev/null
post_to_blackboard "db-designer-2" "SRS" "Database Requirements" "Use NoSQL for flexible schema" > /dev/null
violation=$(check_violations "db-designer-2")

if [ "$violation" = "FOUND" ]; then
    print_result "SQL vs NoSQL conflict" "PASS" "Conflict correctly detected"
else
    print_result "SQL vs NoSQL conflict" "FAIL" "Conflict should be detected"
fi
echo ""

# ====================================================================
# Test 5: Single Entry with Conflicting Patterns (VIOLATION)
# ====================================================================
echo -e "${YELLOW}Test 5: Indecisive Entry with Both REST and GraphQL (Expected: VIOLATION)${NC}"
post_to_blackboard "confused-planner" "SRS" "API Strategy" "We could use REST API or maybe GraphQL would be better" > /dev/null
violation=$(check_violations "confused-planner")

if [ "$violation" = "FOUND" ]; then
    print_result "Indecisive single entry" "PASS" "Conflict correctly detected"
else
    print_result "Indecisive single entry" "FAIL" "Conflict should be detected"
fi
echo ""

# ====================================================================
# Test 6: Frontend Framework Conflict (VIOLATION)
# ====================================================================
echo -e "${YELLOW}Test 6: React vs Angular Conflict (Expected: VIOLATION)${NC}"
post_to_blackboard "frontend-dev-1" "CODE" "UI Framework" "Implementing components in React" > /dev/null
post_to_blackboard "frontend-dev-2" "CODE" "UI Framework" "Building Angular application" > /dev/null
violation=$(check_violations "frontend-dev-2")

if [ "$violation" = "FOUND" ]; then
    print_result "React vs Angular conflict" "PASS" "Conflict correctly detected"
else
    print_result "React vs Angular conflict" "FAIL" "Conflict should be detected"
fi
echo ""

# ====================================================================
# Test 7: Cloud Provider Conflict (VIOLATION)
# ====================================================================
echo -e "${YELLOW}Test 7: AWS vs Azure Conflict (Expected: VIOLATION)${NC}"
post_to_blackboard "devops-1" "TASK_BREAKDOWN" "Cloud Deployment" "Deploy to AWS using EC2 and RDS" > /dev/null
post_to_blackboard "devops-2" "TASK_BREAKDOWN" "Cloud Deployment" "Configure Azure VMs and SQL Database" > /dev/null
violation=$(check_violations "devops-2")

if [ "$violation" = "FOUND" ]; then
    print_result "AWS vs Azure conflict" "PASS" "Conflict correctly detected"
else
    print_result "AWS vs Azure conflict" "FAIL" "Conflict should be detected"
fi
echo ""

# ====================================================================
# Test 8: Same Agent Exploring Options (NO VIOLATION)
# ====================================================================
echo -e "${YELLOW}Test 8: Single Agent Comparing Options (Expected: NO VIOLATION)${NC}"
post_to_blackboard "single-researcher" "SRS" "Option A" "REST API provides simplicity" > /dev/null
post_to_blackboard "single-researcher" "SRS" "Option B" "GraphQL offers flexibility" > /dev/null
violation=$(check_violations "single-researcher")

if [ "$violation" = "NOT_FOUND" ]; then
    print_result "Same agent exploring" "PASS" "No conflict (single agent OK)"
else
    print_result "Same agent exploring" "FAIL" "False positive - same agent comparing options"
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
    echo -e "\n${GREEN}✓ ALL TESTS PASSED!${NC}\n"
    exit 0
else
    PASS_RATE=$(echo "scale=2; $TESTS_PASSED * 100 / $TESTS_RUN" | bc)
    echo -e "\n${YELLOW}⚠ Pass Rate: $PASS_RATE%${NC}\n"
    exit 1
fi
