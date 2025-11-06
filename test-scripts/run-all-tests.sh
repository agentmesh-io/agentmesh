#!/bin/bash

# Master Test Runner
# Runs all test scripts in sequence

set -e  # Exit on error

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "╔════════════════════════════════════════╗"
echo "║   AgentMesh Test Suite Runner         ║"
echo "║   Running All Test Scenarios           ║"
echo "╚════════════════════════════════════════╝"
echo ""

# Check if services are running
echo "→ Checking if AgentMesh services are running..."
if ! docker ps | grep -q agentmesh-app; then
    echo "✗ ERROR: AgentMesh services are not running!"
    echo "  Please start services with: docker-compose up -d"
    exit 1
fi
echo "✓ Services are running"
echo ""

# Wait for services to be ready
echo "→ Waiting for services to be fully ready..."
sleep 5

MAX_RETRIES=30
RETRY_COUNT=0
while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
    if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo "✓ AgentMesh API is ready"
        break
    fi
    RETRY_COUNT=$((RETRY_COUNT + 1))
    echo "  Waiting for API... ($RETRY_COUNT/$MAX_RETRIES)"
    sleep 2
done

if [ $RETRY_COUNT -eq $MAX_RETRIES ]; then
    echo "✗ ERROR: AgentMesh API did not become ready in time"
    exit 1
fi
echo ""

# Run test scripts
TEST_SCRIPTS=(
    "01-tenant-management-test.sh"
    "02-agent-lifecycle-test.sh"
    "03-blackboard-test.sh"
    "04-memory-test.sh"
    "05-mast-test.sh"
)

PASSED=0
FAILED=0
TOTAL=${#TEST_SCRIPTS[@]}

for script in "${TEST_SCRIPTS[@]}"; do
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "Running: $script"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    if bash "${SCRIPT_DIR}/${script}"; then
        PASSED=$((PASSED + 1))
        echo "✓ $script PASSED"
    else
        FAILED=$((FAILED + 1))
        echo "✗ $script FAILED"
    fi
done

echo ""
echo "╔════════════════════════════════════════╗"
echo "║          TEST SUMMARY                  ║"
echo "╚════════════════════════════════════════╝"
echo "Total Tests:  $TOTAL"
echo "Passed:       $PASSED"
echo "Failed:       $FAILED"
echo ""

if [ $FAILED -eq 0 ]; then
    echo "🎉 All tests PASSED!"
    exit 0
else
    echo "⚠️  Some tests FAILED"
    exit 1
fi

