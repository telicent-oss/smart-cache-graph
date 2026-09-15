package io.telicent.utils;

import org.apache.jena.fuseki.servlets.HttpAction;
import org.apache.jena.fuseki.servlets.ServletOps;
import org.apache.jena.riot.web.HttpNames;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UserUtils {

    private UserUtils() {
        // Static utility class, not intended to be instantiated.
    }

    // "Authorization: Bearer: user:NAME"
    private static final Pattern authHeaderPattern = Pattern.compile("\\s*Bearer\\s+user:(\\S*)\s*");

    /**
     * Given a Http servlet request (in HttpAction), find the user.
     */
    public static Function<HttpAction, String> userForRequest() {
        return action ->{
            // Authorization:
            String auser = userFromHTTP(action);
            if ( auser != null )
                return auser;
            return null;
        };
    }

    private static String userFromHTTP(HttpAction action) {
        // HTTP authentication
        String hUser = action.getRequest().getRemoteUser();
        if ( hUser != null )
            return hUser;

        String authHeader = action.getRequestHeader(HttpNames.hAuthorization);
        if ( authHeader == null || authHeader.isBlank() ) {
            // Deliberately returns null rather than raising a bad-request error: a missing
            // Authorization header means "no user", which callers handle, not a malformed request.
            return null;
        }
        // Format "Bearer user:...."
        // Anchored pattern
        // This will be replaced by JWT authentication and moved to a separate filter for request processing
        Matcher m = authHeaderPattern.matcher(authHeader);
        if ( ! m.matches() )
            ServletOps.errorBadRequest("Bad Authorization header");
        return m.group(1);
    }

}
