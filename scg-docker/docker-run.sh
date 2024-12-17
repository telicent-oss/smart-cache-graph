#!/bin/bash

# Consolidated script for building and running Docker containers for Smart Cache Graph
# Pass in "alpine" as a parameter to create the Alpine image
# Pass in "telicent" as a parameter to create the image using telicent's base image.

# Acquire the current directory in case we are running from project root or the local directory
CURRENT_DIR=$(dirname $0)

# Function for getting version number from the repo pom file
function get_version() {
  local version=$(mvn -q -f pom.xml -Dexec.executable=echo -Dexec.args="\${$1}" --non-recursive exec:exec)
  echo "$version"
}

# Fetch the project and Fuseki versions
PROJECT_VERSION=$(get_version project.version)
FUSEKI_SERVER_VERSION=$(get_version ver.fuseki-server)

# Check if versions were successfully extracted
if [[ -z "$PROJECT_VERSION" || -z "$FUSEKI_SERVER_VERSION" ]]; then
    echo "Error: Could not extract version information from POM file"
    exit 1
fi

# Find the relevant Fuseki JAR file from the ./target/dependency directory
FUSEKI_JAR=$(find ${CURRENT_DIR}/target/dependency -name "jena-fuseki-main-${FUSEKI_SERVER_VERSION}.jar" | head -n 1)

# Check if the specific version was found, otherwise find any available jena-fuseki-main JAR
if [[ -z "$FUSEKI_JAR" ]]; then
    FUSEKI_JAR=$(find ${CURRENT_DIR}/target/dependency -name "jena-fuseki-main-*.jar" | head -n 1)
fi

# Check if a JAR file was found
if [[ -z "$FUSEKI_JAR" ]]; then
    echo "Error: Could not find jena-fuseki-main JAR file in ./target/dependency"
    exit 1
fi
# Extract the JAR file name
FUSEKI_JAR_NAME=$(basename "$FUSEKI_JAR")

# Select the Dockerfile to use (default or alpine)
DOCKERFILE="Dockerfile"
IMAGE_TAG="smart-cache-graph:${PROJECT_VERSION}"
if [[ "$1" == "alpine" ]]; then
    DOCKERFILE="Dockerfile.alpine"
    IMAGE_TAG="smart-cache-graph:${PROJECT_VERSION}-alpine"
    shift # Remove the 'alpine' argument from the $@
elif [[ "$1" == "telicent" ]]; then
      DOCKERFILE="Dockerfile.telicent"
      IMAGE_TAG="smart-cache-graph:${PROJECT_VERSION}-telicent"
      shift # Remove the 'telicent' argument from the $@
fi

# Build the Docker image
docker build --build-arg FUSEKI_JAR="${FUSEKI_JAR_NAME}" \
             --build-arg PROJECT_VERSION="${PROJECT_VERSION}" \
             -t $IMAGE_TAG -f ${CURRENT_DIR}/${DOCKERFILE} ${CURRENT_DIR}/..

# Remove any existing container with the same name
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

# Prepare the mount directory for logs, databases, and config
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
    $IMAGE_TAG $ARGS