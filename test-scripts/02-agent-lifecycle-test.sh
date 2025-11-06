#!/bin/bash

# Test Script 2: Agent Lifecycle Tests
# This script tests agent creation, state management, and inter-agent communication

set -e  # Exit on error

BASE_URL="http://localhost:8080"

echo "=================================="
echo "Test 2: Agent Lifecycle Management"
echo "=================================="
echo ""

# Test 2.1: Create agents
echo "→ Test 2.1: Creating specialized agents..."

echo "  Creating Planner Agent..."
RESPONSE=$(curl -s -X POST "${BASE_URL}/api/agents?id=planner-test-001")
if echo "$RESPONSE" | grep -q "planner-test-001"; then
  echo "  ✓ Planner agent created"
else
  echo "  ✗ FAILED: Could not create planner agent"
  exit 1
fi

echo "  Creating Coder Agent..."
RESPONSE=$(curl -s -X POST "${BASE_URL}/api/agents?id=coder-test-001")
if echo "$RESPONSE" | grep -q "coder-test-001"; then
  echo "  ✓ Coder agent created"
else
  echo "  ✗ FAILED: Could not create coder agent"
  exit 1
fi

echo "  Creating Reviewer Agent..."
RESPONSE=$(curl -s -X POST "${BASE_URL}/api/agents?id=reviewer-test-001")
if echo "$RESPONSE" | grep -q "reviewer-test-001"; then
  echo "  ✓ Reviewer agent created"
else
  echo "  ✗ FAILED: Could not create reviewer agent"
  exit 1
fi

echo "  Creating Debugger Agent..."
RESPONSE=$(curl -s -X POST "${BASE_URL}/api/agents?id=debugger-test-001")
if echo "$RESPONSE" | grep -q "debugger-test-001"; then
  echo "  ✓ Debugger agent created"
else
  echo "  ✗ FAILED: Could not create debugger agent"
  exit 1
fi

echo "✓ PASSED: All agents created successfully"
echo ""

# Test 2.2: List all agents
echo "→ Test 2.2: Listing all agents..."
RESPONSE=$(curl -s ${BASE_URL}/api/agents)
echo "Response: $RESPONSE"

AGENT_COUNT=$(echo "$RESPONSE" | grep -o "planner-test-001\|coder-test-001\|reviewer-test-001\|debugger-test-001" | wc -l)
if [ "$AGENT_COUNT" -ge 4 ]; then
  echo "✓ PASSED: All agents listed (found $AGENT_COUNT agents)"
else
  echo "✗ FAILED: Not all agents found in list"
  exit 1
fi
echo ""

# Test 2.3: Start agents
echo "→ Test 2.3: Starting agents..."

echo "  Starting Planner Agent..."
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST ${BASE_URL}/api/agents/planner-test-001/start)
if [ "$STATUS" = "200" ]; then
  echo "  ✓ Planner agent started"
else
  echo "  ✗ FAILED: Could not start planner agent (HTTP $STATUS)"
  exit 1
fi

echo "  Starting Coder Agent..."
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST ${BASE_URL}/api/agents/coder-test-001/start)
if [ "$STATUS" = "200" ]; then
  echo "  ✓ Coder agent started"
else
  echo "  ✗ FAILED: Could not start coder agent (HTTP $STATUS)"
  exit 1
fi

echo "  Starting Reviewer Agent..."
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST ${BASE_URL}/api/agents/reviewer-test-001/start)
if [ "$STATUS" = "200" ]; then
  echo "  ✓ Reviewer agent started"
else
  echo "  ✗ FAILED: Could not start reviewer agent (HTTP $STATUS)"
  exit 1
fi

echo "  Starting Debugger Agent..."
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST ${BASE_URL}/api/agents/debugger-test-001/start)
if [ "$STATUS" = "200" ]; then
  echo "  ✓ Debugger agent started"
else
  echo "  ✗ FAILED: Could not start debugger agent (HTTP $STATUS)"
  exit 1
fi

echo "✓ PASSED: All agents started successfully"
echo ""

# Test 2.4: Inter-agent communication
echo "→ Test 2.4: Testing inter-agent communication..."

echo "  Planner → Coder: Sending task..."
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST ${BASE_URL}/api/agents/message \
  -H "Content-Type: application/json" \
  -d '{
    "fromAgentId": "planner-test-001",
    "toAgentId": "coder-test-001",
    "payload": "Implement user authentication with JWT tokens"
  }')

if [ "$STATUS" = "200" ]; then
  echo "  ✓ Message sent from Planner to Coder"
else
  echo "  ✗ FAILED: Could not send message (HTTP $STATUS)"
  exit 1
fi

echo "  Coder → Reviewer: Sending code for review..."
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST ${BASE_URL}/api/agents/message \
  -H "Content-Type: application/json" \
  -d '{
    "fromAgentId": "coder-test-001",
    "toAgentId": "reviewer-test-001",
    "payload": "Authentication module completed. Please review AuthController.java"
  }')

if [ "$STATUS" = "200" ]; then
  echo "  ✓ Message sent from Coder to Reviewer"
else
  echo "  ✗ FAILED: Could not send message (HTTP $STATUS)"
  exit 1
fi

echo "  Reviewer → Debugger: Reporting issue..."
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST ${BASE_URL}/api/agents/message \
  -H "Content-Type: application/json" \
  -d '{
    "fromAgentId": "reviewer-test-001",
    "toAgentId": "debugger-test-001",
    "payload": "Found issue: Missing input validation in login endpoint"
  }')

if [ "$STATUS" = "200" ]; then
  echo "  ✓ Message sent from Reviewer to Debugger"
else
  echo "  ✗ FAILED: Could not send message (HTTP $STATUS)"
  exit 1
fi

echo "✓ PASSED: Inter-agent communication working"
echo ""

# Test 2.5: View message log
echo "→ Test 2.5: Retrieving message log..."
RESPONSE=$(curl -s ${BASE_URL}/api/agents/messages)
echo "Message log: $RESPONSE"

MESSAGE_COUNT=$(echo "$RESPONSE" | grep -o "fromAgentId" | wc -l)
if [ "$MESSAGE_COUNT" -ge 3 ]; then
  echo "✓ PASSED: Message log contains all messages (found $MESSAGE_COUNT messages)"
else
  echo "✗ FAILED: Message log incomplete"
  exit 1
fi
echo ""

# Test 2.6: Stop an agent
echo "→ Test 2.6: Stopping an agent..."
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST ${BASE_URL}/api/agents/coder-test-001/stop)
if [ "$STATUS" = "200" ]; then
  echo "✓ PASSED: Agent stopped successfully"
else
  echo "✗ FAILED: Could not stop agent (HTTP $STATUS)"
  exit 1
fi
echo ""

# Test 2.7: Test error handling - non-existent agent
echo "→ Test 2.7: Testing error handling (non-existent agent)..."
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST ${BASE_URL}/api/agents/non-existent/start)
if [ "$STATUS" = "404" ]; then
  echo "✓ PASSED: Correctly returns 404 for non-existent agent"
else
  echo "✗ FAILED: Expected 404, got HTTP $STATUS"
  exit 1
fi
echo ""

echo "=================================="
echo "All Agent Lifecycle Tests PASSED"
echo "=================================="
echo ""

