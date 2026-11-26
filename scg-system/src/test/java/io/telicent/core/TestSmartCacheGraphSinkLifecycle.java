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
import io.telicent.smart.cache.distribution.lifecycle.config.DistributionLifecycleConfiguration;
import io.telicent.smart.cache.projectors.Sink;
import io.telicent.smart.cache.projectors.SinkException;
import io.telicent.smart.cache.configuration.Configurator;
import io.telicent.smart.cache.configuration.sources.PropertiesSource;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class TestSmartCacheGraphSinkLifecycle {

    private static final String DISTRIBUTION_ID = "http://example/distribution";

    // Lifecycle states whose data MUST be accepted for ingest (Active is additionally visible at query time).
    private static final String[] VIABLE_STATES = { "Active", "Registered", "Withdrawn" };
    // Lifecycle states whose data MUST NOT be ingested - the first event is DLQ'd, subsequent ones are dropped.
    private static final String[] NON_VIABLE_STATES = { "Deleted", "Unregistered" };

    /**
     * The two payload shapes a {@link SmartCacheGraphSink} can receive. Both are gated by the acceptance wrapper but flow
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
                final var collector = new RDFChangesCollector();
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

    public static Object[][] unregisteredStatesAndPayloads() {
        return statesAndPayloads(new String[] { null, "Unregistered" });
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
        Properties config = new Properties();
        config.setProperty(DistributionLifecycleConfiguration.UNREGISTERED_GRACE_PERIOD, "PT0.005S");
        config.setProperty(DistributionLifecycleConfiguration.UNREGISTERED_GRACE_CHECK_INTERVAL, "PT0.001S");
        Configurator.setSingleSource(new PropertiesSource(config));
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
        Configurator.reset();
        Files.deleteIfExists(this.stateFile);
        Files.deleteIfExists(Path.of(this.stateFile + ".tmp"));
        Files.deleteIfExists(Path.of(this.stateFile + ".bak"));
    }

    // --- Non-routing mode -------------------------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("payloadTypes")
    public void send_ingests_whenNotRouting_evenWithoutLifecycleStateFile(PayloadType payloadType) {
        final var sink = new SmartCacheGraphSink(this.labelled, false);

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
        final var sink = new SmartCacheGraphSink(this.labelled, false);

        sendInWriteTxn(sink, event);

        verify(this.labelsStore, times(1)).addAll(any(), eq(Label.fromText("PERMIT")));
    }

    // --- Routing mode, no lifecycle gating --------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("payloadTypes")
    public void send_rejects_whenRoutingWithoutDistributionId(PayloadType payloadType) {
        final var sink = new SmartCacheGraphSink(this.labelled, true);

        Assertions.assertThrows(JenaKafkaException.class, () -> sink.send(payloadType.event(null)));
    }

    @ParameterizedTest
    @MethodSource("payloadTypes")
    public void send_ingests_whenRoutingWithoutLifecycleStateFile(PayloadType payloadType) {
        final var sink = new SmartCacheGraphSink(this.labelled, true);

        sendInWriteTxn(sink, payloadType.event(DISTRIBUTION_ID));

        Assertions.assertFalse(datasetIsEmpty(), "With no lifecycle state file ingest should not be gated");
    }

    // --- Routing mode, lifecycle gating -----------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("viableStatesAndPayloads")
    public void send_ingests_whenDistributionViable(String state, PayloadType payloadType) throws IOException {
        writeState(stateJson(state));
        final var sink = acceptanceSink(this.stateFile);

        sendInWriteTxn(sink, payloadType.event(DISTRIBUTION_ID));

        Assertions.assertFalse(datasetIsEmpty(), state + " distribution should be ingested");
    }

    @ParameterizedTest
    @MethodSource("payloadTypes")
    public void send_rejects_whenLifecycleStateUnavailable(PayloadType payloadType) {
        // Remove every candidate so the state file cannot be loaded -> state is unavailable -> fail closed.
        final var missingStateFile = Path.of("/no", "/such", "state.json");
        final var sink = acceptanceSink(missingStateFile);

        Assertions.assertThrows(IllegalStateException.class, () -> sink.send(payloadType.event(DISTRIBUTION_ID)),
                                "Ingest must be rejected when lifecycle state is unavailable");
        Assertions.assertTrue(datasetIsEmpty(), "Nothing should be written when the event is rejected");
    }

    @ParameterizedTest
    @MethodSource("nonViableStatesAndPayloads")
    public void send_rejectsFirstEvent_thenDropsSubsequent_forNonViableDistribution(String state,
                                                                                    PayloadType payloadType)
            throws IOException {
        writeState(stateJson(state));
        final var sink = acceptanceSink(this.stateFile);

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
        final Sink<Event<Bytes, RdfPayload>> sink = acceptanceSink(this.stateFile);

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
        final var payload = RdfPayload.of(dsg);

        // When
        final var sink = new SmartCacheGraphSink(this.labelled, true);
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
        final var payload = RdfPayload.of(dsg);

        // When
        final var sink = new SmartCacheGraphSink(this.labelled, true);
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
        final var payload = RdfPayload.of(dsg);

        // When
        final var sink = new SmartCacheGraphSink(this.labelled, true);
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
        final var payload = RdfPayload.of(dsg);

        // When
        final var sink = new SmartCacheGraphSink(this.labelled, false);
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

    @ParameterizedTest
    @MethodSource("payloadTypes")
    public void unknownDistributionIsRejectedWhenStrictRegistrationIsEnabled(PayloadType payloadType) throws IOException {
        writeState("{\"distributions\":{}}");
        var sink = acceptanceSink(this.stateFile, true);
        assertRejected(sink, payloadType.event(DISTRIBUTION_ID), "Unknown distributions require registration");
        sink.send(payloadType.event(DISTRIBUTION_ID));
        Assertions.assertTrue(datasetIsEmpty());
    }

    @ParameterizedTest
    @MethodSource("payloadTypes")
    public void externalFileFailureDoesNotPreventRecovery(PayloadType payloadType) throws IOException {
        writeState("invalid JSON");
        var sink = acceptanceSink(this.stateFile);
        Assertions.assertThrows(IllegalStateException.class, () -> sink.send(payloadType.event(DISTRIBUTION_ID)));
        writeState(stateJson("Active"));
        sendInWriteTxn(sink, payloadType.event(DISTRIBUTION_ID));
        Assertions.assertFalse(datasetIsEmpty());
    }

    @ParameterizedTest
    @MethodSource("payloadTypes")
    public void unrecognisedExternalStateFailsClosed(PayloadType payloadType) throws IOException {
        writeState(stateJson("Unrecognised"));
        var sink = acceptanceSink(this.stateFile);
        Assertions.assertThrows(IllegalArgumentException.class, () -> sink.send(payloadType.event(DISTRIBUTION_ID)));
        Assertions.assertTrue(datasetIsEmpty());
    }

    @ParameterizedTest
    @MethodSource("unregisteredStatesAndPayloads")
    public void commonConfigurationCanAcceptUnregisteredDistributions(String state, PayloadType payloadType)
            throws IOException {
        writeState(state == null ? "{\"distributions\":{}}" : stateJson(state));
        Properties config = new Properties();
        config.setProperty(FMod_DistributionLifecycle.ROUTE_TO_NAMED_GRAPHS, "true");
        config.setProperty(DistributionLifecycleConfiguration.DISTRIBUTION_LIFECYCLE_STATE_FILE, this.stateFile.toString());
        config.setProperty(DistributionLifecycleConfiguration.REJECT_UNREGISTERED, "false");
        Configurator.setSingleSource(new PropertiesSource(config));
        var sink = new FMod_FusekiKafkaSCG().getSinkBuilder().apply(this.dataset);
        sendInWriteTxn(sink, payloadType.event(DISTRIBUTION_ID));
        Assertions.assertFalse(datasetIsEmpty());
    }

    @ParameterizedTest
    @MethodSource("payloadTypes")
    public void acceptanceWrapperRejectsMissingDistributionId(PayloadType payloadType) throws IOException {
        writeState(stateJson("Active"));
        var sink = acceptanceSink(this.stateFile);
        Assertions.assertThrows(SinkException.class, () -> sink.send(payloadType.event(null)));
        Assertions.assertTrue(datasetIsEmpty());
    }

    @ParameterizedTest
    @MethodSource("payloadTypes")
    public void unknownDistributionIsRejectedByDefault(PayloadType payloadType) throws IOException {
        writeState("{\"distributions\":{}}");
        var sink = acceptanceSink(this.stateFile);
        assertRejected(sink, payloadType.event(DISTRIBUTION_ID), "Unknown distributions require registration by default");
        sink.send(payloadType.event(DISTRIBUTION_ID));
        Assertions.assertTrue(datasetIsEmpty());
    }

    @ParameterizedTest
    @MethodSource("payloadTypes")
    public void explicitUnregisteredStateIsRejectedByDefault(PayloadType payloadType) throws IOException {
        writeState(stateJson("Unregistered"));
        var sink = acceptanceSink(this.stateFile);
        assertRejected(sink, payloadType.event(DISTRIBUTION_ID), "Unregistered distributions require registration by default");
        sink.send(payloadType.event(DISTRIBUTION_ID));
        Assertions.assertTrue(datasetIsEmpty());
    }

    // --- Helpers ----------------------------------------------------------------------------------------------------

    private Sink<Event<Bytes, RdfPayload>> acceptanceSink(Path path) {
        return acceptanceSink(path, false);
    }

    private Sink<Event<Bytes, RdfPayload>> acceptanceSink(Path path, boolean explicitlyRequireRegistration) {
        Properties config = new Properties();
        config.setProperty(FMod_DistributionLifecycle.ROUTE_TO_NAMED_GRAPHS, "true");
        config.setProperty(DistributionLifecycleConfiguration.DISTRIBUTION_LIFECYCLE_STATE_FILE, path.toString());
        config.setProperty(DistributionLifecycleConfiguration.UNREGISTERED_GRACE_PERIOD, "PT0.005S");
        config.setProperty(DistributionLifecycleConfiguration.UNREGISTERED_GRACE_CHECK_INTERVAL, "PT0.001S");
        if (explicitlyRequireRegistration) {
            config.setProperty(DistributionLifecycleConfiguration.REJECT_UNREGISTERED, "true");
        }
        Configurator.setSingleSource(new PropertiesSource(config));
        return new FMod_FusekiKafkaSCG().getSinkBuilder().apply(this.dataset);
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

    private void sendInWriteTxn(Sink<Event<Bytes, RdfPayload>> sink, Event<Bytes, RdfPayload> event) {
        // The sink writes directly to the dataset, which is transactional, so drive it inside a write transaction.
        Txn.executeWrite(this.dataset, () -> sink.send(event));
    }

    private boolean datasetIsEmpty() {
        return Txn.calculateRead(this.dataset, () -> this.dataset.stream().findAny().isEmpty());
    }

    private static void assertRejected(Sink<Event<Bytes, RdfPayload>> sink, Event<Bytes, RdfPayload> event, String message) {
        Assertions.assertThrows(SinkException.class, () -> sink.send(event), message);
    }

}
