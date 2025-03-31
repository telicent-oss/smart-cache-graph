package io.telicent.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.riot.WebContent;

import java.io.IOException;

/**
 * Utility class for carrying out common servlet operations
 */
public class ServletUtils {

    public static ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Populate an HTTP Response from given JSON Node
     *
     * @param response     Response to populate
     * @param jsonResponse Response data (in JSON form).
     */
    public static void processResponse(HttpServletResponse response, ObjectNode jsonResponse) {
        String jsonOutput;
        try (ServletOutputStream out = response.getOutputStream()) {
            jsonOutput = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(jsonResponse);
            response.setContentLength(jsonOutput.length());
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
