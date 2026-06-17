FROM eclipse-temurin:21-jre AS scg-deletion-worker
COPY target/scg-deletion-worker-*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]