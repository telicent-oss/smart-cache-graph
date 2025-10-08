package io.telicent.core.auth;

import io.telicent.smart.caches.configuration.auth.TelicentConfigurationAdaptor;
import org.apache.jena.fuseki.main.FusekiServer;

/**
 * A configuration adaptor between Fuseki runtime and the Telicent Core's variant of the JWT Servlet Auth library's
 * automatic configuration mechanism
 */
final class FusekiJwtConfigAdaptor extends TelicentConfigurationAdaptor {

    private final FusekiServer.Builder serverBuilder;

    /**
     * Creates a new configuration adaptor
     *
     * @param serverBuilder Fuseki Server builder
     */
    public FusekiJwtConfigAdaptor(FusekiServer.Builder serverBuilder) {
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
