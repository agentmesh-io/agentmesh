#!/bin/bash

# Grafana Dashboard Import Script
# Generates ConfigMap from dashboard JSON files

set -e

NAMESPACE="monitoring"
DASHBOARD_DIR="$(dirname "$0")"

echo "Creating Grafana dashboards ConfigMap..."

kubectl create configmap grafana-dashboards-agentmesh \
  --from-file="$DASHBOARD_DIR/dashboard-overview.json" \
  --from-file="$DASHBOARD_DIR/dashboard-performance.json" \
  --from-file="$DASHBOARD_DIR/dashboard-mast.json" \
  --namespace="$NAMESPACE" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "✓ Dashboards ConfigMap created"

# Label for Grafana sidecar discovery
kubectl label configmap grafana-dashboards-agentmesh \
  grafana_dashboard="1" \
  --namespace="$NAMESPACE" \
  --overwrite

echo "✓ Dashboard labeled for auto-discovery"
echo ""
echo "Dashboards will be automatically imported to Grafana."
echo "Access Grafana at: http://grafana.monitoring.svc.cluster.local"
