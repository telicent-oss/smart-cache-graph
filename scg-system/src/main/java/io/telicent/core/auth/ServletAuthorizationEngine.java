package io.telicent.core.auth;

import io.telicent.servlet.auth.jwt.PathExclusion;
import io.telicent.servlet.auth.jwt.servlet5.AuthenticatedHttpServletRequest;
import io.telicent.smart.caches.configuration.auth.UserInfo;
import io.telicent.smart.caches.configuration.auth.policy.Policy;
import io.telicent.smart.caches.server.auth.roles.TelicentAuthorizationEngine;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A Servlet runtime specific implementation of the {@link TelicentAuthorizationEngine}
 * <p>
 * The authorization policies <strong>MUST</strong> be predefined when constructing the engine, see
 * {@link SCG_AuthPolicy} for static helpers for generating policy automatically from Fuseki configuration and known
 * static Telicent endpoints
 * </p>
 */
class ServletAuthorizationEngine extends TelicentAuthorizationEngine<ServletAuthorizationContext> {
    private final Map<PathExclusion, Policy> rolesPolicies, permissionsPolicies;
    private final Policy rolesFallback, permissionsFallback;

    /**
     * Creates a new authorization engine
     *
     * @param rolesPolicies       Roles policy mappings
     * @param permissionsPolicies Permissions policy mappings
     * @param rolesFallback       Fallback roles policy, {@link Policy#DENY_ALL} if not set
     * @param permissionsFallback Fallback permissions policy, {@link Policy#DENY_ALL} if not set
     */
    ServletAuthorizationEngine(Map<PathExclusion, Policy> rolesPolicies, Map<PathExclusion, Policy> permissionsPolicies,
                               Policy rolesFallback, Policy permissionsFallback) {
        this.rolesPolicies = new LinkedHashMap<>(Objects.requireNonNull(rolesPolicies, "rolesPolicies"));
        this.permissionsPolicies =
                new LinkedHashMap<>(Objects.requireNonNull(permissionsPolicies, "permissionsPolicies"));
        this.rolesFallback = Objects.requireNonNullElse(rolesFallback, Policy.DENY_ALL);
        this.permissionsFallback = Objects.requireNonNullElse(permissionsFallback, Policy.DENY_ALL);
    }

    @Override
    protected boolean isAuthenticated(ServletAuthorizationContext context) {
        // If the request was authenticated by the FusekiJwtAuthFilter then it will have the JWT Servlet Auth libraries
        // wrapper around the request
        return context.request() instanceof AuthenticatedHttpServletRequest;
    }

    @Override
    protected boolean isValidPath(ServletAuthorizationContext servletAuthorizationContext) {
        // In the context of SCG which is a pure Servlet application that has its own custom request dispatch mechanisms
        // we don't know in advance whether a request is to a valid path or not.  Therefore, just have to assume the
        // path is valid and apply authorization regardless of requested path.
        // Since our fallback policy is DENY_ALL in real deployments anything that goes to an unexpected path would be
        // rejected as unauthorized anyway.
        //
        // NB - Anything that was excluded from authentication would not pass the isAuthenticated() check so won't be
        //      checked for authorization regardless
        return true;
    }

    @Override
    protected Policy getRolesPolicy(ServletAuthorizationContext context) {
        return findPolicy(context, rolesPolicies, this.rolesFallback);
    }

    private Policy findPolicy(ServletAuthorizationContext context, Map<PathExclusion, Policy> policies,
                              Policy fallback) {
        String path = getEffectiveRequestPath(context);

        for (Map.Entry<PathExclusion, Policy> entry : policies.entrySet()) {
            if (entry.getKey().matches(path)) {
                return entry.getValue();
            }
        }
        return fallback;
    }

    private static String getEffectiveRequestPath(ServletAuthorizationContext context) {
        // NB - Due to how Fuseki dispatches requests there are Servlet's that handle general path patterns which then
        //      call into Fuseki's custom dispatch mechanism.  So the effective path for a request which we use for
        //      matching our authorization policy is the servlet path plus the path info
        return context.request().getServletPath() + (StringUtils.isNotBlank(context.request().getPathInfo()) ?
                                                     context.request().getPathInfo() : "");
    }

    @Override
    protected Policy getPermissionsPolicy(ServletAuthorizationContext context) {
        return findPolicy(context, permissionsPolicies, this.permissionsFallback);
    }

    @Override
    protected boolean isUserInRole(ServletAuthorizationContext context, String role) {
        return context.request().isUserInRole(role);
    }

    @Override
    protected boolean hasPermission(ServletAuthorizationContext context, String permission) {
        UserInfo userInfo = (UserInfo) context.request().getAttribute(UserInfo.class.getCanonicalName());
        if (userInfo == null) {
            return false;
        } else {
            return userInfo.getPermissions().contains(permission);
        }
    }
}
