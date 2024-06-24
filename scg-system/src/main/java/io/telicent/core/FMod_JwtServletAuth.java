package io.telicent.core;

import io.telicent.servlet.auth.jwt.JwtServletConstants;
import io.telicent.servlet.auth.jwt.PathExclusion;
import io.telicent.servlet.auth.jwt.configuration.AutomatedConfiguration;
import io.telicent.servlet.auth.jwt.servlet5.JwtAuthFilter;
import io.telicent.servlet.auth.jwt.verification.JwtVerifier;
import io.telicent.smart.cache.configuration.Configurator;
import io.telicent.smart.caches.configuration.auth.AuthConstants;
import io.telicent.smart.caches.configuration.auth.TelicentConfigurationAdaptor;
import jakarta.servlet.FilterConfig;
import org.apache.jena.atlas.lib.Version;
import org.apache.jena.atlas.logging.FmtLog;
import org.apache.jena.fuseki.Fuseki;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiModule;
import org.apache.jena.rdf.model.Model;

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
        FusekiConfigurationAdaptor adaptor = new FusekiConfigurationAdaptor(serverBuilder);
        AutomatedConfiguration.configure(adaptor);
        JwtVerifier jwtVerifier = (JwtVerifier) adaptor.getAttribute(JwtServletConstants.ATTRIBUTE_JWT_VERIFIER);

        if (jwtVerifier == null) {
            FmtLog.error(Fuseki.configLog,
                         "Failed to configure JWT Authentication, {} environment variable was missing or contained invalid value",
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
                                          PathExclusion.parsePathPatterns("/$/ping,/$/metrics,/$/stats/*"));

        // Register the filter
        serverBuilder.addFilter("/*", new FusekiJwtAuthFilter());
    }

    private static final class FusekiJwtAuthFilter extends JwtAuthFilter {

        @Override
        public void init(FilterConfig filterConfig) {
            // Do nothing
            // We explicitly configure the filter at the server setup level so no need to use the default filter
            // behaviour of trying to automatically configure itself from init parameters
        }
    }

    private static final class FusekiConfigurationAdaptor extends TelicentConfigurationAdaptor {

        private final FusekiServer.Builder serverBuilder;

        public FusekiConfigurationAdaptor(FusekiServer.Builder serverBuilder) {
            this.serverBuilder = serverBuilder;
        }

        @Override
        public void setAttribute(String attribute, Object value) {
            this.serverBuilder.addServletAttribute(attribute, value);
        }

        @Override
        public Object getAttribute(String attribute) {
            return this.serverBuilder.getServletAttribute(attribute);
        }
    }
}
