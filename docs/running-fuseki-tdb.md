# Notes on operating Apache Jena Fuseki and TDB

Unless otherwise noted, "TDB" means "[Apache Jena TDB2](https://jena.apache.org/documentation/tdb2/)", 
and not TDB1, although several items of TDB1 documentation apply to TDB2.

For use in Smart Cache Graph, there is an additional label store for each database with RDF
ABAC and also a GraphQL service.

## Hardware

TDB uses memory mapped files for database indexes. These are cached by the
operating system in the filing system cache, not in the JVM heap.  Memory mapped
files form part of the Java process's virtual memory space. The Java process can
therefore appear to be very large but that does not mean the actual physical
process size is large.

The database design benefits from local SSD.

Databases that greatly exceed the size of the machine RAM wil be slow if the
disk is a HDD and/or remote storage.

## Configuration

There are no configuration parameters except some [fine-tuning
parameters](https://jena.apache.org/documentation/tdb/store-parameters.html)
useful only for small scale deployments,. Otherwise TDB

## Heap

A Fuseki server supports multiple databases.  Each database operates
independently; they do not share resources.

Each database uses heap space for query execution and also for caching the RDF
terms in the database. Each database needs about 1.5Gbytes (the "about" is
because the size of caches entries - especially literals can vary between
usage).

The cache is an LRU cache. It fills up over time so it does not hit its maximum
size until after the server has been operating for a while.  A setting for
maximum heap size `-Xmx` of 2 gigabytes per database is a good starting point.

## Backup

On the CORE platform, a database can be rebuilt by starting a new instance and
having it load all events from the knowledge topic.

It is possible to separately have a data backup taken remotely by simply making
an HTTP request:

`GET https://server:3030/database`

assuming that there is an endpoint:

```
:someService rdf:type fuseki:Service ;
    fuseki:name "/database" ;
    fuseki:endpoint [ fuseki:operation fuseki:gsp-r ; ] ;
    ...
```
Note - this must not be accessible outside the CORE platform because it does not implement RDF-ABAC filtering.


The request will stream the data in the database.  The request can specific the content-type.

## Compaction

A Fuseki-TDB database does not reclaim unused automatically which would need
tracking data in-use while transactions were in progress.  A similar situation
happens in PostgreSQL, which provides the "vacuum" operation.

The database can grow quite large. It should be compacted from time-to-time.

Due to this problem, we have an additional module, `Initial Compaction`, has been added
which will call the compact operation after the HTTP server has started. This is automatically
turned on but can be disabled by setting the
environment variable `DISABLE_INITIAL_COMPACTION`.

Compaction endpoints are exposed by Smart Cache Graph:
- `POST /$/compact/<dataset>` for compaction of a single dataset
- `POST /$/compactall` for compaction of all configured datasets

These can execute on a live server. While maintenance operations such as compaction, backup, or
restore are in progress, dataset-backed requests are rejected with HTTP `503 Service Unavailable`
before they attempt to open a transaction. Health check and other control endpoints such as
`/$/ping` remain available during maintenance.

### RO Compaction

RO means read-only: queries remain available while the compacted copy is built, and writes and
Kafka ingest are paused. During the switch to the new copy, dataset requests receive HTTP `503`
instead of waiting for a transaction.

Compactions requested through these endpoints use *RO Compaction (read-only compaction)* by default: the dataset keeps answering
queries while the copy is built, and writes are rejected (HTTP `503`). An RO Compaction:

1. Pauses Kafka ingest into the dataset, waiting for each connector to commit its open batch.
   Connectors configured against the dataset (`/knowledge`) or one of its endpoints
   (`/knowledge/upload`) are both found. If ingest doesn't pause in time the compaction is skipped
   (`SKIPPED_LOCK_CONTENTION`).
2. Waits for any other in-progress write transactions to finish, and skips if they don't.
3. Copies the live data to a new `Data-NNNN` directory. Queries continue against the current copy.
4. Enters the switching phase and rejects new dataset requests with HTTP `503` and a
   `Retry-After: 1` header, as switching normally takes moments. (Other maintenance rejections,
   e.g. writes during the copy or a restore, have no `Retry-After`, as they can last much longer.)
   It waits for
   requests already accepted by the filter to finish, including those that have not started
   their transaction yet, before taking the exclusive lock and switching copies. This prevents
   waiting queries from filling Jetty's request threads and blocking health checks. If requests
   or transactions do not finish within the switch timeout, the new copy is discarded, the
   database is left unchanged and the result is `SKIPPED_LOCK_CONTENTION`. Read access is
   restored after switching, a timeout or an exception.
5. Deletes the old copy and resumes Kafka ingest.

Distribution graph deletions wait for an RO Compaction to finish rather than running during it.

Free disk space is checked first. The compacted copy is written alongside the current one, so the
compaction is skipped (`SKIPPED_INSUFFICIENT_DISK_SPACE`) unless there is room for it, plus 10%
headroom. The compacted size is estimated as follows:

- Most of a bloated store is old copies of index blocks, but TDB2's node data file
  (`nodes-data.obj`) is append-only, so it grows with the live data, not with the garbage.
- So the estimate is the last compacted size, scaled up by how much the node data has grown since.
  The node data size after each compaction is recorded in `.last-compaction-nodes`, next to
  `.last-compaction`.
- If there's no usable last compaction, the estimate is the size of an empty store (about 200 MB)
  plus `COMPACTION_NODE_DATA_RATIO` (default 20) bytes per byte of node data. That's the case when
  there was no previous compaction, or it was of a store with very little data (under 64 MB of node
  data), e.g. the compaction at startup of a new, empty store.
  - A compacted store of default graph data is around 10 times its node data. Quads in named graphs
    take more index space, so the default allows twice that.
  - For example, a store that has grown to 60 GB from about 4 GB of live data, with 431 MB of node
    data, is estimated at about 8.8 GB. Falling back to the current size instead would ask for
    66 GB, refusing to compact the stores that most need it.
- If the node data size can't be found, the estimate is the current size, which is the most the copy
  can need.
- The estimate is never more than the current size.

Each check logs its estimate, what it was based on, and the free space available.

Long-running compactions log their progress every minute, and log a warning if the new copy has
stopped growing.

Compaction at startup always runs with exclusive access, as nothing else is running yet.

| Environment variable | Default | Purpose |
|---|---|---|
| `COMPACTION_RO` | `true` | Set to `false` for the previous behaviour: requested compactions need exclusive access (no active transactions at all), so they are skipped while queries are running, and every request gets `503` while they run |
| `COMPACTION_INGEST_PAUSE_TIMEOUT` | `PT2M` | How long to wait for Kafka ingest to pause |
| `COMPACTION_WRITER_DRAIN_TIMEOUT` | `PT1M` | How long to wait for other write transactions to finish |
| `COMPACTION_SWITCH_TIMEOUT` | `PT1M` | How long to wait for accepted requests and in-flight transactions to finish before switching. New dataset requests receive `503` during this phase |
| `COMPACTION_PROGRESS_LOG_INTERVAL` | `PT1M` | How often a running compaction logs its progress |
| `COMPACTION_DISK_SPACE_CHECK` | `true` | Set to `false` to skip the free disk space check |
| `COMPACTION_NODE_DATA_RATIO` | `20` | Bytes of compacted store per byte of node data, used by the free disk space check when there's no usable previous compaction to scale from |

Durations are ISO-8601.

> [!NOTE]
> RO Compaction doesn't use Jena's `DatabaseMgr.compact`. Jena's compaction switches over
> while queries are running, and a query that spans the switch leaves the compaction waiting
> forever (apache/jena#3488).

Successful responses from both endpoints return JSON:
```json
{
  "status": "ok",
  "datasets": {
    "/knowledge": "COMPACTED"
  }
}
```

Possible per-dataset outcomes are:
- `COMPACTED`
- `SKIPPED_ALREADY_COMPACTED`
- `SKIPPED_PREVIOUSLY_COMPACTED`
- `SKIPPED_LOCK_CONTENTION` (the dataset couldn't be compacted safely right now, e.g. ingest didn't pause, or queries
  didn't finish in time for the switch-over; try again later)
- `SKIPPED_MAINTENANCE_IN_PROGRESS` (a compaction, backup or restore of the dataset is already running)
- `SKIPPED_INSUFFICIENT_DISK_SPACE` (not enough free space for the compacted copy)
- `SKIPPED_NOT_TDB2`

When compaction throws an exception, the endpoint returns HTTP `500` with an
error message that includes dataset information.

## Backup / Restore

In order to improve the resiliency of the Smart Cache Graph the facility to back up the data is currently being worked upon. 
At present only the underlying Fuseki-TDB will be saved. This will be extended to include backing up the security label, and Kafka offsets too.
An obvious follow-up will be the ability to restore from backup.

This facility can be turned on by setting the environment variable `ENABLE_BACKUPS` to true.

### Encryption
It is also possible to encrypt backup files using [PGP](https://en.wikipedia.org/wiki/Pretty_Good_Privacy) for more secure storage or transportation. In order to do this you need to provide a PGP key pair and passphrase using the following enviroment variables:
- `BACKUPS_PRIVATE_KEY_URL` - the URL of the private key file, for example `file:///tmp/private.asc`
- `BACKUPS_PUBLIC_KEY_URL` - the URL of the public key file, for example `file:///tmp/public.asc`
- `BACKUPS_PASSKEY` - the passphrase used when creating the key

If these environment variables are not set, or one of them is missing, encryption will not be applied. A warning message is shown in the application log files if encryption is not enabled.

If you need to create a PGP key pair this can be done as follows:
```shell
gpg --full-generate-key
``` 
Select the default or other appropriate options and choose a passphrase - this will need to be set as the value of the `BACKUPS_PASSKEY` environment variable. Then list the public keys:
```shell
gpg --list-keys --keyid-format=long
```
> [!CAUTION]
> Care must be taken not to loose the passphrase as this will render existing backups unreadable and replacement keys will need to be created for future backups.

Copy the key ID for the public key you just created, e.g. `F2C0B93297F09448`, then export the key to a file:
```shell
gpg --armor --export F2C0B93297F09448 > public.asc
```
Do the same for the private key, first obtaining the private key ID: 
```shell
gpg --list-secret-keys --keyid-format=long
```
Then write this to a file as well:
```shell
gpg --armor --export-secret-keys F2C0B93297F09448 > private.asc
```
## Exclusions

There is an additional Fuseki module `JWT Servlet Auth` that applies authentication to all the endpoints offered by the server.

We have disabled authentication for a number of the utility endpoints listed below:
- `/$/ping`
- `/$/metrics` 
- `/$/stats`

At present, they are hard-coded.  As mentioned above, though, the compact operation can disable write operations during the compaction process and be accessible by non-authorised users. 
Users need to be aware of this when deploying with the `--compact` command line setting. We recommend disabling remote access to this via other means.
