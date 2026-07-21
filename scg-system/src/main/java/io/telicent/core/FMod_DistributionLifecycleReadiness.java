package io.telicent.core;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.telicent.utils.ServletUtils;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.atlas.lib.Version;
import org.apache.jena.atlas.logging.FmtLog;
import org.apache.jena.fuseki.Fuseki;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiModule;
import org.apache.jena.rdf.model.Model;

import java.io.IOException;
import java.util.Set;

import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;

public class FMod_DistributionLifecycleReadiness implements FusekiModule {

    private static final String VERSION =
            Version.versionForClass(FMod_DistributionLifecycleReadiness.class).orElse("<development>");

    @Override
    public String name() {
        return "Distribution Lifecycle Readiness";
    }

    @Override
    public void prepare(FusekiServer.Builder serverBuilder, Set<String> datasetNames, Model configModel) {
        FmtLog.info(Fuseki.configLog, "Telicent Distribution Lifecycle Readiness Module (%s)", VERSION);
        serverBuilder.addServlet("/$/ready", new LifecycleReadinessServlet());
    }

    static final class LifecycleReadinessServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            DistributionLifecycleReadiness.Snapshot readiness =
                    DistributionLifecycleReadiness.getInstance().snapshot();

            ObjectNode json = OBJECT_MAPPER.createObjectNode();
            json.put("ready", readiness.ready());

            ObjectNode lifecycle = json.putObject("lifecycle");
            lifecycle.put("filteringEnabled", readiness.filteringEnabled());
            lifecycle.put("trackerEnabled", readiness.trackerEnabled());
            lifecycle.put("state", readiness.state().name());
            lifecycle.put("reason", readiness.reason());

            response.setStatus(readiness.ready() ? HttpServletResponse.SC_OK : HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            ServletUtils.processResponse(response, json);
        }
    }
}
