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

package io.telicent.core;

import io.telicent.jena.abac.ABAC;
import io.telicent.jena.abac.SysABAC;
import io.telicent.jena.abac.attributes.syntax.AEX;
import io.telicent.jena.abac.core.DatasetGraphABAC;
import io.telicent.jena.abac.labels.Labels;
import io.telicent.smart.cache.configuration.Configurator;
import io.telicent.smart.cache.configuration.sources.PropertiesSource;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.server.DataAccessPoint;
import org.apache.jena.fuseki.server.DataAccessPointRegistry;
import org.apache.jena.fuseki.server.DataService;
import org.apache.jena.fuseki.server.Operation;
import org.apache.jena.kafka.KConnectorDesc;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.core.DatasetGraphWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestFModDistributionLifecycle {

    @AfterEach
    void cleanup() {
        Configurator.reset();
    }

    private static DatasetGraphABAC newAbacDataset() {
        return ABAC.authzDataset(DatasetGraphFactory.createTxnMem(), AEX.strALLOW, Labels.createLabelsStoreMem(),
                                 SysABAC.allowLabel, null);
    }

    @Test
    void abacDatasets_returnsOnlyAbacDatasets() {
        // given
        DatasetGraphABAC abac = newAbacDataset();
        DataService abacService = DataService.newBuilder(abac).addEndpoint(Operation.Query).build();
        FusekiServer server = FusekiServer.create()
                                          .port(0)
                                          .add("/abac", abacService)
                                          .add("/plain", DatasetGraphFactory.createTxnMem())
                                          .build();
        server.start();
        try {
            // when
            Collection<DatasetGraphWrapper> datasets = FMod_DistributionLifecycle.wrappedDatasets(server);

            // then
            assertEquals(1, datasets.size(), "Only the ABAC dataset should be returned");
            assertTrue(datasets.contains(abac));
        } finally {
            server.stop();
        }
    }

    @Test
    void configured_routeToNamedGraphsDisabled_doesNotInstallLifecycleFilter() {
        // given
        Configurator.setSingleSource(new PropertiesSource(new Properties()));
        DatasetGraphABAC abac = mock(DatasetGraphABAC.class);
        DataService abacService = mock(DataService.class);
        DataAccessPoint dap = mock(DataAccessPoint.class);
        DataAccessPointRegistry dapRegistry = mock(DataAccessPointRegistry.class);
        when(abacService.getDataset()).thenReturn(abac);
        when(dap.getDataService()).thenReturn(abacService);
        when(dapRegistry.accessPoints()).thenReturn(List.of(dap));
        FMod_DistributionLifecycle module = new FMod_DistributionLifecycle();

        // when
        module.configured(null, dapRegistry, null);

        // then
        verify(abac, never()).setFilterProvider(any());
    }

    @Test
    void connectionProperties_extractsOnlySecurityAndTlsProperties() {
        // given
        Properties props = new Properties();
        props.setProperty("security.protocol", "SASL_SSL");
        props.setProperty("sasl.mechanism", "PLAIN");
        props.setProperty("ssl.truststore.location", "/etc/kafka/truststore.jks");
        props.setProperty("group.id", "connector-group");
        props.setProperty("max.poll.records", "1000");
        props.setProperty("bootstrap.servers", "localhost:9092");
        props.setProperty("key.deserializer", "org.apache.kafka.common.serialization.BytesDeserializer");
        KConnectorDesc connector =
                new KConnectorDesc(List.of("data-topic"), "localhost:9092", "/ds", null, true, false, null, props);

        // when
        Properties connection = FMod_DistributionLifecycle.connectionProperties(connector);

        // then
        assertEquals(3, connection.size(), "Only the security/TLS properties should be retained");
        assertEquals("SASL_SSL", connection.getProperty("security.protocol"));
        assertEquals("PLAIN", connection.getProperty("sasl.mechanism"));
        assertEquals("/etc/kafka/truststore.jks", connection.getProperty("ssl.truststore.location"));
        assertFalse(connection.containsKey("group.id"), "Consumer group must not be inherited");
        assertFalse(connection.containsKey("max.poll.records"), "Connector tuning must not be inherited");
        assertFalse(connection.containsKey("key.deserializer"), "Connector deserializers must not be inherited");
    }

    @Test
    void disabledByDefault_registersNoTracker() {
        // given
        Configurator.setSingleSource(new PropertiesSource(new Properties()));
        FusekiServer server = FusekiServer.create().port(0).add("/ds", DatasetGraphFactory.createTxnMem()).build();
        server.start();
        FMod_DistributionLifecycle module = new FMod_DistributionLifecycle();
        try {
            // when
            module.serverBeforeStarting(server);

            // then
            assertFalse(module.isRunning(), "No tracker should be started when the feature is disabled");
        } finally {
            module.serverStopped(server);
            server.stop();
        }
    }

    @Test
    void routeToNamedGraphsDisabled_registersNoTracker_evenWhenLifecycleIsEnabled() {
        // given
        Properties properties = new Properties();
        properties.setProperty(FMod_DistributionLifecycle.ENABLE_DISTRIBUTION_LIFECYCLE, "true");
        Configurator.setSingleSource(new PropertiesSource(properties));
        FusekiServer server = FusekiServer.create().port(0).add("/ds", DatasetGraphFactory.createTxnMem()).build();
        server.start();
        FMod_DistributionLifecycle module = new FMod_DistributionLifecycle();
        try {
            // when
            module.serverBeforeStarting(server);

            // then
            assertFalse(module.isRunning(),
                        "No tracker should be started when named-graph routing is disabled");
        } finally {
            module.serverStopped(server);
            server.stop();
        }
    }

    private static KConnectorDesc connectorWithBootstrap(String bootstrapServers) {
        return new KConnectorDesc(List.of("data-topic"), bootstrapServers, "/ds", null, true, false, null,
                                  new Properties());
    }

    @Test
    void selectBootstrapServers_explicitValueWins() {
        // given
        Collection<KConnectorDesc> connectors = List.of(connectorWithBootstrap("connector-host:9092"));

        // when
        String selected = FMod_DistributionLifecycle.selectBootstrapServers(connectors, "explicit-host:9092");

        // then
        assertEquals("explicit-host:9092", selected);
    }

    @Test
    void selectBootstrapServers_singleConnectorClusterIsUsed() {
        // given
        Collection<KConnectorDesc> connectors =
                List.of(connectorWithBootstrap("shared-host:9092"), connectorWithBootstrap("shared-host:9092"));

        // when
        String selected = FMod_DistributionLifecycle.selectBootstrapServers(connectors, null);

        // then
        assertEquals("shared-host:9092", selected);
    }

    @Test
    void selectBootstrapServers_multipleClustersIsAmbiguousAndRefused() {
        // given
        Collection<KConnectorDesc> connectors =
                List.of(connectorWithBootstrap("host-a:9092"), connectorWithBootstrap("host-b:9092"));

        // when
        String selected = FMod_DistributionLifecycle.selectBootstrapServers(connectors, null);

        // then
        assertNull(selected, "Bootstrap servers must not be guessed when connectors target different clusters");
    }

    @Test
    void selectBootstrapServers_noConnectorsYieldsNull() {
        // given, when, then
        assertNull(FMod_DistributionLifecycle.selectBootstrapServers(List.of(), null));
    }

    @Test
    void applicationId_defaultsToSmartCacheGraph_andIsConfigurable() {
        // given
        Configurator.setSingleSource(new PropertiesSource(new Properties()));
        // when, then
        assertEquals(FMod_DistributionLifecycle.DEFAULT_APP_ID, FMod_DistributionLifecycle.applicationId(),
                     "Application id should default to the shared default");

        // given
        Properties properties = new Properties();
        properties.setProperty(FMod_DistributionLifecycle.DISTRIBUTION_LIFECYCLE_APP_ID, "custom-app");
        Configurator.setSingleSource(new PropertiesSource(properties));
        // when, then
        assertEquals("custom-app", FMod_DistributionLifecycle.applicationId(),
                     "Application id should be configurable");
    }

    @Test
    void consumerGroup_defaultsFromApplicationId_andIsConfigurable() {
        // given
        Configurator.setSingleSource(new PropertiesSource(new Properties()));

        // when, then
        assertEquals(FMod_DistributionLifecycle.DEFAULT_CONSUMER_GROUP + "-" +
                     FMod_DistributionLifecycle.DEFAULT_APP_ID,
                     FMod_DistributionLifecycle.consumerGroup(),
                     "Consumer group should default from the application id");

        // given
        Properties properties = new Properties();
        properties.setProperty(FMod_DistributionLifecycle.DISTRIBUTION_LIFECYCLE_APP_ID, "custom-app");
        Configurator.setSingleSource(new PropertiesSource(properties));

        // when, then
        assertEquals(FMod_DistributionLifecycle.DEFAULT_CONSUMER_GROUP + "-custom-app",
                     FMod_DistributionLifecycle.consumerGroup(),
                     "Consumer group should incorporate the configured application id");

        // given
        properties = new Properties();
        properties.setProperty(FMod_DistributionLifecycle.DISTRIBUTION_LIFECYCLE_APP_ID, "custom-app");
        properties.setProperty(FMod_DistributionLifecycle.DISTRIBUTION_LIFECYCLE_CONSUMER_GROUP, "explicit-group");
        Configurator.setSingleSource(new PropertiesSource(properties));

        // when, then
        assertEquals("explicit-group", FMod_DistributionLifecycle.consumerGroup(),
                     "Explicit consumer group should override the derived default");
    }

    @Test
    void stateFilePath_defaultsAndIsConfigurable() {
        // given
        Configurator.setSingleSource(new PropertiesSource(new Properties()));

        // when, then
        assertEquals(FMod_DistributionLifecycle.DEFAULT_STATE_FILE, FMod_DistributionLifecycle.stateFilePath(),
                     "State file should default to the shared default");

        // given
        Properties properties = new Properties();
        properties.setProperty(FMod_DistributionLifecycle.DISTRIBUTION_LIFECYCLE_STATE_FILE, "/tmp/custom.state");
        Configurator.setSingleSource(new PropertiesSource(properties));

        // when, then
        assertEquals("/tmp/custom.state", FMod_DistributionLifecycle.stateFilePath(),
                     "State file should be configurable");
    }
}
