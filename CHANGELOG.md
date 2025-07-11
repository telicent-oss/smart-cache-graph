# Smart Cache Graph

## 0.83.16
- Build improvements:
  - CVE patches

## 0.83.15
- Build improvements:
  - Correct label query service per data-set
  - Add default restore functionality

## 0.83.14
- Build improvements:
  - Label Query Service error handling

## 0.83.13
- Build improvements:
  - dependency patches
  - consolidating backups into zip files
  - CVE fixes
  - update RDF ABAC v1.0.2
- Fixes:
  - fix next number logic for backups
  - correcting documentation/api mismatch

## 0.83.12
- Compress back-ups.
- Build improvements:
  - Addressing CVE-2025-48734
  
## 0.83.11
- Build improvements:
  - Apache Kafka upgraded to 3.9.1
  - GraphQL Jena upgraded to 0.10.2
  - Test dependencies upgraded to latest available 

## 0.83.10
- Build improvements:
  - GraphQL Jena (0.10.1)

## 0.83.9
- Build improvements:
  - GraphQL Jena (0.10.0)

## 0.83.8
- Addressing build issue

## 0.83.7
- Build improvements:
  - GraphQL Jena (0.9.3)
  - Telicent Base Java Image (1.2.11)
  
## 0.83.6
- New Triple Query endpoint
- Build improvements (Telicent Base Image v1.2.10)
- Build improvements (minimal dependency version updates)

## 0.83.5
- Build improvements (Telicent Base Image v1.2.5)

## 0.83.3 / 0.83.4
- Improved kafka dataset backup/restore.

## 0.83.2
- Improved process of kafka dataset names 

## 0.83.1
- Extend label querying functionality (for debug).

## 0.83.0
- Build improvements

## 0.82.19
- Improve blank node processing for upload, back-up & restore of n-quad format.

## 0.82.18
- Sanitise data set names for backup/restore functionality.

## 0.82.17
- Build improvements

## 0.82.16
- Build improvements

## 0.82.15
- Build improvements

## 0.82.14
- Improving resiliency of restore functionality.
- Build improvements
  - RDF ABAC upgrade to 0.73.1

## 0.82.13
- Adding backup / restore functionality.

## 0.82.8
- Updating GraphQL Jena and YAML Fuseki dependencies.

## 0.82.7
- Added safeguard against potential deadlocking during compaction.

## 0.82.6

- Fix a bug where extra Kafka configuration wasn't passed onto the Kafka Producer for CQRS endpoints preventing
  connection to Kafka servers that use security.
- Fuseki Kafka Connector upgraded to 1.5.1 which has some minor fixes around the additional Kafka configuration support.
- Build improvements:
    - RDF ABAC upgraded to 0.72.0
    - Removed explicit Protobuf dependency as transitive dependency via Jena is now an up to date version
    - Various test and build dependencies upgraded to latest available 

## 0.82.5

- Fuseki Kafka Connector upgraded to 1.5.0 which adds improved support for specifying additional Kafka configuration
  necessary to connect to Kafka clusters that use security.
- Apache Jena upgraded to 5.2.0
- Smart Caches Core upgraded to 0.24.0
- Various other dependencies upgraded to latest available


## 0.82.4

- Update Dockerfile to Rocks DB spawning issue by explicitly specifying C lib to use. 
- Build improvements:
  - Patching minor dependencies (as per dependabot).
  - Tidying up POM file, consolidating version approach with other repos.

## 0.82.2

- Add "compactall" endpoint

## 0.82.1

- Database compaction moved to happen potentially twice, but both after HTTP Server is up to avoid crash restart loops
  when time consuming compactions are needed

## 0.82.0

- Upgraded GraphQL implementation to pick up a fix for intermittent "Not in a Transaction" errors during GraphQL query
  execution
- Upgraded JWT Servlet Auth to reduce noise level of authentication exclusion warnings about health check paths i.e.
  `/$/ping`
- Fuseki Kafka Connections are started after the HTTP Server is up to avoid crash restart loops when signficantly behind
  the Kafka topic(s)
- Database compaction happens both before the server startup and after the Kafka Connector startup to maximise
  opportunities for compacting the databases
- Build improvements:
    - JWT Servlet Auth upgraded to 0.17.0
    - GraphQL Jena upgraded to 0.9.0
    - Protobuf upgraded to 4.28.2
    - Smart Caches Core upgraded to 0.23.0
    - Various build and test dependencies upgraded to latest available

## 0.81.8

- Build improvements:
  - Upgrading RDF ABAC to 0.71.9
  - Fixed typos in specification documentation
  - Exclude compact from authentication
  - Fixing logging

## 0.81.4

- Upgraded Fuseki Kafka implementation to further improve message batching strategy
- Upgraded GraphQL implementation to pick up Telicent Graph Schema enhancements
- Build improvements:
    - Fix issues with the Docker image not being correctly build for multi-platform architectures
    - RDF ABAC upgraded to 0.71.7
    - GraphQL upgraded to 0.8.2
    - JWT Servlet Auth upgraded to 0.16.0
    - Log4j upgraded to 2.24.0
    - Logback upgraded to 1.5.8
    - Smart Caches Core upgraded to 0.22.0

## 0.81.1

- Upgraded RDF-ABAC implementation to fix a bug that could cause data to fail to be ingested if it contained large 
  literals
- Upgraded Fuseki Kafka implementation to fix a bug that could cause data to be ingested in very small batches 
  when consumer is caught up with active producer
- Upgraded GraphQL implementation to fix a bug that could cause Nodes to not be returned if they only appeared as the
  objects of triples in the graph
- Build improvements:
    - Fuseki Kafka Connector upgraded to 1.3.4
    - RDF ABAC upgraded to 0.71.5
    - GraphQL upgraded to 0.8.1

## 0.81.0

- Upgraded GraphQL implementation so Telicent Graph schema now allows `limit` and `offset`
- Upgraded RDF-ABAC implementation to provide improved label lookup performance that provides for improved query
  performance
- Fixed a container packaging bug that led to class initialisation failures at runtime under certain configurations
- Build improvements:
    - Apache Jena upgraded to 5.1.0
    - Fuseki Kafka Connector upgraded to 1.3.3
    - GraphQL upgraded to 0.8.0
    - RDF ABAC upgraded to 0.71.4
    - Various build and test dependencies upgraded to latest available

## 0.80.1

First public open source release

Upgrades Telicent dependencies to current open source releases

## 0.80.0

JWT Authentication is now fully supported and tokens are strictly verified.

Improved no auth mode for developer usage.

## 0.70.0

No support for security labelling by wildcard patterns.

Allow any datasets to have a GraphQL service.

Depends on Apache Jena 5.0.0.

## 0.60.0

New in-memory label store.

An in-memory labels store is always used if there is no `authz:labels` property
of an ABAC dataset.

An ABAC dataset no longer needs `authz:labels :databaseLabels` and the
associated `:databaseLabels rdf:type ja:MemoryDataset .`. These cause a warning
and are ignored.
