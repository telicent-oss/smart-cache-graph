# Yaml config parser and RDF config generator

A tool for translating Yaml files to standard Fuseki config files.
Consists of a Yaml parser and RDF generator.

### Yaml config files

In order for the Yaml file to be translated into RDF it need s to follow a certain format.
 Example:
```
version: "1.0"
server:
name: "Fuseki server simple"
services:
- name: "ds"
  endpoints:
    - name: "sparql"
      operation: query
      settings:
      'arq:queryTimeout': "1000,10000"
    - name: "data-update"
      operation: update
      database:  "tdb2-db"

databases:
- name: "tdb2-db"
  dbtype: TDB2
  location: "target/test-DB"
  ```
The file needs a String `version`, a server object, a list of services, and a list of databases.
The server has a required `name` String parameter and optional `settings` map.

Each `service` in the list has a String `name` (required), a list of `endpoints` (required, but can be empty), and String `database` name (required). The list of services is mandatory, but it can be empty.

Each `endpoint` has a String `name` (optional), an `operation` (required), and a map of `settings` (optional).

Each `database` has a String `name` (required), a `dbtype` (required) which takes one of two values: `TIM` - meaning the database is in-memory, or `TDB2` - it is a TDB2 database.
If the `dbtype` is the former, the database has to have a defined String `data`, which is the path to the data file. If the type takes the latter value, the required field is insted `location`, which is the path to the database.
A database also has a `settings` field, which just like in the other objects, is optional and a map.

#### ABAC
A `database` can have one more type: `ABAC`. [ABAC databases](https://github.com/telicent-oss/rdf-abac/tree/bba08411276e139743c038b39382ae477663a5e1) have a number of additional fields, as well as an underlying either
`TDB2` or `TIM` database. 

One of the mandatory ABAC database fields is `dataset`, with the name of the underlying database as the value. 
It also has either the path to an attributes file (`attributes`) or the remote attribute store (`attributes-url`, the path to a labels file(`labels`) or labels store(`labels-store`), default policy when no triple label is found(`triple-default-labels`), the `cache` Boolean, which determines whether the caching is on or off, as well as the the attribute and hierarchy cache sizes and expiry times (`hierarchy-cache-size`, `hierarchy-cache-expiry-time`, `attribute-cache-size`, `attribute-cache-expiry-time`).
The underlying database works the same as any other TIM or TDB2 database.
```
databases:
  - name: "abac-db"
    dbtype: ABAC
    dataset: "dataset-under"
    attributes-url: "http://localhost:3133/users/lookup/{user}"

  - name: "dataset-under"
    dbtype: TIM
    data: "src/main/files/abac/data-and-labels.trig"
```
#### Jena Fuseki Kafka Connectors
The parser also supports [Fuseki-Kafka connector](https://github.com/telicent-oss/jena-fuseki-kafka?tab=readme-ov-file) configuration.
The connectors are defined in an optional `connectors` list. Each has a mandatory destination Fuseki service name(`fuseki-service`) field,
`topic`, `bootstrap-servers`, and `state-file`, as well as optional Boolean `replay-topic` and `sync-topic` fields, and `group-id`.
```
connectors:
  - fuseki-service: "/ds"
    topic: "RDF0"
    bootstrap-servers: "localhost:9092"
    state-file: "Replay-RDF0.state"
    sync-topic: true
  - fuseki-service: "/ds2"
    topic: "RDF0"
    bootstrap-servers: "localhost:9092"
    state-file: "Replay-RDF0.state"
    sync-topic: true
```

#### Prefixes
Users can define custom prefixes that will be translated into rdf. The optional `prefixes` map contains `prefix`-`namespace` pairs and the prefixes can be used by appending them to the start of the word with a colon between.
```
version: "1.0"

prefixes:
- prefix: "fk"
  namespace: "http://jena.apache.org/fuseki/kafka#"
- prefix: "cqrs"
  namespace: "http://telicent.io/cqrs#"
- prefix: "graphql"
  namespace: "https://telicent.io/fuseki/modules/graphql#"
- prefix: "authz"
  namespace: "http://telicent.io/security#"
  
  server:
  name: "Fuseki server simple"
  
  services:
- name: "ds"
  endpoints:
 - name: "upload"
   operation: authz:upload
 - operation: authz:query
 - name: "data-update"
   operation: update
   database:  "abac-tdb2-db"

databases:
- name: "abac-tdb2-db"
  dbtype: ABAC
  dataset: "dataset-under"
  attributes: "abac/attribute-store.ttl"
  labels-store: "target/labels"

- name: "dataset-under"
  dbtype: TDB2
  location: "target/test-abac-DB"
```
### User guide

The `YAMLConfigParser` class contains the `run` method which takes the path to the Yaml file as an argument.
`run` return a `ConfigStruct` - a data structure mimicking the format of the Yaml config file and containing 
all of its fields' values. 

This `ConfigStruct` can then be passed as an argument to `RDFConfigGenerator`'s `createRDFModel` method which will
return an RDF model of the config file in the [standard Fuseki format](https://jena.apache.org/documentation/fuseki2/fuseki-configuration.html). That model can then be written to a TTL file.