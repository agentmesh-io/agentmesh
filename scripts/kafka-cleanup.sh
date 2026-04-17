#!/bin/bash

# Kafka Cleanup Script
# This script removes stale broker registrations from Zookeeper before starting Kafka
# Usage: ./kafka-cleanup.sh

echo "Checking for stale Kafka broker registrations..."

# Wait for Zookeeper to be ready
MAX_RETRIES=30
RETRY_COUNT=0
while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
  if docker exec agentmesh-zookeeper zkCli.sh ls /brokers/ids 2>/dev/null | grep -q "\["; then
    echo "Zookeeper is ready"
    break
  fi
  echo "Waiting for Zookeeper... ($((RETRY_COUNT+1))/$MAX_RETRIES)"
  sleep 2
  RETRY_COUNT=$((RETRY_COUNT+1))
done

if [ $RETRY_COUNT -eq $MAX_RETRIES ]; then
  echo "Error: Zookeeper did not become ready in time"
  exit 1
fi

# Check if broker ID 1 is registered
BROKER_EXISTS=$(docker exec agentmesh-zookeeper zkCli.sh ls /brokers/ids 2>/dev/null | grep -o "1" || echo "")

if [ -n "$BROKER_EXISTS" ]; then
  echo "Stale broker registration found. Cleaning up..."
  docker exec agentmesh-zookeeper zkCli.sh delete /brokers/ids/1 2>/dev/null || true
  docker exec agentmesh-zookeeper zkCli.sh deleteall /controller 2>/dev/null || true
  echo "Cleanup completed"
else
  echo "No stale registrations found"
fi

echo "Ready to start Kafka"
