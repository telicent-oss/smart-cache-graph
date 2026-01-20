package io.telicent.core.auth;

import io.telicent.servlet.auth.jwt.JwtServletConstants;
import io.telicent.servlet.auth.jwt.PathExclusion;
import io.telicent.servlet.auth.jwt.configuration.AutomatedConfiguration;
import io.telicent.servlet.auth.jwt.verification.JwtVerifier;
import io.telicent.smart.cache.configuration.Configurator;
import io.telicent.smart.caches.configuration.auth.*;
import io.telicent.smart.caches.configuration.auth.policy.Policy;
import io.telicent.smart.caches.server.auth.roles.TelicentAuthorizationEngine;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.atlas.lib.Version;
import org.apache.jena.atlas.logging.FmtLog;
import org.apache.jena.fuseki.Fuseki;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiModule;
import org.apache.jena.fuseki.server.DataAccessPoint;
import org.apache.jena.fuseki.server.DataAccessPointRegistry;
import org.apache.jena.rdf.model.Model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class FMod_JwtServletAuth implements FusekiModule {

    private static final String VERSION = Version.versionForClass(FMod_JwtServletAuth.class).orElse("<development>");

    @Override
    public String name() {
        return "JWT Servlet Authentication";
    }

    @Override
    public void prepare(FusekiServer.Builder serverBuilder, Set<String> datasetNames, Model configModel) {
        FmtLog.info(Fuseki.configLog, "Telicent JWT Authentication Module (%s)", VERSION);

        String jwksUrl = Configurator.get(AuthConstants.ENV_JWKS_URL);
        if (Objects.equals(jwksUrl, AuthConstants.AUTH_DISABLED)) {
            Fuseki.configLog.warn(
                    "JWT Authentication explicitly disabled by configuration, no user authentication will be applied");
            return;
        }

        // Configure the JWT Verifier
        FusekiJwtConfigAdaptor adaptor = new FusekiJwtConfigAdaptor(serverBuilder);
        AutomatedConfiguration.configure(adaptor);
        JwtVerifier jwtVerifier = (JwtVerifier) adaptor.getAttribute(JwtServletConstants.ATTRIBUTE_JWT_VERIFIER);

        if (jwtVerifier == null) {
            FmtLog.error(Fuseki.configLog,
                         "Failed to configure JWT Authentication, %s environment variable was missing or contained invalid value",
                         AuthConstants.ENV_JWKS_URL);
            throw new RuntimeException("Failed to configure JWT Authentication");
        } else {
            FmtLog.info(Fuseki.configLog, "JWT Authentication engine is %s",
                        adaptor.getAttribute(JwtServletConstants.ATTRIBUTE_JWT_ENGINE));
            FmtLog.info(Fuseki.configLog, "JWT Authentication configured with verifier %s", jwtVerifier);
        }

        // Disable authentication for purely informative paths that are useful for health checks and metrics
        // Note some of these URLs aren't actually enabled for SCG currently but useful to future-proof our exclusions
        // should we enable these features in future
        serverBuilder.addServletAttribute(JwtServletConstants.ATTRIBUTE_PATH_EXCLUSIONS,
                                          PathExclusion.parsePathPatterns(
                                                  "/$/ping,/$/metrics,/$/stats/*"));

        // Register the filter
        serverBuilder.addFilter("/*", new FusekiJwtAuthFilter());

        // Register the necessary filters for roles and permissions based authorization
        if (Configurator.get(AuthConstants.FEATURE_FLAG_AUTHORIZATION, Boolean::parseBoolean, true)) {
            // Create and register for User Info lookups
            String userInfoEndpoint = Configurator.get(AuthConstants.ENV_USERINFO_URL);
            if (StringUtils.isNotBlank(userInfoEndpoint)) {
                UserInfoLookup userInfoLookup = new RemoteUserInfoLookup(userInfoEndpoint);
                serverBuilder.addFilter("/*", new UserInfoFilter(userInfoLookup));
            }
            // Register an Authorization filter
            serverBuilder.addFilter("/*", new TelicentAuthorizationFilter());
        } else {
            Fuseki.configLog.warn(
                    "The Authorization Policy enforcement feature has been explicitly disabled via configuration variable {}.  If this was not intended please ensure that this feature flag is set to true in your environment.",
                    AuthConstants.FEATURE_FLAG_AUTHORIZATION);
        }
    }

    @Override
    public void configured(FusekiServer.Builder serverBuilder, DataAccessPointRegistry dapRegistry, Model configModel) {
        Map<PathExclusion, Policy> roles = new LinkedHashMap<>();
        Map<PathExclusion, Policy> perms = new LinkedHashMap<>();

        // Generate fixed policies for Telicent mod provided endpoints
        SCG_AuthPolicy.addTelicentEndpointPolicies(roles, perms);

        // Generate dynamic policies for configured datasets
        for (DataAccessPoint dap : dapRegistry.accessPoints()) {
            SCG_AuthPolicy.addDatasetRolesPolicy(roles, dap);
            SCG_AuthPolicy.addDatasetPermissionsPolicy(perms, dap);
        }

        // Create and register our engine
        ServletAuthorizationEngine engine =
                new ServletAuthorizationEngine(roles, perms, Policy.DENY_ALL, Policy.DENY_ALL);
        serverBuilder.addServletAttribute(TelicentAuthorizationEngine.class.getCanonicalName(), engine);
    }
}
