package io.telicent.core;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.telicent.smart.cache.observability.LibraryVersion;
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

import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;

public class FMod_VersionInfo implements FusekiModule {

    private static final String VERSION = Version.versionForClass(FMod_VersionInfo.class).orElse("<development>");
    private static final List<String> VERSION_LIBRARIES = List.of(
            "scg-system",
            "configurator",
            "distribution-lifecycle",
            "jwt-auth-common",
            "event-source-file",
            "event-source-kafka",
            "event-sources-core",
            "observability-core",
            "projector-driver",
            "projectors-core",
            "data-security-core",
            "common",
            "rocksdb",
            "label-store-rocksdb"
    );

    @Override
    public String name() {
        return "Version Info";
    }

    @Override
    public void prepare(FusekiServer.Builder serverBuilder, Set<String> datasetNames, Model configModel) {
        FmtLog.info(Fuseki.configLog, "Telicent Version Info Module (%s)", VERSION);
        serverBuilder.addServlet("/version-info", new VersionInfoServlet());
    }

    static final class VersionInfoServlet extends HttpServlet {

        private static final List<String> STANDARD_FIELDS =
                List.of("buildEnv", "groupId", "name", "artifactId", "version", "revision", "timestamp");

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            ObjectNode json = OBJECT_MAPPER.createObjectNode();
            for (String library : VERSION_LIBRARIES) {
                Properties properties = LibraryVersion.getProperties(library);
                if (properties.isEmpty()) {
                    continue;
                }
                json.set(library, toJson(properties));
            }

            response.setStatus(HttpServletResponse.SC_OK);
            ServletUtils.processResponse(response, json);
        }

        private static ObjectNode toJson(Properties properties) {
            ObjectNode json = OBJECT_MAPPER.createObjectNode();

            for (String key : STANDARD_FIELDS) {
                putIfPresent(json, properties, key);
            }

            TreeSet<String> extras = new TreeSet<>(properties.stringPropertyNames());
            extras.removeAll(STANDARD_FIELDS);
            for (String key : extras) {
                json.put(key, properties.getProperty(key));
            }

            return json;
        }

        private static void putIfPresent(ObjectNode json, Properties properties, String key) {
            String value = properties.getProperty(key);
            if (value != null) {
                json.put(key, value);
            }
        }
    }
}
