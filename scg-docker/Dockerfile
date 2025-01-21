## Dockerfile for Smart Cache Graph

# syntax=docker/dockerfile:1.7


ARG JAVA_VERSION=21

FROM eclipse-temurin:${JAVA_VERSION} AS smart-cache-graph

ARG FUSEKI_DIR=/fuseki

RUN apt-get update && \
    apt-get install -y --no-install-recommends dumb-init && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

WORKDIR $FUSEKI_DIR

RUN useradd fuseki

ARG LOGS=${FUSEKI_DIR}/logs
ARG DATA=${FUSEKI_DIR}/databases
ARG BACKUPS=${FUSEKI_DIR}/backups
ARG CONFIGS=${FUSEKI_DIR}/config
ARG LIB=${FUSEKI_DIR}/lib
ARG AGENTS=${FUSEKI_DIR}/agents
ARG SBOMS=/opt/telicent/sbom

COPY scg-docker/entrypoint.sh .
COPY scg-docker/logback.xml .

RUN \
    mkdir -p $LOGS $DATA $CONFIGS $LIB $AGENTS $SBOMS $BACKUPS && \
    chown -R fuseki ${FUSEKI_DIR} && \
    chmod a+x entrypoint.sh

# Build location of the jars we need.
ARG PROJECT_VERSION
ARG FUSKEI_JAR

## Collected jars
ARG JARS=scg-docker/target/dependency
COPY ${JARS} lib
ARG AGENTS=scg-docker/target/agents
COPY ${AGENTS} agents
ARG SBOM=scg-docker/target/docker-${PROJECT_VERSION}-bom.json
COPY ${SBOM} /opt/telicent/sbom/smart-cache-graph-${PROJECT_VERSION}-bom.json

## Default environment variables.
ENV \
    JAVA_OPTIONS="-Xmx2048m -Xms2048m"  \
    FUSEKI_JAR="${FUSEKI_JAR}"          \
    FUSEKI_DIR="${FUSEKI_DIR}"          \
    ROCKSDB_MUSL_LIBC="false"            \
    LOG_CONFIG_FILE="/fuseki/logback.xml"

USER fuseki

EXPOSE 3030

ENTRYPOINT [ "/usr/bin/dumb-init", "-v", "--single-child", "--", "./entrypoint.sh" ]
CMD []
