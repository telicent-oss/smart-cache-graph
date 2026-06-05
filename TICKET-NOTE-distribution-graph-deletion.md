# Hand-off note: Proactively delete named graphs for deleted distributions

## What was implemented in this change

- `DistributionGraphDeletionListener` (scg-system) — a `DistributionLifecycleListener` that, on a
  `LifecycleAction` transitioning **to** `Deleted`, opens a write transaction on each ABAC dataset and
  deletes the named graph whose URI is the distribution id (the same mapping `SmartCacheGraphSink` uses
  when routing to named graphs), and purges the associated ABAC security labels. Idempotent, per the
  listener contract.
- Unit tests: `TestDistributionGraphDeletionListener` (graph deleted on `Deleted`, labels purged,
  non-`Deleted` transitions ignored, idempotent repeat, missing-graph no-op, multi-dataset, no-datasets).
- Dependency: bumped `dependency.smart-caches-core` 0.39.0 → 0.40.0 and added the `distribution-lifecycle`
  dependency (the `DistributionLifecycleListener` / `LifecycleAction` API only exists from 0.40.0).

This satisfies the ticket's acceptance criterion at the unit level: given a distribution deletion event,
the named graph for the distribution is deleted from the underlying dataset.

## What was deliberately deferred (belongs with the "lifecycle awareness" prerequisite)

SCG does not yet consume the distribution lifecycle event stream. The Kafka consumer / tracker wiring is
left to the prerequisite ticket that makes SCG "lifecycle aware". A draft `FMod_DistributionLifecycle`
that stood up a `DistributionLifecycleTracker` was prototyped and then removed, because it introduced a
**second, independently configured** Kafka consumer (see design note below).

### Integration contract for the wiring ticket

When the shared tracker is created, register this listener against it:

```java
new DistributionGraphDeletionListener(() -> abacDatasetsForServer)
```

where `abacDatasetsForServer` enumerates the server's ABAC datasets, e.g.:

```java
server.getDataAccessPointRegistry().accessPoints().stream()
      .map(dap -> dap.getDataService().getDataset())
      .filter(DatasetGraphABAC.class::isInstance)
      .map(DatasetGraphABAC.class::cast)
      .toList();
```

The listener should be wrapped in core's `AcknowledgingListener` so the platform receives
Requested → InProgress → Completed acks, then added to `DistributionLifecycleTracker.builder().listeners(...)`.

### Design decision: one Kafka configuration, not two

A separate `DistributionLifecycleTracker` consumer is the right tool (the lifecycle topic is a control
stream of `LifecycleAction`/`Acknowledgement` envelopes with its own state store, acks, DLQ and
replay-of-active-events; it does not fit the FK connector's RDF-payload→dataset sink model). That is **not**
the concern.

The concern is configuration duplication. The tracker must **not** read its own `BOOTSTRAP_SERVERS` and
build a fresh consumer config, because it would then not inherit the connector's Kafka client properties
(SSL/SASL/auth) and would fail to connect on a secured cluster. Instead, derive the tracker's connection
settings from the existing connector configuration so there is a single source of truth:

- `FKRegistry.get().getConnectors()` → `KConnectorDesc`
- `KConnectorDesc.getBootstrapServers()` and `KConnectorDesc.getKafkaConsumerProps()` (full client props,
  including security)

The tracker still uses its own topic + consumer group, but shares the bootstrap servers + client/security
properties with the data connectors.

## Test gap to close with the wiring

There is no end-to-end test proving SCG deletes the graph when a *real* lifecycle deletion event is
processed, because there is currently nothing in SCG that consumes such an event. Coverage today:

- Listener deletion behaviour — unit tested here (`TestDistributionGraphDeletionListener`).
- Tracker → listener → ack → DLQ machinery — covered in core (`DockerTestDistributionLifecycleTracker`).

The untested seam is the two joined together inside SCG. Add an integration test (Testcontainers Kafka:
produce a `Deleted` `LifecycleAction`, assert the corresponding named graph is removed from a live Fuseki
dataset) as part of the wiring ticket, once the tracker is started by SCG.

## Implementation note

In `DistributionGraphDeletionListener`, the `LabelsStore` obtained via `dataset.labelsStore()` is borrowed
from the (long-lived) `DatasetGraphABAC` and is intentionally **not** closed, even though `LabelsStore` is
`AutoCloseable` — closing it would tear down the dataset's label store (e.g. release the backing RocksDB
handle) and break subsequent reads/writes.
