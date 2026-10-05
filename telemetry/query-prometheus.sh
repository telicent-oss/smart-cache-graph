#!/usr/bin/env bash
#
# Copyright (C) 2022 Telicent Limited
#

set -euo pipefail

PROMETHEUS_URL=${PROMETHEUS_URL:-http://localhost:9091}

function abort() {
  echo "ERROR: $*" 1>&2
  exit 1
}

function require_command() {
  command -v "$1" >/dev/null 2>&1 || abort "Required command '$1' is not installed"
}

function usage() {
  cat <<EOF
Usage: $(basename "$0") <query-name>

Available query names:
  targets
  custom_metric_names
  native_metric_names
  request_totals
  request_totals_by_endpoint
  label_store_totals
  label_store_rates
  label_store_duplicate_avoidance
  native_top
  jvm_gc_rates
  jvm_gc_paused_percent
  jvm_direct_buffers
  tdb2_disk_usage
EOF
}

function promql_for() {
  case "$1" in
    targets)
      cat <<'EOF'
up{job=~"smart-cache-graph-otel|smart-cache-graph-fuseki"}
EOF
      ;;
    custom_metric_names)
      cat <<'EOF'
count by (__name__) ({job="smart-cache-graph-otel", __name__=~"smartcache_graph_.*"})
EOF
      ;;
    native_metric_names)
      cat <<'EOF'
count by (__name__) ({job="smart-cache-graph-fuseki"})
EOF
      ;;
    request_totals)
      cat <<'EOF'
sum by (__name__) ({job="smart-cache-graph-otel", __name__=~"smartcache_graph_request_(good|bad|total)"})
EOF
      ;;
    request_totals_by_endpoint)
      cat <<'EOF'
sum by (__name__, fuseki_endpoint, db_operation) ({job="smart-cache-graph-otel", __name__=~"smartcache_graph_request_(good|bad|total)"})
EOF
      ;;
    label_store_totals)
      cat <<'EOF'
sum by (__name__, db_name) ({job="smart-cache-graph-otel", __name__=~"smartcache_graph_labels_(add_attempts|cache_noops|writes)_total"})
EOF
      ;;
    label_store_rates)
      cat <<'EOF'
sum by (__name__, db_name) (rate({job="smart-cache-graph-otel", __name__=~"smartcache_graph_labels_(add_attempts|cache_noops|writes)_total"}[5m]))
EOF
      ;;
    label_store_duplicate_avoidance)
      cat <<'EOF'
100 * sum by (db_name) (smartcache_graph_labels_cache_noops_total{job="smart-cache-graph-otel"}) / clamp_min(sum by (db_name) (smartcache_graph_labels_add_attempts_total{job="smart-cache-graph-otel"}), 1)
EOF
      ;;
    jvm_gc_rates)
      cat <<'EOF'
60 * sum by (jvm_gc_name, jvm_gc_action) (rate(jvm_gc_duration_seconds_count{job="smart-cache-graph-otel"}[5m]))
EOF
      ;;
    jvm_gc_paused_percent)
      cat <<'EOF'
100 * sum by (jvm_gc_name) (rate(jvm_gc_duration_seconds_sum{job="smart-cache-graph-otel"}[5m]))
EOF
      ;;
    jvm_direct_buffers)
      cat <<'EOF'
sum by (__name__) ({job="smart-cache-graph-otel", __name__=~"jvm_buffer_(memory_used_bytes|count)", jvm_buffer_pool_name="direct"})
EOF
      ;;
    tdb2_disk_usage)
      cat <<'EOF'
sum by (db_name) (smartcache_graph_tdb2_disk_usage_bytes{job="smart-cache-graph-otel"})
EOF
      ;;
    native_top)
      cat <<'EOF'
topk(15, sum by (__name__) ({job="smart-cache-graph-fuseki"}))
EOF
      ;;
    *)
      return 1
      ;;
  esac
}

require_command curl

if [ $# -ne 1 ]; then
  usage
  exit 1
fi

QUERY_NAME=$1
PROMQL=$(promql_for "${QUERY_NAME}") || {
  usage
  exit 1
}

echo "Query name: ${QUERY_NAME}"
echo "PromQL: ${PROMQL}"
echo ""

if command -v jq >/dev/null 2>&1; then
  curl -gsS --data-urlencode "query=${PROMQL}" "${PROMETHEUS_URL}/api/v1/query" | jq
else
  curl -gsS --data-urlencode "query=${PROMQL}" "${PROMETHEUS_URL}/api/v1/query"
fi
