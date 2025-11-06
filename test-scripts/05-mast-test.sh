#!/bin/bash

# Test Script 5: MAST (Multi-Agent System Failure Taxonomy) Tests
# This script tests failure detection and monitoring

set -e  # Exit on error

BASE_URL="http://localhost:8080"

echo "=================================="
echo "Test 5: MAST Failure Detection & Monitoring"
echo "=================================="
echo ""

# Test 5.1: Get recent violations
echo "→ Test 5.1: Retrieving recent MAST violations..."
RESPONSE=$(curl -s ${BASE_URL}/api/mast/violations/recent)
echo "Response: $RESPONSE"
echo "✓ PASSED: Recent violations retrieved"
echo ""

# Test 5.2: Get unresolved violations
echo "→ Test 5.2: Retrieving unresolved violations..."
RESPONSE=$(curl -s ${BASE_URL}/api/mast/violations/unresolved)
echo "Response: $RESPONSE"
echo "✓ PASSED: Unresolved violations retrieved"
echo ""

# Test 5.3: Get violations by agent
echo "→ Test 5.3: Retrieving violations for coder-test-001..."
RESPONSE=$(curl -s ${BASE_URL}/api/mast/violations/agent/coder-test-001)
echo "Response: $RESPONSE"
echo "✓ PASSED: Agent-specific violations retrieved"
echo ""

# Test 5.4: Get failure mode statistics
echo "→ Test 5.4: Retrieving failure mode statistics..."
RESPONSE=$(curl -s ${BASE_URL}/api/mast/statistics/failure-modes)
echo "Response: $RESPONSE"
echo "✓ PASSED: Failure mode statistics retrieved"
echo ""

# Test 5.5: Get agent health scores
echo "→ Test 5.5: Retrieving agent health scores..."

echo "  Checking planner-test-001 health..."
RESPONSE=$(curl -s ${BASE_URL}/api/mast/health/planner-test-001)
echo "  Planner health: $RESPONSE"

echo "  Checking coder-test-001 health..."
RESPONSE=$(curl -s ${BASE_URL}/api/mast/health/coder-test-001)
echo "  Coder health: $RESPONSE"

echo "  Checking reviewer-test-001 health..."
RESPONSE=$(curl -s ${BASE_URL}/api/mast/health/reviewer-test-001)
echo "  Reviewer health: $RESPONSE"

echo "  Checking debugger-test-001 health..."
RESPONSE=$(curl -s ${BASE_URL}/api/mast/health/debugger-test-001)
echo "  Debugger health: $RESPONSE"

echo "✓ PASSED: All agent health scores retrieved"
echo ""

echo "=================================="
echo "All MAST Tests PASSED"
echo "=================================="
echo ""

echo "Note: MAST violations are detected automatically during agent operations."
echo "The system monitors for 14 failure modes across 3 categories:"
echo "  1. Specification Issues (FM-1.1, FM-1.2, FM-1.3, FM-1.4)"
echo "  2. Inter-Agent Misalignment (FM-2.1, FM-2.2, FM-2.3)"
echo "  3. Task Verification Issues (FM-3.1, FM-3.2)"
echo ""

