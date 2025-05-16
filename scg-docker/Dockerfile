# syntax=docker/dockerfile:1.7

FROM telicent/telicent-java21:1.2.12 AS smart-cache-graph

USER root

# Define arguments with defaults to avoid potential build failures
ARG PROJECT_VERSION
ARG FUSEKI_DIR="/fuseki"

# Define environment variables for Java, Fuseki, and logging
ENV JAVA_OPTIONS="-Xmx2048m -Xms2048m" \
    FUSEKI_JAR="default-fuseki.jar" \
    FUSEKI_DIR="${FUSEKI_DIR}" \
    ROCKSDB_MUSL_LIBC="false" \
    LOG_CONFIG_FILE="${FUSEKI_DIR}/logback.xml"

# Create necessary directories and set permissions in a single efficient step
RUN mkdir -p "$FUSEKI_DIR/logs" "$FUSEKI_DIR/databases" "$FUSEKI_DIR/config" "$FUSEKI_DIR/lib" "$FUSEKI_DIR/agents" "$FUSEKI_DIR/backups" /opt/telicent/sbom
RUN chown -R user:user "$FUSEKI_DIR" /opt/telicent/sbom

WORKDIR "$FUSEKI_DIR"

# Copy application files with correct ownership and permissions
COPY --chown=user:user scg-docker/entrypoint.sh scg-docker/logback.xml ./
COPY --chown=user:user scg-docker/target/dependency lib/
COPY --chown=user:user scg-docker/target/agents agents/
COPY --chown=user:user scg-docker/target/docker-${PROJECT_VERSION}-bom.json /opt/telicent/sbom/smart-cache-graph-${PROJECT_VERSION}-bom.json

# Ensure entrypoint script is executable (already done on copy, but added for safety)
RUN chmod +x entrypoint.sh

# Use a non-root user
USER user

# Expose the Fuseki service port
EXPOSE 3030

# Run using dumb-init for proper signal forwarding and process reaping
ENTRYPOINT [ "/usr/bin/dumb-init", "-v", "--single-child", "--", "./entrypoint.sh" ]