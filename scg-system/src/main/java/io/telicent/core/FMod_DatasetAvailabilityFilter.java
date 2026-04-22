package io.telicent.core;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.atlas.lib.Version;
import org.apache.jena.atlas.logging.FmtLog;
import org.apache.jena.fuseki.Fuseki;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiModule;
import org.apache.jena.fuseki.server.DataAccessPoint;
import org.apache.jena.fuseki.server.DataAccessPointRegistry;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.tdb2.store.DatasetGraphSwitchable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class FMod_DatasetAvailabilityFilter implements FusekiModule {

    private static final String VERSION =
            Version.versionForClass(FMod_DatasetAvailabilityFilter.class).orElse("<development>");
    private static final String LABELS_PREFIX = "/$/labels/";
    private volatile Set<String> datasetNames = Set.of();
    private volatile Map<String, DatasetGraphSwitchable> datasetsByPath = Map.of();

    @Override
    public String name() {
        return "Dataset Availability Filter";
    }

    @Override
    public void prepare(FusekiServer.Builder serverBuilder, Set<String> datasetNames, Model configModel) {
        FmtLog.info(Fuseki.configLog, "Telicent Dataset Availability Filter Module (%s)", VERSION);
        this.datasetNames = Set.copyOf(new HashSet<>(datasetNames));
        serverBuilder.addFilter("/*", new DatasetAvailabilityFilter());
    }

    @Override
    public void configured(FusekiServer.Builder serverBuilder, DataAccessPointRegistry dapRegistry, Model configModel) {
        Map<String, DatasetGraphSwitchable> datasets = new LinkedHashMap<>();
        if (dapRegistry != null) {
            for (DataAccessPoint dap : dapRegistry.accessPoints()) {
                DatasetGraphSwitchable dsg = FMod_InitialCompaction.getTDB2(dap.getDataService().getDataset());
                if (dsg != null) {
                    datasets.put(dap.getName(), dsg);
                }
            }
        }
        this.datasetsByPath = Map.copyOf(datasets);
    }

    static Optional<String> datasetNameForPath(String requestUri, Set<String> datasetNames) {
        if (requestUri == null || requestUri.isBlank() || "/".equals(requestUri)) {
            return Optional.empty();
        }

        if (requestUri.startsWith(LABELS_PREFIX)) {
            return datasetNameFromSuffix(requestUri.substring(LABELS_PREFIX.length()), datasetNames);
        }

        if (requestUri.startsWith("/$/")) {
            return Optional.empty();
        }

        return datasetNameFromSuffix(requestUri.substring(1), datasetNames);
    }

    static Optional<DatasetGraphSwitchable> datasetForPath(String requestUri,
                                                           Set<String> datasetNames,
                                                           Map<String, DatasetGraphSwitchable> datasetsByPath) {
        return datasetNameForPath(requestUri, datasetNames).map(datasetsByPath::get);
    }

    private static Optional<String> datasetNameFromSuffix(String suffix, Set<String> datasetNames) {
        String firstSegment = firstPathSegment(suffix);
        if (firstSegment == null) {
            return Optional.empty();
        }

        String datasetName = "/" + firstSegment;
        if (!datasetNames.contains(datasetName)) {
            return Optional.empty();
        }
        return Optional.of(datasetName);
    }

    private static String firstPathSegment(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }

        int slash = path.indexOf('/');
        String segment = slash >= 0 ? path.substring(0, slash) : path;
        return segment.isBlank() ? null : segment;
    }

    private final class DatasetAvailabilityFilter implements Filter {

        @Override
        public void init(FilterConfig filterConfig) {
            FmtLog.debug(Fuseki.configLog, "Initiating Telicent Dataset Availability Filter Module");
        }

        @Override
        public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
                throws IOException, ServletException {
            HttpServletRequest request = (HttpServletRequest) servletRequest;
            HttpServletResponse response = (HttpServletResponse) servletResponse;

            Optional<String> datasetName = datasetNameForPath(request.getRequestURI(), datasetNames);
            DatasetGraphSwitchable dataset = datasetForPath(request.getRequestURI(), datasetNames, datasetsByPath)
                    .orElse(null);
            if (datasetName.isPresent() && dataset != null && FMod_InitialCompaction.isCompactionInProgress(dataset)) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                response.setContentType("application/json");
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.getWriter().write("{\"error\":\"Dataset " + datasetName.get()
                        + " is temporarily unavailable while compaction is in progress.\"}");
                return;
            }

            chain.doFilter(servletRequest, servletResponse);
        }

        @Override
        public void destroy() {
            FmtLog.debug(Fuseki.configLog, "Destroying Telicent Dataset Availability Filter Module");
        }
    }
}
