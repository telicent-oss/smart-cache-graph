# Configuration for Smart Cache Graph

Smart Cache Graph is an [Apache Jena Fuseki server](https://jena.apache.org/documentation/fuseki2/) with additional
features:

* [ABAC datasets](https://github.com/telicent-oss/rdf-abac/blob/main/docs/abac.md)
* [GraphQL](https://github.com/Telicent-oss/graphql-jena/blob/main/docs/index.md)
* [OpenTelemetry](./opentelemetry.md) support

Smart Cache Graph is configured using a [Fuseki configuration
file](https://jena.apache.org/documentation/fuseki2/fuseki-configuration.html#fuseki-configuration-file).

Data is stored in a Apache Jena database, and wrapped in another layer which has an ABAC labels store and which provides
the quad authorization filtering.

Data and labels can be stored in-memory, or in persistent storage. This must the the same for both data stored and label
stored. 

## Server Configuration:

This is a single file , often called `config.ttl`, and passed to the server on startup from the command line using
`--conf=config.ttl`.

First, there is a series of URI prefix (namespace) declarations:

```
PREFIX :        <#>
PREFIX fuseki:  <http://jena.apache.org/fuseki#>
PREFIX rdf:     <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
PREFIX ja:      <http://jena.hpl.hp.com/2005/11/Assembler#>
PREFIX tdb2:    <http://jena.apache.org/2016/tdb#>

PREFIX authz:   <http://telicent.io/security#>
PREFIX cqrs:    <http://telicent.io/cqrs#>
PREFIX graphql: <https://telicent.io/fuseki/modules/graphql#>
```
then a section describing the server:

```
[] rdf:type fuseki:Server ;
   fuseki:services (
     :knowledgeService
   ) .
```

In this example there is one separate data service for the knowledge topic.

```
:knowledgeService rdf:type fuseki:Service ;
    fuseki:name "/knowledge" ;
    fuseki:endpoint [ fuseki:operation fuseki:query ] ;

    fuseki:endpoint [ 
        fuseki:operation fuseki:query ;
        fuseki:name "sparql" 
    ];
    fuseki:endpoint [
        fuseki:operation fuseki:query ;
        fuseki:name "query" 
    ] ;
    fuseki:endpoint [
        fuseki:operation fuseki:gsp-r ;
        fuseki:name "get"
    ] ;
     
    ## This endpoint is only needed if labelled data is loaded via HTTP
    ## and not via the Kafka 'knowledge' topic.
    ## NB authz:upload - this is the ABAC processor
    fuseki:endpoint [ 
        fuseki:operation authz:upload ;
        fuseki:name "upload"
    ] ;

    ## GraphQL operations
    fuseki:endpoint [ 
        fuseki:operation graphql:graphql ;
        ja:context [ 
            ja:cxtName "graphql:executor" ;
            ja:cxtValue "io.telicent.jena.graphql.execution.telicent.graph.TelicentGraphExecutor"
        ] ;
        fuseki:name "graphql"
    ];
                      
    ## CQRS update
    ## Updates will be generate an RDF patch which is sent to the Kafka topic.
    fuseki:endpoint [ 
        fuseki:operation cqrs:update ;
        # This name (ja:cxtValue) must agree with the connector below.
        ja:context [ 
            ja:cxtName "kafka:topic" ; 
            ja:cxtValue "knowledge" 
        ] ;
        fuseki:name "update" ] ;

    ## This is the ABAC database.
    fuseki:dataset :dataset ;
    .
```

When a data service is created, the "read data" operations `fuseki:query` and `fuseki:gsp-r` are automatically converted
to apply ABAC label filtering.  For "write data" operations you should use the `authz:upload` or `cqrs:update`
operations as shown above.

A Smart Cache Graph server requires an already authenticated user to be provided in the JWT token in the HTTP header.

## ABAC database

### In-memory

```
## --- ABAC dataset
:dataset rdf:type authz:DatasetAuthz ;
    # The underlying dataset that stores the actual data
    authz:dataset :datasetBase;

    ## Default (no label found) is 'deny'
    authz:tripleDefaultLabels "!";

    ## Enable usage of Telicent Auth Server as the Authentication/Authorization source
    ## Location of the server is controlled via USERINFO_URL environment variable
    authz:authServer true;
    .

# Storage of data.
:datasetBase rdf:type ja:MemoryDataset .
```

The labels are stored in-memory.

With an in-memeory database, the data is reloaded from Kafka on start-up.

### Persistent database

```
:dataset rdf:type authz:DatasetAuthz ;
    # The underlying dataset that stores the actual data
    authz:dataset :datasetBase;

    ## Persistent Storage of Labels using RocksDB
    authz:labelsStore [
      authz:labelsStorePath "/path/to/label-store/" ;

      # Disable the legacy mode store in favour of the modern dictionary store
      # The modern store can also automatically migrate data from the legacy store, so this is safe to enable even if 
      # there is existing data in the legacy format.
      authz:labelsStoreLegacy false ;

      # Configure the desired hash function for the modern store
      authz:labelsStoreByHash true ;
      authz:labelsStoreByHashFunction "xx128"
    ] ;

    ## Default (no label found) is 'deny'
    authz:tripleDefaultLabels "!";

    ## Enable usage of Telicent Auth Server as the Authentication/Authorization source
    ## Location of the server is controlled via USERINFO_URL environment variable
    authz:authServer true;
    .

# Persistent Storage of Data using TDB2
:datasetBase rdf:type      tdb:DatasetTDB ;
    tdb:location "/path/to/tdb-store/" ;
    .
```

In this example the `authz:DatasetAuthz` instance is backed by a persistent TDB2 storage for the RDF, and persistent
RocksDB storage for the labels.

## Kafka Connector

Connection to the Kafka topic is given by:

```
## ---- Fuseki-Kafka connector
PREFIX fk:      <http://jena.apache.org/fuseki/kafka#>

<#connector> rdf:type fk:Connector ;
    fk:bootstrapServers    "-- kafka connection URL string --";
    fk:topic               "knowledge";
    ## This should refer to the base dataset path, writes go directly to the dataset
    fk:fusekiServiceName   "/knowledge";
    
    ##fk:syncTopic        false;
    fk:replayTopic      true;
    fk:stateFile        "databases/Replay-RDF.state";
    .

```

The `fk:fusekiServiceName` connects the topic (`fk:topic`) to the data service.
