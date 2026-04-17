# AgentMesh Production Deployment Guide

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Pre-Deployment Checklist](#pre-deployment-checklist)
3. [Infrastructure Setup](#infrastructure-setup)
4. [Deployment Steps](#deployment-steps)
5. [Post-Deployment Validation](#post-deployment-validation)
6. [Monitoring Setup](#monitoring-setup)
7. [Backup and Disaster Recovery](#backup-and-disaster-recovery)
8. [Rollback Procedures](#rollback-procedures)
9. [Scaling Procedures](#scaling-procedures)
10. [Troubleshooting](#troubleshooting)

## Prerequisites

### Infrastructure Requirements

**Kubernetes Cluster**:
- Kubernetes version: 1.26+
- Minimum nodes: 3 (for HA)
- Recommended nodes: 5+
- Node specs (per node):
  - CPU: 4+ cores
  - RAM: 16GB+
  - Disk: 100GB+ SSD

**Cloud Provider Resources** (recommended):
- Managed Kubernetes: EKS, GKE, or AKS
- Managed PostgreSQL: RDS, Cloud SQL, or Azure Database
- Managed Redis: ElastiCache, Memorystore, or Azure Cache
- Load Balancer: Cloud provider's LB
- Object Storage: S3, GCS, or Azure Blob (for backups)

### Required Tools

```bash
# Install required CLI tools
brew install kubectl
brew install helm
brew install kustomize
brew install istioctl  # If using Istio

# Verify versions
kubectl version --client
helm version
kustomize version
```

### Access Requirements

- Kubernetes cluster admin access
- Docker registry access (for pushing images)
- DNS management access
- TLS certificate management (Let's Encrypt or corporate CA)
- Secrets management access (Sealed Secrets, Vault, or cloud provider)

## Pre-Deployment Checklist

### Code Preparation

- [ ] All tests passing (`mvn test`)
- [ ] Build successful (`mvn clean package`)
- [ ] Docker image built and tagged with version
- [ ] Image pushed to registry
- [ ] Git tag created for release
- [ ] Release notes documented

### Configuration Review

- [ ] Environment variables configured
- [ ] Secrets created and sealed
- [ ] Resource limits appropriate
- [ ] HPA thresholds validated
- [ ] Database migration scripts reviewed
- [ ] Redis cluster configuration verified

### Security Checklist

- [ ] TLS certificates ready
- [ ] Network policies defined
- [ ] RBAC roles configured
- [ ] Secrets encrypted (Sealed Secrets or external)
- [ ] Security scanning completed (Trivy, Snyk)
- [ ] Vulnerability assessment passed

### Monitoring & Alerting

- [ ] Prometheus configured
- [ ] Grafana dashboards imported
- [ ] Alert rules applied
- [ ] AlertManager configured
- [ ] PagerDuty/Slack integration tested
- [ ] Log aggregation configured (ELK, Loki)

## Infrastructure Setup

### 1. Create Namespaces

```bash
# Create namespaces
kubectl create namespace agentmesh-prod
kubectl create namespace monitoring

# Label for Istio (if using)
kubectl label namespace agentmesh-prod istio-injection=enabled
```

### 2. Install Monitoring Stack

```bash
# Install Prometheus Operator
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

helm install kube-prometheus-stack prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --set prometheus.prometheusSpec.serviceMonitorSelectorNilUsesHelmValues=false \
  --set grafana.adminPassword=YOUR_SECURE_PASSWORD

# Verify installation
kubectl get pods -n monitoring
```

### 3. Install Cert-Manager

```bash
# Install cert-manager
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.13.2/cert-manager.yaml

# Wait for pods
kubectl wait --for=condition=Ready pods --all -n cert-manager --timeout=300s

# Apply ClusterIssuers
kubectl apply -f k8s/cert-manager.yaml
```

### 4. Install Ingress Controller

**Option A: NGINX Ingress**
```bash
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo update

helm install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx \
  --create-namespace \
  --set controller.service.type=LoadBalancer

# Get external IP
kubectl get svc ingress-nginx-controller -n ingress-nginx
```

**Option B: Istio Service Mesh**
```bash
# Download Istio
curl -L https://istio.io/downloadIstio | ISTIO_VERSION=1.20.0 sh -
cd istio-1.20.0
export PATH=$PWD/bin:$PATH

# Install Istio
istioctl install --set profile=production -y

# Install observability addons
kubectl apply -f samples/addons/prometheus.yaml
kubectl apply -f samples/addons/grafana.yaml
kubectl apply -f samples/addons/kiali.yaml
kubectl apply -f samples/addons/jaeger.yaml
```

### 5. Configure DNS

Update DNS records to point to load balancer:

```
A    agentmesh.example.com         -> LOAD_BALANCER_IP
A    api.agentmesh.example.com     -> LOAD_BALANCER_IP
```

Wait for DNS propagation:
```bash
nslookup agentmesh.example.com
dig agentmesh.example.com
```

## Deployment Steps

### Step 1: Prepare Secrets

```bash
# Navigate to production overlay
cd k8s/overlays/prod

# Copy secret templates
cp secrets.env.template secrets.env
cp postgres-secrets.env.template postgres-secrets.env

# Edit with actual values
vi secrets.env
vi postgres-secrets.env

# Create sealed secrets
kubectl create secret generic agentmesh-secrets \
  --from-env-file=secrets.env \
  --dry-run=client -o yaml | \
  kubeseal -o yaml > agentmesh-sealed-secrets.yaml

kubectl create secret generic postgres-secrets \
  --from-env-file=postgres-secrets.env \
  --dry-run=client -o yaml | \
  kubeseal -o yaml > postgres-sealed-secrets.yaml

# Apply sealed secrets
kubectl apply -f agentmesh-sealed-secrets.yaml
kubectl apply -f postgres-sealed-secrets.yaml

# Verify secrets created
kubectl get secrets -n agentmesh-prod
```

### Step 2: Build and Push Docker Image

```bash
# Build image
cd /path/to/AgentMesh
mvn clean package -DskipTests

docker build -t agentmesh/agentmesh:v1.0.0 .

# Tag for registry
docker tag agentmesh/agentmesh:v1.0.0 YOUR_REGISTRY/agentmesh:v1.0.0

# Push to registry
docker push YOUR_REGISTRY/agentmesh:v1.0.0

# Update kustomization.yaml with correct image
cd k8s/overlays/prod
kustomize edit set image agentmesh=YOUR_REGISTRY/agentmesh:v1.0.0
```

### Step 3: Deploy Database

```bash
# Deploy PostgreSQL
kubectl apply -f k8s/postgres-statefulset.yaml
kubectl apply -f k8s/postgres-service.yaml

# Wait for PostgreSQL ready
kubectl wait --for=condition=Ready pod/postgres-0 -n agentmesh-prod --timeout=300s

# Verify PostgreSQL
kubectl exec -it postgres-0 -n agentmesh-prod -- psql -U agentmesh_prod -c "SELECT version();"
```

### Step 4: Run Database Migrations

```bash
# Deploy AgentMesh temporarily for migrations
kubectl apply -k k8s/overlays/prod

# Wait for first pod
kubectl wait --for=condition=Ready pod -l app=agentmesh -n agentmesh-prod --timeout=300s

# Check migration status
kubectl logs -l app=agentmesh -n agentmesh-prod | grep Flyway

# Verify migrations applied
kubectl exec -it postgres-0 -n agentmesh-prod -- psql -U agentmesh_prod -c "SELECT * FROM flyway_schema_history;"
```

### Step 5: Deploy Redis Cluster

```bash
# Deploy Redis
kubectl apply -f k8s/redis-cluster-statefulset.yaml
kubectl apply -f k8s/redis-service.yaml

# Wait for Redis cluster ready
kubectl wait --for=condition=Ready pod -l app=redis-cluster -n agentmesh-prod --timeout=300s

# Initialize cluster (if not auto-initialized)
kubectl exec -it redis-cluster-0 -n agentmesh-prod -- redis-cli --cluster create \
  $(kubectl get pods -n agentmesh-prod -l app=redis-cluster -o jsonpath='{range.items[*]}{.status.podIP}:6379 ') \
  --cluster-replicas 1

# Verify cluster
kubectl exec -it redis-cluster-0 -n agentmesh-prod -- redis-cli cluster info
```

### Step 6: Deploy Kafka

```bash
# Deploy Zookeeper
kubectl apply -f k8s/zookeeper-statefulset.yaml
kubectl apply -f k8s/zookeeper-service.yaml

# Wait for Zookeeper
kubectl wait --for=condition=Ready pod -l app=zookeeper -n agentmesh-prod --timeout=300s

# Deploy Kafka
kubectl apply -f k8s/kafka-statefulset.yaml
kubectl apply -f k8s/kafka-service.yaml

# Wait for Kafka
kubectl wait --for=condition=Ready pod -l app=kafka -n agentmesh-prod --timeout=300s

# Verify Kafka
kubectl exec -it kafka-0 -n agentmesh-prod -- kafka-topics.sh --list --bootstrap-server localhost:9092
```

### Step 7: Deploy AgentMesh Application

```bash
# Deploy using Kustomize
kubectl apply -k k8s/overlays/prod

# Watch rollout
kubectl rollout status deployment/agentmesh -n agentmesh-prod

# Verify pods running
kubectl get pods -n agentmesh-prod

# Check logs
kubectl logs -l app=agentmesh -n agentmesh-prod --tail=100
```

### Step 8: Deploy Monitoring

```bash
# Apply ServiceMonitors
kubectl apply -f k8s/prometheus-servicemonitor.yaml

# Apply Alert Rules
kubectl apply -f k8s/prometheus-alerts.yaml

# Apply AlertManager config
kubectl apply -f k8s/alertmanager-config.yaml

# Import Grafana dashboards
cd k8s/grafana
./import-dashboards.sh

# Verify Prometheus targets
kubectl port-forward -n monitoring svc/kube-prometheus-stack-prometheus 9090:9090
# Open http://localhost:9090/targets
```

### Step 9: Configure Ingress/Gateway

**NGINX Ingress**:
```bash
kubectl apply -f k8s/overlays/prod/ingress-production.yaml

# Verify certificate issued
kubectl get certificate -n agentmesh-prod
kubectl describe certificate agentmesh-prod-cert -n agentmesh-prod
```

**Istio Gateway**:
```bash
kubectl apply -f k8s/istio/istio-config.yaml
kubectl apply -f k8s/istio/traffic-management.yaml

# Verify gateway
kubectl get gateway -n agentmesh-prod
istioctl analyze -n agentmesh-prod
```

### Step 10: Apply Network Policies

```bash
kubectl apply -f k8s/overlays/prod/network-policy.yaml

# Verify network policies
kubectl get networkpolicy -n agentmesh-prod
```

## Post-Deployment Validation

### Health Checks

```bash
# Check all pods running
kubectl get pods -n agentmesh-prod
# All pods should be Running with 2/2 ready (app + istio sidecar)

# Check health endpoint
curl https://agentmesh.example.com/actuator/health
# Should return: {"status":"UP"}

# Check info endpoint
curl https://agentmesh.example.com/actuator/info
```

### Functional Tests

```bash
# Test tenant creation
curl -X POST https://api.agentmesh.example.com/api/v1/tenants \
  -H "Content-Type: application/json" \
  -d '{"name":"test-tenant","description":"Test Tenant"}'

# Test agent creation
curl -X POST https://api.agentmesh.example.com/api/v1/agents \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: test-tenant" \
  -d '{"name":"test-agent","type":"PLANNING","systemPrompt":"You are a helpful agent"}'

# Test agent execution
curl -X POST https://api.agentmesh.example.com/api/v1/agents/{agentId}/execute \
  -H "Content-Type: application/json" \
  -d '{"input":"Hello, test the system"}'
```

### Performance Tests

```bash
# Run load test
kubectl run loadtest --image=williamyeh/hey:latest --rm -i --tty -- \
  -n 1000 -c 10 -m GET https://api.agentmesh.example.com/actuator/health

# Check HPA scaling
kubectl get hpa -n agentmesh-prod -w

# Monitor metrics
kubectl port-forward -n monitoring svc/grafana 3000:80
# Open http://localhost:3000 (username: admin)
```

### Security Validation

```bash
# Verify TLS
curl -vI https://agentmesh.example.com 2>&1 | grep -i "SSL connection"

# Check mTLS (Istio)
istioctl authn tls-check agentmesh-0.agentmesh-prod

# Verify network policies
kubectl exec -it agentmesh-0 -n agentmesh-prod -- curl postgres-service:5432
# Should work

kubectl run test-pod --image=busybox -n default --rm -it -- wget -O- postgres-service.agentmesh-prod:5432
# Should be blocked by network policy
```

## Monitoring Setup

### Grafana Dashboards

```bash
# Access Grafana
kubectl port-forward -n monitoring svc/kube-prometheus-stack-grafana 3000:80

# Login credentials
Username: admin
Password: YOUR_SECURE_PASSWORD (set during installation)

# Import dashboards
# Navigate to http://localhost:3000
# Dashboards should be auto-imported from ConfigMap
# Verify: Overview, Performance, MAST dashboards exist
```

### Alert Validation

```bash
# Check alert rules loaded
kubectl get prometheusrules -n agentmesh-prod

# Access Prometheus
kubectl port-forward -n monitoring svc/kube-prometheus-stack-prometheus 9090:9090
# Open http://localhost:9090/alerts

# Access AlertManager
kubectl port-forward -n monitoring svc/kube-prometheus-stack-alertmanager 9093:9093
# Open http://localhost:9093
```

### Log Aggregation

**Option A: ELK Stack**
```bash
# Install Elasticsearch
helm install elasticsearch elastic/elasticsearch -n logging --create-namespace

# Install Kibana
helm install kibana elastic/kibana -n logging

# Install Filebeat
helm install filebeat elastic/filebeat -n logging
```

**Option B: Loki**
```bash
# Install Loki stack
helm install loki grafana/loki-stack -n monitoring \
  --set grafana.enabled=false \
  --set promtail.enabled=true
```

## Backup and Disaster Recovery

### Database Backups

**Automated Backups** (CronJob):
```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: postgres-backup
  namespace: agentmesh-prod
spec:
  schedule: "0 2 * * *"  # Daily at 2 AM
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: backup
            image: postgres:16
            command:
            - /bin/sh
            - -c
            - |
              pg_dump -h postgres-service -U agentmesh_prod agentmesh_prod | \
              gzip > /backups/backup-$(date +%Y%m%d-%H%M%S).sql.gz
            env:
            - name: PGPASSWORD
              valueFrom:
                secretKeyRef:
                  name: postgres-secrets
                  key: POSTGRES_PASSWORD
            volumeMounts:
            - name: backups
              mountPath: /backups
          restartPolicy: OnFailure
          volumes:
          - name: backups
            persistentVolumeClaim:
              claimName: postgres-backups
```

**Manual Backup**:
```bash
# Create backup
kubectl exec -it postgres-0 -n agentmesh-prod -- \
  pg_dump -U agentmesh_prod agentmesh_prod | \
  gzip > backup-$(date +%Y%m%d).sql.gz

# Upload to S3
aws s3 cp backup-$(date +%Y%m%d).sql.gz s3://agentmesh-backups/postgres/
```

**Restore from Backup**:
```bash
# Download backup
aws s3 cp s3://agentmesh-backups/postgres/backup-20251109.sql.gz .

# Restore
gunzip backup-20251109.sql.gz
kubectl exec -i postgres-0 -n agentmesh-prod -- \
  psql -U agentmesh_prod agentmesh_prod < backup-20251109.sql
```

### Redis Backups

```bash
# Trigger RDB snapshot
kubectl exec -it redis-cluster-0 -n agentmesh-prod -- redis-cli BGSAVE

# Copy RDB file
kubectl cp agentmesh-prod/redis-cluster-0:/data/dump.rdb ./redis-backup.rdb

# Upload to S3
aws s3 cp redis-backup.rdb s3://agentmesh-backups/redis/
```

### Application State Backup

```bash
# Backup all ConfigMaps and Secrets
kubectl get configmap -n agentmesh-prod -o yaml > configmaps-backup.yaml
kubectl get secret -n agentmesh-prod -o yaml > secrets-backup.yaml

# Backup all Custom Resources
kubectl get all -n agentmesh-prod -o yaml > resources-backup.yaml
```

## Rollback Procedures

### Rollback Deployment

```bash
# View rollout history
kubectl rollout history deployment/agentmesh -n agentmesh-prod

# Rollback to previous version
kubectl rollout undo deployment/agentmesh -n agentmesh-prod

# Rollback to specific revision
kubectl rollout undo deployment/agentmesh -n agentmesh-prod --to-revision=2

# Monitor rollback
kubectl rollout status deployment/agentmesh -n agentmesh-prod

# Verify
kubectl get pods -n agentmesh-prod
```

### Rollback Database Migration

```bash
# Check current version
kubectl exec -it postgres-0 -n agentmesh-prod -- \
  psql -U agentmesh_prod -c "SELECT * FROM flyway_schema_history ORDER BY installed_on DESC LIMIT 5;"

# Rollback not supported by Flyway automatically
# Manual rollback required - use migration down scripts
kubectl exec -it agentmesh-0 -n agentmesh-prod -- \
  curl -X POST localhost:8080/admin/migrations/rollback/V3
```

### Emergency Procedures

**Complete Rollback**:
```bash
# 1. Scale down current version
kubectl scale deployment agentmesh --replicas=0 -n agentmesh-prod

# 2. Update image to previous version
kubectl set image deployment/agentmesh agentmesh=agentmesh/agentmesh:v0.9.0 -n agentmesh-prod

# 3. Scale up
kubectl scale deployment agentmesh --replicas=5 -n agentmesh-prod

# 4. Monitor
kubectl get pods -n agentmesh-prod -w
```

**Circuit Breaker (Stop All Traffic)**:
```bash
# Scale to 0
kubectl scale deployment agentmesh --replicas=0 -n agentmesh-prod

# Or delete Ingress
kubectl delete ingress agentmesh-ingress-prod -n agentmesh-prod
```

## Scaling Procedures

### Manual Scaling

```bash
# Scale deployment
kubectl scale deployment agentmesh --replicas=10 -n agentmesh-prod

# Verify
kubectl get pods -n agentmesh-prod
```

### HPA Tuning

```bash
# Update HPA
kubectl patch hpa agentmesh-hpa -n agentmesh-prod -p '{"spec":{"maxReplicas":15}}'

# View HPA status
kubectl get hpa -n agentmesh-prod

# Describe for details
kubectl describe hpa agentmesh-hpa -n agentmesh-prod
```

### Database Scaling

**Vertical Scaling**:
```bash
# Update resource limits
kubectl set resources statefulset postgres -n agentmesh-prod \
  --limits=cpu=4,memory=8Gi \
  --requests=cpu=2,memory=4Gi

# Restart pods
kubectl delete pod postgres-0 -n agentmesh-prod
```

**Read Replicas** (Cloud Provider):
```bash
# For managed databases (RDS, Cloud SQL), create read replicas via console
# Update application to use read endpoints for queries
```

## Troubleshooting

### Common Issues

**Issue**: Pods in CrashLoopBackOff
```bash
# Check logs
kubectl logs agentmesh-0 -n agentmesh-prod --previous

# Check events
kubectl describe pod agentmesh-0 -n agentmesh-prod

# Common causes:
# - Database connection failed
# - Missing secrets
# - OOMKilled (increase memory limits)
```

**Issue**: High latency
```bash
# Check HPA
kubectl get hpa -n agentmesh-prod

# Check resource usage
kubectl top pods -n agentmesh-prod

# Check database
kubectl exec -it postgres-0 -n agentmesh-prod -- \
  psql -U agentmesh_prod -c "SELECT * FROM pg_stat_activity WHERE state = 'active';"

# Check Grafana Performance dashboard
```

**Issue**: Certificate not issued
```bash
# Check certificate status
kubectl describe certificate agentmesh-prod-cert -n agentmesh-prod

# Check cert-manager logs
kubectl logs -n cert-manager deploy/cert-manager

# Check challenges
kubectl get challenges -n agentmesh-prod

# Manual trigger
kubectl delete certificate agentmesh-prod-cert -n agentmesh-prod
kubectl apply -f k8s/cert-manager.yaml
```

### Emergency Contacts

| Role | Contact | Method |
|------|---------|--------|
| On-Call Engineer | PagerDuty | Automatic page |
| Platform Team Lead | #platform-team | Slack |
| Database Team | #database-team | Slack |
| Infrastructure | #infrastructure | Slack |
| Security Team | #security | Slack |

### Incident Response

1. **Acknowledge**: Acknowledge alert in PagerDuty
2. **Assess**: Check monitoring dashboards
3. **Mitigate**: Apply immediate fix (scale, rollback, circuit breaker)
4. **Communicate**: Update incident channel
5. **Resolve**: Implement permanent fix
6. **Document**: Create incident report
7. **Review**: Post-mortem within 48 hours

## Maintenance Windows

### Planned Maintenance

```bash
# 1. Announce maintenance window
# Send notification 24 hours in advance

# 2. Create maintenance page
kubectl apply -f k8s/maintenance-page.yaml

# 3. Drain nodes one by one
kubectl drain NODE_NAME --ignore-daemonsets --delete-emptydir-data

# 4. Perform maintenance

# 5. Uncordon nodes
kubectl uncordon NODE_NAME

# 6. Remove maintenance page
kubectl delete -f k8s/maintenance-page.yaml
```

### Zero-Downtime Updates

```bash
# Use rolling updates
kubectl set image deployment/agentmesh agentmesh=agentmesh/agentmesh:v1.1.0 -n agentmesh-prod

# Monitor rollout
kubectl rollout status deployment/agentmesh -n agentmesh-prod

# If issues, pause and rollback
kubectl rollout pause deployment/agentmesh -n agentmesh-prod
kubectl rollout undo deployment/agentmesh -n agentmesh-prod
```

## Conclusion

This guide provides comprehensive procedures for deploying, monitoring, and maintaining AgentMesh in production. Always test deployment procedures in staging before production, maintain up-to-date documentation, and conduct regular disaster recovery drills.

For additional support:
- Review: [RUNBOOK.md](./RUNBOOK.md) for operational procedures
- Check: [ENVIRONMENT_CONFIG_GUIDE.md](./ENVIRONMENT_CONFIG_GUIDE.md) for configuration details
- See: [INGRESS_SERVICE_MESH_GUIDE.md](./INGRESS_SERVICE_MESH_GUIDE.md) for traffic management

**Last Updated**: 2025-11-09  
**Version**: 1.0  
**Owner**: Platform Team
