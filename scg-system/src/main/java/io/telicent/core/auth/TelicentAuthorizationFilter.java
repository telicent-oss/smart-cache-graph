package io.telicent.core.auth;

import io.telicent.smart.caches.server.auth.roles.AuthorizationResult;
import io.telicent.smart.caches.server.auth.roles.TelicentAuthorizationEngine;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * A filter that applies Telicent's roles and permissions based authorization policies
 */
final class TelicentAuthorizationFilter implements Filter {
    private static final Logger LOGGER = LoggerFactory.getLogger(TelicentAuthorizationFilter.class);

    @Override
    public void init(FilterConfig filterConfig) {
        // No init required, initialised via constructor parameters
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException,
            ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Check that the authorization engine was configured
        ServletAuthorizationEngine authorizationEngine = (ServletAuthorizationEngine) request.getServletContext().getAttribute(
                TelicentAuthorizationEngine.class.getCanonicalName());
        if (authorizationEngine == null) {
            unauthorized(httpResponse, "Authorization enabled but no Authorization Engine was configured");
            return;
        }

        ServletAuthorizationContext context = new ServletAuthorizationContext(httpRequest);
        AuthorizationResult result = authorizationEngine.authorize(context);
        String clientReasons = StringUtils.join(result.reasons(), ", ");
        String loggingReasons = StringUtils.join(result.loggingReasons(), ", ");
        switch (result.status()) {
            case DENIED:
                // Send a 401 Unauthorized
                LOGGER.warn("Request to {} rejected: {}", context.request().getRequestURI(), loggingReasons);
                unauthorized(httpResponse, "Rejected due to servers authorization policy: " + clientReasons);
                break;
            case ALLOWED:
                // Success, pass request onwards
                LOGGER.info("Request to {} successfully authorized: {}", context.request().getRequestURI(), loggingReasons);
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
                unauthorized(httpResponse, "Rejected due to unknown authorization status: " + result.status());
                break;
        }
    }

    private static void unauthorized(HttpServletResponse httpResponse, String message) throws IOException {
        httpResponse.setContentType("text/plain");
        httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        httpResponse.getWriter().write(message);
    }

}
