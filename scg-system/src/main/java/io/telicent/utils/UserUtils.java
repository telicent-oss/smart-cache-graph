package io.telicent.utils;

import org.apache.jena.fuseki.servlets.HttpAction;
import org.apache.jena.fuseki.servlets.ServletOps;
import org.apache.jena.riot.web.HttpNames;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UserUtils {

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
            String ruser = null;
            return ruser;
        };
    }

    private static String userFromHTTP(HttpAction action) {
        // HTTP authentication
        String hUser = action.getRequest().getRemoteUser();
        if ( hUser != null )
            return hUser;

        String authHeader = action.getRequestHeader(HttpNames.hAuthorization);
        if ( authHeader == null || authHeader.isBlank() ) {
            return null;
            //ServletOps.errorBadRequest("No Authorization header");
        }
        // Format "Bearer user:...."
        // Anchored pattern
        // This will be replaced by JWT authentication and moved to a separate filter for request processing
        Matcher m = authHeaderPattern.matcher(authHeader);
        if ( ! m.matches() )
            ServletOps.errorBadRequest("Bad Authorization header");
        String auser = m.group(1);
        return auser;
    }

}
