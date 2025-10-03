package io.telicent.core.auth;

import io.telicent.jena.abac.AttributeValueSet;
import io.telicent.jena.abac.attributes.AttributeValue;
import io.telicent.jena.abac.attributes.ValueTerm;
import io.telicent.jena.abac.core.AttributesStoreAuthServer;
import io.telicent.jena.abac.fuseki.server.UserInfoEnrichmentFilter;
import io.telicent.servlet.auth.jwt.JwtServletConstants;
import io.telicent.smart.caches.configuration.auth.UserInfo;
import io.telicent.smart.caches.configuration.auth.UserInfoLookup;
import io.telicent.smart.caches.configuration.auth.UserInfoLookupException;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A request filter that augments authenticated requests with {@link UserInfo} for use by subsequent filters e.g.
 * {@link TelicentAuthorizationFilter}
 */
final class UserInfoFilter implements Filter {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserInfoFilter.class);

    private final UserInfoLookup userInfoLookup;

    /**
     * Creates a new filter
     *
     * @param userInfoLookup The {@link UserInfoLookup} to use to obtain User Info for authenticated users
     */
    public UserInfoFilter(UserInfoLookup userInfoLookup) {
        this.userInfoLookup = Objects.requireNonNull(userInfoLookup);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException,
            ServletException {
        // If this is an authenticated request then augment it with User Info (if available)
        String jwt = (String) request.getAttribute(JwtServletConstants.REQUEST_ATTRIBUTE_RAW_JWT);
        if (StringUtils.isNotBlank(jwt)) {
            // Also deal with the RDF-ABAC integration here, need to set the user so that the RDF-ABAC layer has the
            // username, and user info, if available
            // This is kinda clumsy but for now we're stuck with the API that RDF-ABAC gives us so have to jump through
            // some hoops to make things work properly without creating circular dependencies between Smart Caches Core
            // and RDF-ABAC
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            AttributesStoreAuthServer.addIdAndUserName(jwt, httpRequest.getRemoteUser());
            request.setAttribute(UserInfoEnrichmentFilter.ATTR_ABAC_USERNAME, httpRequest.getRemoteUser());

            try {
                // Obtain the actual User Info, convert the attributes to an RDF-ABAC AttributeValueSet and make those
                // available later
                UserInfo userInfo = this.userInfoLookup.lookup(jwt);
                request.setAttribute(UserInfo.class.getCanonicalName(), userInfo);
                AttributesStoreAuthServer.addUserNameAndAttributes(httpRequest.getRemoteUser(), toAttributeValueSet(
                        userInfo.getAttributes()));
            } catch (UserInfoLookupException e) {
                LOGGER.warn("Failed to obtain User Info: {}", e.getMessage());
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * Converts users raw attributes into RDF-ABAC compatible attributes
     *
     * @param attributes Raw attributes
     * @return RDF-ABAC Attribute Value set
     */
    private AttributeValueSet toAttributeValueSet(Map<String, Object> attributes) {
        List<AttributeValue> attrs = new ArrayList<>();
        convertMapToAttributes(attrs, "", attributes);
        return AttributeValueSet.of(attrs);
    }

    private static void convertMapToAttributes(List<AttributeValue> attrs, String prefix,
                                               Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            convertValue(attrs, prefix + entry.getKey(), entry.getValue());
        }
    }

    @SuppressWarnings("unchecked")
    private static void convertValue(List<AttributeValue> attrs, String key, Object value) {
        switch (value) {
            case String strValue -> attrs.add(AttributeValue.of(key, ValueTerm.value(strValue)));
            case Number numberValue -> attrs.add(AttributeValue.of(key, ValueTerm.value(numberValue.toString())));
            case Boolean boolValue -> attrs.add(AttributeValue.of(key, ValueTerm.value(boolValue)));
            case List<?> list -> {
                List<Object> values = (List<Object>) list;
                for (Object v : values) {
                    convertValue(attrs, key, v);
                }
            }
            case Map<?, ?> map -> {
                Map<String, Object> values = (Map<String, Object>) map;
                convertMapToAttributes(attrs, key, values);
            }
            default -> LOGGER.warn("Unsupported value type for attribute {} ignored: {}", key, value.getClass());
        }
    }

    @Override
    public void destroy() {
        try {
            this.userInfoLookup.close();
        } catch (Throwable t) {
            LOGGER.warn("Failed to close UserInfoLookup: {}", t.getMessage());
        }
    }
}
