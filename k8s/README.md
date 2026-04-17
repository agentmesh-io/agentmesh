# Kubernetes Deployment Guide

This directory contains production-ready Kubernetes manifests for deploying AgentMesh.

## Overview

The deployment includes:
- **AgentMesh**: Main application (3 replicas, auto-scaling 3-10)
- **PostgreSQL**: Production database (StatefulSet with 50Gi storage)
- **Redis**: Caching layer (StatefulSet with 10Gi storage)
- **Kafka**: Event streaming (3 brokers with 100Gi storage each)
- **Zookeeper**: Kafka coordination (3 nodes with 10Gi storage each)

## Prerequisites

1. **Kubernetes Cluster** (v1.24+)
   - Minikube, Kind, GKE, EKS, or AKS
   - Minimum 8 CPU cores, 16GB RAM

2. **kubectl** configured to access your cluster

3. **Storage Class** named `standard` (or update manifests)

4. **Ingress Controller** (optional, for ingress.yaml)
   - Nginx Ingress recommended

5. **Cert Manager** (optional, for TLS)
   - For automatic certificate provisioning

## Quick Start

### 1. Deploy Everything

```bash
cd k8s
./deploy.sh
```

This script will:
- Create the `agentmesh` namespace
- Deploy PostgreSQL, Zookeeper, Kafka, Redis
- Deploy AgentMesh application
- Set up HPA (Horizontal Pod Autoscaler)
- Optionally deploy Ingress

### 2. Verify Deployment

```bash
# Check all pods are running
kubectl get pods -n agentmesh

# Check services
kubectl get svc -n agentmesh

# Check PVCs (Persistent Volume Claims)
kubectl get pvc -n agentmesh

# Check HPA status
kubectl get hpa -n agentmesh
```

### 3. Access AgentMesh

**Port Forwarding (Development)**:
```bash
kubectl port-forward -n agentmesh svc/agentmesh-service 8080:8080
```

Then access:
- API: http://localhost:8080/api
- Health: http://localhost:8080/actuator/health
- Metrics: http://localhost:8080/actuator/prometheus

**Ingress (Production)**:
Update `ingress.yaml` with your domain and deploy.

## Manual Deployment

If you prefer to deploy step-by-step:

### 1. Create Namespace

```bash
kubectl apply -f agentmesh-deployment.yaml
# This creates the namespace, configmap, and secret
```

### 2. Deploy PostgreSQL

```bash
kubectl apply -f postgres-statefulset.yaml
kubectl wait --for=condition=ready pod -l app=postgres -n agentmesh --timeout=300s
```

### 3. Deploy Zookeeper & Kafka

```bash
kubectl apply -f kafka-statefulset.yaml
kubectl wait --for=condition=ready pod -l app=zookeeper -n agentmesh --timeout=300s
kubectl wait --for=condition=ready pod -l app=kafka -n agentmesh --timeout=300s
```

### 4. Deploy Redis

```bash
kubectl apply -f redis-statefulset.yaml
kubectl wait --for=condition=ready pod -l app=redis -n agentmesh --timeout=300s
```

### 5. Deploy AgentMesh

```bash
kubectl apply -f agentmesh-deployment.yaml
kubectl wait --for=condition=ready pod -l app=agentmesh -n agentmesh --timeout=300s
```

### 6. Deploy HPA

```bash
kubectl apply -f hpa.yaml
```

### 7. Deploy Ingress (Optional)

```bash
# Update domain in ingress.yaml first
kubectl apply -f ingress.yaml
```

## Configuration

### Secrets

**Before deploying to production**, update the secrets in `agentmesh-deployment.yaml`:

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: agentmesh-secrets
  namespace: agentmesh
type: Opaque
stringData:
  SPRING_DATASOURCE_USERNAME: "agentmesh"
  SPRING_DATASOURCE_PASSWORD: "CHANGE_ME_IN_PRODUCTION"  # ⚠️ UPDATE THIS
  GITHUB_TOKEN: "your-github-token"                       # ⚠️ UPDATE THIS
  OPENAI_API_KEY: "your-openai-key"                       # ⚠️ UPDATE THIS
```

**Recommended**: Use [Sealed Secrets](https://github.com/bitnami-labs/sealed-secrets) or [External Secrets](https://external-secrets.io/) for production.

### Resource Limits

Adjust resource requests/limits based on your workload:

**AgentMesh** (agentmesh-deployment.yaml):
```yaml
resources:
  requests:
    memory: "512Mi"
    cpu: "500m"
  limits:
    memory: "2Gi"
    cpu: "2000m"
```

**PostgreSQL** (postgres-statefulset.yaml):
```yaml
resources:
  requests:
    memory: "512Mi"
    cpu: "500m"
  limits:
    memory: "2Gi"
    cpu: "2000m"
```

### Storage

Update `storageClassName` in StatefulSets if needed:

```yaml
volumeClaimTemplates:
- metadata:
    name: postgres-data
  spec:
    accessModes: ["ReadWriteOnce"]
    resources:
      requests:
        storage: 50Gi
    storageClassName: standard  # ⚠️ Update if needed
```

### Scaling

**Manual Scaling**:
```bash
kubectl scale deployment agentmesh -n agentmesh --replicas=5
```

**Auto-Scaling**:
Configured in `hpa.yaml`:
- Min replicas: 3
- Max replicas: 10
- CPU target: 70%
- Memory target: 80%

## Monitoring

### Health Checks

```bash
# Overall health
kubectl exec -n agentmesh -it deployment/agentmesh -- curl localhost:8080/actuator/health

# Connection pool health
kubectl exec -n agentmesh -it deployment/agentmesh -- curl localhost:8080/actuator/health/connectionPool

# Liveness
kubectl exec -n agentmesh -it deployment/agentmesh -- curl localhost:8080/actuator/health/liveness

# Readiness
kubectl exec -n agentmesh -it deployment/agentmesh -- curl localhost:8080/actuator/health/readiness
```

### Logs

```bash
# All AgentMesh pods
kubectl logs -l app=agentmesh -n agentmesh --tail=100 -f

# Specific pod
kubectl logs -n agentmesh agentmesh-0 --tail=100 -f

# PostgreSQL
kubectl logs -n agentmesh postgres-0 --tail=50

# Redis
kubectl logs -n agentmesh redis-0 --tail=50

# Kafka
kubectl logs -n agentmesh kafka-0 --tail=50
```

### Metrics

```bash
# Prometheus metrics
kubectl exec -n agentmesh -it deployment/agentmesh -- curl localhost:8080/actuator/prometheus

# HPA status
kubectl get hpa -n agentmesh -w

# Resource usage
kubectl top pods -n agentmesh
kubectl top nodes
```

## Troubleshooting

### Pods Not Starting

```bash
# Check pod status
kubectl get pods -n agentmesh

# Describe pod
kubectl describe pod <pod-name> -n agentmesh

# Check events
kubectl get events -n agentmesh --sort-by='.lastTimestamp'

# Check logs
kubectl logs <pod-name> -n agentmesh
```

### Database Connection Issues

```bash
# Test PostgreSQL connection
kubectl exec -n agentmesh postgres-0 -- psql -U agentmesh -d agentmesh -c "SELECT 1"

# Check service
kubectl get svc postgres-service -n agentmesh

# Port forward to test locally
kubectl port-forward -n agentmesh svc/postgres-service 5432:5432
psql -h localhost -U agentmesh -d agentmesh
```

### Redis Connection Issues

```bash
# Test Redis connection
kubectl exec -n agentmesh redis-0 -- redis-cli ping

# Check Redis info
kubectl exec -n agentmesh redis-0 -- redis-cli info

# Port forward
kubectl port-forward -n agentmesh svc/redis-service 6379:6379
redis-cli -h localhost ping
```

### Kafka Issues

```bash
# List topics
kubectl exec -n agentmesh kafka-0 -- kafka-topics --list --bootstrap-server localhost:9092

# Check Zookeeper
kubectl exec -n agentmesh zookeeper-0 -- echo ruok | nc localhost 2181

# Kafka logs
kubectl logs -n agentmesh kafka-0 --tail=100
```

### Storage Issues

```bash
# Check PVC status
kubectl get pvc -n agentmesh

# Check PV status
kubectl get pv

# Describe PVC
kubectl describe pvc <pvc-name> -n agentmesh
```

## Backup & Restore

### PostgreSQL Backup

```bash
# Backup
kubectl exec -n agentmesh postgres-0 -- pg_dump -U agentmesh agentmesh > backup.sql

# Restore
cat backup.sql | kubectl exec -i -n agentmesh postgres-0 -- psql -U agentmesh agentmesh
```

### Redis Backup

```bash
# Trigger save
kubectl exec -n agentmesh redis-0 -- redis-cli BGSAVE

# Copy RDB file
kubectl cp agentmesh/redis-0:/data/dump.rdb ./dump.rdb
```

## Upgrading

### Rolling Update

```bash
# Update image
kubectl set image deployment/agentmesh agentmesh=agentmesh:1.1-SNAPSHOT -n agentmesh

# Check rollout status
kubectl rollout status deployment/agentmesh -n agentmesh

# Rollback if needed
kubectl rollout undo deployment/agentmesh -n agentmesh
```

### Database Migrations

1. Backup database (see above)
2. Apply Flyway/Liquibase migrations
3. Deploy new version
4. Verify application startup

## Cleanup

### Delete Everything

```bash
kubectl delete namespace agentmesh
```

### Delete Specific Components

```bash
kubectl delete -f agentmesh-deployment.yaml
kubectl delete -f redis-statefulset.yaml
kubectl delete -f postgres-statefulset.yaml
kubectl delete -f kafka-statefulset.yaml
kubectl delete -f hpa.yaml
kubectl delete -f ingress.yaml
```

## Production Checklist

- [ ] Update all secrets in `agentmesh-deployment.yaml`
- [ ] Configure persistent storage (update `storageClassName`)
- [ ] Set up TLS certificates (cert-manager or manual)
- [ ] Configure Ingress with your domain
- [ ] Set resource limits based on load testing
- [ ] Enable network policies (included in `ingress.yaml`)
- [ ] Set up monitoring (Prometheus + Grafana)
- [ ] Configure log aggregation (ELK, Loki, etc.)
- [ ] Set up backup strategy for PostgreSQL
- [ ] Configure Redis persistence settings
- [ ] Review and adjust HPA thresholds
- [ ] Test disaster recovery procedures
- [ ] Document runbook procedures

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Ingress Controller                    │
│                   (TLS Termination)                      │
└────────────────────┬────────────────────────────────────┘
                     │
        ┌────────────┼────────────┐
        │            │            │
        ▼            ▼            ▼
   ┌────────┐  ┌────────┐  ┌────────┐
   │AgentM-1│  │AgentM-2│  │AgentM-3│  (3-10 replicas, HPA)
   └───┬────┘  └───┬────┘  └───┬────┘
       │           │            │
       ├───────────┴────────────┤
       │                        │
   ┌───▼────┐              ┌───▼────┐
   │  Redis │              │Postgres│
   │(Cache) │              │  (DB)  │
   └────────┘              └────────┘
       │
   ┌───▼────────────┐
   │  Kafka (3x)    │
   │  Events        │
   └────────────────┘
       │
   ┌───▼────────────┐
   │ Zookeeper (3x) │
   │  Coordination  │
   └────────────────┘
```

## Support

For issues or questions:
1. Check logs: `kubectl logs -l app=agentmesh -n agentmesh`
2. Check events: `kubectl get events -n agentmesh`
3. Review troubleshooting section above
4. Contact platform team

## References

- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Spring Boot on Kubernetes](https://spring.io/guides/gs/spring-boot-kubernetes/)
- [Kafka on Kubernetes](https://strimzi.io/)
- [Redis on Kubernetes](https://redis.io/docs/management/kubernetes/)
