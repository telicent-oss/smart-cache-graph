package io.telicent.core;

import io.telicent.smart.cache.configuration.Configurator;
import org.apache.jena.atlas.lib.Version;
import org.apache.jena.atlas.logging.FmtLog;
import org.apache.jena.fuseki.Fuseki;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiModule;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;

/**
 * A Fuseki module that overrides the Jetty response output buffer size.
 * <p>
 * Fuseki configures Jetty with a 5 MiB output buffer, which is larger than the largest buffer Jetty's default
 * {@code ArrayByteBufferPool} will pool (64 KiB). Every response therefore allocates a new 5 MiB direct buffer that is
 * only freed when the garbage collector runs, which drives native memory growth and, once
 * {@code -XX:MaxDirectMemorySize} is reached, {@code System.gc()} full collections.
 * </p>
 * <p>
 * Set {@value #ENV_OUTPUT_BUFFER_SIZE} (bytes) to override the size; when unset Fuseki's default is left unchanged.
 * Responses larger than the buffer are streamed (chunked) once the buffer fills, so an error that occurs after that
 * point can no longer change the HTTP status code.
 * </p>
 */
public class FMod_JettyOutputBuffer implements FusekiModule {

    /**
     * Environment variable/system property controlling the Jetty output buffer size in bytes
     */
    public static final String ENV_OUTPUT_BUFFER_SIZE = "JETTY_OUTPUT_BUFFER_SIZE";

    private static final String VERSION =
            Version.versionForClass(FMod_JettyOutputBuffer.class).orElse("<development>");

    @Override
    public String name() {
        return "Jetty Output Buffer";
    }

    @Override
    public void serverBeforeStarting(FusekiServer server) {
        int size = Configurator.get(ENV_OUTPUT_BUFFER_SIZE, Integer::parseInt, -1);
        if (size <= 0) {
            return;
        }
        for (Connector connector : server.getJettyServer().getConnectors()) {
            HttpConnectionFactory factory = connector.getConnectionFactory(HttpConnectionFactory.class);
            if (factory != null) {
                HttpConfiguration config = factory.getHttpConfiguration();
                FmtLog.info(Fuseki.configLog, "%s Module (%s): output buffer size %d -> %d bytes", name(), VERSION,
                            config.getOutputBufferSize(), size);
                config.setOutputBufferSize(size);
                config.setOutputAggregationSize(Math.min(config.getOutputAggregationSize(), size));
            }
        }
    }
}
