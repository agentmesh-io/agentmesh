#!/bin/bash

# AgentMesh Phase 5 Week 1 - Startup and Test Script
# After increasing Docker disk to 256GB

set -e  # Exit on error

echo "=========================================="
echo "AgentMesh Phase 5 Week 1 - Startup"
echo "=========================================="
echo

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Check Docker is running
echo -e "${BLUE}Step 1: Checking Docker status...${NC}"
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}✗ Docker is not running${NC}"
    echo "Please start Docker Desktop and run this script again"
    exit 1
fi
echo -e "${GREEN}✓ Docker is running${NC}"
echo

# Check disk space
echo -e "${BLUE}Step 2: Checking Docker disk space...${NC}"
docker system df
echo

# Navigate to AgentMesh directory
cd /Users/univers/projects/agentmesh/AgentMesh

# Start all services
echo -e "${BLUE}Step 3: Starting AgentMesh services...${NC}"
docker-compose -f docker-compose.yml up -d

echo -e "${YELLOW}Waiting for services to start (60 seconds)...${NC}"
sleep 60

# Check service status
echo -e "${BLUE}Step 4: Checking service status...${NC}"
docker-compose ps
echo

# Check Weaviate health
echo -e "${BLUE}Step 5: Checking Weaviate status...${NC}"
WEAVIATE_LOGS=$(docker logs agentmesh-weaviate 2>&1 | tail -20)
if echo "$WEAVIATE_LOGS" | grep -q "read-only"; then
    echo -e "${RED}✗ Weaviate is still in read-only mode${NC}"
    echo "Restarting Weaviate to clear read-only state..."
    docker-compose restart weaviate
    sleep 30
else
    echo -e "${GREEN}✓ Weaviate is writable${NC}"
fi
echo

# Check AgentMesh health
echo -e "${BLUE}Step 6: Checking AgentMesh health...${NC}"
for i in {1..12}; do
    if curl -s http://localhost:8080/actuator/health | grep -q "UP"; then
        echo -e "${GREEN}✓ AgentMesh is UP and healthy${NC}"
        break
    else
        if [ $i -eq 12 ]; then
            echo -e "${RED}✗ AgentMesh not responding after 60 seconds${NC}"
            echo "Check logs: docker logs agentmesh-app"
            exit 1
        fi
        echo "Waiting for AgentMesh to be ready... ($i/12)"
        sleep 5
    fi
done
echo

# Show service URLs
echo -e "${GREEN}=========================================="
echo "All Services Running!"
echo "==========================================${NC}"
echo
echo "AgentMesh API:     http://localhost:8080"
echo "Weaviate:          http://localhost:8080 (Docker internal)"
echo "PostgreSQL:        localhost:5432"
echo "Kafka:             localhost:9092"
echo "Temporal:          localhost:7233"
echo
echo -e "${BLUE}Next Steps:${NC}"
echo "1. Run hybrid search tests:"
echo "   ./test-scripts/07-hybrid-search-test.sh"
echo
echo "2. Check AgentMesh logs:"
echo "   docker logs -f agentmesh-app"
echo
echo "3. Check Weaviate logs:"
echo "   docker logs -f agentmesh-weaviate"
echo
echo -e "${GREEN}Ready for Phase 5 Week 1 testing! 🚀${NC}"
