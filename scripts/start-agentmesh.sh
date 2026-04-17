#!/bin/bash

# AgentMesh Startup Script with Kafka Fix
# This script ensures clean startup by removing stale Kafka registrations
# Usage: ./start-agentmesh.sh

set -e

echo "========================================"
echo "  AgentMesh Infrastructure Startup"
echo "========================================"

# Check if Docker is running
if ! docker info >/dev/null 2>&1; then
  echo "Error: Docker is not running. Please start Docker Desktop."
  exit 1
fi

cd "$(dirname "$0")"

# Stop existing containers
echo ""
echo "Step 1: Stopping existing containers..."
docker-compose down

# Start Zookeeper first
echo ""
echo "Step 2: Starting Zookeeper..."
docker-compose up -d zookeeper

# Wait for Zookeeper to be ready
echo "Waiting for Zookeeper to be ready..."
sleep 15
echo "✓ Zookeeper should be ready"

# Clean stale Kafka registrations
echo ""
echo "Step 3: Cleaning stale Kafka registrations..."
sleep 2
BROKER_CHECK=$(docker exec agentmesh-zookeeper zkCli.sh ls /brokers/ids 2>/dev/null | grep -o "\[.*\]" || echo "[]")
if echo "$BROKER_CHECK" | grep -q "1"; then
  echo "Found stale broker registration, cleaning up..."
  docker exec agentmesh-zookeeper zkCli.sh delete /brokers/ids/1 2>/dev/null || true
  docker exec agentmesh-zookeeper zkCli.sh deleteall /controller 2>/dev/null || true
  sleep 2
  echo "✓ Cleanup completed"
else
  echo "✓ No stale registrations found"
fi

# Start all remaining services
echo ""
echo "Step 4: Starting all services..."
docker-compose up -d

# Wait for services to be healthy
echo ""
echo "Step 5: Waiting for services to be healthy..."
sleep 10

# Check service health
echo ""
echo "Service Status:"
echo "---------------"
docker-compose ps

echo ""
echo "========================================"
echo "  AgentMesh Infrastructure Started"
echo "========================================"
echo ""
echo "Services:"
echo "  - AgentMesh API:  http://localhost:8080"
echo "  - Temporal UI:    http://localhost:8088"
echo "  - Weaviate:       http://localhost:8081"
echo "  - Kafka:          localhost:9092"
echo "  - PostgreSQL:     localhost:5432"
echo "  - Redis:          localhost:6379"
echo ""
echo "To view logs: docker-compose logs -f [service-name]"
echo "To stop:      docker-compose down"
echo ""
