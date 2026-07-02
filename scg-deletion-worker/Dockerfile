ARG JAVA_BASE_IMAGE=telicent/telicent-java21:1.2.49
FROM ${JAVA_BASE_IMAGE} AS scg-deletion-worker
USER root
COPY --chown=user:user scg-deletion-worker/target/scg-deletion-worker-*.jar app.jar
USER user
ENTRYPOINT ["java", "-jar", "/app.jar"]