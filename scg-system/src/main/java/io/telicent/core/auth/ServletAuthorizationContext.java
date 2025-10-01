package io.telicent.core.auth;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Record class for holding request context for authorization decisions
 *
 * @param request HTTP Request
 */
public record ServletAuthorizationContext(HttpServletRequest request) {
}
