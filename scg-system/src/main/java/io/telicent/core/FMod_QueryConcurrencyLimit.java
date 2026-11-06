package io.telicent.core;

import io.opentelemetry.api.metrics.LongCounter;
import io.telicent.otel.JenaMetrics;
import io.telicent.smart.cache.configuration.Configurator;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.Jena;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A Fuseki module that caps the number of SPARQL queries executing concurrently.
 * <p>
 * Fuseki's only built-in cap is Jetty's thread pool (max 20 threads by default) which is shared by every endpoint.
 * This module limits only requests to a SPARQL query endpoint ({@link Operation#Query}), leaving updates, admin, health
 * and other endpoints unaffected. Requests over the limit wait up to {@value #ENV_WAIT_MS} milliseconds for a permit
 * and are then rejected with HTTP 503 and a {@code Retry-After} header.
 * </p>
 * <p>
 * Disabled unless {@value #ENV_MAX_CONCURRENT_QUERIES} is set to a positive integer. The limit should be lower than the
 * Jetty thread pool size, otherwise queries can still occupy every thread. Note that a request waiting for a permit
 * holds its Jetty thread while it waits, so keep {@value #ENV_WAIT_MS} small.
 * </p>
 */
public class FMod_QueryConcurrencyLimit implements FusekiModule {

    /**
     * Environment variable/system property giving the maximum number of concurrent queries; unset or {@code <= 0}
     * disables the limit
     */
    public static final String ENV_MAX_CONCURRENT_QUERIES = "MAX_CONCURRENT_QUERIES";
    /**
     * Environment variable/system property giving how long (ms) a query waits for a permit before being rejected.
     * Defaults to {@value #DEFAULT_WAIT_MS}, i.e. reject immediately.
     */
    public static final String ENV_WAIT_MS = "MAX_CONCURRENT_QUERIES_WAIT_MS";
    static final long DEFAULT_WAIT_MS = 0L;
    /**
     * {@code Retry-After} (seconds) sent with the 503 for rejected queries
     */
    static final String RETRY_AFTER_SECONDS = "1";

    /**
     * Name of the OpenTelemetry counter of queries rejected because the concurrency limit was reached
     */
    public static final String METRIC_QUERIES_REJECTED = "smartcache.graph.query.rejected";

    private static final String VERSION =
            Version.versionForClass(FMod_QueryConcurrencyLimit.class).orElse("<development>");

    // S3077: Publish immutable snapshots by replacing the volatile reference; do not mutate a published value.
    @SuppressWarnings("java:S3077")
    private volatile Map<String, DataService> servicesByPath = Map.of();
    // S3077: Publish immutable snapshots by replacing the volatile reference; do not mutate a published value.
    @SuppressWarnings("java:S3077")
    private volatile Set<String> datasetNames = Set.of();
    private final AtomicLong rejected = new AtomicLong();
    private volatile boolean enabled;
    private volatile LongCounter rejectedCounter;

    @Override
    public String name() {
        return "Query Concurrency Limit";
    }

    @Override
    public void prepare(FusekiServer.Builder serverBuilder, Set<String> datasetNames, Model configModel) {
        final int max = Configurator.get(ENV_MAX_CONCURRENT_QUERIES, Integer::parseInt, -1);
        if (max <= 0) {
            return;
        }
        final long waitMs = Math.max(0L, Configurator.get(ENV_WAIT_MS, Long::parseLong, DEFAULT_WAIT_MS));
        FmtLog.info(Fuseki.configLog, "%s Module (%s): at most %d concurrent queries, waiting up to %d ms",
                    name(), VERSION, max, waitMs);
        this.datasetNames = Set.copyOf(new HashSet<>(datasetNames));
        this.enabled = true;
        this.rejectedCounter = JenaMetrics.getMeter("Jena", Jena.VERSION)
                                          .counterBuilder(METRIC_QUERIES_REJECTED)
                                          .setDescription("Counts SPARQL queries rejected because the concurrent query limit was reached")
                                          .build();
        serverBuilder.addFilter("/*", new QueryLimitFilter(new Semaphore(max, true), waitMs));
    }

    @Override
    public void configured(FusekiServer.Builder serverBuilder, DataAccessPointRegistry dapRegistry, Model configModel) {
        if (!enabled || dapRegistry == null) {
            return;
        }
        final Map<String, DataService> services = new java.util.LinkedHashMap<>();
        for (DataAccessPoint dap : dapRegistry.accessPoints()) {
            services.put(dap.getName(), dap.getDataService());
        }
        this.servicesByPath = Map.copyOf(services);
    }

    /**
     * Decides whether a request is a SPARQL query: a GET/POST to a named endpoint that provides {@link Operation#Query},
     * or a GET/POST to the dataset itself carrying a query ({@code ?query=} or an {@code application/sparql-query} body).
     */
    static boolean isQueryRequest(HttpServletRequest request, String datasetName, DataService service) {
        final String method = request.getMethod();
        final boolean post = "POST".equals(method);
        if (!post && !"GET".equals(method)) {
            return false;
        }
        final String uri = request.getRequestURI();
        final String rest = uri.length() > datasetName.length() ? uri.substring(datasetName.length() + 1) : "";
        final int slash = rest.indexOf('/');
        final String endpointName = slash >= 0 ? rest.substring(0, slash) : rest;
        if (endpointName.isBlank()) {
            final String contentType = request.getContentType();
            return (post && contentType != null && contentType.toLowerCase().startsWith("application/sparql-query"))
                    || request.getParameter("query") != null;
        }
        if (service == null) {
            return false;
        }
        final EndpointSet endpoints = service.getEndpointSet(endpointName);
        return endpoints != null && endpoints.operations().contains(Operation.Query);
    }

    private final class QueryLimitFilter implements Filter {
        private final Semaphore permits;
        private final long waitMs;

        QueryLimitFilter(Semaphore permits, long waitMs) {
            this.permits = permits;
            this.waitMs = waitMs;
        }

        @Override
        public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
                throws IOException, ServletException {
            final HttpServletRequest request = (HttpServletRequest) servletRequest;
            final HttpServletResponse response = (HttpServletResponse) servletResponse;

            final var datasetName = FMod_DatasetAvailabilityFilter.datasetNameForPath(request.getRequestURI(), datasetNames);
            if (datasetName.isEmpty()
                    || !isQueryRequest(request, datasetName.get(), servicesByPath.get(datasetName.get()))) {
                chain.doFilter(servletRequest, servletResponse);
                return;
            }

            if (!acquire()) {
                rejectedCounter.add(1);
                final long total = rejected.incrementAndGet();
                FmtLog.warn(Fuseki.serverLog, "Query rejected, concurrent query limit reached (%d rejected so far)",
                            total);
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                response.setHeader("Retry-After", RETRY_AFTER_SECONDS);
                response.setContentType("application/json");
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.getWriter().write("{\"error\":\"Too many concurrent queries, please retry shortly.\"}");
                return;
            }
            try {
                chain.doFilter(servletRequest, servletResponse);
            } finally {
                permits.release();
            }
        }

        private boolean acquire() {
            if (waitMs <= 0) {
                return permits.tryAcquire();
            }
            try {
                return permits.tryAcquire(waitMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

}
