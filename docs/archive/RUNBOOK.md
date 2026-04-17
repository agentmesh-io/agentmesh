# AgentMesh Alert Runbooks - Quick Reference

## Critical Alerts Runbook

### 🚨 HighMASTViolationRate

**What**: > 10 violations/minute for 5 minutes  
**Impact**: Agents experiencing widespread issues  
**First Response** (< 5 minutes):

```bash
# 1. Check current violation rate
kubectl exec -it agentmesh-0 -- curl localhost:8080/actuator/prometheus | grep mast_violations_total

# 2. View recent violations
kubectl logs -l app=agentmesh --tail=50 | grep "MAST violation"

# 3. Check MAST dashboard
kubectl port-forward -n monitoring svc/grafana 3000:80
# Open http://localhost:3000/d/mast-dashboard
```

**Immediate Actions**:
- If spike correlates with deployment → rollback
- If LLM provider issue → switch provider or reduce load
- If specific agent type → disable that type

**Escalation**: Platform team lead if not resolved in 15 minutes

---

### 🚨 CriticalMASTViolations

**What**: > 2 critical violations/minute for 2 minutes  
**Impact**: Severe agent failures (safety, hallucinations)  
**First Response** (< 5 minutes):

```bash
# 1. Identify critical violations
kubectl logs -l app=agentmesh | grep "severity=CRITICAL" | tail -20

# 2. Check failure mode breakdown
kubectl exec -it agentmesh-0 -- curl localhost:8080/admin/mast/violations?severity=CRITICAL

# 3. Disable affected agent types if safety risk
kubectl exec -it agentmesh-0 -- curl -X POST localhost:8080/admin/agents/disable/TYPE_NAME
```

**Immediate Actions**:
- Identify affected failure modes (FM-01 through FM-40)
- If safety violations (FM-25, FM-26) → immediate shutdown
- If hallucinations (FM-10, FM-12) → switch to more conservative model

**Escalation**: Immediate escalation to on-call + quality team

---

### 🚨 AgentMeshPodsDown

**What**: < 2 pods available for 2 minutes  
**Impact**: High availability compromised, service degradation  
**First Response** (< 2 minutes):

```bash
# 1. Check pod status
kubectl get pods -l app=agentmesh -o wide

# 2. Describe failing pods
kubectl describe pod agentmesh-X

# 3. Check recent events
kubectl get events --sort-by='.lastTimestamp' | grep agentmesh | head -20

# 4. Check logs
kubectl logs agentmesh-X --tail=100
```

**Immediate Actions**:
- If OOMKilled → increase memory limits
- If CrashLoopBackOff → check application logs for startup errors
- If ImagePullBackOff → verify image availability
- If node issues → check node status, drain if needed

**Quick Fix**:
```bash
# Scale up to compensate
kubectl scale deployment agentmesh --replicas=5

# Force restart if needed
kubectl rollout restart deployment agentmesh
```

**Escalation**: Infrastructure team if pod issues persist > 5 minutes

---

### 🚨 DatabaseConnectionPoolExhausted

**What**: > 5 threads waiting for connections for 2 minutes  
**Impact**: Database operations blocked, cascading failures  
**First Response** (< 3 minutes):

```bash
# 1. Check connection pool status
kubectl exec -it agentmesh-0 -- curl localhost:8080/actuator/metrics/hikaricp.connections.active
kubectl exec -it agentmesh-0 -- curl localhost:8080/actuator/metrics/hikaricp.connections.pending

# 2. Identify long-running queries
kubectl exec -it postgres-0 -- psql -U agentmesh -c "
  SELECT pid, now() - query_start as duration, state, query 
  FROM pg_stat_activity 
  WHERE state = 'active' 
  ORDER BY duration DESC 
  LIMIT 10;"

# 3. Kill long-running queries if needed
kubectl exec -it postgres-0 -- psql -U agentmesh -c "SELECT pg_terminate_backend(PID);"
```

**Immediate Actions**:
- Temporarily increase pool size:
  ```bash
  kubectl set env deployment/agentmesh SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=50
  ```
- Kill long-running queries blocking connections
- Scale down load if needed

**Escalation**: Database team if pool exhaustion persists

---

### 🚨 AgentTaskFailureSpike

**What**: > 20% task failure rate for 5 minutes  
**Impact**: Widespread agent execution failures  
**First Response** (< 5 minutes):

```bash
# 1. Check failure breakdown
kubectl logs -l app=agentmesh --tail=200 | grep "task_status=FAILURE" | \
  awk '{print $5}' | sort | uniq -c | sort -rn

# 2. Check LLM provider status
kubectl exec -it agentmesh-0 -- curl localhost:8080/admin/llm/status

# 3. Check error patterns
kubectl logs -l app=agentmesh | grep ERROR | tail -50
```

**Immediate Actions**:
- If LLM timeouts → reduce concurrent requests
- If database errors → check database health
- If specific error pattern → targeted fix
- If recent deployment → rollback

**Quick Fix**:
```bash
# Reduce load
kubectl scale deployment agentmesh --replicas=2

# Rollback if needed
kubectl rollout undo deployment/agentmesh
```

**Escalation**: Platform lead if failures persist > 10 minutes

---

### 🚨 RedisClusterDown

**What**: Redis cluster unreachable for 1 minute  
**Impact**: Cache unavailable, performance degradation  
**First Response** (< 2 minutes):

```bash
# 1. Check Redis pod status
kubectl get pods -l app=redis-cluster

# 2. Check Sentinel status
kubectl exec -it redis-sentinel-0 -- redis-cli -p 26379 SENTINEL masters

# 3. Test connectivity
kubectl exec -it agentmesh-0 -- redis-cli -h redis-cluster-service -p 6379 ping

# 4. Check logs
kubectl logs redis-cluster-0 --tail=100
```

**Immediate Actions**:
- If master down → verify Sentinel triggering failover
- If all nodes down → check network/storage
- Manual failover if needed:
  ```bash
  kubectl exec -it redis-sentinel-0 -- redis-cli -p 26379 SENTINEL failover mymaster
  ```

**Impact**: Application continues but slower (cache misses)

**Escalation**: Infrastructure team immediately

---

### 🚨 PostgreSQLDown

**What**: PostgreSQL unreachable for 1 minute  
**Impact**: CRITICAL - Database unavailable, total outage  
**First Response** (< 1 minute):

```bash
# 1. Check PostgreSQL pod
kubectl get pods -l app=postgres

# 2. Check pod events
kubectl describe pod postgres-0 | grep -A 20 Events

# 3. Check logs
kubectl logs postgres-0 --tail=100

# 4. Check PVC status
kubectl get pvc postgres-data
```

**Immediate Actions**:
- Page database team immediately
- If pod crashed → check for automatic restart
- If PVC issues → storage team escalation
- Activate incident response protocol

**DO NOT** restart pod without database team approval

**Escalation**: IMMEDIATE - Database team + on-call manager

---

## High Severity Quick Actions

### ⚠️ HighDatabaseLatency (P95 > 500ms)

```bash
# Check slow queries
kubectl exec -it postgres-0 -- psql -U agentmesh -c "
  SELECT query, mean_exec_time, calls 
  FROM pg_stat_statements 
  ORDER BY mean_exec_time DESC 
  LIMIT 10;"

# Run ANALYZE
kubectl exec -it postgres-0 -- psql -U agentmesh -c "ANALYZE VERBOSE;"
```

---

### ⚠️ LowCacheHitRate (< 50%)

```bash
# Check cache stats
kubectl exec -it agentmesh-0 -- curl localhost:8080/actuator/metrics/agentmesh.cache

# Increase TTL
kubectl set env deployment/agentmesh CACHE_TTL_VIOLATION=7200
```

---

### ⚠️ HighLLMLatency (P95 > 10s)

```bash
# Reduce concurrent calls
kubectl set env deployment/agentmesh LLM_MAX_CONCURRENT=5

# Switch to faster model
kubectl set env deployment/agentmesh LLM_MODEL=gpt-4o-mini
```

---

## Common Commands Reference

### Pod Management
```bash
# Get all AgentMesh pods
kubectl get pods -l app=agentmesh

# Describe pod
kubectl describe pod POD_NAME

# View logs
kubectl logs POD_NAME --tail=100 -f

# Exec into pod
kubectl exec -it POD_NAME -- /bin/bash

# Delete pod (recreated by deployment)
kubectl delete pod POD_NAME
```

### Deployment Management
```bash
# Scale deployment
kubectl scale deployment agentmesh --replicas=N

# Restart deployment
kubectl rollout restart deployment agentmesh

# Rollback deployment
kubectl rollout undo deployment agentmesh

# Check rollout status
kubectl rollout status deployment agentmesh
```

### Metrics & Monitoring
```bash
# Port-forward Prometheus
kubectl port-forward -n monitoring svc/prometheus 9090:9090

# Port-forward Grafana
kubectl port-forward -n monitoring svc/grafana 3000:80

# Port-forward AlertManager
kubectl port-forward -n monitoring svc/alertmanager-agentmesh 9093:9093

# Check metrics endpoint
kubectl exec -it agentmesh-0 -- curl localhost:8080/actuator/prometheus
```

### Database Operations
```bash
# Connect to PostgreSQL
kubectl exec -it postgres-0 -- psql -U agentmesh

# Check active connections
kubectl exec -it postgres-0 -- psql -U agentmesh -c "SELECT count(*) FROM pg_stat_activity;"

# Kill connection
kubectl exec -it postgres-0 -- psql -U agentmesh -c "SELECT pg_terminate_backend(PID);"
```

### Redis Operations
```bash
# Connect to Redis
kubectl exec -it redis-cluster-0 -- redis-cli

# Check Sentinel
kubectl exec -it redis-sentinel-0 -- redis-cli -p 26379 SENTINEL masters

# Test connectivity
kubectl exec -it agentmesh-0 -- redis-cli -h redis-cluster-service -p 6379 ping
```

---

## Escalation Matrix

| Severity | Response Time | Escalate To | Method |
|----------|--------------|-------------|---------|
| Critical | < 5 minutes | On-call engineer | PagerDuty |
| Critical (DB/Infra) | < 1 minute | Infrastructure team | PagerDuty |
| High | < 1 hour | Team lead | Slack |
| Warning | < 1 day | Team channel | Slack |
| Info | N/A | Logs only | N/A |

## Contact Information

- **On-Call Engineer**: PagerDuty rotation
- **Platform Team Lead**: #platform-team Slack
- **Database Team**: #database-team Slack
- **Infrastructure Team**: #infrastructure Slack
- **Quality Team**: #quality-team Slack

## Post-Incident

After resolving any critical or high severity alert:

1. **Document**: Add incident notes to runbook
2. **Review**: Schedule post-mortem within 48 hours
3. **Update**: Improve alerts/runbooks based on learnings
4. **Communicate**: Update stakeholders on resolution and prevention

---

**Last Updated**: 2025-11-09  
**Version**: 1.0  
**Owner**: Platform Team
