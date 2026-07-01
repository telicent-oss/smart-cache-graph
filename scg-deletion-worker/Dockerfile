FROM eclipse-temurin:21-jre AS scg-deletion-worker
RUN groupadd --system user && useradd --system --gid user --no-create-home user
COPY --chown=user:user scg-deletion-worker/target/scg-deletion-worker-*.jar app.jar
USER user
ENTRYPOINT ["java", "-jar", "/app.jar"]