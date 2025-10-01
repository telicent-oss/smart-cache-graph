package io.telicent.core.auth;

import io.telicent.servlet.auth.jwt.servlet5.JwtAuthFilter;
import jakarta.servlet.FilterConfig;

/**
 * An extension to JWT Servlet Auth library's standard {@link JwtAuthFilter} for Servlet environments with automatic
 * configuration disabled in the {@link #init(FilterConfig)} method since we handle pre-configuring things in
 * {@link FMod_JwtServletAuth}
 */
final class FusekiJwtAuthFilter extends JwtAuthFilter {

    @Override
    public void init(FilterConfig filterConfig) {
        // Do nothing
        // We explicitly configure the filter at the server setup level so no need to use the default filter
        // behaviour of trying to automatically configure itself from init parameters
    }
}
