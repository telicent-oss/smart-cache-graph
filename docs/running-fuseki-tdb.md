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

If the fuseki server is run with `--compact`, then the endpoint `/$/compact`
will perform a database compaction. This can execute on a live server allowing
read-operation to continue to be processed in parallel.  Note that write
operations are held up.
