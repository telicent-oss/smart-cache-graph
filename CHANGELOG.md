# Smart Cache Graph

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
