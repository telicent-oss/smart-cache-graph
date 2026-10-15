# Open Telemetry

[Architecture :: CorePlatform/Observability/observability](https://github.com/Telicent-io/telicent-architecture/blob/main/CorePlatform/Observability/observability.md)

## Fuseki operations

A Fuseki request URL has a path such as 

"/myDatabase"

or

"/myDatabase/sparql"

It names a collection of operation endpoints each of which is a processor that execute the
request.

There can be multiple operation endpoints at the same name - the request is
dispatched based on URL, `Content-Type`, and SPARQL protocol signature: "?query=" for query,
`?update=` for SPARQL update, and `?default and `?graph=` for SPARQL Graph Store
Protocol.

A query can be `application/sparql-query` and the query string in the request
body, or in the URL `?query=`.

## Attributes

(Tagging for signals) 

```
AttributeKey<String> SPARQL_SYSTEM       = AttributeKey.stringKey("sparql");
AttributeKey<String> SPARQL_OPERATION    = AttributeKey.stringKey("sparql.operation");
```

## Naming

OpenTelemetry requires a unique name (per signal(e.g. meter)? per exporter?)

Naming:

```
    [root].db.[database name].[endpoint].[operation].[counter name]
```

#### root

`[root]` is a system name.

"fuseki" for opensource, "smartcache.graph" for Telicent smart cache graph.

#### database

`[database name]` is the first component of the path without the leading slash.

This is `fuseki:name` of the service:

```turtle
:service rdf:type fuseki:Service ;
    fuseki:name "knowledge" ;
```

NB Internally, the canonical form of a Fuseki database name is an absolute
path - it includes the leading slash.

#### endpoint

The endpoint name is the `fuseki:name` of the endpoint.

```
:service rdf:type fuseki:Service ;
    fuseki:name "knowledge" ;
    fuseki:endpoint [ 
        fuseki:operation fuseki:query ;
        fuseki:name "sparql" 
    ];
```

There may be endpoints without a name.

```
:service rdf:type fuseki:Service ;
    fuseki:name "knowledge" ;
    fuseki:endpoint [                                       ## /knowledge/sparql
        fuseki:operation fuseki:query ;
        fuseki:name "sparql" 
    ];
    fuseki:endpoint [ fuseki:operation fuseki:query ; ] ;   ## /knowledge
```

For OpenTelemetry naming, use "_" (U+005F) when there is no name.

#### operation

Fuseki Operation objects have names:

[`org.apache.jena.fuseki.server.Operation`](https://github.com/apache/jena/blob/main/jena-fuseki2/jena-fuseki-core/src/main/java/org/apache/jena/fuseki/server/Operation.java)

* query
* update
* gsp-r
* gsp-rw
* shacl
* upload
* patch

and there may be Telicent additional ones.

#### Counter name

Counters already have names used in the Fuseki native statistics 
(we can of course map the names easily enough)

[CounterName.java](https://github.com/apache/jena/blob/main/jena-fuseki2/jena-fuseki-core/src/main/java/org/apache/jena/fuseki/server/CounterName.java)

All request processors have:

* `request.good`
* `request.bad`
* `request.total`

and also per operation specific counters e.g. `query.timeouts`.

GSP (Graph Store Protocol) is REST-like and the HTTP method determines the action:

Example:

* `http.post.requests`
* `http.post.requests.good`
* `http.post.requests.bad`

Proposal: 

Just support the overall counters:
* `request.good`
* `request.bad`
* `request.total`

and ignore the others for now. 
The dot is the OpenTelemetry namespace path separator.

### Summary

Example:

`smartcache.graph.knowledge.query.request.good`


## Local Telemetry

In order that the OpenTelemetry exporter and Fuseki native metrics can be inspected together, and to aid with local development, we have included a docker set-up that includes Grafana/Prometheus.

It is intended to make the the following more visible:

- the Java agent Prometheus exporter on the SCG container
- the custom `smartcache_graph_*` metrics emitted by the SCG Fuseki module
- the native Fuseki `/$/metrics` endpoint
- endpoint-level differences between `/ds` and `/ds/sparql`
- good versus bad request traffic
- RDF ABAC label assignment attempts, duplicate no-ops, and persistent writes

### Start The Local Stack

The local stack is defined in [`docker-compose-telemetry.yml`](../docker-compose-telemetry.yml) and provisions:

- Smart Cache Graph on `http://localhost:3030`
- the SCG Prometheus exporter on `http://localhost:9466/metrics`
- Prometheus on `http://localhost:9091`
- Grafana on `http://localhost:3001`

The stack uses [`scg-docker/mnt/config/config-abac-local-with-data.ttl`](../scg-docker/mnt/config/config-abac-local-with-data.ttl),
which serves a local in-memory `/ds` dataset seeded from
[`scg-docker/mnt/config/data1.trig`](../scg-docker/mnt/config/data1.trig).

Start it with:

```bash
./docker-build.sh
docker compose -f docker-compose-telemetry.yml up -d
```

### Demo Script

To illustrate the behaviour run this

```bash
./telemetry/demo-telemetry.sh
```

It does the following:

1. waits for Smart Cache Graph, the OTel exporter, and Prometheus to become ready
2. issues successful queries against both `/ds` and `/ds/sparql`
3. issues a `POST` SPARQL query and one deliberately bad query
4. prints sample lines from both exporters together with a short Prometheus summary


### Prometheus Query script

A helper script is included for the common queries:

```bash
./telemetry/query-prometheus.sh targets
./telemetry/query-prometheus.sh request_totals
./telemetry/query-prometheus.sh request_totals_by_endpoint
./telemetry/query-prometheus.sh label_store_totals
./telemetry/query-prometheus.sh label_store_rates
./telemetry/query-prometheus.sh label_store_duplicate_avoidance
./telemetry/query-prometheus.sh native_top
./telemetry/query-prometheus.sh jvm_gc_rates
./telemetry/query-prometheus.sh jvm_gc_paused_percent
./telemetry/query-prometheus.sh jvm_direct_buffers
./telemetry/query-prometheus.sh tdb2_disk_usage
```

The available query names are:

- `targets`
- `custom_metric_names`
- `native_metric_names`
- `request_totals`
- `request_totals_by_endpoint`
- `label_store_totals`
- `label_store_rates`
- `label_store_duplicate_avoidance`
- `native_top`
- `jvm_gc_rates`
- `jvm_gc_paused_percent`
- `jvm_direct_buffers`
- `tdb2_disk_usage`
- `container_memory`
- `container_io_rates`

Representative PromQL expressions:

```promql
up{job=~"smart-cache-graph-otel|smart-cache-graph-fuseki"}
```

```promql
sum by (__name__) (
  {job="smart-cache-graph-otel", __name__=~"smartcache_graph_request_(good|bad|total)"}
)
```

```promql
sum by (__name__, fuseki_endpoint, db_operation) (
  {job="smart-cache-graph-otel", __name__=~"smartcache_graph_request_(good|bad|total)"}
)
```

```promql
topk(15, sum by (__name__) ({job="smart-cache-graph-fuseki"}))
```

### Raw Exporter Checks

These are useful when you want to inspect metric names before looking at Prometheus or Grafana:

```bash
curl -s localhost:9466/metrics | grep '^smartcache_graph_'
curl -s localhost:3030/$/metrics | head
```

The OTel exporter should show metrics such as:

- `smartcache_graph_request_total`
- `smartcache_graph_request_good`
- `smartcache_graph_request_bad`
- `smartcache_graph_labels_add_attempts_total`
- `smartcache_graph_labels_cache_noops_total`
- `smartcache_graph_labels_writes_total`

The native Fuseki metrics endpoint will expose the built-in Fuseki metric names directly.

### What You Should Expect To See

After running the demo script:

- both Prometheus targets should be `up`
- `smartcache_graph_request_total` should increase
- the Grafana label-store section should show cumulative attempts, duplicate no-ops, writes, rates, and duplicate avoidance
- `smartcache_graph_request_good` should increase after the successful queries
- `smartcache_graph_request_bad` should increase because the script sends one intentionally invalid query per round
- the `request_totals_by_endpoint` query should show separate series for `/ds` and `/ds/sparql`
- the Grafana panels for `Custom SCG OTel Metrics`, `Custom SCG Metrics By Endpoint`, and `Native Fuseki Metrics`
  should move

Because the current SCG custom metrics mirror Fuseki counters as gauges, they behave more like current snapshots of the
underlying Fuseki counters than monotonic Prometheus counters. That is expected with the current implementation.

### Grafana

Grafana is provisioned with a dashboard focused on the SCG telemetry setup. Log in at `http://localhost:3001` using
`admin` / `admin`, then open the `Smart Cache Graph Telemetry` dashboard.

The **JVM Memory, GC & Storage** row shows:

- **Full GCs (last hour)** and **GC Events per Minute**: stop-the-world full collections appear as
  `G1 Old Generation` / `end of major GC`. A steady stream of them usually means direct memory is reaching
  `-XX:MaxDirectMemorySize` and the JDK is calling `System.gc()`. Check that `JETTY_OUTPUT_BUFFER_SIZE` is set (see
  [memory-improvements-june-2026.md](memory-improvements-june-2026.md#direct-memory-and-the-jetty-output-buffer)).
- **Time Paused in GC**: the share of time spent in GC pauses. Queries and Kafka ingest are stopped for the duration
  of every stop-the-world pause.
- **Direct Buffer Memory**: direct `ByteBuffer` memory and buffer count. A saw-tooth that climbs to
  `MaxDirectMemorySize` and drops at each full GC is the signature of unpooled response buffers.
- **Heap Used vs Live Set**: heap in use, heap remaining after the last GC and the maximum heap.
- **TDB2 Disk Usage and Growth**: the on-disk size of each TDB2 dataset and its hourly growth. TDB2 is copy-on-write,
  so it grows until compacted.

The **Container Memory & Disk I/O** row shows what TDB2's memory-mapped files are getting from the container. These
figures come from Graph's own cgroup (v2, or v1 as a fallback), so they work in Kubernetes and Docker without cAdvisor.
If there's no cgroup memory accounting (e.g. running outside a container on macOS), they aren't reported.

- **Page Cache Headroom**: the memory limit minus the JVM's own (anonymous) memory. TDB2's indexes live in page cache,
  which counts towards the limit, so this is the most page cache TDB2 can have.
- **Page Cache in Use** and **Memory vs Limit**: memory charged to the container against its limit, split into the JVM
  and page cache. When usage sits at the limit the kernel has to reclaim page cache, flushing dirty pages first.
- **Dirty Pages** and **Dirty and Writeback Pages**: page cache waiting to be written to disk, and being written.
- **Disk Write Rate** and **Disk I/O vs TDB2 Growth**: the container's disk writes and reads, against how fast the TDB2
  directory is growing. Use this during a compaction:
  - The TDB2 directory grows by the size of the new copy.
  - If disk writes are many times that growth, the same dirty pages are being flushed over and over (write
    amplification), and the compaction is limited by writeback and the volume's write throughput.
  - A test compacting a 4.2 GB store on a Raspberry Pi wrote 31 times the copy's size with a 5 GB memory limit, and
    12 times with 12 GB, which compacted 1.75 times faster.
  - So more memory headroom, or a volume with more write throughput, makes compaction faster.
- **Major Faults and Page Cache Refaults**: page faults that needed a disk read, and evicted pages that were needed again
  soon after. Sustained refaults mean the page cache is too small for the working set, e.g. for queries.

The container metrics are:

| Metric | Prometheus name | Type |
|---|---|---|
| `smartcache.graph.container.io.read` / `.io.write` | `smartcache_graph_container_io_read_bytes_total` / `..._io_write_bytes_total` | counter |
| `smartcache.graph.container.memory.page_cache` | `smartcache_graph_container_memory_page_cache_bytes` | gauge |
| `smartcache.graph.container.memory.dirty` / `.writeback` | `smartcache_graph_container_memory_dirty_bytes` / `..._writeback_bytes` | gauge |
| `smartcache.graph.container.memory.anon` | `smartcache_graph_container_memory_anon_bytes` | gauge |
| `smartcache.graph.container.memory.limit` | `smartcache_graph_container_memory_limit_bytes` (not reported if there's no limit) | gauge |
| `smartcache.graph.container.memory.usage` | `smartcache_graph_container_memory_usage_bytes` | gauge |
| `smartcache.graph.container.memory.major_faults` | `smartcache_graph_container_memory_major_faults_total` | counter |
| `smartcache.graph.container.memory.page_cache_refaults` | `smartcache_graph_container_memory_page_cache_refaults_total` | counter |

The direct buffer and after-last-GC metrics are experimental in the OpenTelemetry Java agent, so they need
`OTEL_INSTRUMENTATION_RUNTIME_TELEMETRY_EMIT_EXPERIMENTAL_TELEMETRY=true`. The telemetry compose file and the Helm
chart (when `metrics.enabled` is true) both set it.
