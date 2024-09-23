#!/bin/bash

# Acquire the current directory in case we are running from project root or the local directory
CURRENT_DIR=$(dirname $0)

# Function for getting version number from the repo pom file
function get_version() {
  local version=$(mvn -q -f pom.xml -Dexec.executable=echo -Dexec.args="\${$1}" --non-recursive exec:exec)
  echo "$version"
}

PROJECT_VERSION=$(get_version project.version)
# Check if the PROJECT_VERSION was successfully extracted
if [[ -z "$PROJECT_VERSION" ]]; then
    echo "Error: Could not extract PROJECT_VERSION from POM file"
    exit 1
fi

FUSEKI_SERVER_VERSION=$(get_version ver.fuseki-server)
# Find the relevant Fuseki JAR file from the ./target/dependency directory
FUSEKI_JAR=$(find ${CURRENT_DIR}/target/dependency -name "jena-fuseki-main-${FUSEKI_SERVER_VERSION}.jar" | head -n 1)
# Check if the specific version was found
if [[ -z "$FUSEKI_JAR" ]]; then
    # If not, find any available jena-fuseki-main JAR
    FUSEKI_JAR=$(find ${CURRENT_DIR}/target/dependency -name "jena-fuseki-main-*.jar" | head -n 1)
fi
# Check if a JAR file was found
if [[ -z "$FUSEKI_JAR" ]]; then
    echo "Error: Could not find jena-fuseki-main JAR file in ./target/dependency"
    exit 1
fi
# Extract the JAR file name
FUSEKI_JAR_NAME=$(basename "$FUSEKI_JAR")

# Build the Docker image, passing the FUSEKI_JAR file name and PROJECT_VERSION as build arguments
docker build --build-arg FUSEKI_JAR="${FUSEKI_JAR_NAME}" \
             --build-arg PROJECT_VERSION="${PROJECT_VERSION}" \
             -t smart-cache-graph:"${PROJECT_VERSION}" -f ${CURRENT_DIR}/Dockerfile ${CURRENT_DIR}/..

# Remove previous entry if exists
EXISTING_CONTAINER=$(docker ps -a -q -f name=smart-cache-graph-container)
if [ -n "$EXISTING_CONTAINER" ]; then
    docker stop smart-cache-graph-container 2>/dev/null
    docker rm smart-cache-graph-container 2>/dev/null
fi

# Check if $@ is empty and set it to a default value (in memory database with a /ds dataset)
ARGS="$@"
if [[ -z "$ARGS" ]]; then
    ARGS="--mem /ds"
fi

MNT_DIR=$(pwd)/${CURRENT_DIR}/mnt

# Run the Docker container, mapping port 3030, setting the necessary environment variables
docker run -d \
    -e JAVA_OPTIONS="-Xmx2048m -Xms2048m" \
    -e JWKS_URL="disabled" \
    -p 3030:3030 \
    -v "$MNT_DIR/logs:/fuseki/logs"      \
    -v "$MNT_DIR/databases:/fuseki/databases" \
    -v "$MNT_DIR/config:/fuseki/config"  \
    --name smart-cache-graph-container \
    smart-cache-graph:"${PROJECT_VERSION}" $ARGS
