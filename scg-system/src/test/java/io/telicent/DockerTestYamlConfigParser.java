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

import io.telicent.jena.abac.fuseki.SysFusekiABAC;
import io.telicent.smart.cache.configuration.Configurator;
import org.apache.jena.atlas.io.IO;
import org.apache.jena.atlas.lib.FileOps;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.fuseki.kafka.FKLib;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.system.FusekiLogging;
import org.apache.jena.http.HttpOp;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.WebContent;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.exec.QueryExec;
import org.apache.jena.sparql.exec.RowSet;
import org.apache.jena.sparql.exec.RowSetOps;
import org.apache.jena.sparql.exec.RowSetRewindable;
import org.apache.jena.sparql.exec.http.DSP;
import org.apache.jena.sparql.exec.http.QueryExecHTTP;
import org.apache.jena.sparql.exec.http.QueryExecHTTPBuilder;
import org.apache.jena.sparql.resultset.RDFOutput;
import org.junit.jupiter.api.*;
import org.testcontainers.shaded.org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;

import static io.telicent.core.SmartCacheGraph.construct;
import static org.junit.jupiter.api.Assertions.*;

/*
* Test SCG usage of YAML config files.
*/
class DockerTestYamlConfigParser {

    private static final String DIR = "src/test/files";
    private FusekiServer server;
    private static MockKafka mock;

    private static final String serviceName = "/ds";
    public final String update = "data-update";
    public final String query = "sparql?query=";
    public static String queryStr = "SELECT * { ?s ?p ?o }";
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
    public static RowSetRewindable expectedRSRtdl;
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
        // build a RowSetRewindable expectedRSR for checking the ABAC tests results
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
        Query query = QueryFactory.create(queryStr);
        QueryExec qExec = QueryExec.dataset(DatasetGraphFactory.create(comparisonModel.getGraph())).query(query).build();
        expectedRSR = qExec.select().rewindable();
        Resource s3 = comparisonModel.createResource(baseURI + "s");
        Property p3 = comparisonModel.createProperty(baseURI + "q");
        Literal l3 = comparisonModel.createLiteral("No label");
        comparisonModel.add(s3, p3, l3);
        QueryExec qExec2 = QueryExec.dataset(DatasetGraphFactory.create(comparisonModel.getGraph())).query(query).build();
        expectedRSRtdl = qExec2.select().rewindable();

        // for the kafka connector tests
        mock = new MockKafka();
        Properties consumerProps = new Properties();
        consumerProps.put("bootstrap.servers", mock.getServer());
        Properties producerProps = new Properties();
        producerProps.put("bootstrap.servers", mock.getServer());

        mock.createTopic("RDF0");
    }

    @BeforeEach
    void setUp() throws Exception {
        FusekiLogging.setLogging();
        SysFusekiABAC.init();
        LibTestsSCG.teardownAuthentication();
        LibTestsSCG.disableInitialCompaction();
        expectedRSR.reset();
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
        List<String> arguments = List.of("--conf",DIR + "/yaml/config-tim.yaml");
        server = construct(arguments.toArray(new String[0])).start();
        int port = server.getPort();
        String url = "http://localhost:" + port + "/ds/";
        HttpOp.httpPost(url + update, WebContent.contentTypeSPARQLUpdate, sparqlUpdate);
        String encodedQuery = java.net.URLEncoder.encode(sparqlQuery, java.nio.charset.StandardCharsets.UTF_8);
        String actualResponse = HttpOp.httpGetString(url + query + encodedQuery);
        assertEquals(expectedResponse, actualResponse);
    }

    @Test
    void yaml_config_tdb2() {
        List<String> arguments = List.of("--conf",DIR + "/yaml/config-tdb2.yaml");
        server = construct(arguments.toArray(new String[0])).start();
        int port = server.getPort();
        String url = "http://localhost:" + port + "/ds/";
        HttpOp.httpPost(url + update, WebContent.contentTypeSPARQLUpdate, sparqlUpdate);
        String encodedQuery = java.net.URLEncoder.encode(sparqlQuery, java.nio.charset.StandardCharsets.UTF_8);
        String actualResponse = HttpOp.httpGetString(url + query + encodedQuery);
        assertEquals(expectedResponse, actualResponse);
    }

    @Test
    void yaml_config_kafka_connector() {
        String TOPIC = "RDF0";
        String STATE_DIR = "target/state";
        FileOps.ensureDir(STATE_DIR);
        FileOps.clearDirectory(STATE_DIR);

        File originalConfig = new File(DIR + "/yaml/config-connector-integration-test-1.yaml");
        File actualConfig = replacePlaceholder(originalConfig, "localhost:9092", mock.getServer());
        List<String> arguments = List.of("--conf", actualConfig.getAbsolutePath());

        server = construct(arguments.toArray(new String[0]));
        FKLib.sendFiles(producerProps(), TOPIC, List.of("src/test/files/yaml/data-no-labels.trig"));
        server.start();
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
    }

    @Test
    void yaml_config_custom_prefix() {
        List<String> arguments = List.of("--conf",DIR + "/yaml/config-prefixes-1.yaml");
        server = construct(arguments.toArray(new String[0])).start();
        RowSetRewindable actualResponseRSR;
        load(server);
        actualResponseRSR = query(server, "u1");
        boolean equals = isomorphic(expectedRSR, actualResponseRSR);
        assertTrue(equals);
    }

    @Test
    void fail_yaml_config_bad_file() {
        List<String> arguments = List.of("--conf",DIR + "/yaml/config-no-server-field.yaml");
        RuntimeException ex = assertThrows(RuntimeException.class,() -> server = construct(arguments.toArray(new String[0])).start());
        assertEquals("Failure parsing the YAML config file: java.lang.IllegalArgumentException: 'server' field is missing", ex.getMessage());
    }

    @Test
    void fail_yaml_config_missing_file() {
        List<String> arguments = List.of("--conf",DIR + "/yaml/no-config.yaml");
        RuntimeException ex = assertThrows(RuntimeException.class,() -> server = construct(arguments.toArray(new String[0])).start());
        assertEquals("Failure parsing the YAML config file: java.io.UncheckedIOException: java.io.FileNotFoundException: src/test/files/yaml/no-config.yaml (No such file or directory)", ex.getMessage());
    }

    @Test
    void yaml_config_other_args() {
        List<String> arguments = List.of("--port=0", "--metrics", "--conf",DIR + "/yaml/config-tim.yaml");
        server = construct(arguments.toArray(new String[0])).start();
        int port = server.getPort();
        String url = "http://localhost:" + port + "/ds/";
        HttpOp.httpPost(url + update, WebContent.contentTypeSPARQLUpdate, sparqlUpdate);
        String encodedQuery = java.net.URLEncoder.encode(sparqlQuery, java.nio.charset.StandardCharsets.UTF_8);
        String actualResponse = HttpOp.httpGetString(url + query + encodedQuery);
        assertEquals(expectedResponse, actualResponse);
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
        return query(url, user, queryStr);
    }

    private static RowSetRewindable query(String url, String user, String queryString) {
        RowSetRewindable rowSet =
                QueryExecHTTPBuilder.service(url)
                        .query(queryString)
                        .httpHeader("Authorization", "Bearer user:" + user)
                        .select()
                        .rewindable();
        long x = RowSetOps.count(rowSet);
        rowSet.reset();
        RowSetOps.out(System.out, rowSet);
        return rowSet;
    }

    public static File replacePlaceholder(File input, String find, String replace) {
        try {
            File output =
                    Files.createTempFile("temp", input.getName().substring(input.getName().lastIndexOf('.'))).toFile();
            String contents = IO.readWholeFileAsUTF8(input.getAbsolutePath());
            contents = StringUtils.replace(contents, find, replace);
            IO.writeStringAsUTF8(output.getAbsolutePath(), contents);
            return output;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean isomorphic(RowSet rs1, RowSet rs2) {
        Model m1 = RDFOutput.encodeAsModel(ResultSet.adapt(rs1));
        Model m2 = RDFOutput.encodeAsModel(ResultSet.adapt(rs2));
        return m1.isIsomorphicWith(m2);
    }
}
