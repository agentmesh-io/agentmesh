#!/bin/bash

# AgentMesh Kubernetes Deployment Script
# Deploys AgentMesh and all dependencies to Kubernetes cluster

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Configuration
NAMESPACE="agentmesh"
KUBECTL="kubectl"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}AgentMesh Kubernetes Deployment${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Check prerequisites
echo -e "${YELLOW}Checking prerequisites...${NC}"

if ! command -v kubectl &> /dev/null; then
    echo -e "${RED}Error: kubectl not found${NC}"
    exit 1
fi

if ! kubectl cluster-info &> /dev/null; then
    echo -e "${RED}Error: Unable to connect to Kubernetes cluster${NC}"
    exit 1
fi

echo -e "${GREEN}✓ kubectl installed and cluster accessible${NC}"

# Check if namespace exists
if kubectl get namespace $NAMESPACE &> /dev/null; then
    echo -e "${YELLOW}! Namespace $NAMESPACE already exists${NC}"
    read -p "Do you want to continue? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
else
    echo -e "${GREEN}✓ Namespace $NAMESPACE will be created${NC}"
fi

# Create namespace
echo ""
echo -e "${YELLOW}Creating namespace...${NC}"
kubectl apply -f agentmesh-deployment.yaml | grep "namespace"
echo -e "${GREEN}✓ Namespace created${NC}"

# Deploy PostgreSQL
echo ""
echo -e "${YELLOW}Deploying PostgreSQL...${NC}"
kubectl apply -f postgres-statefulset.yaml
echo -e "${GREEN}✓ PostgreSQL deployment created${NC}"

# Wait for PostgreSQL
echo -e "${YELLOW}Waiting for PostgreSQL to be ready...${NC}"
kubectl wait --for=condition=ready pod -l app=postgres -n $NAMESPACE --timeout=300s
echo -e "${GREEN}✓ PostgreSQL is ready${NC}"

# Deploy Zookeeper
echo ""
echo -e "${YELLOW}Deploying Zookeeper...${NC}"
kubectl apply -f kafka-statefulset.yaml | grep "zookeeper"
echo -e "${GREEN}✓ Zookeeper deployment created${NC}"

# Wait for Zookeeper
echo -e "${YELLOW}Waiting for Zookeeper to be ready...${NC}"
kubectl wait --for=condition=ready pod -l app=zookeeper -n $NAMESPACE --timeout=300s
echo -e "${GREEN}✓ Zookeeper is ready${NC}"

# Deploy Kafka
echo ""
echo -e "${YELLOW}Deploying Kafka...${NC}"
kubectl apply -f kafka-statefulset.yaml | grep "kafka"
echo -e "${GREEN}✓ Kafka deployment created${NC}"

# Wait for Kafka
echo -e "${YELLOW}Waiting for Kafka to be ready...${NC}"
kubectl wait --for=condition=ready pod -l app=kafka -n $NAMESPACE --timeout=300s
echo -e "${GREEN}✓ Kafka is ready${NC}"

# Deploy Redis
echo ""
echo -e "${YELLOW}Deploying Redis...${NC}"
kubectl apply -f redis-statefulset.yaml
echo -e "${GREEN}✓ Redis deployment created${NC}"

# Wait for Redis
echo -e "${YELLOW}Waiting for Redis to be ready...${NC}"
kubectl wait --for=condition=ready pod -l app=redis -n $NAMESPACE --timeout=300s
echo -e "${GREEN}✓ Redis is ready${NC}"

# Deploy AgentMesh
echo ""
echo -e "${YELLOW}Deploying AgentMesh application...${NC}"
kubectl apply -f agentmesh-deployment.yaml | grep -E "deployment|service|configmap|secret|poddisruptionbudget"
echo -e "${GREEN}✓ AgentMesh deployment created${NC}"

# Wait for AgentMesh
echo -e "${YELLOW}Waiting for AgentMesh to be ready...${NC}"
kubectl wait --for=condition=ready pod -l app=agentmesh -n $NAMESPACE --timeout=300s
echo -e "${GREEN}✓ AgentMesh is ready${NC}"

# Deploy HPA
echo ""
echo -e "${YELLOW}Deploying Horizontal Pod Autoscaler...${NC}"
kubectl apply -f hpa.yaml
echo -e "${GREEN}✓ HPA created${NC}"

# Deploy Ingress (optional)
echo ""
read -p "Do you want to deploy Ingress? (y/N) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}Deploying Ingress...${NC}"
    kubectl apply -f ingress.yaml
    echo -e "${GREEN}✓ Ingress created${NC}"
fi

# Show deployment status
echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Deployment Status${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

echo -e "${YELLOW}Pods:${NC}"
kubectl get pods -n $NAMESPACE

echo ""
echo -e "${YELLOW}Services:${NC}"
kubectl get svc -n $NAMESPACE

echo ""
echo -e "${YELLOW}PVCs:${NC}"
kubectl get pvc -n $NAMESPACE

echo ""
echo -e "${YELLOW}HPA:${NC}"
kubectl get hpa -n $NAMESPACE

# Show AgentMesh logs
echo ""
echo -e "${YELLOW}Recent AgentMesh logs:${NC}"
kubectl logs -l app=agentmesh -n $NAMESPACE --tail=20

# Port forwarding instructions
echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Access Instructions${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "${YELLOW}To access AgentMesh locally, run:${NC}"
echo -e "  kubectl port-forward -n $NAMESPACE svc/agentmesh-service 8080:8080"
echo ""
echo -e "${YELLOW}Then access:${NC}"
echo -e "  API: http://localhost:8080/api"
echo -e "  Health: http://localhost:8080/actuator/health"
echo -e "  Metrics: http://localhost:8080/actuator/prometheus"
echo ""

echo -e "${GREEN}Deployment complete!${NC}"
