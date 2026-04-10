#!/usr/bin/env bash
#
# Copyright (C) 2022 Telicent Limited
#

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

SCG_URL=http://localhost:3030
SCG_METRICS_URL=http://localhost:9466/metrics
FUSEKI_METRICS_URL=http://localhost:3030/\$/metrics
PROMETHEUS_URL=http://localhost:9091
DATASET_PATH=/ds
ROUNDS=4
SLEEP_SECS=0.2
REQUEST_ID_PREFIX=scg-telemetry-demo

function abort() {
  echo "ERROR: $*" 1>&2
  exit 1
}

function require_command() {
  command -v "$1" >/dev/null 2>&1 || abort "Required command '$1' is not installed"
}

function wait_for_url() {
  local label=$1
  local url=$2
  local attempts=${3:-60}

  echo "Waiting for ${label} at ${url}"
  for ((i = 1; i <= attempts; i++)); do
    if curl -fsS "${url}" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done

  abort "Timed out waiting for ${label} at ${url}"
}

function issue_get_query() {
  local label=$1
  local endpoint=$2
  local query=$3
  local request_id=$4
  local stats

  stats=$(curl -gsS -o /dev/null \
    -H "Accept: application/sparql-results+json" \
    -H "X-Request-ID: ${request_id}" \
    --data-urlencode "query=${query}" \
    -w "status=%{http_code} time=%{time_total}s size=%{size_download}" \
    "${SCG_URL}${endpoint}")

  echo "  ${label}: ${stats}"
}

function issue_post_query() {
  local label=$1
  local endpoint=$2
  local query=$3
  local request_id=$4
  local stats

  stats=$(curl -sS -o /dev/null \
    -H "Accept: application/sparql-results+json" \
    -H "Content-Type: application/sparql-query" \
    -H "X-Request-ID: ${request_id}" \
    --data "${query}" \
    -w "status=%{http_code} time=%{time_total}s size=%{size_download}" \
    "${SCG_URL}${endpoint}")

  echo "  ${label}: ${stats}"
}

function issue_bad_query() {
  local label=$1
  local endpoint=$2
  local query=$3
  local request_id=$4
  local stats

  stats=$(curl -gsS -o /dev/null \
    -H "Accept: application/sparql-results+json" \
    -H "X-Request-ID: ${request_id}" \
    --data-urlencode "query=${query}" \
    -w "status=%{http_code} time=%{time_total}s size=%{size_download}" \
    "${SCG_URL}${endpoint}" || true)

  echo "  ${label}: ${stats}"
}

require_command curl

wait_for_url "Smart Cache Graph" "${SCG_URL}/\$/metrics"
wait_for_url "OTel exporter" "${SCG_METRICS_URL}"
wait_for_url "Prometheus" "${PROMETHEUS_URL}/-/ready"

echo "Generating Smart Cache Graph traffic against ${SCG_URL}${DATASET_PATH}"
for ((round = 1; round <= ROUNDS; round++)); do
  echo "Round ${round}/${ROUNDS}"
  issue_get_query \
    "dataset root query" \
    "${DATASET_PATH}" \
    "SELECT * WHERE { ?s ?p ?o } LIMIT 5" \
    "${REQUEST_ID_PREFIX}-root-${round}"
  issue_get_query \
    "named sparql query" \
    "${DATASET_PATH}/sparql" \
    "SELECT ?person ?label WHERE { ?person <http://www.w3.org/2000/01/rdf-schema#label> ?label } ORDER BY ?label" \
    "${REQUEST_ID_PREFIX}-sparql-${round}"
  issue_post_query \
    "POST sparql query" \
    "${DATASET_PATH}/sparql" \
    "ASK { <http://example/person4321> ?p ?o }" \
    "${REQUEST_ID_PREFIX}-post-${round}"
  issue_bad_query \
    "bad query" \
    "${DATASET_PATH}/sparql" \
    "SELECT WHERE { ?s ?p ?o }" \
    "${REQUEST_ID_PREFIX}-bad-${round}"
  sleep "${SLEEP_SECS}"
done

echo ""
echo "Quick metric checks"
echo "  OTel exporter:"
curl -fsS "${SCG_METRICS_URL}" | grep -E '^smartcache_graph_' | head -n 12 || true
echo ""
echo "  Native Fuseki metrics:"
curl -fsS "${FUSEKI_METRICS_URL}" | head -n 12 || true
echo ""
echo "  Prometheus summary:"
"${SCRIPT_DIR}/query-prometheus.sh" request_totals || true
echo ""
echo "  Grafana:    http://localhost:3001"
echo "  Prometheus: http://localhost:9091"
echo "  Other queries:"
echo "    ${SCRIPT_DIR}/query-prometheus.sh targets"
echo "    ${SCRIPT_DIR}/query-prometheus.sh custom_metric_names"
echo "    ${SCRIPT_DIR}/query-prometheus.sh request_totals_by_endpoint"
echo "    ${SCRIPT_DIR}/query-prometheus.sh native_metric_names"
