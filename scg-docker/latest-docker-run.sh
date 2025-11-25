#!/usr/bin/env bash
set -euo pipefail

# Run the latest Smart Cache Graph image from Docker Hub.
# Usage:
#   ./latest-docker-run.sh                 # uses defaults; starts in-memory dataset at /ds
#   ./latest-docker-run.sh -- --mem /ds    # explicitly pass Fuseki args after --
#   SCG_IMAGE=telicent/smart-cache-graph:1.2.3 ./latest-docker-run.sh  # override image tag
#   SCG_PORT=4040 ./latest-docker-run.sh   # change host port

# --- Config (overridable via env) ------------------------------------------
IMAGE="${SCG_IMAGE:-telicent/smart-cache-graph:latest}"
CONTAINER_NAME="${SCG_CONTAINER_NAME:-smart-cache-graph-container}"
HOST_PORT="${SCG_PORT:-3030}"

# Common envs used by your image
JAVA_OPTIONS="${JAVA_OPTIONS:--Xmx2048m -Xms2048m}"
#JWKS_URL="${JWKS_URL:-disabled}"
#JWKS_URL="https://auth.telicent.localhost/oauth2/jwks"
JWKS_URL="http://auth-server:9000/oauth2/jwks"
USERINFO_URL="http://auth-server:9000/userinfo"
#JWKS_URL="https://auth.devops.telicent-sandbox.telicent.live/keys"
ALLOW_INSECURE_JWKS=true
ENABLE_LABELS_QUERY="${ENABLE_LABELS_QUERY:-true}"

# Where to create mounts (same structure as before)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MNT_DIR="${SCRIPT_DIR}/mnt"

# --- Checks ----------------------------------------------------------------
if ! command -v docker >/dev/null 2>&1; then
  echo "Error: Docker is required but not installed." >&2
  exit 1
fi

# Quick daemon check
if ! docker info >/dev/null 2>&1; then
  echo "Error: Docker daemon not running or not accessible." >&2
  exit 1
fi

# --- Prepare mounts --------------------------------------------------------
mkdir -p "${MNT_DIR}/logs" "${MNT_DIR}/databases" "${MNT_DIR}/config"

# --- Choose Fuseki args ----------------------------------------------------
FUSEKI_ARGS=()
if [[ "${1:-}" == "--" ]]; then
  shift
  FUSEKI_ARGS=("$@")
else
  # Default if none provided
  if [[ $# -eq 0 ]]; then
    FUSEKI_ARGS=(--mem /ds)
  else
    FUSEKI_ARGS=("$@")
  fi
fi

# --- Pull image (only updates if needed) -----------------------------------
echo "Pulling ${IMAGE} ..."
docker pull "${IMAGE}" >/dev/null

# --- Stop/remove any existing container ------------------------------------
EXISTING="$(docker ps -aq -f name=^${CONTAINER_NAME}$ || true)"
if [[ -n "${EXISTING}" ]]; then
  echo "Stopping and removing existing container ${CONTAINER_NAME} ..."
  docker stop "${CONTAINER_NAME}" >/dev/null 2>&1 || true
  docker rm "${CONTAINER_NAME}"  >/dev/null 2>&1 || true
fi

# --- Run container ---------------------------------------------------------
echo "Starting ${CONTAINER_NAME} on http://localhost:${HOST_PORT} ..."
docker run -d \
  --name "${CONTAINER_NAME}" \
  -e JAVA_OPTIONS="${JAVA_OPTIONS}" \
  -e JWKS_URL="${JWKS_URL}" \
  -e USERINFO_URL="${USERINFO_URL}" \
  -e ALLOW_INSECURE_JWKS=true \
  -e ENABLE_LABELS_QUERY="${ENABLE_LABELS_QUERY}" \
  --network authorizationserver_auth-internal-network \
  -p "${HOST_PORT}:3030" \
  -v "${MNT_DIR}/logs:/fuseki/logs" \
  -v "${MNT_DIR}/databases:/fuseki/databases" \
  -v "${MNT_DIR}/config:/fuseki/config" \
  "${IMAGE}" "${FUSEKI_ARGS[@]}"

echo "Container '${CONTAINER_NAME}' is up."
echo "  Open:   http://localhost:${HOST_PORT}"
echo "  Logs:   docker logs -f ${CONTAINER_NAME}"
echo "  Stop:   docker stop ${CONTAINER_NAME}"
echo "  Remove: docker rm ${CONTAINER_NAME}"
