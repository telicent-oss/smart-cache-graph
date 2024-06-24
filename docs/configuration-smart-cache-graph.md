# Configuration for Smart Cache Graph

Smart Cache Graph is an
[Apache Jena Fuseki server](https://jena.apache.org/documentation/fuseki2/)
with additional features:

* [ABAC datasets](https://github.com/Telicent-io/rdf-abac/docs/abac.md)
* [GraphQL](https://github.com/Telicent-io/graphql-jena/blob/main/docs/index.md)
* [OpenTelemetry](./opentelemetry.md) support

Smart Cache Graph is configured using a [Fuseki configuration
file](https://jena.apache.org/documentation/fuseki2/fuseki-configuration.html#fuseki-configuration-file).

Data is stored in a Apache Jena database, and wrapped in another layer
which has an ABAC labels store and which provides the triple authorization filtering.

Data and labels can be stored in-memory, or in persistent storage. This must the
the same for both data stored and label stored. 

## Server Configuration:

This is a single file , often called "config.ttl", and passed to the server on
startup from the command line using `--conf=config.ttl`.

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
     
    ## This enpoint is onlly need if labelled data is loaded via HTTP
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

When a data service is created, the "read data" operations `fuseki:query` and `fuseki:gsp-r`
are automatically converted to apply ABAC label filtering.

A Smart Cache Graph server requires an already autheticated user to be proivded
in the JWT token in the HTTP header.

## ABAC database

### In-memory

```
## --- ABAC dataset
:dataset rdf:type authz:DatasetAuthz ;
    authz:dataset :datasetBase;
    authz:tripleDefaultLabels "!";    ## Default (no label found) is 'deny'

    ## TC access server for finding the user attributes and attributer hierarchies.
    ## This substitutes the value of the environment variable
    ## or Java system property "USER_ATTRIBUTES_URL".
    ##
    ##   USER_ATTRIBUTES_URL="http://host:port/users/lookup/{user}"
    ##
    ## {user} is replaced with the URL-safe encoding of the user id.
    authz:attributesURL <env:USER_ATTRIBUTES_URL>;
    .

# Storage of data.
:datasetBase rdf:type ja:MemoryDataset .
```

The labels are stored in-memory.

With an in-memeory database, the data is reloaded from Kafka on start-up.

### Persistent database




## Kafka Connector

Connection to the Kafka topic is given by:

```
## ---- Fuseki-Kafka connector
PREFIX fk:      <http://jena.apache.org/fuseki/kafka#>

<#connector> rdf:type fk:Connector ;
    fk:bootstrapServers    "-- kafka connection URL string --";
    fk:topic               "knowledge";
    ## This should refer to an authz:upload endpoint
    fk:fusekiServiceName   "/knowledge";
    
    ##fk:syncTopic        false;
    fk:replayTopic      true;
    fk:stateFile        "databases/Replay-RDF.state";
    .

```

The `fk:fusekiServiceName` connects the topic (`fk:topic`) to the data service.

