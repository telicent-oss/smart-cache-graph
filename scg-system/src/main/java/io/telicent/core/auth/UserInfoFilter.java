package io.telicent.core.auth;

import io.telicent.servlet.auth.jwt.JwtServletConstants;
import io.telicent.smart.caches.configuration.auth.UserInfo;
import io.telicent.smart.caches.configuration.auth.UserInfoLookup;
import io.telicent.smart.caches.configuration.auth.UserInfoLookupException;
import jakarta.servlet.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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
            try {
                UserInfo userInfo = this.userInfoLookup.lookup(jwt);
                request.setAttribute(UserInfo.class.getCanonicalName(), userInfo);
            } catch (UserInfoLookupException e) {
                LOGGER.warn("Failed to obtain User Info: {}", e.getMessage());
            }
        }

        chain.doFilter(request, response);
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
