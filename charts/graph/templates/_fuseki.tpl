{{/*
Copyright (C) 2026 Telicent Limited
*/}}

{{- define "graph.fuseki" -}}
    PREFIX :        <#>
    PREFIX fuseki:  <http://jena.apache.org/fuseki#>
    PREFIX rdf:     <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
    PREFIX rdfs:    <http://www.w3.org/2000/01/rdf-schema#>
    PREFIX ja:      <http://jena.hpl.hp.com/2005/11/Assembler#>
    PREFIX authz:   <http://telicent.io/security#>
    PREFIX cqrs:    <http://telicent.io/cqrs#>
    PREFIX graphql: <https://telicent.io/fuseki/modules/graphql#>
    ## ---- Fuseki-Kafka connector
    PREFIX fk:      <http://jena.apache.org/fuseki/kafka#>
    PREFIX tdb2:    <http://jena.apache.org/2016/tdb#>
    [] rdf:type fuseki:Server ;
        ## Disable SERVICE (call out) in SPARQL.
        ja:context [  ja:cxtName "arq:httpServiceAllowed" ; ja:cxtValue "false" ] ;
        ## Data services enabled.
        fuseki:services (
            :knowledgeService
            :ontologyService
            :catalogService
        ) .
    ## --------
    :knowledgeService rdf:type fuseki:Service ;
        # http://host:port/knowledge
        fuseki:name "/knowledge" ;
        fuseki:endpoint [
            # SPARQL query service on "/knowledge/sparql"
            fuseki:operation fuseki:query ;
            fuseki:name "sparql" ;
            ja:context [
                ja:cxtName "arq:queryTimeout" ;
                ja:cxtValue "120000,120000"
            ] ;
        ];
        fuseki:endpoint [
            # SPARQL query service on "/knowledge/query"
            fuseki:operation fuseki:query ;
            fuseki:name "query" ;
            ja:context [
                ja:cxtName "arq:queryTimeout" ;
                ja:cxtValue "120000,120000"
            ] ;
        ] ;
        ## Updates will be generate an RDF patch which is sent to the Kafka topic.
        ## This exposes update to all users and should only be applied in development environments. Pending access admin/user pools solution.
        fuseki:endpoint [
            # CQRS update service on "/knowledge/update"
            fuseki:operation cqrs:update ;
            # This name (ja:cxtValue) must agree with the connector below.
            ja:context [
                ja:cxtName "kafka:topic" ;
                ja:cxtValue "knowledge"
            ];
            fuseki:name "update"
        ];
        fuseki:endpoint [
            # GraphQL operations
            fuseki:operation graphql:graphql ;
            ja:context [
                ja:cxtName "graphql:executor" ;
                ja:cxtValue "io.telicent.jena.graphql.execution.telicent.graph.TelicentGraphExecutor"
            ] ;
            fuseki:name "graphql"
        ];
        fuseki:endpoint [
            # SPARQL Graph Store Protocol (read) on "/knowledge/get"
            fuseki:operation fuseki:gsp-r ;
            fuseki:name "get"
        ] ;
        fuseki:endpoint [
            # SHACL validation on "/knowledge/shacl"
            fuseki:operation fuseki:shacl ;
            fuseki:name "shacl"
        ] ;
        # Knowledge dataset to use
        fuseki:dataset :datasetAuth ;
        .
    ## Dataset with security labels / ABAC.
    :datasetAuth rdf:type authz:DatasetAuthz ;
        ## Config item where labels are stored
        authz:labelsStore [ 
          authz:labelsStorePath "/fuseki/databases/knowledgeLabels.db" ;
          authz:labelsStoreLegacy {{ .Values.graph.legacyLabels }} ;
          {{- if not .Values.graph.legacyLabels }}
          authz:labelsStoreByHash true ;
          authz:labelsStoreByHashFunction "xx128" ;
          {{- end }}
        ] ;
        authz:dataset :datasetAuthBase;
        authz:tripleDefaultLabels "!";
        ## Use Telicent Auth Server
        authz:authServer true;
        .
    ## Storage of data on filesystem.
    :datasetAuthBase rdf:type      tdb2:DatasetTDB2 ;
        tdb2:location "/fuseki/databases/knowledge";
        .
    ## --------
    :ontologyService rdf:type fuseki:Service ;
        # http://host:port/ontology
        fuseki:name "/ontology" ;
        fuseki:endpoint [
            # SPARQL query service on "/ontology/sparql"
            fuseki:operation fuseki:query ;
            fuseki:name "sparql"
        ];
        fuseki:endpoint [
            # SPARQL query service on "/ontology/query"
            fuseki:operation fuseki:query ;
            fuseki:name "query"
        ] ;
        ## Updates will be generate an RDF patch which is sent to the Kafka topic.
        ## This exposes update to all users and should only be applied in development environments. Pending access admin/user pools solution.
        fuseki:endpoint [
            # CQRS update service on "/ontology/update"
            fuseki:operation cqrs:update ;
            # This name (ja:cxtValue) must agree with the connector below.
            ja:context [
                ja:cxtName "kafka:topic" ;
                ja:cxtValue "ontology"
            ];
            fuseki:name "update" ] ;
        fuseki:endpoint [
            # GraphQL operations
            fuseki:operation graphql:graphql ;
            ja:context [
                ja:cxtName "graphql:executor" ;
                ja:cxtValue "io.telicent.jena.graphql.execution.telicent.graph.TelicentGraphExecutor"
            ] ;
            fuseki:name "graphql"
        ];
        fuseki:endpoint [
            # SPARQL Graph Store Protocol (read) on "/ontology/get"
            fuseki:operation fuseki:gsp-r ;
            fuseki:name "get"
        ] ;
        fuseki:endpoint [
            # SHACL validation on "/ontology/shacl"
            fuseki:operation fuseki:shacl ;
            fuseki:name "shacl"
        ] ;
        # Ontology dataset to use
        fuseki:dataset :ontologyDataset ;
        .
    ## With security labels.
    ## --- ABAC dataset
    :ontologyDataset rdf:type authz:DatasetAuthz ;
        ## Config item where labels are stored
        authz:labelsStore [ 
          authz:labelsStorePath "/fuseki/databases/ontologyLabels.db" ;
          authz:labelsStoreLegacy {{ .Values.graph.legacyLabels }} ;
          {{- if not .Values.graph.legacyLabels }}
          authz:labelsStoreByHash true ;
          authz:labelsStoreByHashFunction "xx128" ;
          {{- end }}
        ] ;
        authz:dataset :datasetOntoBase;
        authz:tripleDefaultLabels "!";
        # Use Telicent Auth Server
        authz:authServer true;
        .
    ## Storage of data on filesystem.
    :datasetOntoBase rdf:type tdb2:DatasetTDB2 ;
        tdb2:location "/fuseki/databases/ontology";
        .
    ## --------
    :catalogService rdf:type fuseki:Service ;
        fuseki:name "/catalog" ;
        fuseki:endpoint [
            # SPARQL query service on "/catalog/sparql"
            fuseki:operation fuseki:query ;
            fuseki:name "sparql"
        ];
        fuseki:endpoint [
            # SPARQL query service on "/catalog/query"
            fuseki:operation fuseki:query ;
            fuseki:name "query"
        ] ;
        ## Updates will be generate an RDF patch which is sent to the Kafka topic.
        ## This exposes update to all users and should only be applied in development environments. Pending access admin/user pools solution.
        fuseki:endpoint [
            # CQRS update service on "/catalog/update"
            fuseki:operation cqrs:update ;
            # This name (ja:cxtValue) must agree with the connector below.
            ja:context [
                ja:cxtName "kafka:topic" ;
                ja:cxtValue "catalog"
            ];
            fuseki:name "update" ] ;
        fuseki:endpoint [
            # GraphQL operations
            fuseki:operation graphql:graphql ;
            ja:context [
                ja:cxtName "graphql:executor" ;
                ja:cxtValue "io.telicent.jena.graphql.execution.telicent.graph.TelicentGraphExecutor"
            ] ;
            fuseki:name "graphql"
        ];
        fuseki:endpoint [
            # SPARQL Graph Store Protocol (read) on "/catalog/get"
            fuseki:operation fuseki:gsp-r ;
            fuseki:name "get"
        ] ;
        fuseki:endpoint [
            # SHACL validation on "/catalog/shacl"
            fuseki:operation fuseki:shacl ;
            fuseki:name "shacl"
        ] ;
        # Catalog dataset to use
        fuseki:dataset :catalogDataset ;
        .
    ## With security labels.
    ## --- ABAC dataset
    :catalogDataset rdf:type authz:DatasetAuthz ;
        ## Config item where labels are stored (only define if not in memory)
        authz:labelsStore [ 
          authz:labelsStorePath "/fuseki/databases/catalogLabels.db" ;
          authz:labelsStoreLegacy {{ .Values.graph.legacyLabels }} ;
          {{- if not .Values.graph.legacyLabels }}
          authz:labelsStoreByHash true ;
          authz:labelsStoreByHashFunction "xx128" ;
          {{- end }}
        ] ;
        authz:dataset :datasetCatBase;
        authz:tripleDefaultLabels "!";
        ## Use Telicent Auth Server
        authz:authServer true;
        .
    ## Storage of data on filesystem.
    :datasetCatBase rdf:type tdb2:DatasetTDB2 ;
        tdb2:location "/fuseki/databases/catalog";
        .
    ## --------
    <#kafkaCluster> rdf:type fk:Cluster ;
        fk:bootstrapServers    {{ .Values.global.kafka.bootstrapServers | quote }};
        # Additional Kafka Configuration properties are loaded from a file specified via
        # an environment variable
        # Empty default (the :} at the end) means if this variable is not set then no extra
        # properties are loaded
        fk:configFile       "env:{KAFKA_CONFIG_FILE_PATH:}"
        .
    <#connector> rdf:type fk:Connector ;
        fk:cluster             <#kafkaCluster> ;
        fk:topic               "knowledge";
        fk:dlqTopic            "knowledge.dlq";
        fk:fusekiServiceName   "/knowledge";
        ## From 0.90.0 onwards this is mandatory if defining more than one connector
        ## and each connector MUST have a unique Group ID
        fk:groupId "graph-knowledge";
        ## fk:replayTopic -- true for in-memory storage / false for TDB2 storage
        fk:replayTopic      false;
        fk:stateFile        "/fuseki/databases/Replay-RDF.state";
        .
    <#ontologyConnector> rdf:type fk:Connector ;
        fk:cluster             <#kafkaCluster> ;
        fk:topic               "ontology";
        fk:dlqTopic            "ontology.dlq";
        ## This should refer to the target dataset
        fk:fusekiServiceName   "/ontology";
        ## From 0.90.0 onwards this is mandatory if defining more than one connector
        ## and each connector MUST have a unique Group ID
        fk:groupId "graph-ontology";
        ## fk:replayTopic -- true for in-memory storage / false for TDB2 storage
        fk:replayTopic      false;
        fk:stateFile        "/fuseki/databases/Replay-Ontology-RDF.state";
        .
    <#catalogConnector> rdf:type fk:Connector ;
        fk:cluster             <#kafkaCluster> ;
        fk:topic               "catalog";
        fk:dlqTopic            "catalog.dlq";
        ## This should refer to the target dataset
        fk:fusekiServiceName   "/catalog";
        ## From 0.90.0 onwards this is mandatory if defining more than one connector
        ## and each connector MUST have a unique Group ID
        fk:groupId "graph-catalog";
        ## fk:replayTopic -- true for in-memory storage / false for TDB2 storage
        fk:replayTopic      false;
        fk:stateFile        "/fuseki/databases/Replay-Catalog-RDF.state";
        .

{{- end  -}}
