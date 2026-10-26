package io.telicent.core;

import io.telicent.jena.graphql.fuseki.SysGraphQL;
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
import org.apache.jena.fuseki.server.DataService;
import org.apache.jena.fuseki.server.EndpointSet;
import org.apache.jena.fuseki.server.Operation;
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

    /**
     * {@code Retry-After} (seconds) sent with the 503 for requests rejected while an RO Compaction switches over
     */
    static final String RETRY_AFTER_SWITCHING_SECONDS = "1";


    private static final String VERSION =
            Version.versionForClass(FMod_DatasetAvailabilityFilter.class).orElse("<development>");
    private static final String LABELS_PREFIX = "/$/labels/";
    private volatile Set<String> datasetNames = Set.of();
    private volatile Map<String, DatasetGraphSwitchable> datasetsByPath = Map.of();
    private volatile Map<String, DataService> servicesByPath = Map.of();

    /**
     * Fuseki operations that only read the dataset. GraphQL queries run in a read transaction and the GraphQL endpoint
     * has no mutations, so it is a read too.
     */
    static final Set<Operation> READ_OPERATIONS =
            Set.of(Operation.Query, Operation.GSP_R, Operation.GSP_Direct_R, Operation.PREFIXES_R,
                   SysGraphQL.OP_GRAPHQL);

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
        Map<String, DataService> services = new LinkedHashMap<>();
        if (dapRegistry != null) {
            for (DataAccessPoint dap : dapRegistry.accessPoints()) {
                services.put(dap.getName(), dap.getDataService());
                DatasetGraphSwitchable dsg = FMod_InitialCompaction.getTDB2(dap.getDataService().getDataset());
                if (dsg != null) {
                    datasets.put(dap.getName(), dsg);
                }
            }
        }
        this.datasetsByPath = Map.copyOf(datasets);
        this.servicesByPath = Map.copyOf(services);
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

    /**
     * Decides whether a request to a dataset only reads it, so may proceed during an RO Compaction.
     * <p>
     * Reads are: GET, HEAD and OPTIONS requests; POSTs to an endpoint whose operations are all read-only (e.g. a SPARQL
     * query or GraphQL endpoint); label lookups; and SPARQL queries POSTed to the dataset itself. Anything else, including
     * operations this filter can't classify, is treated as a write.
     * </p>
     *
     * @param request     Request
     * @param datasetName Dataset name, e.g. {@code /knowledge}
     * @param service     The dataset's data service, if known
     * @return True if the request only reads the dataset
     */
    static boolean isReadRequest(HttpServletRequest request, String datasetName, DataService service) {
        String method = request.getMethod();
        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
            return true;
        }
        if (!"POST".equals(method)) {
            return false;
        }
        String uri = request.getRequestURI();
        if (uri.startsWith(LABELS_PREFIX)) {
            // Label lookups read the labels store
            return true;
        }
        String rest = uri.length() > datasetName.length() ? uri.substring(datasetName.length() + 1) : "";
        String endpointName = firstPathSegment(rest);
        if (endpointName == null) {
            // POST to the dataset itself: only a SPARQL query reads
            String contentType = request.getContentType();
            return (contentType != null && contentType.toLowerCase().startsWith("application/sparql-query"))
                    || request.getParameter("query") != null;
        }
        if (service == null) {
            return false;
        }
        EndpointSet endpoints = service.getEndpointSet(endpointName);
        return endpoints != null && !endpoints.isEmpty() && READ_OPERATIONS.containsAll(endpoints.operations());
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
            if (dataset == null || datasetName.isEmpty()) {
                chain.doFilter(servletRequest, servletResponse);
                return;
            }
            boolean read = isReadRequest(request, datasetName.get(), servicesByPath.get(datasetName.get()));
            try (DatasetMaintenanceRegistry.RequestAdmission admission =
                         DatasetMaintenanceRegistry.admitRequest(dataset, read)) {
                if (admission.allowed()) {
                    chain.doFilter(servletRequest, servletResponse);
                    return;
                }
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                if (admission.switching()) {
                    // An RO Compaction switching to its compacted copy normally takes moments, so tell clients they can
                    // retry almost immediately. Other maintenance (e.g. a restore) can take far longer, so isn't given
                    // a Retry-After, which would only encourage clients to retry repeatedly.
                    response.setHeader("Retry-After", RETRY_AFTER_SWITCHING_SECONDS);
                }
                response.setContentType("application/json");
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.getWriter().write("{\"error\":\"Dataset " + datasetName.get()
                        + " is temporarily unavailable while " + admission.rejection() + " is in progress.\"}");
            }
        }

        @Override
        public void destroy() {
            FmtLog.debug(Fuseki.configLog, "Destroying Telicent Dataset Availability Filter Module");
        }
    }
}
