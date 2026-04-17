#!/bin/bash

# Redis Cluster Management Script
# Manage Redis Cluster operations: create, check status, scale, backup

set -e

NAMESPACE="agentmesh"
REDIS_CLUSTER_SIZE=6

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

function show_usage() {
    echo -e "${BLUE}Redis Cluster Management${NC}"
    echo ""
    echo "Usage: $0 <command>"
    echo ""
    echo "Commands:"
    echo "  deploy        - Deploy Redis Cluster"
    echo "  init          - Initialize cluster (run after deploy)"
    echo "  status        - Check cluster status"
    echo "  info          - Show cluster info"
    echo "  nodes         - List cluster nodes"
    echo "  health        - Check health of all nodes"
    echo "  rebalance     - Rebalance cluster slots"
    echo "  backup        - Backup all cluster data"
    echo "  restore       - Restore cluster from backup"
    echo "  scale         - Scale cluster (add nodes)"
    echo "  failover      - Trigger manual failover"
    echo "  delete        - Delete Redis Cluster"
    echo ""
}

function deploy_cluster() {
    echo -e "${YELLOW}Deploying Redis Cluster...${NC}"
    kubectl apply -f redis-cluster.yaml
    
    echo -e "${YELLOW}Waiting for pods to be ready...${NC}"
    kubectl wait --for=condition=ready pod -l app=redis-cluster -n $NAMESPACE --timeout=300s
    
    echo -e "${GREEN}✓ Redis Cluster deployed${NC}"
    echo -e "${YELLOW}Run '$0 init' to initialize the cluster${NC}"
}

function init_cluster() {
    echo -e "${YELLOW}Initializing Redis Cluster...${NC}"
    
    # Delete old init job if exists
    kubectl delete job redis-cluster-init -n $NAMESPACE --ignore-not-found=true
    
    # Create init job
    kubectl apply -f redis-cluster.yaml | grep "job"
    
    echo -e "${YELLOW}Waiting for cluster initialization...${NC}"
    kubectl wait --for=condition=complete job/redis-cluster-init -n $NAMESPACE --timeout=120s
    
    echo -e "${GREEN}✓ Redis Cluster initialized${NC}"
    
    # Show cluster info
    cluster_info
}

function cluster_status() {
    echo -e "${YELLOW}Redis Cluster Status:${NC}"
    echo ""
    
    # Check pods
    echo -e "${BLUE}Pods:${NC}"
    kubectl get pods -l app=redis-cluster -n $NAMESPACE
    
    echo ""
    echo -e "${BLUE}Services:${NC}"
    kubectl get svc -l app=redis-cluster -n $NAMESPACE
    
    echo ""
    echo -e "${BLUE}PVCs:${NC}"
    kubectl get pvc -l app=redis-cluster -n $NAMESPACE
}

function cluster_info() {
    echo -e "${YELLOW}Redis Cluster Info:${NC}"
    echo ""
    
    kubectl exec -n $NAMESPACE redis-cluster-0 -- redis-cli cluster info
    
    echo ""
    echo -e "${YELLOW}Cluster Nodes:${NC}"
    kubectl exec -n $NAMESPACE redis-cluster-0 -- redis-cli cluster nodes
}

function cluster_nodes() {
    echo -e "${YELLOW}Redis Cluster Nodes:${NC}"
    echo ""
    
    for i in $(seq 0 $((REDIS_CLUSTER_SIZE - 1))); do
        echo -e "${BLUE}Node redis-cluster-$i:${NC}"
        kubectl exec -n $NAMESPACE redis-cluster-$i -- redis-cli --cluster check \
            redis-cluster-$i.redis-cluster-headless.agentmesh.svc.cluster.local:6379 || true
        echo ""
    done
}

function check_health() {
    echo -e "${YELLOW}Checking cluster health...${NC}"
    echo ""
    
    all_healthy=true
    for i in $(seq 0 $((REDIS_CLUSTER_SIZE - 1))); do
        if kubectl exec -n $NAMESPACE redis-cluster-$i -- redis-cli ping | grep -q PONG; then
            echo -e "${GREEN}✓ redis-cluster-$i: HEALTHY${NC}"
        else
            echo -e "${RED}✗ redis-cluster-$i: UNHEALTHY${NC}"
            all_healthy=false
        fi
    done
    
    echo ""
    if [ "$all_healthy" = true ]; then
        echo -e "${GREEN}All nodes are healthy${NC}"
    else
        echo -e "${RED}Some nodes are unhealthy${NC}"
    fi
}

function rebalance_cluster() {
    echo -e "${YELLOW}Rebalancing cluster...${NC}"
    
    kubectl exec -n $NAMESPACE redis-cluster-0 -- redis-cli --cluster rebalance \
        redis-cluster-0.redis-cluster-headless.agentmesh.svc.cluster.local:6379 \
        --cluster-use-empty-masters
    
    echo -e "${GREEN}✓ Cluster rebalanced${NC}"
}

function backup_cluster() {
    echo -e "${YELLOW}Backing up Redis Cluster...${NC}"
    
    BACKUP_DIR="./redis-backup-$(date +%Y%m%d-%H%M%S)"
    mkdir -p "$BACKUP_DIR"
    
    for i in $(seq 0 $((REDIS_CLUSTER_SIZE - 1))); do
        echo -e "${YELLOW}Backing up redis-cluster-$i...${NC}"
        
        # Trigger BGSAVE
        kubectl exec -n $NAMESPACE redis-cluster-$i -- redis-cli BGSAVE
        sleep 2
        
        # Copy RDB file
        kubectl cp $NAMESPACE/redis-cluster-$i:/data/dump.rdb "$BACKUP_DIR/dump-$i.rdb"
        
        # Copy AOF file
        kubectl cp $NAMESPACE/redis-cluster-$i:/data/appendonly.aof "$BACKUP_DIR/appendonly-$i.aof" || true
        
        echo -e "${GREEN}✓ redis-cluster-$i backed up${NC}"
    done
    
    echo -e "${GREEN}Backup complete: $BACKUP_DIR${NC}"
}

function restore_cluster() {
    echo -e "${RED}WARNING: This will restore data to the cluster${NC}"
    read -p "Continue? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
    
    read -p "Enter backup directory: " BACKUP_DIR
    
    if [ ! -d "$BACKUP_DIR" ]; then
        echo -e "${RED}Backup directory not found${NC}"
        exit 1
    fi
    
    for i in $(seq 0 $((REDIS_CLUSTER_SIZE - 1))); do
        echo -e "${YELLOW}Restoring redis-cluster-$i...${NC}"
        
        # Copy RDB file
        kubectl cp "$BACKUP_DIR/dump-$i.rdb" $NAMESPACE/redis-cluster-$i:/data/dump.rdb
        
        # Copy AOF file if exists
        if [ -f "$BACKUP_DIR/appendonly-$i.aof" ]; then
            kubectl cp "$BACKUP_DIR/appendonly-$i.aof" $NAMESPACE/redis-cluster-$i:/data/appendonly.aof
        fi
        
        # Restart pod to load data
        kubectl delete pod redis-cluster-$i -n $NAMESPACE
        
        echo -e "${GREEN}✓ redis-cluster-$i restored${NC}"
    done
    
    echo -e "${GREEN}Restore complete${NC}"
}

function scale_cluster() {
    echo -e "${YELLOW}Scaling cluster...${NC}"
    read -p "Enter new size (current: $REDIS_CLUSTER_SIZE): " NEW_SIZE
    
    if [ "$NEW_SIZE" -le "$REDIS_CLUSTER_SIZE" ]; then
        echo -e "${RED}New size must be greater than current size${NC}"
        exit 1
    fi
    
    # Scale StatefulSet
    kubectl scale statefulset redis-cluster -n $NAMESPACE --replicas=$NEW_SIZE
    
    echo -e "${YELLOW}Waiting for new pods...${NC}"
    kubectl wait --for=condition=ready pod -l app=redis-cluster -n $NAMESPACE --timeout=300s
    
    echo -e "${GREEN}✓ Cluster scaled to $NEW_SIZE nodes${NC}"
    echo -e "${YELLOW}You may need to manually add new nodes to the cluster${NC}"
}

function trigger_failover() {
    read -p "Enter node ID to failover (check with 'nodes' command): " NODE_ID
    
    echo -e "${YELLOW}Triggering failover for node $NODE_ID...${NC}"
    
    kubectl exec -n $NAMESPACE redis-cluster-0 -- redis-cli cluster failover
    
    echo -e "${GREEN}✓ Failover triggered${NC}"
}

function delete_cluster() {
    echo -e "${RED}WARNING: This will delete the entire Redis Cluster and all data${NC}"
    read -p "Continue? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
    
    echo -e "${YELLOW}Deleting Redis Cluster...${NC}"
    kubectl delete -f redis-cluster.yaml
    
    echo -e "${GREEN}✓ Redis Cluster deleted${NC}"
}

# Main
case "${1:-}" in
    deploy)
        deploy_cluster
        ;;
    init)
        init_cluster
        ;;
    status)
        cluster_status
        ;;
    info)
        cluster_info
        ;;
    nodes)
        cluster_nodes
        ;;
    health)
        check_health
        ;;
    rebalance)
        rebalance_cluster
        ;;
    backup)
        backup_cluster
        ;;
    restore)
        restore_cluster
        ;;
    scale)
        scale_cluster
        ;;
    failover)
        trigger_failover
        ;;
    delete)
        delete_cluster
        ;;
    *)
        show_usage
        exit 1
        ;;
esac
