#!/bin/bash

# AgentMesh Restart Script
# Handles port conflicts, Kafka state issues, and clean service restart

set -e

echo "🔄 AgentMesh Clean Restart Script"
echo "=================================="
echo ""

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Step 1: Stop all services
echo -e "${BLUE}Step 1: Stopping all AgentMesh services...${NC}"
docker-compose down 2>/dev/null || true
echo -e "${GREEN}✓ Services stopped${NC}"
echo ""

# Step 2: Check for and stop external conflicting containers
echo -e "${BLUE}Step 2: Checking for port conflicts...${NC}"

# Check for righthand containers
RIGHTHAND_CONTAINERS=$(docker ps -q --filter "name=righthand" 2>/dev/null || true)
if [ ! -z "$RIGHTHAND_CONTAINERS" ]; then
    echo -e "${YELLOW}Found righthand containers using ports 5432/6379${NC}"
    echo -e "${YELLOW}Stopping: $(docker ps --filter 'name=righthand' --format '{{.Names}}' | tr '\n' ' ')${NC}"
    docker stop $RIGHTHAND_CONTAINERS 2>/dev/null || true
    echo -e "${GREEN}✓ Righthand containers stopped${NC}"
else
    echo -e "${GREEN}✓ No conflicting righthand containers${NC}"
fi

# Check for other processes on critical ports
check_port() {
    local port=$1
    local service=$2
    local pid=$(lsof -ti :$port 2>/dev/null || true)
    
    if [ ! -z "$pid" ]; then
        local process=$(ps -p $pid -o comm= 2>/dev/null || echo "unknown")
        if [[ ! "$process" =~ "com.docker" ]]; then
            echo -e "${YELLOW}Warning: Port $port ($service) is in use by process: $process (PID: $pid)${NC}"
            echo -e "${YELLOW}You may need to stop this process manually${NC}"
            return 1
        fi
    fi
    return 0
}

echo "Checking critical ports..."
check_port 5432 "PostgreSQL" || true
check_port 6379 "Redis" || true
check_port 9092 "Kafka" || true
check_port 8080 "AgentMesh API" || true
check_port 3001 "AgentMesh UI" || true
echo ""

# Step 3: Clean Kafka/Zookeeper state if requested
if [ "$1" == "--clean-kafka" ] || [ "$1" == "--full-clean" ]; then
    echo -e "${BLUE}Step 3: Cleaning Kafka/Zookeeper state...${NC}"
    
    # Remove Kafka containers
    docker rm -f agentmesh-kafka agentmesh-zookeeper 2>/dev/null || true
    
    # Remove volumes
    echo "Removing Kafka/Zookeeper volumes..."
    docker volume rm -f agentmesh_kafka_data 2>/dev/null || true
    docker volume rm -f agentmesh_zookeeper_data 2>/dev/null || true
    docker volume rm -f agentmesh_zookeeper_logs 2>/dev/null || true
    
    echo -e "${GREEN}✓ Kafka/Zookeeper state cleaned${NC}"
else
    echo -e "${BLUE}Step 3: Skipping Kafka clean (use --clean-kafka to clean)${NC}"
fi

if [ "$1" == "--full-clean" ]; then
    echo -e "${YELLOW}Full clean requested - removing all volumes...${NC}"
    docker volume rm -f agentmesh_postgres_data 2>/dev/null || true
    docker volume rm -f agentmesh_redis_data 2>/dev/null || true
    docker volume rm -f agentmesh_weaviate_data 2>/dev/null || true
    docker volume rm -f agentmesh_prometheus_data 2>/dev/null || true
    docker volume rm -f agentmesh_grafana_data 2>/dev/null || true
    echo -e "${GREEN}✓ All volumes cleaned${NC}"
fi
echo ""

# Step 4: Wait for ports to be released
echo -e "${BLUE}Step 4: Waiting for ports to be released...${NC}"
sleep 2
echo -e "${GREEN}✓ Ports should be available${NC}"
echo ""

# Step 5: Start infrastructure services first
echo -e "${BLUE}Step 5: Starting infrastructure services...${NC}"
echo "Starting: PostgreSQL, Redis, Zookeeper, Kafka, Transformers"

docker-compose up -d postgres redis zookeeper kafka t2v-transformers

echo "Waiting for infrastructure to be healthy..."
max_attempts=30
attempt=0

while [ $attempt -lt $max_attempts ]; do
    postgres_health=$(docker inspect --format='{{.State.Health.Status}}' agentmesh-postgres 2>/dev/null || echo "unknown")
    redis_health=$(docker inspect --format='{{.State.Health.Status}}' agentmesh-redis 2>/dev/null || echo "unknown")
    kafka_health=$(docker inspect --format='{{.State.Health.Status}}' agentmesh-kafka 2>/dev/null || echo "unknown")
    
    if [ "$postgres_health" == "healthy" ] && [ "$redis_health" == "healthy" ] && [ "$kafka_health" == "healthy" ]; then
        echo -e "${GREEN}✓ Infrastructure services are healthy${NC}"
        break
    fi
    
    echo -n "."
    sleep 2
    attempt=$((attempt + 1))
done

if [ $attempt -eq $max_attempts ]; then
    echo -e "${RED}✗ Infrastructure services did not become healthy in time${NC}"
    echo "Current status:"
    docker-compose ps postgres redis kafka zookeeper
    exit 1
fi
echo ""

# Step 6: Start platform services
echo -e "${BLUE}Step 6: Starting platform services...${NC}"
echo "Starting: Weaviate, Temporal"

docker-compose up -d weaviate temporal

echo "Waiting for platform services..."
sleep 5
echo -e "${GREEN}✓ Platform services started${NC}"
echo ""

# Step 7: Start application services
echo -e "${BLUE}Step 7: Starting application services...${NC}"
echo "Starting: AgentMesh API, Prometheus, Grafana"

docker-compose up -d agentmesh-api prometheus grafana

echo "Waiting for API to be ready..."
sleep 10
echo -e "${GREEN}✓ Application services started${NC}"
echo ""

# Step 8: Start UI
echo -e "${BLUE}Step 8: Starting UI...${NC}"
docker-compose up -d agentmesh-ui

sleep 5
echo -e "${GREEN}✓ UI started${NC}"
echo ""

# Step 9: Show final status
echo -e "${BLUE}Step 9: Final Status${NC}"
echo "===================="
echo ""

docker-compose ps

echo ""
echo -e "${GREEN}✅ AgentMesh startup complete!${NC}"
echo ""
echo "Services available at:"
echo "  🌐 AgentMesh UI:      http://localhost:3001"
echo "  🔧 AgentMesh API:     http://localhost:8080"
echo "  📊 Grafana:           http://localhost:3000 (admin/agentmesh123)"
echo "  📈 Prometheus:        http://localhost:9090"
echo ""
echo -e "${YELLOW}⚠ Auto-BADS is not running${NC}"
echo "The 'Submit Idea' feature requires Auto-BADS on port 8083."
echo "Start it with:"
echo -e "  ${BLUE}cd ../Auto-BADS && ./start-autobads.sh${NC}"
echo ""
echo -e "${YELLOW}Usage:${NC}"
echo "  ./restart-agentmesh.sh              # Normal restart"
echo "  ./restart-agentmesh.sh --clean-kafka # Clean Kafka/Zookeeper state"
echo "  ./restart-agentmesh.sh --full-clean  # Clean all data volumes"
