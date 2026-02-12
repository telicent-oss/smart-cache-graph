#!/bin/sh

# Runs in the smart-cache-graph container, with directory /fuseki/.
## This starts Fuseki.
## Command line arguments are passed from "docker run"

# env | sort

MAIN=io.telicent.core.MainSmartCacheGraph
FUSEKI_LIB="${FUSEKI_DIR}/lib"

## All in one directory
FUSEKI_CP="$FUSEKI_LIB"'/*'

if [ -z "${LOG4J_CONFIGURATION_FILE}" ] && [ -f "${FUSEKI_DIR}/log4j2.properties" ]; then
  LOG4J_CONFIGURATION_FILE="${FUSEKI_DIR}/log4j2.properties"
fi
if [ -n "${LOG4J_CONFIGURATION_FILE}" ]; then
  JAVA_OPTIONS="-Dlog4j2.configurationFile=${LOG4J_CONFIGURATION_FILE} ${JAVA_OPTIONS}"
fi

env | grep "OTEL" >/dev/null 2>&1
if [ $? -eq 0 ]; then
  export FUSEKI_FMOD_OTEL=true
  if [ -z "${OTEL_SERVICE_NAME}" ]; then
    export OTEL_SERVICE_NAME="smart-cache-graph"
  fi
  JAVA_OPTIONS="-javaagent:${FUSEKI_DIR}/agents/opentelemetry-javaagent.jar ${JAVA_OPTIONS}"
fi
echo "java" $JAVA_OPTIONS -cp "$FUSEKI_CP" $MAIN "$@"
exec "java" $JAVA_OPTIONS -cp "$FUSEKI_CP" $MAIN "$@"
