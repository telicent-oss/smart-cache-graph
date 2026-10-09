package io.telicent.core;

import io.telicent.jena.abac.ABAC;
import io.telicent.jena.abac.SysABAC;
import io.telicent.jena.abac.attributes.syntax.AEX;
import io.telicent.jena.abac.core.AttributesStoreLocal;
import io.telicent.jena.abac.core.DatasetGraphABAC;
import io.telicent.jena.abac.core.VocabAuthz;
import io.telicent.jena.abac.labels.Label;
import io.telicent.jena.abac.labels.Labels;
import io.telicent.jena.abac.labels.LabelsStore;
import io.telicent.smart.cache.payloads.RdfPayload;
import io.telicent.smart.cache.security.data.distribution.DistributionLifecycleStateFile;
import io.telicent.smart.cache.security.data.labels.DatasetGraphLabelled;
import io.telicent.smart.cache.security.data.plugins.DataSecurityPluginLoader;
import io.telicent.smart.cache.sources.Event;
import io.telicent.smart.cache.sources.EventHeader;
import io.telicent.smart.cache.sources.Header;
import io.telicent.smart.cache.sources.TelicentHeaders;
import io.telicent.smart.cache.sources.memory.SimpleEvent;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.kafka.JenaKafkaException;
import org.apache.jena.rdfpatch.changes.RDFChangesCollector;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.system.Txn;
import org.apache.kafka.common.utils.Bytes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class TestSmartCacheGraphSinkLifecycle {

    private static final String DISTRIBUTION_ID = "http://example/distribution";

    // Lifecycle states whose data MUST be accepted for ingest (Active is additionally visible at query time).
    private static final String[] VIABLE_STATES = { "Active", "Registered", "Withdrawn" };
    // Lifecycle states whose data MUST NOT be ingested - the first event is DLQ'd, subsequent ones are dropped.
    private static final String[] NON_VIABLE_STATES = { "Deleted", "Unregistered" };

    /**
     * The two payload shapes a {@link SmartCacheGraphSink} can receive. Both are gated by the same lifecycle logic but flow
     * through different code paths ({@code applyDatasetEvent} vs {@code applyRdfPatchEvent}), so every scenario is run
     * against both.
     */
    private enum PayloadType {
        DATASET {
            @Override
            RdfPayload payload() {
                final DatasetGraph dsg = DatasetGraphFactory.create();
                dsg.add(Quad.create(Quad.defaultGraphIRI, TRIPLE_S, TRIPLE_P, TRIPLE_O));
                return RdfPayload.of(dsg);
            }
        },
        PATCH {
            @Override
            RdfPayload payload() {
                final RDFChangesCollector collector = new RDFChangesCollector();
                collector.start();
                collector.add(Quad.defaultGraphIRI, TRIPLE_S, TRIPLE_P, TRIPLE_O);
                collector.finish();
                return RdfPayload.of(collector.getRDFPatch());
            }
        };

        static final Node TRIPLE_S = NodeFactory.createURI("http://example/s");
        static final Node TRIPLE_P = NodeFactory.createURI("http://example/p");
        static final Node TRIPLE_O = NodeFactory.createURI("http://example/o");

        abstract RdfPayload payload();

        Event<Bytes, RdfPayload> event(String distributionId) {
            final List<EventHeader> headers = distributionId == null
                                              ? List.of()
                                              : List.of(new Header(TelicentHeaders.DISTRIBUTION_ID, distributionId));
            return new SimpleEvent<>(headers, null, payload());
        }
    }

    public static Object[][] payloadTypes() {
        return new Object[][] { { PayloadType.DATASET }, { PayloadType.PATCH } };
    }

    public static Object[][] viableStatesAndPayloads() {
        return statesAndPayloads(VIABLE_STATES);
    }

    public static Object[][] nonViableStatesAndPayloads() {
        return statesAndPayloads(NON_VIABLE_STATES);
    }

    private static Object[][] statesAndPayloads(String[] states) {
        final List<Object[]> combos = new ArrayList<>();
        for (String state : states) {
            for (PayloadType payloadType : PayloadType.values()) {
                combos.add(new Object[] { state, payloadType });
            }
        }
        return combos.toArray(new Object[0][]);
    }

    private DatasetGraphABAC dataset;
    private DatasetGraphLabelled labelled;
    private LabelsStore labelsStore;
    private Path stateFile;

    @BeforeEach
    public void setUp() throws IOException {
        this.labelsStore = spy(Labels.createLabelsStoreMem());
        this.dataset = ABAC.authzDataset(DatasetGraphFactory.createTxnMem(),
                                         AEX.strALLOW,
                                         this.labelsStore,
                                         SysABAC.denyLabel,
                                         new AttributesStoreLocal());
        this.labelled = DataSecurityPluginLoader.load().prepareLabelledDataset(this.dataset).orElseThrow();
        this.stateFile = Files.createTempFile("scg-test-sink-lifecycle-", ".json");
    }

    @AfterEach
    public void tearDown() throws IOException {
        Files.deleteIfExists(this.stateFile);
        Files.deleteIfExists(Path.of(this.stateFile + ".tmp"));
        Files.deleteIfExists(Path.of(this.stateFile + ".bak"));
    }

    // --- Non-routing mode -------------------------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("payloadTypes")
    public void send_ingests_whenNotRouting_evenWithoutLifecycleStateFile(PayloadType payloadType) {
        final SmartCacheGraphSink sink = new SmartCacheGraphSink(this.labelled, false, null);

        sendInWriteTxn(sink, payloadType.event(null));

        Assertions.assertFalse(datasetIsEmpty(), "Event should be ingested when not routing to named graphs");
    }

    @Test
    public void send_datasetPayload_appliesEventLabelAsOneBatch() {
        final DatasetGraph dsg = DatasetGraphFactory.create();
        dsg.add(Quad.create(Quad.defaultGraphIRI, PayloadType.TRIPLE_S, PayloadType.TRIPLE_P, PayloadType.TRIPLE_O));
        dsg.add(Quad.create(Quad.defaultGraphIRI, NodeFactory.createURI("http://example/s2"),
                            PayloadType.TRIPLE_P, PayloadType.TRIPLE_O));
        final Event<Bytes, RdfPayload> event = new SimpleEvent<>(
                List.of(new Header(TelicentHeaders.SECURITY_LABEL, "PERMIT")), null, RdfPayload.of(dsg));
        final SmartCacheGraphSink sink = new SmartCacheGraphSink(this.labelled, false, null);

        sendInWriteTxn(sink, event);

        verify(this.labelsStore, times(1)).addAll(any(), eq(Label.fromText("PERMIT")));
    }

    // --- Routing mode, no lifecycle gating --------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("payloadTypes")
    public void send_rejects_whenRoutingWithoutDistributionId(PayloadType payloadType) {
        final SmartCacheGraphSink sink = new SmartCacheGraphSink(this.labelled, true, null);

        Assertions.assertThrows(JenaKafkaException.class, () -> sink.send(payloadType.event(null)));
    }

    @ParameterizedTest
    @MethodSource("payloadTypes")
    public void send_ingests_whenRoutingWithoutLifecycleStateFile(PayloadType payloadType) {
        final SmartCacheGraphSink sink = new SmartCacheGraphSink(this.labelled, true, null);

        sendInWriteTxn(sink, payloadType.event(DISTRIBUTION_ID));

        Assertions.assertFalse(datasetIsEmpty(), "With no lifecycle state file ingest should not be gated");
    }

    // --- Routing mode, lifecycle gating -----------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("viableStatesAndPayloads")
    public void send_ingests_whenDistributionViable(String state, PayloadType payloadType) throws IOException {
        writeState(stateJson(state));
        final SmartCacheGraphSink sink = new SmartCacheGraphSink(this.labelled, true, lifecycleStateFile());

        sendInWriteTxn(sink, payloadType.event(DISTRIBUTION_ID));

        Assertions.assertFalse(datasetIsEmpty(), state + " distribution should be ingested");
    }

    @ParameterizedTest
    @MethodSource("payloadTypes")
    public void send_rejects_whenLifecycleStateUnavailable(PayloadType payloadType) throws IOException {
        // Remove every candidate so the state file cannot be loaded -> state is unavailable -> fail closed.
        Path stateFile = Path.of("/no", "/such", "state.json");
        final SmartCacheGraphSink sink = new SmartCacheGraphSink(this.labelled, true, new DistributionLifecycleStateFile(stateFile, null));

        assertRejected(sink, payloadType.event(DISTRIBUTION_ID),
                       "Ingest must be rejected (DLQ) when lifecycle state is unavailable");
        Assertions.assertTrue(datasetIsEmpty(), "Nothing should be written when the event is rejected");
    }

    @ParameterizedTest
    @MethodSource("nonViableStatesAndPayloads")
    public void send_rejectsFirstEvent_thenDropsSubsequent_forNonViableDistribution(String state,
                                                                                    PayloadType payloadType)
            throws IOException {
        writeState(stateJson(state));
        final SmartCacheGraphSink sink = new SmartCacheGraphSink(this.labelled, true, lifecycleStateFile());

        // First event for a non-viable (Deleted/Unregistered) distribution is dead-lettered so the reason is visible.
        assertRejected(sink, payloadType.event(DISTRIBUTION_ID),
                       "First event for a " + state + " distribution must be rejected (DLQ)");

        // Subsequent events for the same non-viable distribution are silently dropped, not rejected.
        sink.send(payloadType.event(DISTRIBUTION_ID));

        Assertions.assertTrue(datasetIsEmpty(), "No data should be written for a " + state + " distribution");
    }

    @ParameterizedTest
    @MethodSource("nonViableStatesAndPayloads")
    public void send_resumesIngest_whenNonViableDistributionBecomesActiveAgain(String state, PayloadType payloadType)
            throws IOException {
        writeState(stateJson(state));
        final SmartCacheGraphSink sink = new SmartCacheGraphSink(this.labelled, true, lifecycleStateFile());

        assertRejected(sink, payloadType.event(DISTRIBUTION_ID),
                       "Pre-condition: first event for a " + state + " distribution is rejected");

        // The distribution comes back to life; the sink should clear its rejected-tracking and ingest again.
        writeState(stateJson("Active"));

        sendInWriteTxn(sink, payloadType.event(DISTRIBUTION_ID));

        Assertions.assertFalse(datasetIsEmpty(), "Ingest should resume once the distribution is Active again");
    }

    @Test
    public void givenNoStateFile_whenEventsReceived_thenApplied() {
        // Given
        final DatasetGraph dsg = DatasetGraphFactory.create();
        dsg.add(Quad.create(Quad.defaultGraphIRI, PayloadType.TRIPLE_S, PayloadType.TRIPLE_P, PayloadType.TRIPLE_O));
        RdfPayload payload = RdfPayload.of(dsg);

        // When
        final SmartCacheGraphSink sink = new SmartCacheGraphSink(this.labelled, true);
        sink.send(new SimpleEvent<>(
                List.of(new Header(TelicentHeaders.DISTRIBUTION_ID, DISTRIBUTION_ID)), null, payload));

        // Then
        Assertions.assertFalse(datasetIsEmpty());
    }

    @Test
    public void givenQuadsInLabelsGraph_whenEventsReceived_thenNotAppliedToDataset() {
        // Given
        final DatasetGraph dsg = DatasetGraphFactory.create();
        dsg.add(Quad.create(VocabAuthz.graphForLabels, PayloadType.TRIPLE_S, PayloadType.TRIPLE_P,
                            PayloadType.TRIPLE_O));
        RdfPayload payload = RdfPayload.of(dsg);

        // When
        final SmartCacheGraphSink sink = new SmartCacheGraphSink(this.labelled, true);
        sink.send(new SimpleEvent<>(
                List.of(new Header(TelicentHeaders.DISTRIBUTION_ID, DISTRIBUTION_ID)), null, payload));

        // Then
        Assertions.assertTrue(datasetIsEmpty());
    }

    @Test
    public void givenEventSecurityLabels_whenEventsReceived_thenLabelAppliedToCorrectGraph() {
        // Given
        final DatasetGraph dsg = DatasetGraphFactory.create();
        dsg.add(Quad.create(Quad.defaultGraphIRI, PayloadType.TRIPLE_S, PayloadType.TRIPLE_P, PayloadType.TRIPLE_O));
        RdfPayload payload = RdfPayload.of(dsg);

        // When
        final SmartCacheGraphSink sink = new SmartCacheGraphSink(this.labelled, true);
        sink.send(new SimpleEvent<>(
                List.of(new Header(TelicentHeaders.DISTRIBUTION_ID, DISTRIBUTION_ID),
                        new Header(TelicentHeaders.SECURITY_LABEL, "clearance=O")), null, payload));

        // Then
        Assertions.assertFalse(datasetIsEmpty());
        Assertions.assertEquals(dataset.labelsStore()
                                   .labelForQuad(
                                           Quad.create(NodeFactory.createURI(DISTRIBUTION_ID), PayloadType.TRIPLE_S,
                                                       PayloadType.TRIPLE_P,
                                                       PayloadType.TRIPLE_O)), Label.fromText("clearance=O"));
    }

    @Test
    public void givenEventSecurityLabels_whenEventsReceived_thenLabelAppliedToDeclaredGraph() {
        // Given
        final DatasetGraph dsg = DatasetGraphFactory.create();
        dsg.add(Quad.create(Quad.defaultGraphIRI, PayloadType.TRIPLE_S, PayloadType.TRIPLE_P, PayloadType.TRIPLE_O));
        RdfPayload payload = RdfPayload.of(dsg);

        // When
        final SmartCacheGraphSink sink = new SmartCacheGraphSink(this.labelled, false);
        sink.send(new SimpleEvent<>(
                List.of(new Header(TelicentHeaders.DISTRIBUTION_ID, DISTRIBUTION_ID),
                        new Header(TelicentHeaders.SECURITY_LABEL, "clearance=O")), null, payload));

        // Then
        Assertions.assertFalse(datasetIsEmpty());
        Assertions.assertEquals(dataset.labelsStore()
                                   .labelForQuad(
                                           Quad.create(Quad.defaultGraphIRI, PayloadType.TRIPLE_S,
                                                       PayloadType.TRIPLE_P,
                                                       PayloadType.TRIPLE_O)), Label.fromText("clearance=O"));
    }

    // --- Helpers ----------------------------------------------------------------------------------------------------

    private DistributionLifecycleStateFile lifecycleStateFile() {
        return new DistributionLifecycleStateFile(this.stateFile, null);
    }

    private static String stateJson(String state) {
        return """
                {
                  "distributions" : {
                    "%s" : "%s"
                  }
                }
                """.formatted(DISTRIBUTION_ID, state);
    }

    private void writeState(String json) throws IOException {
        Files.writeString(this.stateFile, json, StandardCharsets.UTF_8);
    }

    private void sendInWriteTxn(SmartCacheGraphSink sink, Event<Bytes, RdfPayload> event) {
        // The sink writes directly to the dataset, which is transactional, so drive it inside a write transaction.
        Txn.executeWrite(this.dataset, () -> sink.send(event));
    }

    private boolean datasetIsEmpty() {
        return Txn.calculateRead(this.dataset, () -> this.dataset.stream().findAny().isEmpty());
    }

    private static void assertRejected(SmartCacheGraphSink sink, Event<Bytes, RdfPayload> event, String message) {
        Assertions.assertThrows(JenaKafkaException.class, () -> sink.send(event), message);
    }
}
