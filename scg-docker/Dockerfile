# syntax=docker/dockerfile:1.7

FROM telicent/telicent-java21:1.1.2 AS smart-cache-graph

USER root

ARG PROJECT_VERSION
ARG FUSEKI_DIR=/fuseki

ENV \
    JAVA_OPTIONS="-Xmx2048m -Xms2048m" \
    FUSEKI_JAR="default-fuseki.jar" \
    FUSEKI_DIR="${FUSEKI_DIR}" \
    ROCKSDB_MUSL_LIBC="false" \
    LOG_CONFIG_FILE="${FUSEKI_DIR}/logback.xml"

RUN groupadd -r telicent-service && \
    useradd -r -g telicent-service -d /app/ telicent-service && \
    mkdir -p $FUSEKI_DIR/logs $FUSEKI_DIR/databases $FUSEKI_DIR/config $FUSEKI_DIR/lib $FUSEKI_DIR/agents /opt/telicent/sbom && \
    chown -R telicent-service:telicent-service $FUSEKI_DIR /opt/telicent/sbom

WORKDIR $FUSEKI_DIR

# Copy files into the image
COPY scg-docker/entrypoint.sh scg-docker/logback.xml ./
COPY scg-docker/target/dependency lib/
COPY scg-docker/target/agents agents/
COPY scg-docker/target/docker-${PROJECT_VERSION}-bom.json /opt/telicent/sbom/smart-cache-graph-${PROJECT_VERSION}-bom.json

# Set permissions
RUN chmod a+x entrypoint.sh

USER telicent-service

EXPOSE 3030

# Healthcheck (optional)
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 CMD curl --fail http://localhost:3030 || exit 1

ENTRYPOINT [ "/usr/bin/dumb-init", "-v", "--single-child", "--", "./entrypoint.sh" ]
