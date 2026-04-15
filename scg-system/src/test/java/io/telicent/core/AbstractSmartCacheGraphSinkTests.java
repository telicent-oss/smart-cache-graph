package io.telicent.core;

import io.telicent.LibTestsSCG;
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
import io.telicent.jena.abac.labels.LabelsStore;
import io.telicent.smart.cache.configuration.Configurator;
import io.telicent.smart.cache.payloads.RdfPayload;
import io.telicent.smart.cache.projectors.Sink;
import io.telicent.smart.cache.sources.Event;
import io.telicent.smart.cache.sources.EventHeader;
import io.telicent.smart.cache.sources.Header;
import io.telicent.smart.cache.sources.TelicentHeaders;
import io.telicent.smart.cache.sources.memory.SimpleEvent;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.server.DataService;
import org.apache.jena.fuseki.server.Operation;
import org.apache.jena.fuseki.system.FusekiLogging;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.kafka.FusekiKafka;
import org.apache.jena.kafka.JenaKafkaException;
import org.apache.jena.kafka.common.FusekiSink;
import org.apache.jena.query.TxnType;
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
import org.apache.jena.system.Txn;
import org.apache.kafka.common.utils.Bytes;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertFalse;
import static io.telicent.LibTestsSCG.queryNoToken;
import static io.telicent.LibTestsSCG.queryWithToken;
import static org.junit.jupiter.api.Assertions.*;

public abstract class AbstractSmartCacheGraphSinkTests {
    private static final AttributeValue attrPermit =
            AttributeValue.of(Attribute.create("PERMIT"), AttributeValue.dftTrue);
    private static final AttributeValue attrOther =
            AttributeValue.of(Attribute.create("OTHER"), AttributeValue.dftTrue);
    private static final AttributeValue attrNotPermitted =
            AttributeValue.of(Attribute.create("PERMIT"), ValueTerm.FALSE);
    private static final String userPublic = "public";      // Registered user, no attributes
    private static final String userPermit = "userPermit";  // Registered user, attribute PERMIT=true
    private static final String userOther = "userOther";    // Registered, attribute OTHER=true
    // SPARQL query to get every thing, anywhere in the dataset (subject to ABAC)
    private static final String queryAll = "SELECT * { { ?s ?p ?o } UNION { GRAPH ?g { ?s ?p ?o } } }";
    private static final String queryDefault = "SELECT * { ?s ?p ?o }";
    private static final String queryUnion = "SELECT * { GRAPH <urn:x-arq:UnionGraph> { ?s ?p ?o } }";
    // Fuseki service name. ABAC Dataset.
    private static final String dsName = "/ds";
    // Fuseki service name. Access to the non-ABAC storage database for test inspection.
    private static final String dsBase = "/base";

    static {
        FusekiLogging.setLogging();
    }

    private static DatasetGraphABAC getDatasetABAC(FusekiServer server) {
        return (DatasetGraphABAC) server.getDataAccessPointRegistry().get(dsName).getDataService().getDataset();
    }

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

    public static List<EventHeader> toHeaders(Map<String, String> headers) {
        return headers.entrySet()
                      .stream()
                      .map(e -> new Header(e.getKey(), e.getValue()))
                      .map(h -> (EventHeader) h)
                      .toList();
    }

    private static void dumpState(LabelsStore labelsStore, AttributesStore attributesStore) {
        AbstractSmartCacheGraphSinkTests.dumpLabelStore(labelsStore);
        AbstractSmartCacheGraphSinkTests.dumpAttributesStore(attributesStore);
        System.out.println();
    }

    private static void dumpLabelStore(LabelsStore labelsStore) {
        if (labelsStore != null) {
            System.out.println("-- Labels");
            RDFWriter.source(labelsStore.asGraph()).lang(Lang.TTL).output(System.out);
        } else {
            System.out.println("-- Labels -- null");
        }
        System.out.println();
    }

    private static void dumpAttributesStore(AttributesStore attributesStore) {
        if (attributesStore != null) {
            System.out.println("-- User Attributes");
            attributesStore.users().forEach(u -> {
                AttributeValueSet avs = attributesStore.attributes(u);
                System.out.printf("%s %s\n", u, avs);
            });
        } else {
            System.out.println("-- User Attributes -- null");
        }
    }

    @Test
    final void processorSCG_load_good_ttl_1() {
        TestAction action =
                (Sink<Event<Bytes, RdfPayload>> proc, FusekiServer server, DatasetGraph dsgBase, DatasetGraph dsg) -> {
                    String URL = server.datasetURL(dsName);
                    checkDatasetSize(dsgBase, 0);
                    sendEvent(dsg, proc, """
                            PREFIX : <http://example/>
                            :s :p "turtle" .
                            """, WebContent.contentTypeTurtle, attrPermit);
                    // Check base has changed.
                    checkDatasetSize(dsgBase, 1);
                    // Check visibility
                    verifyCounts(URL, queryAll, 1L, 0L);
                };
        runTestProcessorSCGWithAuth(action);
    }

    @Test
    final void processorSCG_load_good_ttl_2() {
        TestAction action =
                (Sink<Event<Bytes, RdfPayload>> proc, FusekiServer server, DatasetGraph dsgBase, DatasetGraph dsg) -> {
                    String URL = server.datasetURL(dsName);
                    checkDatasetSize(dsgBase, 0);
                    sendEvent(dsg, proc, """
                            PREFIX : <http://example/>
                            :s :p "turtle" .
                            """, WebContent.contentTypeTurtle, attrPermit);

                    checkDatasetSize(dsgBase, 1);
                    verifyCounts(URL, queryAll, 1L, 0L);
                };
        runTestProcessorSCGWithAuth(action);
    }

    @Test
    final void processorSCG_load_good_ttl_3() {
        TestAction action =
                (Sink<Event<Bytes, RdfPayload>> proc, FusekiServer server, DatasetGraph dsgBase, DatasetGraph dsg) -> {
                    String URL = server.datasetURL(dsName);
                    checkDatasetSize(dsgBase, 0);
                    sendEvent(dsg, proc, """
                            PREFIX : <http://example/>
                            [] :p "turtle" .
                            """, WebContent.contentTypeTurtle, attrPermit);
                    // Check base has changed.
                    checkDatasetSize(dsgBase, 1);
                    // Check visibility
                    verifyCounts(URL, queryAll, 1L, 0L);
                };
        runTestProcessorSCGWithAuth(action);
    }

    @Test
    final void processorSCG_load_good_ttl_4() {
        TestAction action =
                (Sink<Event<Bytes, RdfPayload>> proc, FusekiServer server, DatasetGraph dsgBase, DatasetGraph dsg) -> {
                    String URL = server.datasetURL(dsName);
                    checkDatasetSize(dsgBase, 0);
                    sendEvent(dsg, proc, """
                            PREFIX : <http://example/>
                            _:B4fdc6f181b76ac466cb362d495282137 :p "turtle" .
                            """, WebContent.contentTypeTurtle, attrPermit);
                    // Check base has changed.
                    checkDatasetSize(dsgBase, 1);
                    // Check visibility
                    verifyCounts(URL, queryAll, 1L, 0L);
                };
        runTestProcessorSCGWithAuth(action);
    }

    @Test
    final void processorSCG_load_good_nq_1() {
        TestAction action =
                (Sink<Event<Bytes, RdfPayload>> proc, FusekiServer server, DatasetGraph dsgBase, DatasetGraph dsg) -> {
                    String URL = server.datasetURL(dsName);
                    checkDatasetSize(dsgBase, 0);
                    sendEvent(dsg, proc, """
                            <http://example/s> <http://example/p> "turtle" .
                            """, WebContent.contentTypeNQuads, attrPermit);
                    // Check base has changed.
                    checkDatasetSize(dsgBase, 1);
                    // Check visibility
                    verifyCounts(URL, queryAll, 1L, 0L);
                };
        runTestProcessorSCGWithAuth(action);
    }

    @Test
    final void processorSCG_load_good_nq_2() {
        TestAction action =
                (Sink<Event<Bytes, RdfPayload>> proc, FusekiServer server, DatasetGraph dsgBase, DatasetGraph dsg) -> {
                    String URL = server.datasetURL(dsName);
                    checkDatasetSize(dsgBase, 0);
                    sendEvent(dsg, proc, """
                            _:B4fdc6f181b76ac466cb362d495282137 <http://example/p> "turtle" .
                            """, WebContent.contentTypeNQuads, attrPermit);

                    checkDatasetSize(dsgBase, 1);
                    verifyCounts(URL, queryAll, 1L, 0L);
                };
        runTestProcessorSCGWithAuth(action);
    }

    @Test
    final void processorSCG_load_bad_1() {
        TestAction action =
                (Sink<Event<Bytes, RdfPayload>> proc, FusekiServer server, DatasetGraph dsgBase, DatasetGraph dsg) -> {
                    String URL = server.datasetURL(dsName);
                    LibTestsSCG.withLevel(FusekiKafka.LOG, "FATAL", () -> {
                        checkDatasetSize(dsgBase, 0);
                        dsg.begin(TxnType.WRITE);
                        sendEvent(dsg, proc, """
                                JUNK
                                """, WebContent.contentTypeTurtle, attrPermit);
                        dsg.commit();
                    });
                    // Should be no data loaded.
                    checkDatasetSize(dsgBase, 0);

                    long c1 = count(URL, queryAll, userPermit);
                    assertEquals(0L, c1, "Count (user:permit)");

                    // No data, no labels.
                    DatasetGraphABAC dsgz = AbstractSmartCacheGraphSinkTests.getDatasetABAC(server);
                    Txn.executeRead(dsgz, () -> {
                        assertTrue(dsgz.getBase().getDefaultGraph().isEmpty());
                        assertTrue(dsgz.labelsStore().isEmpty());
                    });
                };
        runTestProcessorSCGWithAuth(action);
    }

    @Test
    final void processorSCG_patch_1_add2() {
        TestAction action =
                (Sink<Event<Bytes, RdfPayload>> proc, FusekiServer server, DatasetGraph dsgBase, DatasetGraph dsg) -> {
                    String URL = server.datasetURL(dsName);
                    checkDatasetSize(dsgBase, 0);
                    sendEvent(dsg, proc, """
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
    final void processorSCG_patch_2_add1_add1() {
        TestAction action =
                (Sink<Event<Bytes, RdfPayload>> proc, FusekiServer server, DatasetGraph dsgBase, DatasetGraph dsg) -> {
                    String URL = server.datasetURL(dsName);
                    checkDatasetSize(dsgBase, 0);
                    sendEvent(dsg, proc, """
                            TX .
                            PA "ex" <http://ex/> .
                            A <http://ex/s1> <http://ex/p> "triple1" .
                            TC .
                            """, WebContent.contentTypePatch, attrPermit);
                    checkDatasetSize(dsgBase, 1);

                    sendEvent(dsg, proc, """
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
    final void processorSCG_patch_3_add2_delete1() {
        TestAction action =
                (Sink<Event<Bytes, RdfPayload>> proc, FusekiServer server, DatasetGraph dsgBase, DatasetGraph dsg) -> {
                    String URL = server.datasetURL(dsName);
                    checkDatasetSize(dsgBase, 0);
                    sendEvent(dsg, proc, """
                            TX .
                            PA "ex" <http://ex/> .
                            A <http://ex/s1> <http://ex/p> "triple1" .
                            A <http://ex/s2> <http://ex/p> "triple2" .
                            TC .
                            """, WebContent.contentTypePatch, attrPermit);
                    checkDatasetSize(dsgBase, 2);
                    sendEvent(dsg, proc, """
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

    /**
     * No label - dataset default applies which in this setup is "deny"
     */
    @Test
    final void processorSCG_patch_4_add_no_label() {
        TestAction action =
                (Sink<Event<Bytes, RdfPayload>> proc, FusekiServer server, DatasetGraph dsgBase, DatasetGraph dsg) -> {
                    String URLauthz = server.datasetURL(dsName);
                    checkDatasetSize(dsgBase, 0);
                    sendEvent(dsg, proc, """
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

    /**
     * Add quad. Ignored.
     */
    @Test
    final void processorSCG_patch_5_quad() {
        TestAction action =
                (Sink<Event<Bytes, RdfPayload>> proc, FusekiServer server, DatasetGraph dsgBase, DatasetGraph dsg) -> {
                    String URLauthz = server.datasetURL(dsName);
                    checkDatasetSize(dsgBase, 0);

                    LibTestsSCG.withLevel(FusekiKafka.LOG, "ERROR", () -> {
                        sendEvent(dsg, proc, """
                                A <http://ex/s2> <http://ex/p> "triple2" <http://ex/namedGraph> .
                                """, WebContent.contentTypePatch, attrPermit);
                    });
                    checkDatasetSize(dsgBase, 1);
                    if (supportsLabellingQuads()) {
                        long c1 = count(URLauthz, queryAll, userPermit);
                        assertEquals(1L, c1);
                    }

                    // Check quads count.
                    checkDatasetSize(dsgBase, 1);
                };
        runTestProcessorSCGWithAuth(action);
    }

    /**
     * Bad patch syntax.
     */
    @Test
    final void processorSCG_patch_6_bad_patch() {
        TestAction action =
                (Sink<Event<Bytes, RdfPayload>> proc, FusekiServer server, DatasetGraph dsgBase, DatasetGraph dsg) -> {
                    String URLauthz = server.datasetURL(dsName);
                    checkDatasetSize(dsgBase, 0);
                    LibTestsSCG.withLevel(FusekiKafka.LOG, "FATAL", () -> {
                        sendEvent(dsg, proc, "A JUNK .", WebContent.contentTypePatch, null);
                    });
                    checkDatasetSize(dsgBase, 0);

                    long c1 = count(URLauthz, queryAll, userPermit);
                    assertEquals(0L, c1);
                    checkDatasetSize(dsgBase, 0);
                };
        runTestProcessorSCGWithAuth(action);
    }

    @Test
    final void processorSCG_load_good_differentDSG_noSecurityApplied() {
        TestAction action =
                (Sink<Event<Bytes, RdfPayload>> proc, FusekiServer server, DatasetGraph dsgBase, DatasetGraph dsg) -> {
                    String URL = server.datasetURL(dsName);
                    checkDatasetSize(dsgBase, 0);
                    String messageBody = """
                            PREFIX : <http://example/>
                            :s :p "turtle" .
                            """;
                    Map<String, String> headers = Map.of(HttpNames.hContentType, WebContent.contentTypeTurtle);
                    byte[] data = messageBody.getBytes(StandardCharsets.UTF_8);
                    Event<Bytes, RdfPayload> request =
                            new SimpleEvent<>(AbstractSmartCacheGraphSinkTests.toHeaders(headers), null,
                                              RdfPayload.of(WebContent.contentTypeTurtle, data));

                    LibTestsSCG.withLevel(FusekiKafka.LOG, "FATAL", () -> {
                        Txn.executeWrite(dsg, () -> proc.send(request));
                    });
                    // Test was on a different, non-auth dataset
                    checkDatasetSize(dsgBase, 0);

                    verifyCounts(URL, queryAll, 1L, 1L);
                    // Look at the alternative dataset used in the test.
                    checkDatasetSize(server.getDataAccessPointRegistry().get(dsName).getDataService().getDataset(), 1);
                };
        runTestProcessorSCGWithGivenDSG(action, DatasetGraphFactory.create());
    }

    @Test
    final void processorSCG_load_good_patch_differentDSG_noLabel() {
        TestAction action =
                (Sink<Event<Bytes, RdfPayload>> proc, FusekiServer server, DatasetGraph dsgBase, DatasetGraph dsg) -> {
                    String URL = server.datasetURL(dsName);
                    checkDatasetSize(dsgBase, 0);
                    sendEvent(dsg, proc, """
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
        runTestProcessorSCGWithGivenDSG(action, createBaseDataset());
    }

    private long count(String URL, String queryString, String user) {
        RowSet rowSet = (user == null) ? queryNoToken(URL, queryString) : queryWithToken(URL, queryString, user);
        long c = RowSetOps.count(rowSet);
        return c;
    }

    private void checkDatasetSize(DatasetGraph dsg, int expectedCount) {
        Txn.executeRead(dsg, () -> {
            try (QueryExec qExec = QueryExecDataset.newBuilder().dataset(dsg).query(queryAll).build()) {
                RowSet rowSet = qExec.select();
                long c = RowSetOps.count(rowSet);
                assertEquals(expectedCount, c, "Dataset size");
            }
        });
    }

    private void checkSizeNoAuth(FusekiServer server, String serviceName, long expectedCount) {
        String URL = server.datasetURL(serviceName);
        // No auth call.
        long c = count(URL, queryAll, null);
        // The storage should have the triple.
        assertEquals(expectedCount, c);
    }

    private void sendEvent(DatasetGraph dsg, Sink<Event<Bytes, RdfPayload>> sink, String body, String contentType,
                           AttributeValue securityLabel) {
        Map<String, String> headers = (securityLabel == null) ? Map.of(HttpNames.hContentType, contentType) :
                                      Map.of(HttpNames.hContentType, contentType, SysABAC.hSecurityLabel,
                                             securityLabel.asString());
        sendEvent(dsg, sink, body, headers);
    }

    private void sendEvent(DatasetGraph dsg, Sink<Event<Bytes, RdfPayload>> sink, String body,
                           Map<String, String> headers) {
        Event<Bytes, RdfPayload> event = new SimpleEvent<>(AbstractSmartCacheGraphSinkTests.toHeaders(headers), null,
                                                           RdfPayload.of(headers.get("Content-Type"),
                                                                         body.getBytes(StandardCharsets.UTF_8)));
        Txn.executeWrite(dsg, () -> {
            try {
                sink.send(event);
            } catch (JenaKafkaException e) {
                // Ignored for the purposes of these tests, some tests are intentionally sending bad data and verifying
                // that it isn't applied
            }
        });
    }

    private void sendEventWithExceptions(DatasetGraph dsg, Sink<Event<Bytes, RdfPayload>> sink, String body,
                                         Map<String, String> headers) {
        Event<Bytes, RdfPayload> event = new SimpleEvent<>(AbstractSmartCacheGraphSinkTests.toHeaders(headers), null,
                                                           RdfPayload.of(headers.get("Content-Type"),
                                                                         body.getBytes(StandardCharsets.UTF_8)));
        sink.send(event);
    }

    private void runTestProcessorSCGWithAuth(TestAction execTestAction) {
        // Set up a DatasetGraphABAC in a Fuseki/SCG
        DatasetGraph dsgBase = createBaseDataset();
        LabelsStore labelsStore = createLabelsStore();
        AttributesStoreModifiable attributesStore = new AttributesStoreLocal();
        // Register - no attributes.
        attributesStore.put(userPublic, AttributeValueSet.of());
        attributesStore.put(userPermit, AttributeValueSet.of("PERMIT"));
        attributesStore.put(userOther, AttributeValueSet.of("OTHER"));

        DatasetGraphABAC dsgz = ABAC.authzDataset(dsgBase, AEX.strALLOW,   // API access label
                                                  labelsStore, SysABAC.denyLabel,    // Dataset data label default.
                                                  attributesStore);
        DataService dataSrv = DataService.newBuilder(dsgz).addEndpoint(Operation.Query).build();
        runTestProcessorWithGivenDSGAndService(execTestAction, dsgBase, dsgz, dataSrv);
    }

    /**
     * Creates a fresh concrete instance of a {@link LabelsStore} to use in tests
     *
     * @return Fresh concrete labels store
     */
    protected abstract LabelsStore createLabelsStore();

    private void runTestProcessorSCGWithGivenDSG(TestAction execTestAction, DatasetGraph dsg) {
        DatasetGraph dsgBase = createBaseDataset();
        DataService dataSrv = DataService.newBuilder(dsg).addEndpoint(Operation.Query).build();
        runTestProcessorWithGivenDSGAndService(execTestAction, dsgBase, dsg, dataSrv);
    }

    private void runTestProcessorWithGivenDSGAndService(TestAction execTestAction, DatasetGraph dsgBase,
                                                        DatasetGraph dsg, DataService dataSrv) {
        FusekiServer server = SmartCacheGraph.serverBuilder().port(0).add(dsName, dataSrv).add(dsBase, dsgBase).build();
        server.start();
        try {
            if (dsg instanceof DatasetGraphABAC abac) {
                try (SmartCacheGraphSink sink = new SmartCacheGraphSink(abac, false)) {
                    execTestAction.execTest(sink, server, dsgBase, dsg);
                }
            } else {
                try (FusekiSink<DatasetGraph> sink = FusekiSink.builder().dataset(dsg).build()) {
                    execTestAction.execTest(sink, server, dsgBase, dsg);
                }
            }
        } finally {
            server.stop();
        }
    }

    private void sendEventWithDistributionId(DatasetGraph dsg, Sink<Event<Bytes, RdfPayload>> sink, String body,
                                             String contentType, AttributeValue securityLabel, String distributionId) {
        Map<String, String> headers = new HashMap<>();
        headers.put(HttpNames.hContentType, contentType);
        if (securityLabel != null) {
            headers.put(SysABAC.hSecurityLabel, securityLabel.asString());
        }
        if (distributionId != null) {
            headers.put(TelicentHeaders.DISTRIBUTION_ID, distributionId);
        }
        sendEventWithExceptions(dsg, sink, body, headers);
    }

    /**
     * Indicates whether the {@link LabelsStore} implementation under test (as returned by {@link #createLabelsStore()})
     * supports labelling of quads.  The return value of this method is used to skip some tests via JUnit
     * {@link Assumptions} that require that functionality.
     *
     * @return True if the labels store supports labelling quads, false otherwise
     */
    protected abstract boolean supportsLabellingQuads();

    private void runTestProcessorSCGWithAuthNamedGraph(TestAction execTestAction) {
        Assumptions.assumeTrue(supportsLabellingQuads());

        DatasetGraph dsgBase = createBaseDataset();
        LabelsStore labelsStore = createLabelsStore();
        AttributesStoreModifiable attributesStore = new AttributesStoreLocal();
        attributesStore.put(userPublic, AttributeValueSet.of());
        attributesStore.put(userPermit, AttributeValueSet.of("PERMIT"));
        attributesStore.put(userOther, AttributeValueSet.of("OTHER"));

        DatasetGraphABAC dsgz =
                ABAC.authzDataset(dsgBase, AEX.strALLOW, labelsStore, SysABAC.denyLabel, attributesStore);
        DataService dataSrv = DataService.newBuilder(dsgz).addEndpoint(Operation.Query).build();

        FusekiServer server = SmartCacheGraph.serverBuilder().port(0).add(dsName, dataSrv).add(dsBase, dsgBase).build();
        server.start();
        try {
            try (SmartCacheGraphSink sink = new SmartCacheGraphSink(dsgz, true)) {
                execTestAction.execTest(sink, server, dsgBase, dsgz);
            }
        } finally {
            server.stop();
        }
    }

    /**
     * Creates a fresh empty {@link DatasetGraph} to use for tests.  This will be wrapped in a {@link DatasetGraphABAC}
     * as part of the tests.
     * <p>
     * Derived test classes may override this if they wish to exercise tests against a different dataset graph
     * implementation.
     * </p>
     *
     * @return Fresh empty dataset
     */
    protected DatasetGraph createBaseDataset() {
        return DatasetGraphFactory.createTxnMem();
    }

    @Test
    final void processorSCG_namedGraph_routesDataToNamedGraph() {
        String namedGraph = "http://example/graph1";
        TestAction action =
                (Sink<Event<Bytes, RdfPayload>> proc, FusekiServer server, DatasetGraph dsgBase, DatasetGraph dsg) -> {
                    checkDatasetSize(dsgBase, 0);
                    dsg.begin(TxnType.WRITE);
                    sendEventWithDistributionId(dsg, proc, """
                            PREFIX : <http://example/>
                            :s :p "turtle" .
                            """, WebContent.contentTypeTurtle, attrPermit, namedGraph);
                    dsg.commit();

                    verifyDefaultGraphEmpty(dsgBase);
                    verifyNamedGraphsHaveData(dsgBase, namedGraph);

                    String URL = server.datasetURL(dsName);
                    verifyCounts(URL, queryAll, 1L, 0L);
                    verifyDefaultGraphEmpty(URL);
                };
        runTestProcessorSCGWithAuthNamedGraph(action);
    }

    @Test
    final void processorSCG_namedGraph_securityLabelStillApplied() {
        String namedGraph = "http://example/graph1";
        TestAction action =
                (Sink<Event<Bytes, RdfPayload>> proc, FusekiServer server, DatasetGraph dsgBase, DatasetGraph dsg) -> {
                    dsg.begin(TxnType.WRITE);
                    sendEventWithDistributionId(dsg, proc, """
                            PREFIX : <http://example/>
                            :s :p "turtle" .
                            """, WebContent.contentTypeTurtle, attrPermit, namedGraph);
                    dsg.commit();

                    DatasetGraphABAC abac = AbstractSmartCacheGraphSinkTests.getDatasetABAC(server);
                    verifyNamedGraphsHaveData(dsgBase, namedGraph);
                    assertFalse(abac.labelsStore().isEmpty(), "Labels store should not be empty");

                    String URL = server.datasetURL(dsName);
                    verifyCounts(URL, queryAll, 1L, 0L);
                    verifyDefaultGraphEmpty(URL);
                };
        runTestProcessorSCGWithAuthNamedGraph(action);
    }

    private void verifyCounts(String URL, String query, long expectedWithPermitAttribute,
                              long expectedWithOtherAttribute) {
        long c1 = count(URL, query, userPermit);
        assertEquals(expectedWithPermitAttribute, c1, "Count (user:permit)");
        long c2 = count(URL, query, userOther);
        assertEquals(expectedWithOtherAttribute, c2, "Count (user:other)");
    }

    @Test
    final void processorSCG_namedGraph_missingDistributionIdThrows() {
        TestAction action =
                (Sink<Event<Bytes, RdfPayload>> proc, FusekiServer server, DatasetGraph dsgBase, DatasetGraph dsg) -> {
                    checkDatasetSize(dsgBase, 0);
                    JenaKafkaException ex =
                            assertThrows(JenaKafkaException.class, () -> sendEventWithDistributionId(dsg, proc, """
                                    PREFIX : <http://example/>
                                    :s :p "turtle" .
                                    """, WebContent.contentTypeTurtle, attrPermit, null));
                    assertInstanceOf(IllegalArgumentException.class, ex.getCause());
                    assertEquals("No distribution id specified when in routing mode", ex.getCause().getMessage());
                    checkDatasetSize(dsgBase, 0);
                };
        runTestProcessorSCGWithAuthNamedGraph(action);
    }

    @Test
    final void processorSCG_namedGraph_differentDistributionIdsRouteToDifferentGraphs() {
        String namedGraph1 = "http://example/graph1";
        String namedGraph2 = "http://example/graph2";
        TestAction action =
                (Sink<Event<Bytes, RdfPayload>> proc, FusekiServer server, DatasetGraph dsgBase, DatasetGraph dsg) -> {
                    dsg.begin(TxnType.WRITE);
                    sendEventWithDistributionId(dsg, proc, """
                            PREFIX : <http://example/>
                            :s :p "value1" .
                            """, WebContent.contentTypeTurtle, attrPermit, namedGraph1);
                    sendEventWithDistributionId(dsg, proc, """
                            PREFIX : <http://example/>
                            :s :p "value2" .
                            """, WebContent.contentTypeTurtle, attrPermit, namedGraph2);
                    dsg.commit();

                    verifyNamedGraphsHaveData(dsgBase, namedGraph1, namedGraph2);

                    String URL = server.datasetURL(dsName);
                    verifyCounts(URL, queryAll, 2L, 0L);
                    verifyDefaultGraphEmpty(URL);
                    verifyCounts(URL, queryUnion, 2L, 0L);
                };
        runTestProcessorSCGWithAuthNamedGraph(action);
    }

    private static void verifyNamedGraphsHaveData(DatasetGraph dsgBase, String... namedGraphs) {
        Txn.executeRead(dsgBase, () -> {
            for (String graph : namedGraphs) {
                assertFalse(dsgBase.getGraph(NodeFactory.createURI(graph)).isEmpty(),
                            "Graph " + graph + " should have data");
            }
        });
    }

    private static void verifyNamedGraphsEmpty(DatasetGraph dsgBase, String... namedGraphs) {
        Txn.executeRead(dsgBase, () -> {
            for (String graph : namedGraphs) {
                assertTrue(dsgBase.getGraph(NodeFactory.createURI(graph)).isEmpty(),
                           "Graph " + graph + " should be empty");
            }
        });
    }

    @Test
    final void processorSCG_namedGraph_differentDistributionIdsRouteToDifferentGraphs_andLabelsPreventAccess() {
        String namedGraph1 = "http://example/graph1";
        String namedGraph2 = "http://example/graph2";
        TestAction action =
                (Sink<Event<Bytes, RdfPayload>> proc, FusekiServer server, DatasetGraph dsgBase, DatasetGraph dsg) -> {
                    dsg.begin(TxnType.WRITE);
                    sendEventWithDistributionId(dsg, proc, """
                            PREFIX : <http://example/>
                            :s :p "value1" .
                            """, WebContent.contentTypeTurtle, attrNotPermitted, namedGraph1);
                    sendEventWithDistributionId(dsg, proc, """
                            PREFIX : <http://example/>
                            :s :p "value2" .
                            """, WebContent.contentTypeTurtle, attrNotPermitted, namedGraph2);
                    dsg.commit();

                    verifyNamedGraphsHaveData(dsgBase, namedGraph1, namedGraph2);

                    String URL = server.datasetURL(dsName);
                    verifyCounts(URL, queryAll, 0L, 0L);
                    verifyDefaultGraphEmpty(URL);
                    verifyCounts(URL, queryUnion, 0L, 0L);
                };
        runTestProcessorSCGWithAuthNamedGraph(action);
    }

    @Test
    final void processorSCG_normalMode_distributionIdIgnored() {
        TestAction action =
                (Sink<Event<Bytes, RdfPayload>> proc, FusekiServer server, DatasetGraph dsgBase, DatasetGraph dsg) -> {
                    checkDatasetSize(dsgBase, 0);
                    dsg.begin(TxnType.WRITE);
                    sendEventWithDistributionId(dsg, proc, """
                            PREFIX : <http://example/>
                            :s :p "turtle" .
                            """, WebContent.contentTypeTurtle, attrPermit, "http://example/graph1");
                    dsg.commit();
                    checkDatasetSize(dsgBase, 1);
                    verifyDefaultGraphNonEmpty(dsgBase);

                    String URL = server.datasetURL(dsName);
                    verifyCounts(URL, queryAll, 1L, 0L);
                    verifyCounts(URL, queryDefault, 1L, 0L);
                };
        runTestProcessorSCGWithAuth(action);
    }

    private static void verifyDefaultGraphNonEmpty(DatasetGraph dsgBase) {
        Txn.executeRead(dsgBase, () -> assertFalse(dsgBase.getDefaultGraph().isEmpty(),
                                                   "Default graph should have data in normal mode"));
    }

    @Test
    final void processorSCG_namedGraph_rdfPatch_differentDistributionIdsRouteToDifferentGraphs() {
        String namedGraph1 = "http://example/graph1";
        String namedGraph2 = "http://example/graph2";
        TestAction action =
                (Sink<Event<Bytes, RdfPayload>> proc, FusekiServer server, DatasetGraph dsgBase, DatasetGraph dsg) -> {
                    sendEventWithDistributionId(dsg, proc, """
                            TX .
                            A <http://example/s> <http://example/p> "value1" .
                            TC .
                            """, WebContent.contentTypePatch, attrPermit, namedGraph1);
                    sendEventWithDistributionId(dsg, proc, """
                            TX .
                            A <http://example/s> <http://example/p> "value1" .
                            TC .
                            """, WebContent.contentTypePatch, attrPermit, namedGraph2);

                    verifyNamedGraphsHaveData(dsgBase, namedGraph1, namedGraph2);
                    verifyDefaultGraphEmpty(dsgBase);

                    String URL = server.datasetURL(dsName);
                    verifyCounts(URL, queryAll, 2L, 0L);
                    verifyDefaultGraphEmpty(URL);
                    verifyCounts(URL, queryUnion, 1L, 0L);
                };
        runTestProcessorSCGWithAuthNamedGraph(action);
    }

    private static void verifyDefaultGraphEmpty(DatasetGraph dsgBase) {
        Txn.executeRead(dsgBase,
                        () -> assertTrue(dsgBase.getDefaultGraph().isEmpty(), "Default graph should be empty"));
    }

    private void verifyDefaultGraphEmpty(String URL) {
        verifyCounts(URL, queryDefault, 0L, 0L);
    }

    @Test
    final void processorSCG_namedGraph_rdfPatch_differentDistributionIdsRouteToDifferentGraphs_andSameTripleInDifferentNamedGraphsHasIndependentLabels() {
        String namedGraph1 = "http://example/graph1";
        String namedGraph2 = "http://example/graph2";
        TestAction action =
                (Sink<Event<Bytes, RdfPayload>> proc, FusekiServer server, DatasetGraph dsgBase, DatasetGraph dsg) -> {
                    sendEventWithDistributionId(dsg, proc, """
                            TX .
                            A <http://example/s> <http://example/p> "value1" .
                            TC .
                            """, WebContent.contentTypePatch, attrPermit, namedGraph1);
                    sendEventWithDistributionId(dsg, proc, """
                            TX .
                            A <http://example/s> <http://example/p> "value1" .
                            TC .
                            """, WebContent.contentTypePatch, attrOther, namedGraph2);

                    verifyNamedGraphsHaveData(dsgBase, namedGraph1, namedGraph2);
                    verifyDefaultGraphEmpty(dsgBase);

                    String URL = server.datasetURL(dsName);
                    verifyCounts(URL, queryAll, 1L, 1L);
                    verifyDefaultGraphEmpty(URL);
                    verifyCounts(URL, queryUnion, 1L, 1L);
                };
        runTestProcessorSCGWithAuthNamedGraph(action);
    }

    @Test
    final void processorSCG_namedGraph_rdfPatch_noDistributionId_throws() {
        TestAction action =
                (Sink<Event<Bytes, RdfPayload>> proc, FusekiServer server, DatasetGraph dsgBase, DatasetGraph dsg) -> {
                    assertThrows(JenaKafkaException.class, () -> sendEventWithDistributionId(dsg, proc, """
                            TX .
                            A <http://example/s> <http://example/p> "value1" .
                            TC .
                            """, WebContent.contentTypePatch, attrPermit, null));
                };
        runTestProcessorSCGWithAuthNamedGraph(action);
    }

    @Test
    final void processorSCG_namedGraph_rdfPatch_addThenDelete_graphIsEmpty() {
        String namedGraph = "http://example/graph1";
        TestAction action =
                (Sink<Event<Bytes, RdfPayload>> proc, FusekiServer server, DatasetGraph dsgBase, DatasetGraph dsg) -> {
                    String URL = server.datasetURL(dsName);
                    sendEventWithDistributionId(dsg, proc, """
                            TX .
                            A <http://example/s> <http://example/p> "value1" .
                            TC .
                            """, WebContent.contentTypePatch, attrPermit, namedGraph);

                    verifyNamedGraphsHaveData(dsgBase, namedGraph);
                    verifyCounts(URL, queryAll, 1L, 0L);
                    verifyDefaultGraphEmpty(URL);

                    sendEventWithDistributionId(dsg, proc, """
                            TX .
                            D <http://example/s> <http://example/p> "value1" .
                            TC .
                            """, WebContent.contentTypePatch, attrPermit, namedGraph);

                    verifyNamedGraphsEmpty(dsgBase, namedGraph);
                    verifyCounts(URL, queryAll, 0L, 0L);
                    verifyDefaultGraphEmpty(URL);
                };
        runTestProcessorSCGWithAuthNamedGraph(action);
    }

    // The test - directly send requests to the sink and check by making HTTP requests to the Fuseki server.
    interface TestAction {
        void execTest(Sink<Event<Bytes, RdfPayload>> sink, FusekiServer server, DatasetGraph dsgBase, DatasetGraph dsg);
    }
}
