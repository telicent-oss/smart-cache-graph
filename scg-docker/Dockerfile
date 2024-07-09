## Dockerfile for Smart Cache Graph

# syntax=docker/dockerfile:1.7

ARG JAVA_VERSION=21

##FROM openjdk:${JAVA_VERSION}
FROM --platform=${BUILDPLATFORM} eclipse-temurin:${JAVA_VERSION} AS smart-cache-graph

ARG FUSEKI_DIR=/fuseki

RUN apt-get update && \
    apt-get install -y --no-install-recommends tini && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

WORKDIR $FUSEKI_DIR

RUN useradd fuseki

ARG LOGS=${FUSEKI_DIR}/logs
ARG DATA=${FUSEKI_DIR}/databases
ARG CONFIGS=${FUSEKI_DIR}/config
ARG LIB=${FUSEKI_DIR}/lib
ARG AGENTS=${FUSEKI_DIR}/agents
ARG SBOMS=/opt/telicent/sbom

COPY entrypoint.sh .
COPY logback.xml .

RUN \
    mkdir -p $LOGS $DATA $CONFIGS $LIB $AGENTS $SBOMS && \
    chown -R fuseki ${FUSEKI_DIR} && \
    chmod a+x entrypoint.sh

# Build location of the jars we need.
## Collected jars
ARG JARS=target/dependency
COPY ${JARS} lib
ARG AGENTS=target/agents
COPY ${AGENTS} agents
ARG PROJECT_VERSION
ARG SBOM=target/docker-${PROJECT_VERSION}-bom.json
COPY ${SBOM} /opt/telicent/sbom/smart-cache-graph-${PROJECT_VERSION}-bom.json

## Default environment variables.
ENV \
    JAVA_OPTIONS="-Xmx2048m -Xms2048m"  \
    FUSEKI_JAR="${FUSEKI_JAR}"          \
    FUSEKI_DIR="${FUSEKI_DIR}"          \
    LOG_CONFIG_FILE="/fuseki/logback.xml"

ENV TINI_SUBREAPER=true

ENV TINI_SUBREAPER=true

USER fuseki

EXPOSE 3030

ENTRYPOINT [ "/usr/bin/tini", "--", "./entrypoint.sh" ]
CMD []
