/*
 *  Copyright (c) Telicent Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.telicent;

import io.telicent.jena.abac.core.Attributes;
import io.telicent.jena.abac.core.AttributesStore;
import io.telicent.jena.abac.fuseki.SysFusekiABAC;
import io.telicent.jena.abac.services.AttributeService;
import io.telicent.jena.abac.services.SimpleAttributesStore;
import io.telicent.smart.cache.configuration.Configurator;
import org.apache.jena.atlas.lib.FileOps;
import org.apache.jena.cmd.CmdException;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.fuseki.kafka.lib.FKLib;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.system.FusekiLogging;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.http.HttpOp;
import org.apache.jena.kafka.KafkaConnectorAssembler;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.SysRIOT;
import org.apache.jena.riot.WebContent;
import org.apache.jena.sparql.ARQException;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.exec.QueryExec;
import org.apache.jena.sparql.exec.RowSet;
import org.apache.jena.sparql.exec.RowSetOps;
import org.apache.jena.sparql.exec.RowSetRewindable;
import org.apache.jena.sparql.exec.http.DSP;
import org.apache.jena.sparql.exec.http.QueryExecHTTP;
import org.apache.jena.sparql.exec.http.QueryExecHTTPBuilder;
import org.apache.jena.sparql.resultset.ResultSetCompare;
import org.apache.jena.sys.JenaSystem;
import org.apache.jena.system.G;
import org.junit.jupiter.api.*;
import org.yaml.snakeyaml.scanner.ScannerException;

import java.util.List;
import java.util.Properties;

import static io.telicent.core.SmartCacheGraph.construct;
import static io.telicent.jena.abac.services.LibAuthService.serviceURL;
import static org.junit.jupiter.api.Assertions.*;

/*
* Test SCG usage of varying configuration parameters.
*/
class TestYamlConfigParser {

    private static final String DIR = "src/test/files";
    private static String STATE_DIR = "target/state";
    private FusekiServer server;
    private static MockKafka mock;

    private static final String serviceName = "/ds";
    public final String update = "data-update";
    public final String query = "sparql?query=";
    public final String expectedResponse = """
                                <?xml version="1.0"?>
                                <sparql xmlns="http://www.w3.org/2005/sparql-results#">
                                  <head>
                                    <variable name="character"/>
                                  </head>
                                  <results>
                                    <result>
                                      <binding name="character">
                                        <uri>https://looneytunes-graph.com/New_Character</uri>
                                      </binding>
                                    </result>
                                  </results>
                                </sparql>
                                """;
    public final String sparqlQuery = """
                PREFIX : <https://looneytunes-graph.com/>
                PREFIX mfg: <http://myfavs-graph.com/>
                SELECT ?character
                WHERE {
                     ?character a :Looney_Tunes_Character ;
                                :name "New Character" .
                }""";

    public final String sparqlUpdate = """
                    PREFIX rdf:      <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                    PREFIX xsd:      <http://www.w3.org/2001/XMLSchema#>
                    PREFIX : <https://looneytunes-graph.com/>
                    INSERT DATA {
                    :New_Character a :Looney_Tunes_Character ;
                        :name "New Character" ;
                        :species "Hare" ;
                        :gender "Male" ;
                        :made_debut_appearance_in :A_Wild_Hare ;
                        :created_by :Tex_Avery ;
                        :personality_trait "Cunning" ;
                        :personality_trait "Smart" ;
                        :personality_trait "Charismatic" ;
                        :known_for_catchphrase "What's up, doc?" .
                   }""";
    public static RowSetRewindable expectedRSR;
    public final String sparqlUpdateConnector = """
                    PREFIX : <http://example/>
                    INSERT DATA {
                        :s :p4 12355 .
                        :s :p5 45655 .
                   }""";

    Properties producerProps() {
        Properties producerProps = new Properties();
        producerProps.put("bootstrap.servers", mock.getServer());
        return producerProps;
    }

    @BeforeAll
    public static void before() {

        Model comparisonModel = ModelFactory.createDefaultModel();
        String baseURI = "http://example/";
        Resource s1 = comparisonModel.createResource(baseURI + "s1");
        Property p1 = comparisonModel.createProperty(baseURI + "p1");
        Literal l1 = comparisonModel.createTypedLiteral(1234, XSDDatatype.XSDinteger);
        Resource s2 = comparisonModel.createResource(baseURI + "s");
        Property p2 = comparisonModel.createProperty(baseURI + "p2");
        Literal l2 = comparisonModel.createTypedLiteral(789, XSDDatatype.XSDinteger);
        comparisonModel.add(s1, p1, l1);
        comparisonModel.add(s2, p2, l2);

        String queryString = "SELECT ?s ?p ?o WHERE { ?s ?p ?o }";
        Query query = QueryFactory.create(queryString);
        QueryExecution qe = QueryExecutionFactory.create(query, comparisonModel);
        QueryExec qExec = QueryExec.dataset(DatasetGraphFactory.create(comparisonModel.getGraph())).query(query).build();
        expectedRSR = qExec.select().rewindable();

        mock = new MockKafka();
        Properties consumerProps = new Properties();
        consumerProps.put("bootstrap.servers", mock.getServer());
        Properties producerProps = new Properties();
        producerProps.put("bootstrap.servers", mock.getServer());

        mock.createTopic("RDF0");
        mock.createTopic("RDF1");
        mock.createTopic("RDF2");
        mock.createTopic("RDF_Patch");
    }



    @BeforeEach
    void setUp() throws Exception {
        FusekiLogging.setLogging();
        SysFusekiABAC.init();
        LibTestsSCG.teardownAuthentication();
        LibTestsSCG.disableInitialCompaction();
    }

    @AfterEach
    void clearDown() {
        if (null != server) {
            server.stop();
        }
        Configurator.reset();
    }

    @AfterAll
    public static void after() {
        mock.stop();
    }


    @Test
    void yaml_config_tim() {
        // given
        List<String> arguments = List.of("--conf",DIR + "/yaml/config-tim.yaml");
        String[] args = arguments.toArray(new String[0]);
        // when
        server = construct(arguments.toArray(new String[0])).start();
        int port = server.getPort();
        String url = "http://localhost:" + port + "/ds/";
        HttpOp.httpPost(url + update, WebContent.contentTypeSPARQLUpdate, sparqlUpdate);
        String encodedQuery = java.net.URLEncoder.encode(sparqlQuery, java.nio.charset.StandardCharsets.UTF_8);
        String actualResponse = HttpOp.httpGetString(url + query + encodedQuery);

        // then
        assertEquals(expectedResponse, actualResponse);
    }

    @Test
    void yaml_config_tdb2() {
        // given
        List<String> arguments = List.of("--conf",DIR + "/yaml/config-tdb2.yaml");
        String[] args = arguments.toArray(new String[0]);
        // when
        server = construct(arguments.toArray(new String[0])).start();
        int port = server.getPort();
        String url = "http://localhost:" + port + "/ds/";
        HttpOp.httpPost(url + update, WebContent.contentTypeSPARQLUpdate, sparqlUpdate);
        String encodedQuery = java.net.URLEncoder.encode(sparqlQuery, java.nio.charset.StandardCharsets.UTF_8);
        String actualResponse = HttpOp.httpGetString(url + query + encodedQuery);

        // then
        assertEquals(expectedResponse, actualResponse);
    }

    @Test
    void yaml_config_abac() {
        // given
        List<String> arguments = List.of("--conf",DIR + "/yaml/config-abac-multiple.yaml");
        // when
        server = construct(arguments.toArray(new String[0])).start();
        RowSetRewindable actualResponseRSR;
        load(server);
        actualResponseRSR = query(server, "u1"); // 3

        // then
        boolean equals = ResultSetCompare.isomorphic(expectedRSR, actualResponseRSR);
        assertTrue(equals);
    }


    @Test
    void yaml_config_kafka_connector() {
        String TOPIC = "RDF0";
        Graph graph = configuration(DIR + "/yaml/config-connector-integration-test-1.ttl", mock.getServer());
        FileOps.ensureDir(STATE_DIR);
        FileOps.clearDirectory(STATE_DIR);
        // given
        List<String> arguments = List.of("--conf",DIR + "/yaml/config-connector-integration-test-1.yaml");
        // when
        FKLib.sendFiles(producerProps(), TOPIC, List.of("src/test/files/yaml/data-no-labels.trig"));
        server = construct(arguments.toArray(new String[0])).start();
        try {
            String URL = "http://localhost:"+server.getHttpPort()+"/ds";
            RowSet rowSet = QueryExecHTTP.service(URL).query("SELECT (count(*) AS ?C) {?s ?p ?o}").select();
            int count = ((Number)rowSet.next().get("C").getLiteralValue()).intValue();
            Assertions.assertEquals(6, count);
            HttpOp.httpPost(URL + "/update", WebContent.contentTypeSPARQLUpdate, sparqlUpdateConnector);
            RowSet rowSet2 = QueryExecHTTP.service(URL).query("SELECT (count(*) AS ?C) {?s ?p ?o}").select();
            int count2 = ((Number)rowSet2.next().get("C").getLiteralValue()).intValue();
            Assertions.assertEquals(8, count2);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // then
    }

    /*
    @Test
    void yaml_config_custom_prefix() {
        // given
        List<String> arguments = List.of("--conf",DIR + "/yaml/config-tdb2.yaml");
        String[] args = arguments.toArray(new String[0]);
        // when
        server = construct(arguments.toArray(new String[0])).start();
        int port = server.getPort();
        String url = "http://localhost:" + port + "/ds/";
        HttpOp.httpPost(url + update, WebContent.contentTypeSPARQLUpdate, sparqlUpdate);
        String encodedQuery = java.net.URLEncoder.encode(sparqlQuery, java.nio.charset.StandardCharsets.UTF_8);
        String actualResponse = HttpOp.httpGetString(url + query + encodedQuery);

        // then
        assertEquals(expectedResponse, actualResponse);
    }*/

    @Test
    void fail_yaml_config_bad_file() {
        // given
        List<String> arguments = List.of("--conf",DIR + "/yaml/config-no-server-field.yaml");
        String[] args = arguments.toArray(new String[0]);
        // when
        RuntimeException ex = assertThrows(RuntimeException.class,() -> {
            server = construct(arguments.toArray(new String[0])).start();
        });
        assertEquals("Failure parsing the YAML config file: java.lang.IllegalArgumentException: 'server' field is missing", ex.getMessage());

    }

    @Test
    void fail_yaml_config_missing_file() {
        // given
        List<String> arguments = List.of("--conf",DIR + "/yaml/no-config.yaml");
        String[] args = arguments.toArray(new String[0]);
        // when
        RuntimeException ex = assertThrows(RuntimeException.class,() -> {
            server = construct(arguments.toArray(new String[0])).start();
        });
        assertEquals("Failure parsing the YAML config file: java.io.UncheckedIOException: java.io.FileNotFoundException: src/test/files/yaml/no-config.yaml (No such file or directory)", ex.getMessage());
    }



    private static void load(FusekiServer server) {
        String URL = "http://localhost:" + server.getPort() + serviceName;
        String uploadURL = URL + "/upload";
        load(uploadURL, DIR + "/yaml/data-and-labels.trig");
    }

    private static RowSetRewindable query(FusekiServer server, String user) {
        String URL = "http://localhost:" + server.getPort() + serviceName;
        return query(URL, user);
    }

    private static void load(String uploadURL, String filename) {
        DSP.service(uploadURL).POST(filename);
    }

    private static RowSetRewindable query(String url, String user) {
        String queryString = "SELECT * { ?s ?p ?o }";
        return query(url, user, queryString);
    }

    private static RowSetRewindable query(String url, String user, String queryString) {
        System.out.println("Query: " + user);
        RowSetRewindable rowSet =
                QueryExecHTTPBuilder.service(url)
                        .query(queryString)
                        .httpHeader("Authorization", "Bearer user:" + user)
                        .select()
                        .rewindable();
        long x = RowSetOps.count(rowSet);
        System.out.printf("User = %s ; returned %d results\n", user, x);
        rowSet.reset();
        RowSetOps.out(System.out, rowSet);
        return rowSet;
    }

    private Graph configuration(String filename, String bootstrapServers) {
        Graph graph = RDFParser.source(filename).toGraph();
        List<Triple> triplesBootstrapServers = G.find(graph,
                null,
                KafkaConnectorAssembler.pKafkaBootstrapServers,
                null).toList();
        triplesBootstrapServers.forEach(t->{
            graph.delete(t);
            graph.add(t.getSubject(), t.getPredicate(), NodeFactory.createLiteralString(bootstrapServers));
        });

        FileOps.ensureDir("target/state");
        List<Triple> triplesStateFile = G.find(graph, null, KafkaConnectorAssembler.pStateFile, null).toList();
        triplesStateFile.forEach(t->{
            graph.delete(t);
            String fn = t.getObject().getLiteralLexicalForm();
            graph.add(t.getSubject(), t.getPredicate(), NodeFactory.createLiteralString(STATE_DIR+"/"+fn));
        });

        return graph;
    }

}
