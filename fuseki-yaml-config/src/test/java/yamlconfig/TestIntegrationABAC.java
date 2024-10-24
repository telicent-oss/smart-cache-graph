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

package yamlconfig;

import io.telicent.jena.abac.core.Attributes;
import io.telicent.jena.abac.core.AttributesStore;
import io.telicent.jena.abac.core.AttributesStoreModifiable;
import io.telicent.jena.abac.fuseki.FMod_ABAC;
import io.telicent.jena.abac.services.SimpleAttributesStore;
import org.apache.jena.atlas.lib.FileOps;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiModule;
import org.apache.jena.fuseki.main.sys.FusekiModules;
import org.apache.jena.fuseki.system.FusekiLogging;
import org.apache.jena.graph.Graph;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.exec.*;
import org.apache.jena.sparql.exec.http.DSP;
import org.apache.jena.sparql.exec.http.QueryExecHTTPBuilder;
import org.apache.jena.sparql.resultset.ResultSetCompare;
import org.apache.jena.sys.JenaSystem;
import org.apache.jena.tdb2.TDB2Factory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;
import static yamlconfig.ConfigConstants.*;

/** Tests for servers with ABAC databases. */
public class TestIntegrationABAC {
    YAMLConfigParser ycp = new YAMLConfigParser();
    RDFConfigGenerator rcg = new RDFConfigGenerator();

    private static final String DIR = "src/main/files/";
    private static final String serviceName = "/ds";
    public static RowSetRewindable expectedRSR;

    @BeforeAll
    public static void before() {
        FileOps.ensureDir("target/files");
        FileOps.ensureDir("target/files/rdf");
        JenaSystem.init();

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
    }

    @AfterEach
    public void after() {
        expectedRSR.reset();
    }
    static {FusekiLogging.setLogging();}

    @Test
    public void inMemoryABACDatabaseTest() {
        ConfigStruct config = ycp.runYAMLParser("src/test/files/yaml/correct/abac/config-abac-tim.yaml");
        Model model = rcg.createRDFModel(config);
        try (FileOutputStream out = new FileOutputStream("src/test/files/rdf/abac/config-test-abac-1.ttl")) {
            model.write(out, "TTL");
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        RowSetRewindable actualResponseRSR;
        FusekiServer server = server("src/test/files/rdf/abac/config-test-abac-1.ttl");
        server.start();
        load(server);
        actualResponseRSR = query(server, "u1"); // 3
        server.stop();

        boolean equals = ResultSetCompare.isomorphic(expectedRSR, actualResponseRSR);
        assertTrue(equals);
    }

    @Test
    public void tdb2ABACDatabaseTest() {
        ConfigStruct config = ycp.runYAMLParser("src/test/files/yaml/correct/abac/config-abac-tdb2.yaml");
        Model model = rcg.createRDFModel(config);
        try (FileOutputStream out = new FileOutputStream("src/test/files/rdf/abac/config-test-abac-2.ttl")) {
            model.write(out, "TTL");
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        RowSetRewindable actualResponseRSR;
        FusekiServer server = server("src/test/files/rdf/abac/config-test-abac-2.ttl");
        server.start();
        load(server);
        actualResponseRSR = query(server, "u1"); // 3
        server.stop();

        boolean equals = ResultSetCompare.isomorphic(expectedRSR, actualResponseRSR);
        assertTrue(equals);
    }

    @Test
    public void attributesURLTest() {
        Graph g = RDFParser.source(DIR+"abac/attribute-store.ttl").toGraph();
        AttributesStore attrStore = Attributes.buildStore(g);
        String mockServerURL = SimpleAttributesStore.run(3133, attrStore);

        ConfigStruct config = ycp.runYAMLParser("src/test/files/yaml/correct/abac/config-abac-remote-attributes-2.yaml");
        Model model = rcg.createRDFModel(config);
        try (FileOutputStream out = new FileOutputStream("src/test/files/rdf/abac/config-test-abac-3.ttl")) {
            model.write(out, "TTL");
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        RowSetRewindable actualResponseRSR;
        FusekiServer server = server("src/test/files/rdf/abac/config-test-abac-3.ttl");
        server.start();
        load(server);
        actualResponseRSR = query(server, "u1"); // 3
        server.stop();

        boolean equals = ResultSetCompare.isomorphic(expectedRSR, actualResponseRSR);
        assertTrue(equals);
    }

    @Test
    public void cacheTestFalse() {
        Graph g = RDFParser.source(DIR+"abac/attribute-store.ttl").toGraph();
        AttributesStore attrStore = Attributes.buildStore(g);
        String mockServerURL = SimpleAttributesStore.run(3132, attrStore);

        ConfigStruct config = ycp.runYAMLParser("src/test/files/yaml/correct/abac/config-abac-remote-attributes.yaml");
        Model model = rcg.createRDFModel(config);
        try (FileOutputStream out = new FileOutputStream("src/test/files/rdf/abac/config-test-abac-4.ttl")) {
            model.write(out, "TTL");
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        RowSetRewindable actualResponseRSR;
        FusekiServer server = server("src/test/files/rdf/abac/config-test-abac-4.ttl");
        server.start();
        load(server);
        actualResponseRSR = query(server, "u1"); // 3

        boolean equals = ResultSetCompare.isomorphic(expectedRSR, actualResponseRSR);
        assertTrue(equals);
        expectedRSR.rewindable().reset();
        actualResponseRSR.reset();

        Graph g2 = RDFParser.source(DIR+"abac/attribute-store-modified.ttl").toGraph();
        Attributes.populateStore(g2, (AttributesStoreModifiable) attrStore);

        RowSetRewindable modifiedResponseRSR = query(server, "u1");
        server.stop();

        boolean stillEquals = ResultSetCompare.isomorphic(modifiedResponseRSR, actualResponseRSR);
        assertFalse(stillEquals);
    }

    @Test
    public void cacheTestTrue() {
        Graph g = RDFParser.source(DIR+"abac/attribute-store.ttl").toGraph();
        AttributesStore attrStore = Attributes.buildStore(g);
        String mockServerURL = SimpleAttributesStore.run(3134, attrStore);

        ConfigStruct config = ycp.runYAMLParser("src/test/files/yaml/correct/abac/config-abac-cache.yaml");
        Model model = rcg.createRDFModel(config);
        try (FileOutputStream out = new FileOutputStream("src/test/files/rdf/abac/config-test-abac-5.ttl")) {
            model.write(out, "TTL");
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        RowSetRewindable actualResponseRSR;
        FusekiServer server = server("src/test/files/rdf/abac/config-test-abac-5.ttl");
        server.start();
        load(server);
        actualResponseRSR = query(server, "u1"); // 3

        boolean equals = ResultSetCompare.isomorphic(expectedRSR, actualResponseRSR);
        assertTrue(equals);
        expectedRSR.rewindable().reset();
        actualResponseRSR.reset();

        Graph g2 = RDFParser.source(DIR+"abac/attribute-store-modified.ttl").toGraph();
        Attributes.populateStore(g2, (AttributesStoreModifiable) attrStore);

        RowSetRewindable modifiedResponseRSR = query(server, "u1");
        server.stop();

        boolean stillEquals = ResultSetCompare.isomorphic(modifiedResponseRSR, actualResponseRSR);
        assertTrue(stillEquals);
    }

    @Test
    public void prefixAuthzTest() {
        Graph g = RDFParser.source(DIR+"abac/attribute-store.ttl").toGraph();
        AttributesStore attrStore = Attributes.buildStore(g);
        String mockServerURL = SimpleAttributesStore.run(3135, attrStore);

        ConfigStruct config = ycp.runYAMLParser("src/test/files/yaml/correct/prefixes/config-prefixes-2.yaml");
        Model model = rcg.createRDFModel(config);
        try (FileOutputStream out = new FileOutputStream("src/test/files/rdf/abac/config-test-abac-prefixes.ttl")) {
            model.write(out, "TTL");
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        RowSetRewindable actualResponseRSR;
        FusekiServer server = server("src/test/files/rdf/abac/config-test-abac-prefixes.ttl");
        server.start();
        load(server);
        actualResponseRSR = query(server, "u1"); // 3
        server.stop();

        boolean equals = ResultSetCompare.isomorphic(expectedRSR, actualResponseRSR);
        assertTrue(equals);
    }

    @Test
    public void labelsTest() {
        Graph g = RDFParser.source(DIR+"abac/attribute-store.ttl").toGraph();
        AttributesStore attrStore = Attributes.buildStore(g);
        String mockServerURL = SimpleAttributesStore.run(3136, attrStore);

        ConfigStruct config = ycp.runYAMLParser("src/test/files/yaml/correct/abac/config-abac-labels-file.yaml");
        Model model = rcg.createRDFModel(config);
        try (FileOutputStream out = new FileOutputStream("src/test/files/rdf/abac/config-test-abac-labels-file.ttl")) {
            model.write(out, "TTL");
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        RowSetRewindable actualResponseRSR;
        FusekiServer server = server("src/test/files/rdf/abac/config-test-abac-labels-file.ttl");
        server.start();
        load(server);
        actualResponseRSR = query(server, "u1"); // 3
        server.stop();

        boolean equals = ResultSetCompare.isomorphic(expectedRSR, actualResponseRSR);
        assertTrue(equals);
    }

    @Test
    public void labelsStoreTest() {
        Graph g = RDFParser.source(DIR+"abac/attribute-store.ttl").toGraph();
        AttributesStore attrStore = Attributes.buildStore(g);
        String mockServerURL = SimpleAttributesStore.run(3138, attrStore);

        ConfigStruct config = ycp.runYAMLParser("src/test/files/yaml/correct/abac/config-abac-labelsstore-path.yaml");
        Model model = rcg.createRDFModel(config);
        try (FileOutputStream out = new FileOutputStream("src/test/files/rdf/abac/config-test-abac-labelsstore-path.ttl")) {
            model.write(out, "TTL");
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        RowSetRewindable actualResponseRSR;
        FusekiServer server = server("src/test/files/rdf/abac/config-test-abac-labelsstore-path.ttl");
        server.start();

        Dataset dataset = TDB2Factory.connectDataset("target/labels-store");
        dataset.begin(org.apache.jena.query.ReadWrite.WRITE);
        try {
            Model labelsStoreModel = dataset.getDefaultModel();
            RDFDataMgr.read(labelsStoreModel, "src/test/files/rdf/abac/abac/labels.ttl", Lang.TURTLE);
            dataset.commit();
        } catch (Exception e) {
            log.error(e.getMessage());
            dataset.abort();
        } finally {
            dataset.end();
        }

        load(server);
        actualResponseRSR = query(server, "u1"); // 3
        server.stop();

        boolean equals = ResultSetCompare.isomorphic(expectedRSR, actualResponseRSR);
        assertTrue(equals);
    }

    @Test
    public void tripleDefaultLabelsTest() {
        Graph g = RDFParser.source(DIR+"abac/attribute-store.ttl").toGraph();
        AttributesStore attrStore = Attributes.buildStore(g);
        String mockServerURL = SimpleAttributesStore.run(3137, attrStore);

        ConfigStruct config = ycp.runYAMLParser("src/test/files/yaml/correct/abac/config-abac-tdl.yaml");
        Model model = rcg.createRDFModel(config);
        try (FileOutputStream out = new FileOutputStream("src/test/files/rdf/abac/config-test-abac-tdl.ttl")) {
            model.write(out, "TTL");
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        RowSetRewindable actualResponseRSR;
        FusekiServer server = server("src/test/files/rdf/abac/config-test-abac-tdl.ttl");
        server.start();
        load(server);
        actualResponseRSR = query(server, "u1"); // 3
        server.stop();

        Model comparisonModel = ModelFactory.createDefaultModel();
        String baseURI = "http://example/";
        Resource s1 = comparisonModel.createResource(baseURI + "s1");
        Property p1 = comparisonModel.createProperty(baseURI + "p1");
        Literal l1 = comparisonModel.createTypedLiteral(1234, XSDDatatype.XSDinteger);
        Resource s2 = comparisonModel.createResource(baseURI + "s");
        Property p2 = comparisonModel.createProperty(baseURI + "p2");
        Literal l2 = comparisonModel.createTypedLiteral(789, XSDDatatype.XSDinteger);
        Resource s3 = comparisonModel.createResource(baseURI + "s");
        Property p3 = comparisonModel.createProperty(baseURI + "q");
        Literal l3 = comparisonModel.createLiteral("No label");
        comparisonModel.add(s1, p1, l1);
        comparisonModel.add(s2, p2, l2);
        comparisonModel.add(s3, p3, l3);

        String queryString = "SELECT ?s ?p ?o WHERE { ?s ?p ?o }";
        Query query = QueryFactory.create(queryString);
        QueryExecution qe = QueryExecutionFactory.create(query, comparisonModel);
        QueryExec qExec = QueryExec.dataset(DatasetGraphFactory.create(comparisonModel.getGraph())).query(query).build();
        RowSetRewindable expectedRSRtdl = qExec.select().rewindable();

        boolean equals = ResultSetCompare.isomorphic(expectedRSRtdl, actualResponseRSR);
        assertTrue(equals);
    }

    private static void load(FusekiServer server) {
        String URL = "http://localhost:" + server.getPort() + serviceName;
        String uploadURL = URL + "/upload";
        load(uploadURL, DIR + "abac/data-and-labels.trig");
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

    private static FusekiServer server(String config) {
        FusekiModule fmod = new FMod_ABAC();
        FusekiModules mods = FusekiModules.create(fmod);
        return FusekiServer.create().port(3131)
                .fusekiModules(mods)
                .parseConfigFile(config)
                .build();
    }
}
