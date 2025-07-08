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

import static io.telicent.LibTestsSCG.queryNoToken;
import static io.telicent.LibTestsSCG.queryWithToken;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import io.telicent.core.FKProcessorSCG;
import io.telicent.core.SmartCacheGraph;
import io.telicent.jena.abac.ABAC;
import io.telicent.jena.abac.AttributeValueSet;
import io.telicent.jena.abac.SysABAC;
import io.telicent.jena.abac.attributes.Attribute;
import io.telicent.jena.abac.attributes.AttributeValue;
import io.telicent.jena.abac.attributes.ValueTerm;
import io.telicent.jena.abac.attributes.syntax.AEX;
import io.telicent.jena.abac.core.AttributesStore;
import io.telicent.jena.abac.core.AttributesStoreLocal;
import io.telicent.jena.abac.core.AttributesStoreModifiable;
import io.telicent.jena.abac.core.DatasetGraphABAC;
import io.telicent.jena.abac.labels.Labels;
import io.telicent.jena.abac.labels.LabelsStore;
import io.telicent.smart.cache.configuration.Configurator;
import org.apache.jena.atlas.lib.Bytes;
import org.apache.jena.fuseki.kafka.FKProcessor;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.server.DataService;
import org.apache.jena.fuseki.server.Operation;
import org.apache.jena.fuseki.system.FusekiLogging;
import org.apache.jena.kafka.FusekiKafka;
import org.apache.jena.kafka.RequestFK;
import org.apache.jena.kafka.ResponseFK;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFWriter;
import org.apache.jena.riot.WebContent;
import org.apache.jena.riot.web.HttpNames;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.exec.QueryExec;
import org.apache.jena.sparql.exec.QueryExecDataset;
import org.apache.jena.sparql.exec.RowSet;
import org.apache.jena.sparql.exec.RowSetOps;
import org.junit.jupiter.api.*;

/** Test the FusekiKafka processor inside SCG. */
@TestMethodOrder(MethodOrderer.MethodName.class)
class TestFKProcessorSCG {

    static { FusekiLogging.setLogging(); }

    private static final AttributeValue attrPermit = AttributeValue.of(Attribute.create("PERMIT"), AttributeValue.dftTrue);
    private static final AttributeValue attrOther  = AttributeValue.of(Attribute.create("OTHER"),  AttributeValue.dftTrue);

    private static final String userPublic = "public";      // Registered user, no attributes
    private static final String userPermit = "userPermit";  // Registered user, attribute PERMIT=true
    private static final String userOther = "userOther";    // Registered, attribute OTHER=true

    // SPARQL query to get every thing, anywhere in the dataset (subject to ABAC)
    private static final String queryAll = "SELECT * { { ?s ?p ?o } UNION { GRAPH ?g { ?s ?p ?o } } }";

    // Fuseki service name. ABAC Dataset.
    private static final String dsName = "/ds";
    // Fuseki service name. Access to the non-ABAC storage database for test inspection.
    private static final String dsBase = "/base";

    private static DatasetGraphABAC getDatasetABAC(FusekiServer server) {
        return (DatasetGraphABAC)server.getDataAccessPointRegistry().get(dsName).getDataService().getDataset();
    }

    // The test - directly send requests to the FKProcessor and check by making HTTP requests to the Fuseki server.
    interface TestAction { public void execTest(FKProcessor proc, FusekiServer server, DatasetGraph dsgBase); }

    @BeforeAll
    static void beforeClass() throws Exception {
        LibTestsSCG.setupAuthentication();
        LibTestsSCG.disableInitialCompaction();
    }

    @AfterAll
    static void afterClass() throws Exception {
        LibTestsSCG.teardownAuthentication();
        Configurator.reset();
    }

    @Test
    void processorSCG_load_good_ttl_1() {
        TestAction action = (FKProcessor proc, FusekiServer server, DatasetGraph dsgBase) -> {
            String URL = server.datasetURL(dsName);
            checkDatasetSize(dsgBase, 0);
            processorRequest(proc, """
                    PREFIX : <http://example/>
                    :s :p "turtle" .
                    """, WebContent.contentTypeTurtle, attrPermit);
            // Check base has changed.
            checkDatasetSize(dsgBase, 1);
            // Check visibility
            long c1 = count(URL, queryAll, userPermit);
            assertEquals(1L, c1, "Count (user:permit)");
            long c2 = count(URL, queryAll, userOther);
            assertEquals(0L, c2, "Count (user:other)");
        };
        runTestProcessorSCGWithAuth(action);
    }

    @Test
    void processorSCG_load_good_ttl_2() {
        TestAction action = (FKProcessor proc, FusekiServer server, DatasetGraph dsgBase) -> {
            String URL = server.datasetURL(dsName);
            checkDatasetSize(dsgBase, 0);
            processorRequest(proc, """
                    PREFIX : <http://example/>
                    :s :p "turtle" .
                    """, WebContent.contentTypeTurtle, attrPermit);

            checkDatasetSize(dsgBase, 1);
            long c1 = count(URL, queryAll, userPermit);
            assertEquals(1L, c1, "Count (user:permit)");
            long c2 = count(URL, queryAll, userOther);
            assertEquals(0L, c2, "Count (user:other)");
        };
        runTestProcessorSCGWithAuth(action);
    }

    @Test
    void processorSCG_load_good_ttl_3() {
        TestAction action = (FKProcessor proc, FusekiServer server, DatasetGraph dsgBase) -> {
            String URL = server.datasetURL(dsName);
            checkDatasetSize(dsgBase, 0);
            processorRequest(proc, """
                    PREFIX : <http://example/>
                    [] :p "turtle" .
                    """, WebContent.contentTypeTurtle, attrPermit);
            // Check base has changed.
            checkDatasetSize(dsgBase, 1);
            // Check visibility
            long c1 = count(URL, queryAll, userPermit);
            assertEquals(1L, c1, "Count (user:permit)");
            long c2 = count(URL, queryAll, userOther);
            assertEquals(0L, c2, "Count (user:other)");
        };
        runTestProcessorSCGWithAuth(action);
    }

    @Test
    void processorSCG_load_good_ttl_4() {
        TestAction action = (FKProcessor proc, FusekiServer server, DatasetGraph dsgBase) -> {
            String URL = server.datasetURL(dsName);
            checkDatasetSize(dsgBase, 0);
            processorRequest(proc, """
                    PREFIX : <http://example/>
                    _:B4fdc6f181b76ac466cb362d495282137 :p "turtle" .
                    """, WebContent.contentTypeTurtle, attrPermit);
            // Check base has changed.
            checkDatasetSize(dsgBase, 1);
            // Check visibility
            long c1 = count(URL, queryAll, userPermit);
            assertEquals(1L, c1, "Count (user:permit)");
            long c2 = count(URL, queryAll, userOther);
            assertEquals(0L, c2, "Count (user:other)");
        };
        runTestProcessorSCGWithAuth(action);
    }

    @Test
    void processorSCG_load_good_nq_1() {
        TestAction action = (FKProcessor proc, FusekiServer server, DatasetGraph dsgBase) -> {
            String URL = server.datasetURL(dsName);
            checkDatasetSize(dsgBase, 0);
            processorRequest(proc, """
                    <http://example/s> <http://example/p> "turtle" .
                    """, WebContent.contentTypeNQuads, attrPermit);
            // Check base has changed.
            checkDatasetSize(dsgBase, 1);
            // Check visibility
            long c1 = count(URL, queryAll, userPermit);
            assertEquals(1L, c1, "Count (user:permit)");
            long c2 = count(URL, queryAll, userOther);
            assertEquals(0L, c2, "Count (user:other)");
        };
        runTestProcessorSCGWithAuth(action);
    }

    @Test
    void processorSCG_load_good_nq_2() {
        TestAction action = (FKProcessor proc, FusekiServer server, DatasetGraph dsgBase) -> {
            String URL = server.datasetURL(dsName);
            checkDatasetSize(dsgBase, 0);
            processorRequest(proc, """
                    _:B4fdc6f181b76ac466cb362d495282137 <http://example/p> "turtle" .
                    """, WebContent.contentTypeNQuads, attrPermit);

            checkDatasetSize(dsgBase, 1);
            long c1 = count(URL, queryAll, userPermit);
            assertEquals(1L, c1, "Count (user:permit)");
            long c2 = count(URL, queryAll, userOther);
            assertEquals(0L, c2, "Count (user:other)");
        };
        runTestProcessorSCGWithAuth(action);
    }

    @Test
    void processorSCG_load_bad_1() {
        TestAction action = (FKProcessor proc, FusekiServer server, DatasetGraph dsgBase) -> {
            String URL = server.datasetURL(dsName);
            LibTestsSCG.withLevel(FusekiKafka.LOG, "FATAL", ()->{
                checkDatasetSize(dsgBase, 0);
                processorRequest(proc, """
                        JUNK
                        """, WebContent.contentTypeTurtle, attrPermit);
            });
            // Should be no data loaded.
            checkDatasetSize(dsgBase, 0);

            long c1 = count(URL, queryAll, userPermit);
            assertEquals(0L, c1, "Count (user:permit)");

            // No data, no labels.
            DatasetGraphABAC dsgz = getDatasetABAC(server);
            assertTrue(dsgz.getBase().getDefaultGraph().isEmpty());
            assertTrue(dsgz.labelsStore().isEmpty());
        };
        runTestProcessorSCGWithAuth(action);
    }

    @Test
    void processorSCG_patch_1_add2() {
        TestAction action = (FKProcessor proc, FusekiServer server, DatasetGraph dsgBase) -> {
            String URL = server.datasetURL(dsName);
            checkDatasetSize(dsgBase, 0);
            processorRequest(proc, """
                    TX .
                    PA "ex" <http://ex/> .
                    A <http://ex/s1> <http://ex/p> "triple1" .
                    A <http://ex/s2> <http://ex/p> "triple2" .
                    TC .
                    """, WebContent.contentTypePatch, attrPermit);
            checkDatasetSize(dsgBase, 2);
            long c1 = count(URL, queryAll, userPermit);
            assertEquals(2L, c1);
            long c2 = count(URL, queryAll, userOther);
            assertEquals(0L, c2);
            checkDatasetSize(dsgBase, 2);
        };
        runTestProcessorSCGWithAuth(action);
    }

    @Test
    void processorSCG_patch_2_add1_add1() {
        TestAction action = (FKProcessor proc, FusekiServer server, DatasetGraph dsgBase) -> {
            String URL = server.datasetURL(dsName);
            checkDatasetSize(dsgBase, 0);
            processorRequest(proc, """
                    TX .
                    PA "ex" <http://ex/> .
                    A <http://ex/s1> <http://ex/p> "triple1" .
                    TC .
                    """, WebContent.contentTypePatch, attrPermit);
            checkDatasetSize(dsgBase, 1);

            processorRequest(proc, """
                             A <http://ex/s2> <http://ex/p> "triple2" .
                             """, WebContent.contentTypePatch, attrPermit);
            checkDatasetSize(dsgBase, 2);

            long c1 = count(URL, queryAll, userPermit);
            assertEquals(2L, c1);
            long c2 = count(URL, queryAll, userOther);
            assertEquals(0L, c2);
            checkDatasetSize(dsgBase, 2);
        };
        runTestProcessorSCGWithAuth(action);
    }

    @Test
    void processorSCG_patch_3_add2_delete1() {
        TestAction action = (FKProcessor proc, FusekiServer server, DatasetGraph dsgBase) -> {
            String URL = server.datasetURL(dsName);
            checkDatasetSize(dsgBase, 0);
            processorRequest(proc, """
                             TX .
                             PA "ex" <http://ex/> .
                             A <http://ex/s1> <http://ex/p> "triple1" .
                             A <http://ex/s2> <http://ex/p> "triple2" .
                             TC .
                             """, WebContent.contentTypePatch, attrPermit);
            checkDatasetSize(dsgBase, 2);
            processorRequest(proc, """
                             TX .
                             D <http://ex/s2> <http://ex/p> "triple2" .
                             TX .
                             """, WebContent.contentTypePatch, attrPermit);
            checkDatasetSize(dsgBase, 1);
            long c = count(URL, queryAll, userPermit);
            assertEquals(1L, c);
            checkDatasetSize(dsgBase, 1);
        };
        runTestProcessorSCGWithAuth(action);
    }

    /** No label - dataset default applies which in this setup is "deny" */
    @Test
    void processorSCG_patch_4_add_no_label() {
        TestAction action = (FKProcessor proc, FusekiServer server, DatasetGraph dsgBase) -> {
            String URLauthz = server.datasetURL(dsName);
            checkDatasetSize(dsgBase, 0);
            processorRequest(proc, """
                             A <http://ex/s2> <http://ex/p> "triple2" .
                             """, WebContent.contentTypePatch, AttributeValue.of("", ValueTerm.value(null)));
            checkDatasetSize(dsgBase, 1);

            long c1 = count(URLauthz, queryAll, userPermit);
            // Default is deny.
            assertEquals(0L, c1);
            // The storage should have the triple.
            checkDatasetSize(dsgBase, 1);
        };
        runTestProcessorSCGWithAuth(action);
    }

    /** Add quad. Ignored. */
    @Test
    void processorSCG_patch_5_quad() {
        TestAction action = (FKProcessor proc, FusekiServer server, DatasetGraph dsgBase) -> {
            String URLauthz = server.datasetURL(dsName);
            checkDatasetSize(dsgBase, 0);

            // Warning - not an error.
            LibTestsSCG.withLevel(FusekiKafka.LOG, "ERROR", ()-> processorRequest(proc, """
                    A <http://ex/s2> <http://ex/p> "triple2" <http://ex/namedGraph> .
                    """, WebContent.contentTypePatch, attrPermit));
            checkDatasetSize(dsgBase, 1);
            long c1 = count(URLauthz, queryAll, userPermit);
            assertEquals(0L, c1);

            // Check quads count.
            checkDatasetSize(dsgBase, 1);
        };
        runTestProcessorSCGWithAuth(action);
    }

    /** Bad patch syntax. */
    @Test
    void processorSCG_patch_6_bad_patch() {
        TestAction action = (FKProcessor proc, FusekiServer server, DatasetGraph dsgBase) -> {
            String URLauthz = server.datasetURL(dsName);
            checkDatasetSize(dsgBase, 0);
            LibTestsSCG.withLevel(FusekiKafka.LOG, "FATAL", ()-> processorRequest(proc, "A JUNK .", WebContent.contentTypePatch, null));
            checkDatasetSize(dsgBase, 0);

            long c1 = count(URLauthz, queryAll, userPermit);
            assertEquals(0L, c1);
            checkDatasetSize(dsgBase, 0);
        };
        runTestProcessorSCGWithAuth(action);
    }

    @Test
    void processorSCG_load_good_notInBatch() {
        TestAction action = (FKProcessor proc, FusekiServer server, DatasetGraph dsgBase) -> {
            String URL = server.datasetURL(dsName);
            checkDatasetSize(dsgBase, 0);
            String messageBody = """
                    PREFIX : <http://example/>
                    :s :p "turtle" .
                    """;
            Map<String, String> headers = Map.of(HttpNames.hContentType, WebContent.contentTypeTurtle, SysABAC.hSecurityLabel, attrOther.asString());
            byte[] bytes = Bytes.asUTF8bytes(messageBody);
            RequestFK request = new RequestFK("knowledge", headers, bytes);
            LibTestsSCG.withLevel(FusekiKafka.LOG, "FATAL", ()->{
                ResponseFK response = proc.process(request);
                assertNotNull(response);
            });
            // It happened even though not batched.
            checkDatasetSize(dsgBase, 1);
            long c1 = count(URL, queryAll, userPermit);
            assertEquals(0L, c1, "Count (user:permit)");
            long c2 = count(URL, queryAll, userOther);
            assertEquals(1L, c2, "Count (user:other)");
            checkDatasetSize(dsgBase, 1);
        };
        runTestProcessorSCGWithAuth(action);
    }

    @Test
    void processorSCG_load_good_differentDSG_noSecurityApplied() {
        TestAction action = (FKProcessor proc, FusekiServer server, DatasetGraph dsgBase) -> {
            String URL = server.datasetURL(dsName);
            checkDatasetSize(dsgBase, 0);
            String messageBody = """
                    PREFIX : <http://example/>
                    :s :p "turtle" .
                    """;
            Map<String, String> headers = Map.of(HttpNames.hContentType, WebContent.contentTypeTurtle);
            byte[] bytes = Bytes.asUTF8bytes(messageBody);
            RequestFK request = new RequestFK("knowledge", headers, bytes);

            LibTestsSCG.withLevel(FusekiKafka.LOG, "FATAL", ()->{
                ResponseFK response = proc.process(request);
                assertNotNull(response);
            });
            // Test was on a different, non-auth dataset
            checkDatasetSize(dsgBase, 0);

            long c1 = count(URL, queryAll, userPermit);
            assertEquals(1L, c1, "Count (user:permit)");
            long c2 = count(URL, queryAll, userOther);
            assertEquals(1L, c2, "Count (user:other)");
            // Look at the alternative dataset used in the test.
            checkDatasetSize(server.getDataAccessPointRegistry().get(dsName).getDataService().getDataset(), 1);
        };
        runTestProcessorSCGWithGivenDSG(action, DatasetGraphFactory.create());
    }

    @Test
    void processorSCG_load_good_patch_differentDSG_noLabel() {
        TestAction action = (FKProcessor proc, FusekiServer server, DatasetGraph dsgBase) -> {
            String URL = server.datasetURL(dsName);
            checkDatasetSize(dsgBase, 0);
            processorRequest(proc, """
                    TX .
                    PA "ex" <http://ex/> .
                    A <http://ex/s1> <http://ex/p> "triple1" .
                    A <http://ex/s2> <http://ex/p> "triple2" .
                    TC .
                    """, WebContent.contentTypePatch, null);
            // Test was on a different dataset
            checkDatasetSize(dsgBase, 0);

            long c1 = count(URL, queryAll, userPermit);
            assertEquals(2L, c1);
            long c2 = count(URL, queryAll, userOther);
            assertEquals(2L, c2);
            checkDatasetSize(server.getDataAccessPointRegistry().get(dsName).getDataService().getDataset(), 2);
        };
        runTestProcessorSCGWithGivenDSG(action, DatasetGraphFactory.createTxnMem());
    }

    @Test
    void processorSCG_load_bad_securityNotSupported() {
        TestAction action = (FKProcessor proc, FusekiServer server, DatasetGraph dsgBase) -> {
            String URL = server.datasetURL(dsName);
            checkDatasetSize(dsgBase, 0);
            String messageBody = """
                    PREFIX : <http://example/>
                    :s :p "turtle" .
                    """;
            Map<String, String> headers = Map.of(HttpNames.hContentType, WebContent.contentTypeTurtle, SysABAC.hSecurityLabel, attrOther.asString());
            byte[] bytes = Bytes.asUTF8bytes(messageBody);
            RequestFK request = new RequestFK("knowledge", headers, bytes);

            LibTestsSCG.withLevel(FusekiKafka.LOG, "FATAL", ()->{
                ResponseFK response = proc.process(request);
                assertNotNull(response);
            });
            // Load didn't happen.
            checkDatasetSize(dsgBase, 0);

            long c1 = count(URL, queryAll, userPermit);
            assertEquals(0L, c1, "Count (user:permit)");
            long c2 = count(URL, queryAll, userOther);
            assertEquals(0L, c2, "Count (user:other)");

            // Check no change
            checkDatasetSize(dsgBase, 0);
        };

        // Instead of auth setup, use this.
        runTestProcessorSCGWithGivenDSG(action, DatasetGraphFactory.createTxnMem());
    }

    @Test
    void processorSCG_load_patch_differentDSG_securityLabelNotHandled() {
        TestAction action = (FKProcessor proc, FusekiServer server, DatasetGraph dsgBase) -> {
            String URL = server.datasetURL(dsName);
            String URLbase = server.datasetURL(dsBase);
            checkDatasetSize(dsgBase, 0);

            LibTestsSCG.withLevel(FusekiKafka.LOG, "FATAL", ()-> processorRequest(proc, """
                    TX .
                    PA "ex" <http://ex/> .
                    A <http://ex/s1> <http://ex/p> "triple1" .
                    A <http://ex/s2> <http://ex/p> "triple2" .
                    TC .
                    """, WebContent.contentTypePatch, attrPermit));

            long c1 = count(URL, queryAll, userPermit);
            assertEquals(0L, c1);
            long c2 = count(URL, queryAll, userOther);
            assertEquals(0L, c2);

            checkDatasetSize(server.getDataAccessPointRegistry().get(dsName).getDataService().getDataset(), 0);
        };

        runTestProcessorSCGWithGivenDSG(action, DatasetGraphFactory.createTxnMem());
    }

    private long count(String URL, String queryString, String user) {
        RowSet rowSet =
                (user == null )
                ? queryNoToken(URL, queryString)
                : queryWithToken(URL, queryString, user);
        long c = RowSetOps.count(rowSet);
        return c;
    }

    private void checkDatasetSize(DatasetGraph dsg, int expectedCount) {
        try ( QueryExec qExec = QueryExecDataset.newBuilder().dataset(dsg).query(queryAll).build() ) {
            RowSet rowSet = qExec.select();
            long c = RowSetOps.count(rowSet);
            assertEquals(expectedCount, c, "Dataset size");
        }
    }

    private void checkSizeNoAuth(FusekiServer server, String serviceName, long expectedCount) {
        String URL = server.datasetURL(serviceName);
        // No auth call.
        long c = count(URL, queryAll, null);
        // The storage should have the triple.
        assertEquals(expectedCount, c);
    }

    private void processorRequest(FKProcessor proc, String body, String contentType, AttributeValue securityLabel) {
        Map<String, String> headers = (securityLabel == null)
                                      ? Map.of(HttpNames.hContentType, contentType)
                                      : Map.of(HttpNames.hContentType, contentType, SysABAC.hSecurityLabel, securityLabel.asString());
        processorRequest(proc, body, headers);
    }

    private void processorRequest(FKProcessor proc, String body, Map<String, String> headers) {
        byte[] bytes = Bytes.asUTF8bytes(body);
        RequestFK request = new RequestFK("knowledge", headers, bytes);
        long startOffset = 900;
        int batchSize = 1;
        long finishOffset = 900 + batchSize;
        proc.startBatch(1, startOffset);
        ResponseFK response = proc.process(request);
        proc.finishBatch(batchSize, startOffset,  finishOffset);
        assertNotNull(response);
    }

    private void runTestProcessorSCGWithAuth(TestAction execTestAction) {
        // Setup a DatasetGraphABAC in a Fuseki/SCG with a FKProcessor.
        DatasetGraph dsgBase = DatasetGraphFactory.createTxnMem();
        LabelsStore labelsStore = Labels.createLabelsStoreMem();
        AttributesStoreModifiable attributesStore = new AttributesStoreLocal();
        // Register - no attributes.
        attributesStore.put(userPublic, AttributeValueSet.of());
        attributesStore.put(userPermit, AttributeValueSet.of("PERMIT"));
        attributesStore.put(userOther,  AttributeValueSet.of("OTHER"));

        DatasetGraphABAC dsgz = ABAC.authzDataset(dsgBase,
                AEX.strALLOW,   // API access label
                labelsStore,
                SysABAC.denyLabel,    // Dataset data label default.
                attributesStore);
        DataService dataSrv = DataService
                .newBuilder(dsgz)
                .addEndpoint(Operation.Query)
                .build();
        runTestProcessorWithGivenDSGAndService(execTestAction, dsgBase, dsgz, dataSrv);
    }

    private void runTestProcessorSCGWithGivenDSG(TestAction execTestAction, DatasetGraph dsg) {
        DatasetGraph dsgBase = DatasetGraphFactory.createTxnMem();
        DataService dataSrv = DataService.newBuilder(dsg)
                                         .addEndpoint(Operation.Query)
                                         .build();
        runTestProcessorWithGivenDSGAndService(execTestAction, dsgBase, dsg, dataSrv);
    }

    private void runTestProcessorWithGivenDSGAndService(TestAction execTestAction, DatasetGraph dsgBase, DatasetGraph dsg, DataService dataSrv) {
        FusekiServer server = SmartCacheGraph.serverBuilder().port(0)
                                             .add(dsName, dataSrv)
                                             .add(dsBase, dsgBase)
                                             .build();
        server.start();
        try {
            FKProcessor proc = new FKProcessorSCG(dsg, "http://base/request/", server);
            execTestAction.execTest(proc, server, dsgBase);
        } finally { server.stop(); }
    }

    private static void dumpState(LabelsStore labelsStore, AttributesStore attributesStore) {
        dumpLabelStore(labelsStore);
        dumpAttributesStore(attributesStore);
        System.out.println();
    }

    private static void dumpLabelStore(LabelsStore labelsStore) {
        if ( labelsStore != null ) {
            System.out.println("-- Labels");
            RDFWriter.source(labelsStore.asGraph()).lang(Lang.TTL).output(System.out);
        } else
            System.out.println("-- Labels -- null");
        System.out.println();
    }

    private static void dumpAttributesStore(AttributesStore attributesStore) {
        if ( attributesStore != null ) {
            System.out.println("-- User Attributes");
            attributesStore.users().forEach(u -> {
                AttributeValueSet avs = attributesStore.attributes(u);
                System.out.printf("%s %s\n", u, avs);
            });
        } else {
            System.out.println("-- User Attributes -- null");
        }
    }
}
