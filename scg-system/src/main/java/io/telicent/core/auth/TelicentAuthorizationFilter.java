package io.telicent.core.auth;

import io.telicent.smart.caches.server.auth.roles.AuthorizationResult;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Objects;

/**
 * A filter that applies Telicent's roles and permissions based authorization policies
 */
final class TelicentAuthorizationFilter implements Filter {
    private static final Logger LOGGER = LoggerFactory.getLogger(TelicentAuthorizationFilter.class);

    private final ServletAuthorizationEngine authorizationEngine;

    /**
     * Creates a new authorization filter
     *
     * @param authorizationEngine Authorization engine that makes authorization decisions
     */
    TelicentAuthorizationFilter(ServletAuthorizationEngine authorizationEngine) {
        this.authorizationEngine = Objects.requireNonNull(authorizationEngine);
    }

    @Override
    public void init(FilterConfig filterConfig) {
        // No init required, initialised via constructor parameters
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException,
            ServletException {
        ServletAuthorizationContext context = new ServletAuthorizationContext((HttpServletRequest) request);
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        AuthorizationResult result = this.authorizationEngine.authorize(context);
        String allReasons = StringUtils.join(result.reasons(), ", ");
        switch (result.status()) {
            case DENIED:
                // Send a 401 Unauthorized
                LOGGER.warn("Request to {} rejected: {}", context.request().getRequestURI(), allReasons);
                httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                httpResponse.getWriter().write("Rejected due to servers authorization policy: " + allReasons);
                break;
            case ALLOWED:
                // Success, pass request onwards
                LOGGER.info("Request to {} successfully authorized: {}", context.request().getRequestURI(), allReasons);
                chain.doFilter(request, response);
                break;
            case NOT_APPLICABLE:
                // Not applicable, pass request onwards
                chain.doFilter(request, response);
                break;
            default:
                // Unexpected authorization status, fail-safe by rejecting the request
                LOGGER.warn(
                        "Request to {} rejected due to authorization engine returning unexpected authorization status: {}",
                        context.request().getRequestURI(), result.status());
                httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                httpResponse.getWriter().write("Rejected due to unknown authorization status: " + result.status());
                break;
        }
    }

}
