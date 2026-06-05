package io.telicent.distribution;

import io.telicent.core.FMod_DistributionLifecycleFilter;
import io.telicent.jena.abac.ABAC;
import io.telicent.jena.abac.DatasetFilterProvider;
import io.telicent.jena.abac.DefaultDatasetFilterProvider;
import io.telicent.jena.abac.SysABAC;
import io.telicent.jena.abac.attributes.syntax.AEX;
import io.telicent.jena.abac.core.AttributesStoreLocal;
import io.telicent.jena.abac.core.DatasetGraphABAC;
import io.telicent.jena.abac.labels.Labels;
import io.telicent.smart.cache.configuration.Configurator;
import io.telicent.smart.cache.configuration.sources.PropertiesSource;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestDistributionLifecycleFilters {

    private static final String STATE_FILE_PATH = "/tmp/scg-test-lifecycle-state.json";

    private DatasetGraphABAC dataset;

    @BeforeEach
    void setUp() {
        Configurator.reset();
        this.dataset = ABAC.authzDataset(DatasetGraphFactory.createTxnMem(),
                                         AEX.strALLOW,
                                         Labels.createLabelsStoreMem(),
                                         SysABAC.denyLabel,
                                         new AttributesStoreLocal());
    }

    @AfterEach
    void tearDown() {
        Configurator.reset();
    }

    @Test
    void installIfConfigured_returnsFalse_whenLifecycleFilterAlreadyInstalled() {
        configureStateFile(STATE_FILE_PATH);
        DistributionLifecycleDatasetFilterProvider existing = new DistributionLifecycleDatasetFilterProvider(
                new DistributionLifecycleStateFile(Path.of(STATE_FILE_PATH), null), null);
        dataset.setFilterProvider(existing);

        boolean installed = DistributionLifecycleFilters.installIfConfigured(dataset);

        assertFalse(installed, "Should not reinstall when a lifecycle filter is already present");
        assertSame(existing, dataset.getFilterProvider(),
                   "Existing lifecycle filter provider should be left untouched");
    }

    @Test
    void installIfConfigured_returnsFalse_whenStateFileNotConfigured() {
        // Note: deliberately no configureStateFile() call here.

        boolean installed = DistributionLifecycleFilters.installIfConfigured(dataset);

        assertFalse(installed, "Should not install when no state file path is configured");
        assertNull(dataset.getFilterProvider(),
                   "No filter provider should have been installed when state file is unconfigured");
    }

    @Test
    void installIfConfigured_returnsTrue_andInstallsFreshFilter_whenStateFileConfigured() {
        configureStateFile(STATE_FILE_PATH);
        assertNull(dataset.getFilterProvider(), "Pre-condition: dataset has no filter provider");

        boolean installed = DistributionLifecycleFilters.installIfConfigured(dataset);

        assertTrue(installed, "Should install when state file is configured");
        assertInstanceOf(DistributionLifecycleDatasetFilterProvider.class, dataset.getFilterProvider(),
                         "Installed filter provider should be the lifecycle one");
    }

    @Test
    void installIfConfigured_returnsTrue_andWrapsExistingDelegate_whenStateFileConfigured() {
        configureStateFile(STATE_FILE_PATH);
        DatasetFilterProvider existingDelegate = new DefaultDatasetFilterProvider();
        dataset.setFilterProvider(existingDelegate);

        boolean installed = DistributionLifecycleFilters.installIfConfigured(dataset);

        assertTrue(installed, "Should install (wrapping the existing delegate) when state file configured");
        assertInstanceOf(DistributionLifecycleDatasetFilterProvider.class, dataset.getFilterProvider(),
                         "Installed filter provider should be the lifecycle one");
        // The integration test processorSCG_namedGraph_lifecycleFilterComposesWithExistingDatasetFilterProvider
        // in AbstractSmartCacheGraphSinkTests verifies that the delegate is actually invoked during query;
        // here we only need to verify that the wrapping install path returned true and replaced the field.
    }

    @Test
    void installIfConfigured_returnsTrue_andInstallsFreshFilter_whenApplicationIdAlsoConfigured() {
        Properties props = new Properties();
        props.setProperty(FMod_DistributionLifecycleFilter.DISTRIBUTION_LIFECYCLE_STATE_FILE, STATE_FILE_PATH);
        props.setProperty(FMod_DistributionLifecycleFilter.DISTRIBUTION_LIFECYCLE_APP_ID, "some-application-id");
        Configurator.addSource(new PropertiesSource(props));

        boolean installed = DistributionLifecycleFilters.installIfConfigured(dataset);

        assertTrue(installed, "Application id is optional - install should still succeed");
        assertInstanceOf(DistributionLifecycleDatasetFilterProvider.class, dataset.getFilterProvider());
    }

    private static void configureStateFile(String path) {
        Properties props = new Properties();
        props.setProperty(FMod_DistributionLifecycleFilter.DISTRIBUTION_LIFECYCLE_STATE_FILE, path);
        Configurator.addSource(new PropertiesSource(props));
    }
}