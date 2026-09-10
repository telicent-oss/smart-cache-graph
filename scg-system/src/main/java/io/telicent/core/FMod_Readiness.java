package io.telicent.core;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.telicent.servlet.auth.jwt.JwtServletConstants;
import io.telicent.utils.ServletUtils;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.atlas.lib.Version;
import org.apache.jena.atlas.logging.FmtLog;
import org.apache.jena.fuseki.Fuseki;
import org.apache.jena.fuseki.kafka.FKS;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiModule;
import org.apache.jena.rdf.model.Model;

import java.util.Set;

import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;

/**
 * Modules that provides a proper health checking readiness probe for Smart Cache Graph
 */
public class FMod_Readiness implements FusekiModule {

    private static final String VERSION =
            Version.versionForClass(FMod_Readiness.class).orElse("<development>");

    @Override
    public String name() {
        return "Readiness Probe";
    }

    @Override
    public void prepare(FusekiServer.Builder serverBuilder, Set<String> datasetNames, Model configModel) {
        FmtLog.info(Fuseki.configLog, "Telicent Readiness Probe Module (%s)", VERSION);
        serverBuilder.addServlet("/$/ready", new ReadinessServlet());
    }

    /**
     * Readiness servlet that generates the readiness response
     * <p>
     * This will either be a {@code 200 OK} or a {@code 503 Service Unavailable} depending on the assessed healthiness
     * of the service.  The response follows our standard JSON format with a {@code healthy} field indicating
     * {@code true}/{@code false}, a {@code reasons} array listing any reasons for the calculated health status, and a
     * {@code config} object listing relevant configuration.
     * </p>
     */
    static final class ReadinessServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            // Obtain the information used to inform the readiness health check response
            DistributionLifecycleReadiness.Snapshot readiness =
                    DistributionLifecycleReadiness.getInstance().snapshot();
            int failedKafkaThreads = FKS.failed();
            int runningKafkaThreads = FKS.running();
            int launchedKafkaThreads = FKS.launched();
            boolean healthy = isHealthy(readiness, failedKafkaThreads);

            // Build a response in the format of our standardised Health Check response
            ObjectNode json = OBJECT_MAPPER.createObjectNode();
            json.put("healthy", healthy);
            ArrayNode reasons = json.putArray("reasons");
            reasons.add(readiness.reason());
            if (failedKafkaThreads > 0) {
                reasons.add(String.format("%d Kafka polling threads have failed (%d launched, %d running, %d failed)",
                                          failedKafkaThreads, launchedKafkaThreads, runningKafkaThreads,
                                          failedKafkaThreads));
            }

            ObjectNode config = json.putObject("config");
            config.put("filteringEnabled", readiness.filteringEnabled());
            config.put("trackerEnabled", readiness.trackerEnabled());
            config.put("state", readiness.state().name());
            config.put("launchedKafkaPollThreads", launchedKafkaThreads);
            config.put("failedKafkaPollThreads", failedKafkaThreads);
            config.put("runningKafkaPollThreads", runningKafkaThreads);
            Object jwtVerifier = request.getServletContext().getAttribute(JwtServletConstants.ATTRIBUTE_JWT_VERIFIER);
            config.put("authentication", jwtVerifier != null ? jwtVerifier.toString() : "disabled");

            response.setStatus(
                    healthy ? HttpServletResponse.SC_OK : HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            ServletUtils.processResponse(response, json);
        }

        /**
         * Makes a determination whether the service should be considered healthy, this ultimately determines whether
         * the readiness servlet returns a 200 OK or 503 Service Unavailable
         *
         * @param readiness          Distribution Lifecycle readiness information
         * @param failedKafkaThreads Number of reported failed Kafka threads (if any)
         * @return True if healthy, false otherwise
         */
        private static boolean isHealthy(DistributionLifecycleReadiness.Snapshot readiness, int failedKafkaThreads) {
            return readiness.ready() && failedKafkaThreads == 0;
        }
    }
}
