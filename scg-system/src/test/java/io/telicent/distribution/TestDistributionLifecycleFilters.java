//package io.telicent.distribution;
//
//import io.telicent.jena.abac.ABAC;
//import io.telicent.jena.abac.DatasetFilterProvider;
//import io.telicent.jena.abac.DefaultDatasetFilterProvider;
//import io.telicent.jena.abac.SysABAC;
//import io.telicent.jena.abac.attributes.syntax.AEX;
//import io.telicent.jena.abac.core.AttributesStoreLocal;
//import io.telicent.jena.abac.core.DatasetGraphABAC;
//import io.telicent.jena.abac.labels.Labels;
//import org.apache.jena.sparql.core.DatasetGraphFactory;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//
//import java.nio.file.Path;
//
//import static org.junit.jupiter.api.Assertions.assertFalse;
//import static org.junit.jupiter.api.Assertions.assertInstanceOf;
//import static org.junit.jupiter.api.Assertions.assertNull;
//import static org.junit.jupiter.api.Assertions.assertSame;
//import static org.junit.jupiter.api.Assertions.assertTrue;
//
//class TestDistributionLifecycleFilters {
//
//    private static final String STATE_FILE_PATH = "/tmp/scg-test-lifecycle-state.json";
//    private static final String APPLICATION_ID = "some-application-id";
//
//    private DatasetGraphABAC dataset;
//
//    @BeforeEach
//    void setUp() {
//        this.dataset = ABAC.authzDataset(DatasetGraphFactory.createTxnMem(),
//                                         AEX.strALLOW,
//                                         Labels.createLabelsStoreMem(),
//                                         SysABAC.denyLabel,
//                                         new AttributesStoreLocal());
//    }
//
//    @Test
//    void installIfConfigured_returnsFalse_whenLifecycleFilterAlreadyInstalled() {
//        DistributionLifecycleDatasetFilterProvider existing = new DistributionLifecycleDatasetFilterProvider(
//                new DistributionLifecycleStateFile(Path.of(STATE_FILE_PATH), null), null);
//        dataset.setFilterProvider(existing);
//
//        boolean installed = DistributionLifecycleFilters.installIfConfigured(dataset, APPLICATION_ID, STATE_FILE_PATH);
//
//        assertFalse(installed, "Should not reinstall when a lifecycle filter is already present");
//        assertSame(existing, dataset.getFilterProvider(),
//                   "Existing lifecycle filter provider should be left untouched");
//    }
//
//    @Test
//    void installIfConfigured_returnsTrue_whenInstallingWithExplicitArguments() {
//        boolean installed = DistributionLifecycleFilters.installIfConfigured(dataset, APPLICATION_ID, STATE_FILE_PATH);
//
//        assertTrue(installed, "Should install when explicit lifecycle arguments are provided");
//        assertInstanceOf(DistributionLifecycleDatasetFilterProvider.class, dataset.getFilterProvider(),
//                         "Installed filter provider should be the lifecycle one");
//    }
//
//    @Test
//    void installIfConfigured_returnsTrue_andInstallsFreshFilter_whenStateFileConfigured() {
//        assertNull(dataset.getFilterProvider(), "Pre-condition: dataset has no filter provider");
//
//        boolean installed = DistributionLifecycleFilters.installIfConfigured(dataset, APPLICATION_ID, STATE_FILE_PATH);
//
//        assertTrue(installed, "Should install when state file is configured");
//        assertInstanceOf(DistributionLifecycleDatasetFilterProvider.class, dataset.getFilterProvider(),
//                         "Installed filter provider should be the lifecycle one");
//    }
//
//    @Test
//    void installIfConfigured_returnsTrue_andWrapsExistingDelegate_whenStateFileConfigured() {
//        DatasetFilterProvider existingDelegate = new DefaultDatasetFilterProvider();
//        dataset.setFilterProvider(existingDelegate);
//
//        boolean installed = DistributionLifecycleFilters.installIfConfigured(dataset, APPLICATION_ID, STATE_FILE_PATH);
//
//        assertTrue(installed, "Should install (wrapping the existing delegate) when state file configured");
//        assertInstanceOf(DistributionLifecycleDatasetFilterProvider.class, dataset.getFilterProvider(),
//                         "Installed filter provider should be the lifecycle one");
//        // The integration test processorSCG_namedGraph_lifecycleFilterComposesWithExistingDatasetFilterProvider
//        // in AbstractSmartCacheGraphSinkTests verifies that the delegate is actually invoked during query;
//        // here we only need to verify that the wrapping install path returned true and replaced the field.
//    }
//
//    @Test
//    void installIfConfigured_returnsTrue_whenApplicationIdIsNull() {
//        boolean installed = DistributionLifecycleFilters.installIfConfigured(dataset, null, STATE_FILE_PATH);
//
//        assertTrue(installed, "Application id is optional - install should still succeed");
//        assertInstanceOf(DistributionLifecycleDatasetFilterProvider.class, dataset.getFilterProvider());
//    }
//}