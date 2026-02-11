#!/bin/bash

# AgentMesh Status Checker
# Quick health check for all services

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo "🔍 AgentMesh Status Check"
echo "========================="
echo ""

# Check Docker containers
echo -e "${BLUE}Docker Services:${NC}"
docker-compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}"
echo ""

# Check individual service health
echo -e "${BLUE}Health Checks:${NC}"

check_health() {
    local container=$1
    local name=$2
    local health=$(docker inspect --format='{{.State.Health.Status}}' $container 2>/dev/null)
    
    if [ "$health" == "healthy" ]; then
        echo -e "  ${GREEN}✓${NC} $name: healthy"
        return 0
    elif [ "$health" == "unhealthy" ]; then
        echo -e "  ${RED}✗${NC} $name: unhealthy"
        return 1
    elif [ "$health" == "" ]; then
        # No health check defined
        local status=$(docker inspect --format='{{.State.Status}}' $container 2>/dev/null)
        if [ "$status" == "running" ]; then
            echo -e "  ${GREEN}✓${NC} $name: running (no health check)"
            return 0
        else
            echo -e "  ${RED}✗${NC} $name: not running"
            return 1
        fi
    else
        echo -e "  ${YELLOW}⚠${NC} $name: $health"
        return 1
    fi
}

# Infrastructure
check_health agentmesh-postgres "PostgreSQL"
check_health agentmesh-redis "Redis"
check_health agentmesh-zookeeper "Zookeeper"
check_health agentmesh-kafka "Kafka"
echo ""

# Platform
check_health agentmesh-transformers "Transformers"
check_health agentmesh-weaviate "Weaviate"
check_health agentmesh-temporal "Temporal"
echo ""

# Application
check_health agentmesh-api-server "AgentMesh API"
check_health agentmesh-ui "AgentMesh UI"
check_health agentmesh-prometheus "Prometheus"
check_health agentmesh-grafana "Grafana"
echo ""

# Check URLs
echo -e "${BLUE}URL Tests:${NC}"

test_url() {
    local url=$1
    local name=$2
    local response=$(curl -s -o /dev/null -w "%{http_code}" $url 2>/dev/null)
    
    if [ "$response" == "200" ] || [ "$response" == "302" ]; then
        echo -e "  ${GREEN}✓${NC} $name ($url): $response"
        return 0
    else
        echo -e "  ${RED}✗${NC} $name ($url): $response"
        return 1
    fi
}

test_url "http://localhost:8080/actuator/health" "API Health"
test_url "http://localhost:3001" "UI"
test_url "http://localhost:9090/-/healthy" "Prometheus"
test_url "http://localhost:3000/api/health" "Grafana"
echo ""

# Check Auto-BADS
echo -e "${BLUE}Auto-BADS (Standalone):${NC}"
AUTOBADS_RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8083/actuator/health 2>/dev/null)
if [ "$AUTOBADS_RESPONSE" == "200" ]; then
    echo -e "  ${GREEN}✓${NC} Auto-BADS is running on port 8083"
else
    echo -e "  ${YELLOW}⚠${NC} Auto-BADS is not running (expected - run standalone)"
    echo -e "    Start with: cd ../Auto-BADS && mvn spring-boot:run"
fi
echo ""

# Port check
echo -e "${BLUE}Port Usage:${NC}"
echo "  PostgreSQL:  5432 - $(lsof -ti :5432 >/dev/null 2>&1 && echo -e '${GREEN}in use${NC}' || echo -e '${RED}free${NC}')"
echo "  Redis:       6379 - $(lsof -ti :6379 >/dev/null 2>&1 && echo -e '${GREEN}in use${NC}' || echo -e '${RED}free${NC}')"
echo "  Kafka:       9092 - $(lsof -ti :9092 >/dev/null 2>&1 && echo -e '${GREEN}in use${NC}' || echo -e '${RED}free${NC}')"
echo "  API:         8080 - $(lsof -ti :8080 >/dev/null 2>&1 && echo -e '${GREEN}in use${NC}' || echo -e '${RED}free${NC}')"
echo "  Auto-BADS:   8083 - $(lsof -ti :8083 >/dev/null 2>&1 && echo -e '${GREEN}in use${NC}' || echo -e '${RED}free${NC}')"
echo "  UI:          3001 - $(lsof -ti :3001 >/dev/null 2>&1 && echo -e '${GREEN}in use${NC}' || echo -e '${RED}free${NC}')"
echo ""

# Summary
echo -e "${BLUE}Quick Actions:${NC}"
echo "  View logs:        docker-compose logs -f [service]"
echo "  Restart service:  docker-compose restart [service]"
echo "  Full restart:     ./restart-agentmesh.sh"
echo "  Clean Kafka:      ./restart-agentmesh.sh --clean-kafka"
