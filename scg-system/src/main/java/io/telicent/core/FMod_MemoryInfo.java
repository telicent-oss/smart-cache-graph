package io.telicent.core;

import io.telicent.smart.cache.configuration.Configurator;
import io.telicent.smart.cache.observability.RuntimeInfo;
import io.telicent.smart.cache.projectors.utils.PeriodicAction;
import org.apache.jena.atlas.lib.Version;
import org.apache.jena.atlas.logging.FmtLog;
import org.apache.jena.fuseki.Fuseki;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiAutoModule;
import org.apache.jena.rdf.model.Model;

import java.time.Duration;
import java.util.Set;

/**
 * A Fuseki module that periodically reports memory usage information to the logs
 */
public class FMod_MemoryInfo implements FusekiAutoModule {

    private static final String VERSION = Version.versionForClass(FMod_MemoryInfo.class).orElse("<development>");

    @Override
    public String name() {
        return "Memory Info Reporting Module";
    }

    @Override
    public void prepare(FusekiServer.Builder serverBuilder, Set<String> datasetNames, Model configModel) {
        FmtLog.info(Fuseki.configLog, "Memory Info Reporting Module (%s)", VERSION);

        long interval = Configurator.get("MEMORY_INFO_INTERVAL", Long::parseLong, 5L);
        if (interval > 0) {
            FmtLog.info(Fuseki.configLog, "Configured to report memory information every %d minutes", interval);
            PeriodicAction memoryReporter =
                    new PeriodicAction(() -> RuntimeInfo.printMemoryInfo(Fuseki.configLog), Duration.ofMinutes(5));
            serverBuilder.setServletAttribute(this.getClass().getCanonicalName(), memoryReporter);
            memoryReporter.autoTrigger();
        } else {
            Fuseki.configLog.info("Memory information reporting disabled, MEMORY_INFO_INTERVAL set to negative value");
        }
    }

    @Override
    public void serverStopped(FusekiServer server) {
        PeriodicAction memoryReporter =
                (PeriodicAction) server.getServletContext().getAttribute(this.getClass().getCanonicalName());
        if (memoryReporter != null) {
            memoryReporter.cancelAutoTrigger();
        }
    }
}
