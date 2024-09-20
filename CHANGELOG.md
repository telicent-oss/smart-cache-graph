# Smart Cache Graph

## 0.81.8
- Build improvements:
  - Upgrading RDF ABAC to 0.71.9
  - Fixed typos in specification documentation
  - Exclude compact from authentication

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
    - Various build and test dependencies upgraded to latest available

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
