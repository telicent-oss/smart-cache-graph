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
  native_top
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
