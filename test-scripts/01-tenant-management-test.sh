#!/bin/bash

# Test Script 1: Tenant Management Tests
# This script tests tenant creation, tier upgrades, and project management

set -e  # Exit on error

BASE_URL="http://localhost:8080"
TENANT_ID=""
PROJECT_ID=""

echo "=================================="
echo "Test 1: Tenant Management"
echo "=================================="
echo ""

# Cleanup: Remove test data from previous runs
echo "→ Cleanup: Removing test data from previous runs..."
CLEANUP_RESPONSE=$(curl -s -X DELETE "${BASE_URL}/api/tenants/cleanup?orgIdPattern=org-test-,org-upgrade-test" || echo "{\"deleted\":0}")
echo "✓ Cleanup complete"
echo ""

# Test 1.1: Create a new tenant (FREE tier)
echo "→ Test 1.1: Creating new tenant (FREE tier)..."
RESPONSE=$(curl -s -X POST ${BASE_URL}/api/tenants \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Organization",
    "organizationId": "org-test-001",
    "tier": "FREE"
  }')

echo "Response: $RESPONSE"
TENANT_ID=$(echo $RESPONSE | grep -o '"id":"[^"]*"' | cut -d'"' -f4)

if [ -z "$TENANT_ID" ]; then
  echo "✗ FAILED: Could not extract tenant ID"
  exit 1
else
  echo "✓ PASSED: Tenant created with ID: $TENANT_ID"
fi
echo ""

# Test 1.2: Get tenant by ID
echo "→ Test 1.2: Retrieving tenant by ID..."
RESPONSE=$(curl -s ${BASE_URL}/api/tenants/${TENANT_ID})
echo "Response: $RESPONSE"

if echo "$RESPONSE" | grep -q "$TENANT_ID"; then
  echo "✓ PASSED: Tenant retrieved successfully"
else
  echo "✗ FAILED: Could not retrieve tenant"
  exit 1
fi
echo ""

# Test 1.3: Get tenant by organization ID
echo "→ Test 1.3: Retrieving tenant by organization ID..."
RESPONSE=$(curl -s ${BASE_URL}/api/tenants/org/org-test-001)
echo "Response: $RESPONSE"

if echo "$RESPONSE" | grep -q "org-test-001"; then
  echo "✓ PASSED: Tenant retrieved by organization ID"
else
  echo "✗ FAILED: Could not retrieve tenant by organization ID"
  exit 1
fi
echo ""

# Test 1.4: Upgrade tenant to PREMIUM tier
echo "→ Test 1.4: Upgrading tenant to PREMIUM tier..."
RESPONSE=$(curl -s -X PUT ${BASE_URL}/api/tenants/${TENANT_ID}/tier \
  -H "Content-Type: application/json" \
  -d '{
    "tier": "PREMIUM"
  }')

echo "Response: $RESPONSE"

if echo "$RESPONSE" | grep -q "PREMIUM"; then
  echo "✓ PASSED: Tenant upgraded to PREMIUM tier"
else
  echo "✗ FAILED: Could not upgrade tenant tier"
  exit 1
fi
echo ""

# Test 1.5: Create first project
echo "→ Test 1.5: Creating first project..."
RESPONSE=$(curl -s -X POST ${BASE_URL}/api/tenants/${TENANT_ID}/projects \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Project 1",
    "projectKey": "proj-test-001",
    "description": "Test project for tenant management validation"
  }')

echo "Response: $RESPONSE"

if echo "$RESPONSE" | grep -q "proj-test-001"; then
  echo "✓ PASSED: First project created successfully"
else
  echo "✗ FAILED: Could not create first project"
  exit 1
fi
echo ""

# Test 1.6: Create second project
echo "→ Test 1.6: Creating second project..."
RESPONSE=$(curl -s -X POST ${BASE_URL}/api/tenants/${TENANT_ID}/projects \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Project 2",
    "projectKey": "proj-test-002",
    "description": "Second test project to verify tenant limits"
  }')

echo "Response: $RESPONSE"

if echo "$RESPONSE" | grep -q "proj-test-002"; then
  echo "✓ PASSED: Second project created successfully"
else
  echo "✗ FAILED: Could not create second project"
  exit 1
fi
echo ""

echo "=================================="
echo "All Tenant Management Tests PASSED"
echo "Tenant ID: $TENANT_ID"
echo "=================================="
echo ""

# Export for other test scripts
echo "export TEST_TENANT_ID=$TENANT_ID" > /tmp/agentmesh-test-vars.sh

