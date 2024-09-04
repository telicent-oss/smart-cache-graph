package io.telicent.core;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.atlas.lib.Version;
import org.apache.jena.atlas.logging.FmtLog;
import org.apache.jena.fuseki.Fuseki;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiModule;
import org.apache.jena.rdf.model.Model;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * FMod responsible for obtaining the Request ID from the incoming request.
 * Creating a new ID if it is missing.
 */
public class FMod_RequestIDFilter implements FusekiModule {
    private static final String VERSION = Version.versionForClass(FMod_RequestIDFilter.class).orElse("<development>");

    /**
     * HTTP Header, and Logger {@link MDC} key, used to specify the Request ID associated with an HTTP Request
     */
    public static final String REQUEST_ID = "Request-ID";

    /**
     * The maximum possible length of a client supplied Request ID, a Request ID above this length will be truncated to
     * this maximum.
     */
    public static final int MAX_CLIENT_REQUEST_ID_LENGTH = UUID.randomUUID().toString().length();

    /**
     * Use a unique starting point each time the server starts up so even if Clients are providing custom Request IDs
     * the server will continue to append unique suffixes to them
     */
    private static final AtomicLong CLIENT_ID_SUFFIX = new AtomicLong(System.currentTimeMillis());

    @Override
    public String name() {
        return "Request ID Capture ";
    }

    @Override
    public void prepare(FusekiServer.Builder serverBuilder, Set<String> datasetNames, Model configModel) {
        FmtLog.info(Fuseki.configLog, "Telicent Request ID Filter Module (%s)", VERSION);
        serverBuilder.addFilter("/*", new FusekiRequestIDFilter());
    }

    private static final class FusekiRequestIDFilter implements Filter {

        @Override
        public void init(FilterConfig filterConfig) {
            // Do nothing
            // We explicitly configure the filter at the server setup level so no need to use the default filter
            // behaviour of trying to automatically configure itself from init parameters
            FmtLog.debug(Fuseki.configLog, "Initiating Telicent Request ID Filter Module");
        }

        @Override
        public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws
                IOException, ServletException {
            HttpServletRequest request = (HttpServletRequest) servletRequest;
            HttpServletResponse response = (HttpServletResponse) servletResponse;

            String requestId = request.getHeader(REQUEST_ID);
            if (isNotBlank(requestId)) {
                // If the Client provided their own Request ID we append a unique suffix each time.  This allows each
                // request to be uniquely identified while also allowing clients to use the same Request ID for a sequence
                // of related requests.  We impose a maximum length on the client provided ID to avoid them supplying
                // something ridiculous and effectively allowing them to DDoS the logging.
                if (requestId.length() > MAX_CLIENT_REQUEST_ID_LENGTH) {
                    requestId = requestId.substring(0, MAX_CLIENT_REQUEST_ID_LENGTH);
                }
                requestId = requestId + "/" + CLIENT_ID_SUFFIX.incrementAndGet();
            } else {
                requestId = UUID.randomUUID().toString();
            }
            response.addHeader(REQUEST_ID, requestId);

            // Place into the Logging MDC so logging patterns can include this if desired
            MDC.put(REQUEST_ID, requestId);
            try {
                chain.doFilter(servletRequest, servletResponse);
            } finally {
                MDC.remove(REQUEST_ID);
            }
        }

        @Override
        public void destroy() {
            // Do nothing
            FmtLog.debug(Fuseki.configLog, "Destroying Telicent Request ID Filter Module");
        }
    }

    public static boolean isNotBlank(String str) {
        return str != null && !str.trim().isEmpty();
    }
}