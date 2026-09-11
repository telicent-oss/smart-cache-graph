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
2. **Auth**: `UserInfoService` exchanges the presented token for User Info at the Auth Server's `/userinfo`
   endpoint. A non-200 response means the token is invalid or expired (401). The `ADMIN_SYSTEM` role is checked
   against the roles in that response, never against the claims in the token itself, so a self-signed JWT
   claiming the role gets nowhere (403 if the role is absent). If `/userinfo` is unreachable the request is
   refused rather than allowed through
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
    userinfo-url: ${USERINFO_URL:http://auth.telicent.localhost/userinfo}

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
| `USERINFO_URL` | `http://auth.telicent.localhost/userinfo` | Auth Server userinfo endpoint used to validate the token and obtain the user's roles. Required - the service will not start without it |

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
FROM telicent/telicent-java21:1.2.59 AS scg-deletion-worker
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
java -jar target/scg-deletion-worker-1.1.0-SNAPSHOT.jar \
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

| Dependency | Version | Purpose |
|------------|---------|---------|
| Spring Boot | 4.1.1 | Web, Actuator, Async |
| Apache Kafka | 3.9.2 | Consumer/Producer clients |
| Apache Jena | 6.2.0 | RDF parsing, RDF Patch generation |
| Telicent RDF ABAC | 3.1.6 | Label-to-node generation for RDF parsing |
| Telicent `event-sources-core` | 1.4.0 | Kafka event header constants |
| Jackson | 2.22.2 | JSON serialization |

Versions are those resolved by Maven at the time of writing; the POM is authoritative.

### Test

| Dependency | Version | Purpose |
|------------|---------|---------|
| Testcontainers | 1.21.4 | Kafka integration tests |
| JUnit Jupiter | 6.1.3 | Unit/integration testing |
| Mockito | 5.23.0 | Mocking |
| Spring Boot Test | 4.1.1 | `@SpringBootTest` and MockMvc support |

---

## Security

- All endpoints require an `Authorization` header (Bearer token)
- Role validation is performed against the roles returned by the Auth Server's `/userinfo` endpoint, never
  against the claims in the presented token, so a self-signed JWT claiming a role it has not been granted
  gets nowhere
- Only users with the `ADMIN_SYSTEM` role can trigger or view deletion jobs
- Failures are closed: if `/userinfo` is unreachable or its response cannot be parsed, the request is
  refused rather than allowed through

### Implementation note: `UserInfoService` and SC-Core

`UserInfoService` hand-rolls the `/userinfo` call deliberately, to keep this stop-gap worker's dependency
footprint small. Smart Caches Core provides the same thing properly, in
`io.telicent.smart-caches:jwt-auth-common`:

| Class | Replaces |
|-------|----------|
| `RemoteUserInfoLookup` | the `HttpClient` call and response parsing in `UserInfoService` |
| `UserInfo` | the private `UserInfoResponse` record (also carries `permissions`, `sub`, `attributes`, `preferred_name`) |
| `CachingUserInfoLookup` | nothing — adds a Caffeine cache over the lookup, which this worker does not have |
| `TelicentRoles.ADMIN_SYSTEM` | the `ADMIN_SYSTEM` string literal |

`scg-system`'s `io.telicent.core.auth.UserInfoFilter`, and `jaxrs-base-server`'s `UserInfoLookupInit`,
show the intended usage. **Any longer-lived replacement for this worker should prefer that library over
the code here.** Two things to know before adopting it (both verified against jwt-auth-common 1.4.0):

- `UserInfo.getRoles()` returns `null` for an explicit `{"roles": null}` response; only a *missing* `roles`
  key defaults to an empty list. Guard before streaming it. The `UserInfoResponse` record here handles
  both cases
- jwt-auth-common's POM declares `tools.jackson.core:jackson-databind` (Jackson 3) while its bytecode
  actually uses `com.fasterxml` (Jackson 2, arriving transitively via `jwt-servlet-auth-core`). Both
  Jackson lines are already on this worker's classpath — 2.22.2 directly, and 3.1.5 via
  `event-sources-core` — so adopting the library adds no new Jackson, but the declared/actual mismatch is
  worth knowing about when reasoning about which `ObjectMapper` is in play

---

## Monitoring

- **Health**: `GET /actuator/health`
- **Logs**: Structured JSON logging via Logback
- **Job Tracking**: In-memory `JobRegistry` (not persistent across restarts)