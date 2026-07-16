# SCG Deletion Worker

## Overview

The **SCG Deletion Worker** is a Spring Boot service that enables deletion of RDF data distributions from the Telicent Smart Cache Graph (SCG) Kafka event streams. It processes existing RDF patches for a given distribution and produces corresponding delete patches, effectively removing that distribution's data from downstream consumers.

### Key Capabilities

- **Distribution Deletion**: Delete all RDF data associated with a specific distribution ID from Kafka topics
- **RDF Patch Inversion**: Automatically converts RDF insert patches into equivalent delete patches
- **Loop Detection**: Self-terminates when it encounters its own generated delete events, ensuring idempotent completion
- **Admin-Only Access**: Secured via Auth-Server requiring `ADMIN_SYSTEM` role
- **Async Job Processing**: Runs deletion jobs asynchronously with status tracking via REST API
- **Observability**: Spring Boot Actuator health endpoints

**Known Limitation**: RDF delete patches remove triples from TDB but do not remove corresponding labels from the Label Store (RDFChangesApplyWithLabels intentionally skips label deletion to prevent malicious label stripping via delete+re-add). This is acceptable given the assumption that distributions do not share overlapping data. This limitation should be addressed in the full data management solution.

---

## Architecture

```
┌─────────────────┐     ┌─────────────────────────────────────────┐
│   REST Client   │────▶│  DeletionJobController                  │
│  (Admin User)   │     │  POST /jobs/delete-distribution         │
└─────────────────┘     └─────────────────┬───────────────────────┘
                                          │
                                          ▼
                                ┌─────────────────────┐
                                │  JobRegistry        │
                                │  (In-memory store)  │
                                └──────────┬──────────┘
                                           │
                                           ▼
                                ┌─────────────────────┐
                                │  DeletionJobService │
                                │  @Async execution   │
                                └──────────┬──────────┘
                                           │
                    ┌──────────────────────┼──────────────────────┐
                    ▼                      ▼                      ▼
         ┌─────────────────┐      ┌─────────────────┐   ┌─────────────────┐
         │ DeletionJob     │      │ RDFPatchInverter│   │ DeletionJob     │
         │ Consumer        │─────▶│                 │──▶│ Producer        │
         │                 │      │ - Parse quads   │   │                 │
         │ - Seek to start │      │ - Create deletes│   │ - Add headers   │
         │ - Filter by     │      │ - Output patch  │   │ - Send to Kafka │
         │   distribution  │      └─────────────────┘   └─────────────────┘
         │ - Detect own    │
         │   events        │
         └─────────────────┘
```

---

## How It Works

### Deletion Flow

1. **Trigger**: Admin calls `POST /jobs/delete-distribution?distribution-id=<id>` with valid `Authorization` header
2. **Auth**: `UserInfoService` validates the token against an external userinfo endpoint, checking for `ADMIN_SYSTEM` role
3. **Register**: `JobRegistry` creates a new `JobState` with unique `jobId`, status `RUNNING`
4. **Process** (async):
   - `DeletionJobConsumer` assigns all topic partitions and seeks to offset 0
   - Polls records, filtering for the target `distribution-id`
   - Skips records already covered by a previous deletion job (via `Original-Offset` header tracking which source offsets have already been deleted)
   - Detects own output via `Deletion-Job-Id` header for loop termination
5. **Invert**: For each candidate, `RDFPatchInverter`:
   - Parses RDF quads (N-Quads, Turtle, TriG, N-Triples, RDF/XML supported)
   - Creates delete operations for each quad
   - Serializes as RDF Patch (`application/rdf-patch`)
6. **Produce**: `DeletionJobProducer` sends delete patches back to the same topic with:
   - `Content-Type: application/rdf-patch`
   - `Operation: delete`
   - `Deletion-Job-Id: <jobId>`
   - `Original-Offset: <source offset>`
   - Modified `Distribution-ID: <original>-deletion`
7. **Complete**: Job status updated to `COMPLETED` with patch count, or `FAILED` on error

### Loop Detection

The consumer detects its own output by checking for the `Deletion-Job-Id` header matching the current job ID. When encountered, it processes any remaining unprocessed candidates and terminates. This ensures:
- Idempotency: Re-running the same job won't create duplicate deletions
- Completeness: All pre-existing records for the distribution are processed
- Safety: New records written during the job won't be missed

---

## API Reference

### Delete Distribution

```http
curl -X POST \
  -H "Authorization: Bearer sess_xxx" \
  -H "User-Agent: Mozilla/5.0 ..." \
  -H "Accept-Language: en-US,en;q=0.9" \
  "https://api.system-integration.telicent-sandbox.telicent.live/deletion-worker/jobs/delete-distribution?distribution-id=<id>"
```

**Responses:**

| Status | Description |
|--------|-------------|
| 202 Accepted | Job started, returns `{"jobId": "<uuid>"}` |
| 400 Bad Request | Missing or blank `distribution-id` |
| 401 Unauthorized | Missing `Authorization` header, or invalid/expired session |
| 403 Forbidden | User lacks `ADMIN_SYSTEM` role |

### Get Job Status

```http
curl -X GET \
  -H "Authorization: Bearer sess_xxx" \
  -H "User-Agent: Mozilla/5.0 ..." \
  "https://api.system-integration.telicent-sandbox.telicent.live/deletion-worker/jobs/<jobId>"
```

**Response (200 OK):**
```json
{
  "jobId": "uuid",
  "distributionId": "my-distribution",
  "status": "RUNNING|COMPLETED|FAILED",
  "startedAt": "2026-07-15T10:30:00Z",
  "errorMessage": null,
  "patchesSent": 42
}
```

**Other Responses:**

| Status | Description |
|--------|-------------|
| 401 Unauthorized | Missing `Authorization` header |
| 403 Forbidden | User lacks `ADMIN_SYSTEM` role |
| 404 Not Found | Job ID not found |

---

## Configuration

All configuration via `application.yml` or environment variables:

```yaml
server:
  port: 8080

deletion-worker:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    config-file-path: ${KAFKA_CONFIG_FILE_PATH:}  # Optional Kafka client properties file
  topic: ${KAFKA_TOPIC:RDF}
  auth:
    userinfo-url: ${USERINFO_URL:https://auth.system-integration.telicent-sandbox.telicent.live/userinfo}

management:
  endpoints:
    web:
      exposure:
        include: health
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker addresses |
| `KAFKA_CONFIG_FILE_PATH` | (empty) | Path to additional Kafka client properties file |
| `KAFKA_TOPIC` | `RDF` | Topic to read from and write delete patches to |
| `USERINFO_URL` | Telicent sandbox URL | Userinfo endpoint for role validation |

---

## Building

### Prerequisites

- Java 21
- Maven 3.9+

### Build Commands

```bash
# Compile and run tests
mvn clean verify

# Build JAR (skipping tests)
mvn clean package -DskipTests

# Build Docker image
docker buildx build --platform linux/arm64 \
  -t scg-deletion-worker:local \
  -f scg-deletion-worker/Dockerfile \
  --build-arg PROJECT_VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout -pl scg-deletion-worker) \
  --target scg-deletion-worker \
  --load \
  .
```

### Docker

```dockerfile
# Multi-stage build using Telicent base image
ARG JAVA_BASE_IMAGE=telicent/telicent-java21:1.2.49
FROM ${JAVA_BASE_IMAGE} AS scg-deletion-worker
USER root
ARG PROJECT_VERSION
COPY --chown=user:user scg-deletion-worker/target/scg-deletion-worker-${PROJECT_VERSION}.jar app.jar
USER user
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Run with Docker Compose:

```bash
cd scg-deletion-worker
export PROJECT_VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout -pl .)
docker-compose up --build
```

---

## Running Locally

### Manual Run

```bash
# Start Kafka (e.g., via docker-compose)
docker compose -f scg-docker/docker-compose-kafka.yml up -d --build
# Run the application
java -jar target/scg-deletion-worker-1.0.10-SNAPSHOT.jar \
  --deletion-worker.kafka.bootstrap-servers=localhost:9092 \
  --deletion-worker.topic=RDF
```

---

## Project Structure

```
scg-deletion-worker/
├── src/
│   ├── main/
│   │   ├── java/io/telicent/deletion/
│   │   │   ├── DeletionWorkerApplication.java      # Spring Boot entry point
│   │   │   ├── DeletionWorkerConstants.java        # Header constants
│   │   │   ├── RDFPatchInverter.java               # RDF quad → delete patch
│   │   │   ├── DeletionJobConsumer.java            # Kafka consumer + loop detection
│   │   │   ├── DeletionJobProducer.java            # Kafka producer + headers
│   │   │   ├── DeletionJobException.java           # Custom exception
│   │   │   ├── config/
│   │   │   │   └── DeletionWorkerProperties.java   # Configuration binding
│   │   │   ├── controller/
│   │   │   │   └── DeletionJobController.java      # REST endpoints
│   │   │   ├── model/
│   │   │   │   ├── JobState.java                   # Job state record
│   │   │   │   └── JobStatus.java                  # RUNNING/COMPLETED/FAILED
│   │   │   └── service/
│   │   │       ├── DeletionJobService.java         # Async job orchestration
│   │   │       ├── JobRegistry.java                # In-memory job store
│   │   │       └── UserInfoService.java            # Auth/role validation
│   │   └── resources/
│   │       └── application.yml                     # Default configuration
│   └── test/
│       ├── java/io/telicent/deletion/
│       │   ├── *IntegrationTest.java               # Kafka integration tests
│       │   ├── *Test.java                          # Unit tests
│       │   └── KafkaIntegrationTestBase.java       # Shared test infrastructure
│       └── resources/
│           └── application.yml                     # Test configuration
├── Dockerfile
├── docker-compose.yml
├── pom.xml
└── README.md
```

---

## Dependencies

### Core

| Dependency | Purpose |
|------------|---------|
| Spring Boot 3.5.14 | Web, Actuator, Async |
| Apache Kafka 3.x | Consumer/Producer clients |
| Apache Jena 4.x | RDF parsing, RDF Patch generation |
| Telicent RDF ABAC | Label-to-node generation for RDF parsing |
| Jackson | JSON serialization |

### Test

| Dependency | Purpose |
|------------|---------|
| Testcontainers | Kafka integration tests |
| JUnit 5 + Mockito | Unit/integration testing |
| Spring Boot Test | @SpringBootTest support |

---

## Security

- All endpoints require `Authorization` header (Bearer token)
- Role validation is performed by calling the auth-server's `/userinfo` endpoint using the session ID from the `X-Auth-Session-Id` header, which is injected by the auth-server during Traefik's forward auth validation flow.
  For direct API calls, provide a valid session ID as `Authorization: Bearer sess_xxx` along with matching browser fingerprint headers (`User-Agent`, `Accept-Language`, `Sec-*`) — the auth-server uses these for fingerprint validation when calling `/userinfo`.
- Only users with `ADMIN_SYSTEM` role can trigger or view deletion jobs

---

## Monitoring

- **Health**: `GET /actuator/health`
- **Logs**: Structured JSON logging via Logback
- **Job Tracking**: In-memory `JobRegistry` (not persistent across restarts)