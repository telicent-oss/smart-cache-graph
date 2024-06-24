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
