#!/bin/bash

###############################################################################
# AgentMesh Load Test Script
# Tests concurrent agent execution with various load levels
###############################################################################

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
GRAFANA_URL="${GRAFANA_URL:-http://localhost:3000}"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_header() {
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

# Setup
TENANT_ID="load-test-tenant-$(date +%s)"
PROJECT_ID="load-test-project"
RESULTS_DIR="load-test-results-$(date +%Y%m%d-%H%M%S)"

mkdir -p "$RESULTS_DIR"

print_header "AgentMesh Load Test"
echo "Target URL: $BASE_URL"
echo "Tenant ID: $TENANT_ID"
echo "Results Dir: $RESULTS_DIR"
echo ""

# Function to execute planner agent
execute_planner() {
    local instance=$1
    local correlation_id="load-test-${instance}-$(date +%s%N)"
    local start_time=$(date +%s%N)
    
    local response=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/agents/execute/planner" \
        -H "Content-Type: application/json" \
        -H "X-Correlation-ID: $correlation_id" \
        -H "X-Tenant-ID: $TENANT_ID" \
        -d "{
            \"tenantId\": \"$TENANT_ID\",
            \"projectId\": \"${PROJECT_ID}-${instance}\",
            \"userRequest\": \"Create a user management API with CRUD operations for user profile instance ${instance}\"
        }")
    
    local http_code=$(echo "$response" | tail -n 1)
    local body=$(echo "$response" | head -n -1)
    local end_time=$(date +%s%N)
    local duration=$(( (end_time - start_time) / 1000000 )) # Convert to ms
    
    if [ "$http_code" = "200" ]; then
        local status=$(echo "$body" | jq -r '.status // "unknown"')
        echo "$instance,$correlation_id,$duration,$http_code,$status" >> "$RESULTS_DIR/requests.csv"
        echo "✅ Instance $instance: ${duration}ms (Status: $status)"
    else
        echo "$instance,$correlation_id,$duration,$http_code,error" >> "$RESULTS_DIR/requests.csv"
        echo "❌ Instance $instance: ${duration}ms (HTTP $http_code)"
    fi
}

# Function to run concurrent tests
run_concurrent_test() {
    local concurrency=$1
    local name=$2
    
    print_header "Load Test: $name (Concurrency: $concurrency)"
    
    # Initialize results file
    echo "instance,correlation_id,duration_ms,http_code,status" > "$RESULTS_DIR/requests.csv"
    
    local start_time=$(date +%s)
    
    # Launch concurrent requests
    for i in $(seq 1 $concurrency); do
        execute_planner $i &
    done
    
    # Wait for all background jobs
    wait
    
    local end_time=$(date +%s)
    local total_duration=$((end_time - start_time))
    
    # Analyze results
    print_info "Analyzing results..."
    
    local total_requests=$(wc -l < "$RESULTS_DIR/requests.csv" | tr -d ' ')
    total_requests=$((total_requests - 1)) # Subtract header
    
    local successful=$(grep ",200," "$RESULTS_DIR/requests.csv" | wc -l | tr -d ' ')
    local failed=$((total_requests - successful))
    local success_rate=$(awk "BEGIN {printf \"%.1f\", ($successful/$total_requests)*100}")
    
    # Calculate latencies
    local min_latency=$(tail -n +2 "$RESULTS_DIR/requests.csv" | cut -d',' -f3 | sort -n | head -n1)
    local max_latency=$(tail -n +2 "$RESULTS_DIR/requests.csv" | cut -d',' -f3 | sort -n | tail -n1)
    local avg_latency=$(tail -n +2 "$RESULTS_DIR/requests.csv" | cut -d',' -f3 | awk '{sum+=$1; count++} END {printf "%.0f", sum/count}')
    
    # Calculate p95 and p99
    local p95_latency=$(tail -n +2 "$RESULTS_DIR/requests.csv" | cut -d',' -f3 | sort -n | awk '{all[NR] = $0} END{print all[int(NR*0.95)]}')
    local p99_latency=$(tail -n +2 "$RESULTS_DIR/requests.csv" | cut -d',' -f3 | sort -n | awk '{all[NR] = $0} END{print all[int(NR*0.99)]}')
    
    # Calculate throughput
    local throughput=$(awk "BEGIN {printf \"%.2f\", $total_requests/$total_duration}")
    
    # Print results
    echo ""
    print_header "Test Results: $name"
    echo "Duration:           ${total_duration}s"
    echo "Total Requests:     $total_requests"
    echo "Successful:         $successful"
    echo "Failed:             $failed"
    echo "Success Rate:       ${success_rate}%"
    echo "Throughput:         ${throughput} req/s"
    echo ""
    echo "Latency Stats:"
    echo "  Min:              ${min_latency}ms"
    echo "  Avg:              ${avg_latency}ms"
    echo "  Max:              ${max_latency}ms"
    echo "  P95:              ${p95_latency}ms"
    echo "  P99:              ${p99_latency}ms"
    echo ""
    
    # Check if targets met
    if [ "$failed" -eq 0 ]; then
        print_success "All requests successful!"
    else
        print_warning "Some requests failed: $failed/$total_requests"
    fi
    
    if [ "$p95_latency" -lt 5000 ]; then
        print_success "P95 latency < 5s target met (${p95_latency}ms)"
    else
        print_warning "P95 latency > 5s target (${p95_latency}ms)"
    fi
    
    # Save summary
    cat > "$RESULTS_DIR/summary_${concurrency}.txt" <<EOF
Load Test Summary: $name
Concurrency: $concurrency
Duration: ${total_duration}s
Total Requests: $total_requests
Successful: $successful
Failed: $failed
Success Rate: ${success_rate}%
Throughput: ${throughput} req/s

Latency:
  Min: ${min_latency}ms
  Avg: ${avg_latency}ms
  Max: ${max_latency}ms
  P95: ${p95_latency}ms
  P99: ${p99_latency}ms
EOF
    
    echo ""
    sleep 5
}

# Check services are up
print_info "Checking services..."
if ! curl -s "$BASE_URL/actuator/health" | grep -q "UP"; then
    print_error "AgentMesh is not healthy!"
    exit 1
fi
print_success "AgentMesh is UP"

if ! curl -s "$PROMETHEUS_URL/-/healthy" | grep -q "Prometheus"; then
    print_warning "Prometheus not available (monitoring disabled)"
else
    print_success "Prometheus is UP"
fi

if ! curl -s "$GRAFANA_URL/api/health" | grep -q "ok"; then
    print_warning "Grafana not available (monitoring disabled)"
else
    print_success "Grafana is UP"
fi

echo ""
print_info "Starting load tests in 5 seconds..."
sleep 5

# Run tests with different concurrency levels
run_concurrent_test 10 "Light Load"
run_concurrent_test 25 "Medium Load"
run_concurrent_test 50 "Heavy Load"

print_header "Load Test Complete!"
print_info "Results saved to: $RESULTS_DIR"
print_info ""
print_info "View metrics:"
print_info "  Prometheus: $PROMETHEUS_URL"
print_info "  Grafana: $GRAFANA_URL"
print_info ""
print_info "Useful queries:"
print_info "  Agent tasks: rate(agentmesh_agent_tasks_total[5m])"
print_info "  Latency p95: histogram_quantile(0.95, agentmesh_agent_execution_duration_seconds)"
print_info "  Success rate: rate(agentmesh_agent_tasks_success_total[5m]) / rate(agentmesh_agent_tasks_total[5m])"
