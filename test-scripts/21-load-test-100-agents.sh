#!/bin/bash

# 100-Agent Load Test
# Tests AgentMesh performance under high concurrent load with:
# - 100 concurrent agents
# - Connection pool monitoring
# - Redis cache metrics
# - MAST detection performance
# - Throughput and latency measurements

set -e

BASE_URL="http://localhost:8080"
TENANT_ID="loadtest-tenant"
PROJECT_ID="loadtest-project"
NUM_AGENTS=100
ITERATIONS_PER_AGENT=10
CONCURRENT_REQUESTS=20

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}100-Agent Load Test${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Check if services are running
echo -e "${YELLOW}Checking service availability...${NC}"
if ! curl -s "${BASE_URL}/actuator/health" > /dev/null; then
    echo -e "${RED}Error: AgentMesh service not available at ${BASE_URL}${NC}"
    exit 1
fi

if ! curl -s "${BASE_URL}/actuator/health" | grep -q '"status":"UP"'; then
    echo -e "${RED}Error: AgentMesh service is not healthy${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Services are up and healthy${NC}"

# Create tenant
echo ""
echo -e "${YELLOW}Setting up test tenant...${NC}"
TENANT_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/tenants" \
    -H "Content-Type: application/json" \
    -d "{
        \"id\": \"${TENANT_ID}\",
        \"name\": \"Load Test Tenant\",
        \"tier\": \"ENTERPRISE\",
        \"dataRegion\": \"us-east-1\"
    }")

if echo "$TENANT_RESPONSE" | grep -q '"id"'; then
    echo -e "${GREEN}✓ Tenant created: ${TENANT_ID}${NC}"
else
    echo -e "${YELLOW}! Tenant may already exist${NC}"
fi

# Create project
PROJECT_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/projects?tenantId=${TENANT_ID}" \
    -H "Content-Type: application/json" \
    -d "{
        \"name\": \"Load Test Project\",
        \"description\": \"100-agent load testing\",
        \"vcsProvider\": \"github\",
        \"projectManagementProvider\": \"github-projects\"
    }")

if echo "$PROJECT_RESPONSE" | grep -q '"id"'; then
    echo -e "${GREEN}✓ Project created: ${PROJECT_ID}${NC}"
else
    echo -e "${YELLOW}! Project may already exist${NC}"
fi

# Create 100 agents
echo ""
echo -e "${YELLOW}Creating ${NUM_AGENTS} agents...${NC}"
AGENT_IDS=()
for i in $(seq 1 $NUM_AGENTS); do
    AGENT_ID="loadtest-agent-${i}"
    
    AGENT_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/agents?tenantId=${TENANT_ID}&projectId=${PROJECT_ID}" \
        -H "Content-Type: application/json" \
        -d "{
            \"id\": \"${AGENT_ID}\",
            \"name\": \"Load Test Agent ${i}\",
            \"role\": \"Developer\",
            \"capabilities\": [\"CODE\", \"TEST\", \"REVIEW\"],
            \"status\": \"IDLE\"
        }")
    
    if echo "$AGENT_RESPONSE" | grep -q '"id"'; then
        AGENT_IDS+=("$AGENT_ID")
        if [ $((i % 20)) -eq 0 ]; then
            echo -e "${GREEN}  Created $i agents...${NC}"
        fi
    else
        echo -e "${RED}Failed to create agent ${i}${NC}"
    fi
done
echo -e "${GREEN}✓ Created ${#AGENT_IDS[@]} agents${NC}"

# Function to post blackboard entries for an agent
post_blackboard_entries() {
    local agent_id=$1
    local iterations=$2
    local successes=0
    local failures=0
    local total_time=0
    
    for j in $(seq 1 $iterations); do
        local start_time=$(date +%s%N)
        
        local entry_type=$([ $((j % 3)) -eq 0 ] && echo "CODE" || echo "TASK")
        local content="Agent ${agent_id} iteration ${j} - $(date +%s)"
        
        local response=$(curl -s -w "\n%{http_code}" -X POST \
            "${BASE_URL}/api/blackboard?tenantId=${TENANT_ID}&projectId=${PROJECT_ID}" \
            -H "Content-Type: application/json" \
            -d "{
                \"agentId\": \"${agent_id}\",
                \"entryType\": \"${entry_type}\",
                \"content\": \"${content}\",
                \"metadata\": {}
            }")
        
        local http_code=$(echo "$response" | tail -n 1)
        local end_time=$(date +%s%N)
        local elapsed=$((($end_time - $start_time) / 1000000)) # Convert to ms
        
        total_time=$(($total_time + $elapsed))
        
        if [ "$http_code" = "200" ] || [ "$http_code" = "201" ]; then
            successes=$(($successes + 1))
        else
            failures=$(($failures + 1))
        fi
    done
    
    local avg_latency=$(($total_time / $iterations))
    echo "${agent_id},${successes},${failures},${avg_latency}"
}

# Export function for parallel execution
export -f post_blackboard_entries
export BASE_URL TENANT_ID PROJECT_ID

# Run load test
echo ""
echo -e "${YELLOW}Starting load test...${NC}"
echo -e "${BLUE}Configuration:${NC}"
echo -e "  Agents: ${NUM_AGENTS}"
echo -e "  Iterations per agent: ${ITERATIONS_PER_AGENT}"
echo -e "  Total requests: $((NUM_AGENTS * ITERATIONS_PER_AGENT))"
echo -e "  Concurrent workers: ${CONCURRENT_REQUESTS}"
echo ""

# Capture initial metrics
INITIAL_POOL=$(curl -s "${BASE_URL}/actuator/health" | grep -o '"connectionPool":{[^}]*}' || echo "")

LOAD_TEST_START=$(date +%s)

# Run agents in parallel batches
RESULTS_FILE="/tmp/agentmesh-load-test-results.txt"
> "$RESULTS_FILE"

echo -e "${YELLOW}Running concurrent load test...${NC}"
for i in $(seq 0 $CONCURRENT_REQUESTS $((NUM_AGENTS - 1))); do
    batch_end=$((i + CONCURRENT_REQUESTS))
    if [ $batch_end -gt $NUM_AGENTS ]; then
        batch_end=$NUM_AGENTS
    fi
    
    # Run batch in parallel
    for j in $(seq $i $(($batch_end - 1))); do
        agent_id="${AGENT_IDS[$j]}"
        post_blackboard_entries "$agent_id" "$ITERATIONS_PER_AGENT" >> "$RESULTS_FILE" &
    done
    
    # Wait for batch to complete
    wait
    
    echo -e "${GREEN}  Completed batch: agents $i to $(($batch_end - 1))${NC}"
done

LOAD_TEST_END=$(date +%s)
TOTAL_DURATION=$((LOAD_TEST_END - LOAD_TEST_START))

# Capture final metrics
FINAL_POOL=$(curl -s "${BASE_URL}/actuator/health" | grep -o '"connectionPool":{[^}]*}' || echo "")

# Analyze results
echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Load Test Results${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

TOTAL_SUCCESSES=0
TOTAL_FAILURES=0
TOTAL_LATENCY=0
AGENT_COUNT=0

while IFS=',' read -r agent_id successes failures avg_latency; do
    TOTAL_SUCCESSES=$(($TOTAL_SUCCESSES + $successes))
    TOTAL_FAILURES=$(($TOTAL_FAILURES + $failures))
    TOTAL_LATENCY=$(($TOTAL_LATENCY + $avg_latency))
    AGENT_COUNT=$(($AGENT_COUNT + 1))
done < "$RESULTS_FILE"

AVG_LATENCY=$(($TOTAL_LATENCY / $AGENT_COUNT))
THROUGHPUT=$(($TOTAL_SUCCESSES / $TOTAL_DURATION))

echo -e "${YELLOW}Performance Metrics:${NC}"
echo -e "  Total Duration: ${TOTAL_DURATION}s"
echo -e "  Successful Requests: ${GREEN}${TOTAL_SUCCESSES}${NC}"
echo -e "  Failed Requests: ${RED}${TOTAL_FAILURES}${NC}"
echo -e "  Average Latency: ${AVG_LATENCY}ms"
echo -e "  Throughput: ${THROUGHPUT} req/s"
echo ""

# Check connection pool health
echo -e "${YELLOW}Connection Pool Metrics:${NC}"
POOL_HEALTH=$(curl -s "${BASE_URL}/actuator/health/connectionPool" || echo "{}")
echo "$POOL_HEALTH" | grep -o '"[^"]*":[^,}]*' | while read line; do
    echo -e "  $line"
done
echo ""

# Check MAST violations
echo -e "${YELLOW}MAST Detection Results:${NC}"
VIOLATIONS=$(curl -s "${BASE_URL}/api/mast/violations/unresolved?tenantId=${TENANT_ID}&projectId=${PROJECT_ID}" || echo "[]")
VIOLATION_COUNT=$(echo "$VIOLATIONS" | grep -o '"id"' | wc -l)
echo -e "  Detected Violations: ${VIOLATION_COUNT}"

if [ $VIOLATION_COUNT -gt 0 ]; then
    echo ""
    echo -e "${YELLOW}Top 5 Violations:${NC}"
    echo "$VIOLATIONS" | head -5
fi
echo ""

# Performance assessment
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Assessment${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

if [ $TOTAL_FAILURES -eq 0 ]; then
    echo -e "${GREEN}✓ All requests successful${NC}"
else
    FAILURE_RATE=$((100 * $TOTAL_FAILURES / ($TOTAL_SUCCESSES + $TOTAL_FAILURES)))
    echo -e "${RED}✗ Failure rate: ${FAILURE_RATE}%${NC}"
fi

if [ $AVG_LATENCY -lt 1000 ]; then
    echo -e "${GREEN}✓ Excellent latency: ${AVG_LATENCY}ms (< 1s)${NC}"
elif [ $AVG_LATENCY -lt 3000 ]; then
    echo -e "${YELLOW}! Acceptable latency: ${AVG_LATENCY}ms (1-3s)${NC}"
else
    echo -e "${RED}✗ High latency: ${AVG_LATENCY}ms (> 3s)${NC}"
fi

if [ $THROUGHPUT -gt 50 ]; then
    echo -e "${GREEN}✓ High throughput: ${THROUGHPUT} req/s${NC}"
elif [ $THROUGHPUT -gt 20 ]; then
    echo -e "${YELLOW}! Moderate throughput: ${THROUGHPUT} req/s${NC}"
else
    echo -e "${RED}✗ Low throughput: ${THROUGHPUT} req/s${NC}"
fi

echo ""
echo -e "${BLUE}Load test complete!${NC}"
echo ""

# Cleanup
rm -f "$RESULTS_FILE"
