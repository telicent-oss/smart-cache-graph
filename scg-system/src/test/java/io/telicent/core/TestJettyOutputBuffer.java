package io.telicent.core;

import io.telicent.smart.caches.configuration.auth.AuthConstants;
import io.telicent.smart.cache.configuration.Configurator;
import io.telicent.smart.cache.configuration.sources.PropertiesSource;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static io.telicent.LibTestsSCG.disableInitialCompaction;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestJettyOutputBuffer {

    private FusekiServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop();
        }
        Configurator.reset();
    }

    private static int startAndGetOutputBufferSize(TestJettyOutputBuffer test, Properties properties) {
        properties.put(AuthConstants.ENV_JWKS_URL, AuthConstants.AUTH_DISABLED);
        Configurator.setSingleSource(new PropertiesSource(properties));
        disableInitialCompaction();
        test.server = SmartCacheGraph.serverBuilder().port(0).add("/ds", DatasetGraphFactory.createTxnMem()).build().start();
        return test.server.getJettyServer().getConnectors()[0].getConnectionFactory(HttpConnectionFactory.class)
                          .getHttpConfiguration().getOutputBufferSize();
    }

    @Test
    public void givenNoOverride_whenStarting_thenFusekiDefaultIsKept() {
        assertEquals(5 * 1024 * 1024, startAndGetOutputBufferSize(this, new Properties()));
    }

    @Test
    public void givenOverride_whenStarting_thenOutputBufferSizeIsApplied() {
        Properties properties = new Properties();
        properties.put(FMod_JettyOutputBuffer.ENV_OUTPUT_BUFFER_SIZE, "32768");
        assertEquals(32768, startAndGetOutputBufferSize(this, properties));
    }

    @Test
    public void givenInvalidOverride_whenStarting_thenFusekiDefaultIsKept() {
        Properties properties = new Properties();
        properties.put(FMod_JettyOutputBuffer.ENV_OUTPUT_BUFFER_SIZE, "0");
        assertEquals(5 * 1024 * 1024, startAndGetOutputBufferSize(this, properties));
    }
}
