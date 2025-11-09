# Grafana Dashboards Guide

## Overview

AgentMesh includes **3 pre-built Grafana dashboards** for comprehensive monitoring:

1. **Overview Dashboard** - High-level metrics and KPIs
2. **Performance Dashboard** - Latency and performance metrics
3. **MAST Dashboard** - Health and violation tracking

## Dashboard Descriptions

### 1. Overview Dashboard (`dashboard-overview.json`)

**Purpose**: Executive summary and real-time operations monitoring

**Panels**:
- **Active Agents** (Stat) - Current active agent count
- **Agent Success Rate** (Stat) - Task success percentage
- **Cache Hit Rate** (Stat) - Caching effectiveness
- **Unresolved MAST Violations** (Stat) - Health indicator
- **Agent Task Execution Rate** (Graph) - Tasks/sec over time
- **LLM Token Consumption** (Graph) - Token usage trend
- **MAST Violations by Severity** (Pie Chart) - Severity distribution
- **Blackboard Operations** (Graph) - Read/write activity
- **HikariCP Pool Utilization** (Graph) - Connection pool usage

**Use Cases**:
- Daily operations monitoring
- Executive reporting
- Quick health checks
- Capacity planning

### 2. Performance Dashboard (`dashboard-performance.json`)

**Purpose**: Deep-dive into latency and performance bottlenecks

**Panels**:
- **LLM Call Latency** (Graph) - P50, P95, P99 latencies
- **Database Query Latency** (Graph) - P95 query time with alerts
- **Blackboard Query Latency** (Graph) - Average query time
- **Vector Search Latency** (Graph) - P99 search time
- **Agent Execution Time by Type** (Graph) - Performance by agent type
- **Cache Operation Latency** (Graph) - P95 cache ops by cache name
- **Self-Correction Duration** (Graph) - Average correction time
- **JVM Memory Usage** (Graph) - Heap utilization

**Alerts**:
- High Database Latency (> 500ms P95)

**Use Cases**:
- Performance optimization
- Identifying slow components
- SLA monitoring
- Troubleshooting latency issues

### 3. MAST Dashboard (`dashboard-mast.json`)

**Purpose**: Agent health and MAST violation monitoring

**Panels**:
- **MAST Violations per Minute** (Graph) - Total and critical violations with alerts
- **Violations by Failure Mode** (Graph) - Breakdown by FM code
- **Violations by Category** (Pie Chart) - Category distribution
- **Top 10 Violation Modes** (Table) - Most common failures (last hour)
- **Severity Distribution** (Bar Gauge) - LOW, MEDIUM, HIGH, CRITICAL counts
- **Unresolved Violations Trend** (Graph) - Backlog trend
- **Self-Correction Success Rate** (Stat) - Correction effectiveness
- **Self-Correction Attempts** (Graph) - Attempts/success/failures over time

**Alerts**:
- High MAST Violations (> 10/min average over 5 min)

**Use Cases**:
- Agent health monitoring
- Quality assurance
- Incident response
- Continuous improvement tracking

## Installation

### Method 1: Automated Import (Recommended)

```bash
# Navigate to Grafana directory
cd k8s/grafana

# Run import script
./import-dashboards.sh
```

This creates a ConfigMap with all dashboards and labels it for Grafana sidecar auto-discovery.

### Method 2: Manual Import

```bash
# Create ConfigMap manually
kubectl create configmap grafana-dashboards-agentmesh \
  --from-file=dashboard-overview.json \
  --from-file=dashboard-performance.json \
  --from-file=dashboard-mast.json \
  --namespace=monitoring

# Label for auto-discovery
kubectl label configmap grafana-dashboards-agentmesh \
  grafana_dashboard="1" \
  --namespace=monitoring
```

### Method 3: Grafana UI Import

1. Access Grafana UI:
   ```bash
   kubectl port-forward -n monitoring svc/grafana 3000:80
   ```

2. Open http://localhost:3000

3. Navigate: **Dashboards** → **Import** → **Upload JSON file**

4. Import each dashboard JSON file

5. Select folder: **AgentMesh**

## Configuration

### Grafana Sidecar Auto-Discovery

If using Prometheus Operator with Grafana sidecar:

```yaml
# Grafana Helm values
grafana:
  sidecar:
    dashboards:
      enabled: true
      label: grafana_dashboard
      folder: /var/lib/grafana/dashboards
```

Dashboards are automatically discovered and imported.

### Manual Configuration

Edit `grafana-dashboard-provider` ConfigMap:

```yaml
apiVersion: 1
providers:
- name: 'AgentMesh'
  orgId: 1
  folder: 'AgentMesh'
  type: file
  disableDeletion: false
  updateIntervalSeconds: 30
  allowUiUpdates: true
  options:
    path: /var/lib/grafana/dashboards/agentmesh
```

## Accessing Dashboards

### Via Port-Forward

```bash
# Forward Grafana port
kubectl port-forward -n monitoring svc/grafana 3000:80

# Access at http://localhost:3000
# Default credentials: admin / admin (change on first login)
```

### Via Ingress

If Grafana Ingress is configured:

```bash
# Check Ingress
kubectl get ingress -n monitoring grafana

# Access via hostname (e.g., https://grafana.example.com)
```

### Via NodePort

If using NodePort service:

```bash
# Get NodePort
kubectl get svc -n monitoring grafana

# Access at http://<node-ip>:<nodeport>
```

## Customization

### Adding Panels

1. Access dashboard in Grafana UI
2. Click **Add Panel**
3. Select **Add a new panel**
4. Configure query:
   ```promql
   # Example: Custom metric
   sum(rate(agentmesh_custom_metric[5m]))
   ```
5. Adjust visualization settings
6. Save dashboard

### Modifying Queries

Edit JSON file directly:

```json
{
  "targets": [{
    "expr": "your_prometheus_query_here",
    "legendFormat": "{{label}}"
  }]
}
```

Re-import dashboard after changes.

### Variables

Add template variables for filtering:

```json
{
  "templating": {
    "list": [
      {
        "name": "tenant",
        "type": "query",
        "query": "label_values(agentmesh_agent_tasks_total, tenant_id)",
        "label": "Tenant"
      }
    ]
  }
}
```

Use in queries: `{tenant_id=\"$tenant\"}`

## Alert Configuration

Dashboards include embedded alerts:

### Database Latency Alert

**Condition**: P95 query time > 500ms
**Frequency**: Evaluated every 1 minute
**Notification**: Sends to default Grafana notification channel

**Configuration**:
```json
{
  "alert": {
    "conditions": [{
      "evaluator": {"params": [0.5], "type": "gt"},
      "query": {"params": ["A", "5m", "now"]},
      "reducer": {"type": "avg"}
    }],
    "frequency": "1m",
    "name": "High Database Latency",
    "message": "Database queries are slow (P95 > 500ms)"
  }
}
```

### MAST Violations Alert

**Condition**: Violations > 10/min (avg over 5 min)
**Frequency**: Evaluated every 1 minute
**Severity**: Critical

## Best Practices

### 1. Dashboard Organization

- **Folder Structure**: Organize by service (AgentMesh, Infrastructure, etc.)
- **Naming Convention**: Prefix with service name (e.g., "AgentMesh - Overview")
- **Tags**: Use tags for easy filtering (`agentmesh`, `performance`, `mast`)

### 2. Panel Design

- **Titles**: Clear, descriptive panel titles
- **Units**: Specify units (seconds, bytes, percent)
- **Legends**: Use meaningful legend formats with labels
- **Colors**: Use thresholds for visual alerts (green/yellow/red)

### 3. Time Ranges

- **Default**: 1 hour for operations dashboards
- **Performance**: 6 hours for trend analysis
- **Troubleshooting**: Flexible (last 15m to 24h)

### 4. Refresh Rates

- **High Frequency**: 10s - 30s for operations
- **Medium Frequency**: 1m - 5m for analysis
- **Low Frequency**: 5m - 15m for reports

## Troubleshooting

### Dashboards Not Appearing

**Check ConfigMap**:
```bash
kubectl get cm -n monitoring grafana-dashboards-agentmesh
kubectl describe cm -n monitoring grafana-dashboards-agentmesh
```

**Check Labels**:
```bash
kubectl get cm -n monitoring grafana-dashboards-agentmesh --show-labels
```

Should have `grafana_dashboard=1`.

**Check Grafana Logs**:
```bash
kubectl logs -n monitoring deployment/grafana | grep dashboard
```

### No Data in Panels

**Verify Prometheus**:
```bash
# Check Prometheus targets
kubectl port-forward -n monitoring svc/prometheus 9090:9090
# Visit http://localhost:9090/targets
# Ensure agentmesh targets are "UP"
```

**Test Query**:
```bash
# In Prometheus UI
agentmesh_agents_active
```

If no data, check ServiceMonitor configuration.

### Slow Dashboard Loading

**Optimize Queries**:
- Use recording rules for complex queries
- Reduce time range
- Limit number of series

**Example**:
```promql
# Instead of
sum(rate(agentmesh_cache_hits_total[5m])) / (sum(rate(agentmesh_cache_hits_total[5m])) + sum(rate(agentmesh_cache_misses_total[5m])))

# Use recording rule
agentmesh:cache:hit_rate
```

## Maintenance

### Updating Dashboards

1. Make changes in Grafana UI
2. Export dashboard JSON:
   - **Dashboard Settings** → **JSON Model** → Copy JSON
3. Save to file (e.g., `dashboard-overview.json`)
4. Update ConfigMap:
   ```bash
   ./import-dashboards.sh
   ```

### Version Control

Keep dashboard JSON files in Git:
```bash
git add k8s/grafana/*.json
git commit -m "Update Grafana dashboards"
```

### Backup

Export all dashboards:
```bash
# Using Grafana API
for dashboard in $(curl -s "http://admin:admin@localhost:3000/api/search?query=&" | jq -r '.[].uid'); do
  curl -s "http://admin:admin@localhost:3000/api/dashboards/uid/$dashboard" | jq '.dashboard' > "backup-$dashboard.json"
done
```

## References

- [Grafana Documentation](https://grafana.com/docs/grafana/latest/)
- [Prometheus Query Examples](https://prometheus.io/docs/prometheus/latest/querying/examples/)
- [Grafana Dashboard Best Practices](https://grafana.com/docs/grafana/latest/best-practices/best-practices-for-creating-dashboards/)
- [AgentMesh Metrics Guide](../prometheus-metrics-guide.md)

## Dashboard Screenshots

*Note: Add screenshots here after deployment*

### Overview Dashboard
- Active agents, success rate, cache performance
- Real-time task execution and token consumption

### Performance Dashboard
- Latency percentiles (P50, P95, P99)
- Database, cache, and search performance

### MAST Dashboard
- Violation trends and severity distribution
- Self-correction effectiveness
