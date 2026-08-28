FROM telicent/telicent-java21:1.2.60 AS scg-deletion-worker
USER root
ARG PROJECT_VERSION
COPY --chown=user:user scg-deletion-worker/target/scg-deletion-worker-${PROJECT_VERSION}.jar app.jar
USER user
ENTRYPOINT ["java", "-jar", "app.jar"]