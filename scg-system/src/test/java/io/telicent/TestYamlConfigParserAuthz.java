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
import io.telicent.jena.abac.services.SimpleAttributesStore;
import io.telicent.smart.cache.configuration.Configurator;
import org.apache.jena.atlas.io.IO;
import org.apache.jena.atlas.lib.FileOps;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.fuseki.kafka.lib.FKLib;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.system.FusekiLogging;
import org.apache.jena.graph.Graph;
import org.apache.jena.http.HttpOp;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.*;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.exec.QueryExec;
import org.apache.jena.sparql.exec.RowSet;
import org.apache.jena.sparql.exec.RowSetOps;
import org.apache.jena.sparql.exec.RowSetRewindable;
import org.apache.jena.sparql.exec.http.DSP;
import org.apache.jena.sparql.exec.http.QueryExecHTTP;
import org.apache.jena.sparql.exec.http.QueryExecHTTPBuilder;
import org.apache.jena.sparql.resultset.ResultSetCompare;
import org.apache.jena.tdb2.TDB2Factory;
import org.junit.jupiter.api.*;
import org.testcontainers.shaded.org.apache.commons.lang3.StringUtils;
import yamlconfig.ConfigConstants;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;

import static io.telicent.LibTestsSCG.*;
import static io.telicent.TestSmartCacheGraphIntegration.launchServer;
import static io.telicent.core.SmartCacheGraph.construct;
import static io.telicent.core.SmartCacheGraph.log;
import static org.junit.jupiter.api.Assertions.*;

/*
* Test SCG usage of varying configuration parameters.
*/
class TestYamlConfigParserAuthz {

    private static final String DIR = "src/test/files";
    private FusekiServer server;
    private static MockKafka mock;

    private static final String serviceName = "ds";
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
        QueryExec qExec = QueryExec.dataset(DatasetGraphFactory.create(comparisonModel.getGraph())).query(query).build();
        expectedRSR = qExec.select().rewindable();
        Resource s3 = comparisonModel.createResource(baseURI + "s");
        Property p3 = comparisonModel.createProperty(baseURI + "q");
        Literal l3 = comparisonModel.createLiteral("No label");
        comparisonModel.add(s3, p3, l3);
        QueryExec qExec2 = QueryExec.dataset(DatasetGraphFactory.create(comparisonModel.getGraph())).query(query).build();
        expectedRSRtdl = qExec2.select().rewindable();

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
        LibTestsSCG.setupAuthentication();
        LibTestsSCG.disableInitialCompaction();
        expectedRSR.reset();
    }

    @AfterEach
    void clearDown() throws Exception {
        if (null != server) {
            server.stop();
        }
        Configurator.reset();
        LibTestsSCG.teardownAuthentication();
    }

    @Test
    void yaml_config_abac_tim() {
        List<String> arguments = List.of("--conf",DIR + "/yaml/config-abac-tim.yaml");
        server = construct(arguments.toArray(new String[0])).start();
        RowSetRewindable actualResponseRSR;
        String validToken = tokenForUser("u1");
        //doesn't pass unless I upload! Even though there's a data field in the file
        // changing the path to absolute manually in the yaml file doesn't help
        //LibTestsSCG.uploadFile(server.serverURL() + serviceName + "/upload", DIR + "/yaml/data-and-labels.trig");//load(server);
        actualResponseRSR = QueryExecHTTPBuilder.service(server.serverURL() + serviceName)
                .query("SELECT * { ?s ?p ?o }")
                .httpHeader(LibTestsSCG.tokenHeader(),
                        LibTestsSCG.tokenHeaderValue(validToken))
                .select().rewindable();
        RowSetOps.out(System.out, actualResponseRSR);
        boolean equals = ResultSetCompare.isomorphic(expectedRSR, actualResponseRSR);
        assertTrue(equals);
    }

    @Test
    void yaml_config_abac_tdb2() {
        List<String> arguments = List.of("--conf",DIR + "/yaml/config-abac-tdb2.yaml");
        server = construct(arguments.toArray(new String[0])).start();
        RowSetRewindable actualResponseRSR;
        String validToken = tokenForUser("u1");
        //doesn't pass unless I upload! Even though there's a data field in the file
        LibTestsSCG.uploadFile(server.serverURL() + serviceName + "/upload", DIR + "/yaml/data-and-labels.trig");//load(server);
        //uploading this way doesn't seem to work. Could this also be the reason for the labelsStore failures?
        /*Dataset dataset = TDB2Factory.connectDataset("target/test-db");
        dataset.begin(org.apache.jena.query.ReadWrite.WRITE);
        try {
            Model databaseModel = dataset.getDefaultModel();
            RDFDataMgr.read(databaseModel, "src/test/files/yaml/data-and-labels.trig", Lang.TURTLE);
            dataset.commit();
        } catch (Exception e) {
            ConfigConstants.log.error(e.getMessage());
            dataset.abort();
        } finally {
            dataset.end();
        }*/
        actualResponseRSR = QueryExecHTTPBuilder.service(server.serverURL() + serviceName)
                .query("SELECT * { ?s ?p ?o }")
                .httpHeader(LibTestsSCG.tokenHeader(),
                        LibTestsSCG.tokenHeaderValue(validToken))
                .select().rewindable();
        RowSetOps.out(System.out, actualResponseRSR);
        boolean equals = ResultSetCompare.isomorphic(expectedRSR, actualResponseRSR);
        assertTrue(equals);
    }

    @Test
    void yaml_config_abac_labels_store() {
        List<String> arguments = List.of("--conf",DIR + "/yaml/config-abac-labels-store.yaml");
        server = construct(arguments.toArray(new String[0])).start();
        RowSetRewindable actualResponseRSR;
        String validToken = tokenForUser("u1");
        //doesn't pass unless I upload! Even though there's a data field in the file
        LibTestsSCG.uploadFile(server.serverURL() + serviceName + "/upload", DIR + "/yaml/data-no-labels.trig");//load(server);
        //uploading this way doesn't seem to work. Could this also be the reason for the labelsStore failures?
        /*Dataset dataset = TDB2Factory.connectDataset("target/test-db");
        dataset.begin(org.apache.jena.query.ReadWrite.WRITE);
        try {
            Model databaseModel = dataset.getDefaultModel();
            RDFDataMgr.read(databaseModel, "src/test/files/yaml/data-and-labels.trig", Lang.TURTLE);
            dataset.commit();
        } catch (Exception e) {
            ConfigConstants.log.error(e.getMessage());
            dataset.abort();
        } finally {
            dataset.end();
        }*/
        Dataset dataset = TDB2Factory.connectDataset("target/labels-test");
        dataset.begin(org.apache.jena.query.ReadWrite.WRITE);
        try {
            Model labelsStoreModel = dataset.getDefaultModel();
            RDFDataMgr.read(labelsStoreModel, "src/test/files/yaml/labels.ttl", Lang.TURTLE);
            dataset.commit();
        } catch (Exception e) {
            ConfigConstants.log.error(e.getMessage());
            dataset.abort();
        } finally {
            dataset.end();
        }
        actualResponseRSR = QueryExecHTTPBuilder.service(server.serverURL() + serviceName)
                .query("SELECT * { ?s ?p ?o }")
                .httpHeader(LibTestsSCG.tokenHeader(),
                        LibTestsSCG.tokenHeaderValue(validToken))
                .select().rewindable();
        RowSetOps.out(System.out, actualResponseRSR);
        boolean equals = ResultSetCompare.isomorphic(expectedRSR, actualResponseRSR);
        assertTrue(equals);
    }

    @Test
    void yaml_config_abac_attributes_store() {
        Graph g = RDFParser.source(DIR+"/yaml/attribute-store.ttl").toGraph();
        AttributesStore attrStore = Attributes.buildStore(g);
        String mockServerURL = SimpleAttributesStore.run(3132, attrStore);

        List<String> arguments = List.of("--conf",DIR + "/yaml/config-abac-remote-attributes.yaml");
        server = construct(arguments.toArray(new String[0])).start();
        String validToken = tokenForUser("u1");
        //doesn't pass unless I upload! Even though there's a data field in the file
        LibTestsSCG.uploadFile(server.serverURL() + serviceName + "/upload", DIR + "/yaml/data-and-labels.trig");//load(server);

        RowSetRewindable actualResponseRSR = QueryExecHTTPBuilder.service(server.serverURL() + serviceName)
                .query("SELECT * { ?s ?p ?o }")
                .httpHeader(LibTestsSCG.tokenHeader(),
                        LibTestsSCG.tokenHeaderValue(validToken))
                .select().rewindable();
        boolean equals = ResultSetCompare.isomorphic(expectedRSR, actualResponseRSR);
        assertTrue(equals);
    }

    @Test
    void yaml_config_abac_labels() {
        List<String> arguments = List.of("--conf",DIR + "/yaml/config-abac-labels.yaml");
        server = construct(arguments.toArray(new String[0])).start();
        RowSetRewindable actualResponseRSR;
        String validToken = tokenForUser("u1");
        //doesn't pass unless I upload! Even though there's a data field in the file
        LibTestsSCG.uploadFile(server.serverURL() + serviceName + "/upload", DIR + "/yaml/data-and-labels.trig");//load(server);
        actualResponseRSR = QueryExecHTTPBuilder.service(server.serverURL() + serviceName)
                .query("SELECT * { ?s ?p ?o }")
                .httpHeader(LibTestsSCG.tokenHeader(),
                        LibTestsSCG.tokenHeaderValue(validToken))
                .select().rewindable();
        RowSetOps.out(System.out, actualResponseRSR);
        boolean equals = ResultSetCompare.isomorphic(expectedRSR, actualResponseRSR);
        assertTrue(equals);
    }

    @Test
    void yaml_config_abac_triple_default_labels() {
        List<String> arguments = List.of("--conf",DIR + "/yaml/config-abac-tdl.yaml");
        server = construct(arguments.toArray(new String[0])).start();
        RowSetRewindable actualResponseRSR;
        String validToken = tokenForUser("u1");
        //doesn't pass unless I upload! Even though there's a data field in the file
        LibTestsSCG.uploadFile(server.serverURL() + serviceName + "/upload", DIR + "/yaml/data-no-labels.trig");//load(server);
        actualResponseRSR = QueryExecHTTPBuilder.service(server.serverURL() + serviceName)
                .query("SELECT * { ?s ?p ?o }")
                .httpHeader(LibTestsSCG.tokenHeader(),
                        LibTestsSCG.tokenHeaderValue(validToken))
                .select().rewindable();
        RowSetOps.out(System.out, actualResponseRSR);
        boolean equals = ResultSetCompare.isomorphic(expectedRSRtdl, actualResponseRSR);
        assertTrue(equals);
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
        String validToken = tokenForUser("u1");
        try {
            String URL = "http://localhost:"+server.getHttpPort()+"/ds";
            RowSet rowSet = QueryExecHTTPBuilder.service(server.serverURL() + serviceName)
                    .query("SELECT (count(*) AS ?C) {?s ?p ?o}")
                    .httpHeader(LibTestsSCG.tokenHeader(),
                            LibTestsSCG.tokenHeaderValue(validToken))
                    .select();//QueryExecHTTP.service(URL).query("SELECT (count(*) AS ?C) {?s ?p ?o}").select();
            int count = ((Number)rowSet.next().get("C").getLiteralValue()).intValue();
            Assertions.assertEquals(6, count);
            HttpOp.httpPost(URL + "/update", WebContent.contentTypeSPARQLUpdate, sparqlUpdateConnector);
            RowSet rowSet2 = QueryExecHTTPBuilder.service(server.serverURL() + serviceName)
                    .query("SELECT (count(*) AS ?C) {?s ?p ?o}")
                    .httpHeader(LibTestsSCG.tokenHeader(),
                            LibTestsSCG.tokenHeaderValue(validToken))
                    .select();//QueryExecHTTP.service(URL).query("SELECT (count(*) AS ?C) {?s ?p ?o}").select();
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
        String validToken = tokenForUser("u1");
        //doesn't pass unless I upload! Even though there's a data field in the file
        LibTestsSCG.uploadFile(server.serverURL() + serviceName + "/upload", DIR + "/yaml/data-and-labels.trig");//load(server);
        actualResponseRSR = QueryExecHTTPBuilder.service(server.serverURL() + serviceName)
                .query("SELECT * { ?s ?p ?o }")
                .httpHeader(LibTestsSCG.tokenHeader(),
                        LibTestsSCG.tokenHeaderValue(validToken))
                .select().rewindable();
        RowSetOps.out(System.out, actualResponseRSR);
        boolean equals = ResultSetCompare.isomorphic(expectedRSR, actualResponseRSR);
        assertTrue(equals);
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
}
