package io.telicent.utils;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.riot.WebContent;

import java.io.IOException;

import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;

/**
 * Utility class for carrying out common servlet operations
 */
public class ServletUtils {

    public final static String HTTP = "http://";
    public final static String HTTPS = "https://";

    /**
     * Populate an HTTP Response from given JSON Node
     *
     * @param response     Response to populate
     * @param jsonResponse Response data (in JSON form).
     */
    public static void processResponse(HttpServletResponse response, ObjectNode jsonResponse) {
        String jsonOutput;
        try (ServletOutputStream out = response.getOutputStream()) {
            jsonOutput = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(jsonResponse);
            // NB - We can't set Content-Length based on the string length because Java strings are UTF-16 BUT we're
            //      sending a UTF-8 response so when Jetty encodes the string into UTF-8 anything that requires more
            //      than one byte to encode would cause the declared Content-Length to be wrong, this leads to Jetty
            //      aborting the request
            response.setContentType(WebContent.contentTypeJSON);
            response.setCharacterEncoding(WebContent.charsetUTF8);
            out.print(jsonOutput);
        } catch (IOException ex) {
            response.setStatus(HttpServletResponse.SC_UNPROCESSABLE_CONTENT);
        }
    }

    /**
     * Populate an HTTP Response with error data
     *
     * @param response     Response to populate
     * @param jsonResponse Response data (in JSON form)
     * @param status       HTTP status code for response
     * @param message      Description of error encountered
     */
    public static void handleError(HttpServletResponse response, ObjectNode jsonResponse, int status, String message) {
        response.setStatus(status);
        jsonResponse.put("error", message);
        processResponse(response, jsonResponse);
    }
}
