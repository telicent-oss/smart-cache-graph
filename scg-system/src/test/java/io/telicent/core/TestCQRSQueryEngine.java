package io.telicent.core;

import io.telicent.smart.cache.configuration.Configurator;
import io.telicent.smart.cache.configuration.sources.PropertiesSource;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdfpatch.RDFChanges;
import org.apache.jena.rdfpatch.system.DatasetGraphChanges;
import org.apache.jena.sparql.algebra.Algebra;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.engine.Plan;
import org.apache.jena.sparql.engine.QueryEngineFactory;
import org.apache.jena.sparql.engine.QueryEngineRegistry;
import org.apache.jena.sparql.util.Context;
import org.apache.jena.sys.JenaSystem;
import org.junit.jupiter.api.*;

import java.util.Properties;

import static org.mockito.Mockito.mock;

public class TestCQRSQueryEngine {

    static {
        JenaSystem.init();
    }

    private static final Query QUERY = QueryFactory.create("SELECT * WHERE { ?s ?p ?o }");
    private static final Op OP = Algebra.compile(QUERY);
    private static final DatasetGraph DATASET = new DatasetGraphChanges(DatasetGraphFactory.empty(), mock(RDFChanges.class));
    private static final Context CONTEXT = new Context();

    @BeforeAll
    public static void setupContext() {
        Context.setCurrentDateTime(CONTEXT);
    }

    @BeforeEach
    public void setup() {
        teardown();
    }

    @AfterAll
    public static void teardown() {
        CQRSQueryEngine.unregister();
        Configurator.reset();
    }

    @Test
    public void givenCQRSEngineRegistered_whenRegisteringAgain_thenNoOp() {
        // Given
        CQRSQueryEngine.register();

        // When and Then
        CQRSQueryEngine.register();
    }

    @Test
    public void givenNoCQRSEngineRegistered_whenObtainingQueryEngineFactory_thenGenericFactoryReturned() {
        // Given and When
        QueryEngineFactory factory = QueryEngineRegistry.findFactory(QUERY, DATASET, CONTEXT);

        // Then
        Assertions.assertFalse(factory instanceof CQRSQueryEngine.CQRSQueryEngineFactory);
    }

    @Test
    public void givenCQRSEngineRegistered_whenRoutingDisabled_thenGenericEngineReturned() {
        // Given
        CQRSQueryEngine.register();

        // When
        QueryEngineFactory factory = QueryEngineRegistry.findFactory(QUERY, DATASET, CONTEXT);

        // Then
        Assertions.assertFalse(factory instanceof CQRSQueryEngine.CQRSQueryEngineFactory);
    }

    @Test
    public void givenCQRSEngineRegistered_whenRoutingEnabled_thenGenericEngineReturned() {
        // Given
        CQRSQueryEngine.register();

        // When
        enableNamedGraphRouting();
        QueryEngineFactory factory = QueryEngineRegistry.findFactory(QUERY, DATASET, CONTEXT);

        // Then
        Assertions.assertTrue(factory instanceof CQRSQueryEngine.CQRSQueryEngineFactory);
    }

    private static void enableNamedGraphRouting() {
        Properties properties = new Properties();
        properties.put(FMod_DistributionLifecycle.ROUTE_TO_NAMED_GRAPHS, true);
        Configurator.setSingleSource(new PropertiesSource(properties));
    }

    @Test
    public void givenCQRSFactory_whenRoutingEnabled_thenAcceptsQueriesForDatasetGraphChanges_andCanProducePlan() {
        // Given
        QueryEngineFactory factory = CQRSQueryEngine.getFactory();

        // When
        enableNamedGraphRouting();

        // Then
        Assertions.assertTrue(factory.accept(QUERY, DATASET, CONTEXT));

        // And
        Plan plan = factory.create(QUERY, DATASET, null, CONTEXT);
        Assertions.assertNotNull(plan);
    }

    @Test
    public void givenCQRSFactory_whenRoutingDisabled_thenDoesNotAcceptsQueriesForDatasetGraphChanges() {
        // Given and When
        QueryEngineFactory factory = CQRSQueryEngine.getFactory();

        // Then
        Assertions.assertFalse(factory.accept(QUERY, DATASET, CONTEXT));
    }

    @Test
    public void givenCQRSFactory_whenRoutingEnabled_thenDoesNotAcceptsQueriesForOtherDataset() {
        // Given
        QueryEngineFactory factory = CQRSQueryEngine.getFactory();

        // When
        enableNamedGraphRouting();

        // Then
        Assertions.assertFalse(factory.accept(QUERY, DatasetGraphFactory.empty(), CONTEXT));
    }

    @Test
    public void givenCQRSFactory_whenRoutingEnabled_thenAcceptsOpsForDatasetGraphChanges_andCanProducePlan() {
        // Given
        QueryEngineFactory factory = CQRSQueryEngine.getFactory();

        // When
        enableNamedGraphRouting();

        // Then
        Assertions.assertTrue(factory.accept(OP, DATASET, CONTEXT));

        // And
        Plan plan = factory.create(OP, DATASET, null, CONTEXT);
        Assertions.assertNotNull(plan);
    }

    @Test
    public void givenCQRSFactory_whenRoutingEnabled_thenDoesNotAcceptsOpsForOtherDatasets() {
        // Given
        QueryEngineFactory factory = CQRSQueryEngine.getFactory();

        // When
        enableNamedGraphRouting();

        // Then
        Assertions.assertFalse(factory.accept(OP, DatasetGraphFactory.empty(), CONTEXT));
    }

    @Test
    public void givenCQRSFactory_whenRoutingDisabled_thenDoesNotAcceptsOpsForDatasetGraphChanges() {
        // Given and When
        QueryEngineFactory factory = CQRSQueryEngine.getFactory();

        // Then
        Assertions.assertFalse(factory.accept(OP, DATASET, CONTEXT));
    }
}
