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
import io.telicent.jena.abac.labels.LabelsStore;
import io.telicent.jena.abac.labels.LabelsStoreRocksDB;
import io.telicent.jena.abac.labels.StoreFmtByString;
import io.telicent.jena.abac.services.SimpleAttributesStore;
import io.telicent.smart.cache.configuration.Configurator;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.system.FusekiLogging;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.*;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.exec.QueryExec;
import org.apache.jena.sparql.exec.RowSetOps;
import org.apache.jena.sparql.exec.RowSetRewindable;
import org.apache.jena.sparql.exec.http.QueryExecHTTPBuilder;
import org.apache.jena.sparql.resultset.ResultSetCompare;
import org.junit.jupiter.api.*;
import org.rocksdb.RocksDBException;


import java.io.File;
import java.util.List;

import static io.telicent.LibTestsSCG.*;
import static io.telicent.core.SmartCacheGraph.construct;
import static io.telicent.jena.abac.labels.Labels.createLabelsStoreRocksDB;
import static org.junit.jupiter.api.Assertions.*;

/*
* Test SCG usage of varying configuration parameters.
*/
class TestYamlConfigParserAuthz {

    private static final String DIR = "src/test/files";
    private FusekiServer server;

    private static final String serviceName = "ds";
    public static RowSetRewindable expectedRSR;
    public static RowSetRewindable expectedRSRtdl;
    public static String queryStr = "SELECT * { ?s ?p ?o }";

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
        Query query = QueryFactory.create(queryStr);
        QueryExec qExec = QueryExec.dataset(DatasetGraphFactory.create(comparisonModel.getGraph())).query(query).build();
        expectedRSR = qExec.select().rewindable();
        Resource s3 = comparisonModel.createResource(baseURI + "s");
        Property p3 = comparisonModel.createProperty(baseURI + "q");
        Literal l3 = comparisonModel.createLiteral("No label");
        comparisonModel.add(s3, p3, l3);
        QueryExec qExec2 = QueryExec.dataset(DatasetGraphFactory.create(comparisonModel.getGraph())).query(query).build();
        expectedRSRtdl = qExec2.select().rewindable();
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
        LibTestsSCG.uploadFile(server.serverURL() + serviceName + "/upload", DIR + "/yaml/data-and-labels.trig");//load(server);
        actualResponseRSR = QueryExecHTTPBuilder.service(server.serverURL() + serviceName)
                .query(queryStr)
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
        LibTestsSCG.uploadFile(server.serverURL() + serviceName + "/upload", DIR + "/yaml/data-and-labels.trig");//load(server);
        actualResponseRSR = QueryExecHTTPBuilder.service(server.serverURL() + serviceName)
                .query(queryStr)
                .httpHeader(LibTestsSCG.tokenHeader(),
                        LibTestsSCG.tokenHeaderValue(validToken))
                .select().rewindable();
        RowSetOps.out(System.out, actualResponseRSR);
        boolean equals = ResultSetCompare.isomorphic(expectedRSR, actualResponseRSR);
        assertTrue(equals);
    }

    @Test
    void yaml_config_abac_labels_store() throws RocksDBException {
        List<String> arguments = List.of("--conf",DIR + "/yaml/config-abac-labels-store.yaml");
        server = construct(arguments.toArray(new String[0])).start();
        RowSetRewindable actualResponseRSR;
        String validToken = tokenForUser("u1");
        LibTestsSCG.uploadFile(server.serverURL() + serviceName + "/upload", DIR + "/yaml/data-and-labels.trig");

        LabelsStore labelsStore = createLabelsStoreRocksDB(new File("target/labels-test"), LabelsStoreRocksDB.LabelMode.Merge, null, new StoreFmtByString());
        Model model = ModelFactory.createDefaultModel();
        model.read(DIR + "/yaml/data-and-labels.trig", "TRIG");
        StmtIterator iterator = model.listStatements();
        Triple triple;
        if (iterator.hasNext()) {
            iterator.next();
            triple = iterator.nextStatement().asTriple();
            assertEquals(labelsStore.labelsForTriples(triple).getFirst(), "manager");
        }
        if (iterator.hasNext()) {
            iterator.next();
            triple = iterator.nextStatement().asTriple();
            assertEquals(labelsStore.labelsForTriples(triple).getFirst(), "level-1");
        }
        iterator.close();

        actualResponseRSR = QueryExecHTTPBuilder.service(server.serverURL() + serviceName)
                .query(queryStr)
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
        LibTestsSCG.uploadFile(server.serverURL() + serviceName + "/upload", DIR + "/yaml/data-and-labels.trig");//load(server);

        RowSetRewindable actualResponseRSR = QueryExecHTTPBuilder.service(server.serverURL() + serviceName)
                .query(queryStr)
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
        LibTestsSCG.uploadFile(server.serverURL() + serviceName + "/upload", DIR + "/yaml/data-and-labels.trig");//load(server);
        actualResponseRSR = QueryExecHTTPBuilder.service(server.serverURL() + serviceName)
                .query(queryStr)
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
        LibTestsSCG.uploadFile(server.serverURL() + serviceName + "/upload", DIR + "/yaml/data-no-labels.trig");//load(server);
        actualResponseRSR = QueryExecHTTPBuilder.service(server.serverURL() + serviceName)
                .query(queryStr)
                .httpHeader(LibTestsSCG.tokenHeader(),
                        LibTestsSCG.tokenHeaderValue(validToken))
                .select().rewindable();
        RowSetOps.out(System.out, actualResponseRSR);
        boolean equals = ResultSetCompare.isomorphic(expectedRSRtdl, actualResponseRSR);
        assertTrue(equals);
    }

    @Test
    void yaml_config_custom_prefix() {
        List<String> arguments = List.of("--conf",DIR + "/yaml/config-prefixes-1.yaml");
        server = construct(arguments.toArray(new String[0])).start();
        RowSetRewindable actualResponseRSR;
        String validToken = tokenForUser("u1");
        LibTestsSCG.uploadFile(server.serverURL() + serviceName + "/upload", DIR + "/yaml/data-and-labels.trig");//load(server);
        actualResponseRSR = QueryExecHTTPBuilder.service(server.serverURL() + serviceName)
                .query(queryStr)
                .httpHeader(LibTestsSCG.tokenHeader(),
                        LibTestsSCG.tokenHeaderValue(validToken))
                .select().rewindable();
        RowSetOps.out(System.out, actualResponseRSR);
        boolean equals = ResultSetCompare.isomorphic(expectedRSR, actualResponseRSR);
        assertTrue(equals);
    }
}
