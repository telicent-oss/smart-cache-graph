package io.telicent.access.servlets;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.telicent.access.services.AccessLabelsService;
import io.telicent.labels.LabelsQueryParser;
import io.telicent.labels.LabelsQueryParser.ObjectMode;
import io.telicent.labels.QuadLabels;
import io.telicent.utils.SmartCacheGraphException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.fuseki.servlets.ActionErrorException;
import org.apache.jena.fuseki.servlets.HttpAction;
import org.apache.jena.fuseki.system.ActionCategory;
import org.apache.jena.kafka.FusekiKafka;
import org.apache.jena.sparql.core.Quad;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;
import static io.telicent.utils.ServletUtils.handleError;
import static io.telicent.utils.ServletUtils.processResponse;

/**
 * Access controlled variant of the {@code /$/labels/<dataset>} endpoint, accepting the same request and returning the
 * same response format, but only for quads visible to the requesting user.
 */
// S1948: Embedded Jetty does not serialise these servlets or replicate their injected service state.
@SuppressWarnings("java:S1948")
public class AccessLabelsServlet extends HttpServlet {

    private static final Logger LOG = FusekiKafka.LOG;

    private final AccessLabelsService labelsService;

    public AccessLabelsServlet(AccessLabelsService labelsService) {
        this.labelsService = labelsService;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        try (final InputStream inputStream = request.getInputStream()) {
            final List<Quad> patterns = LabelsQueryParser.parse(inputStream, ObjectMode.TYPED);
            final HttpAction action = new HttpAction(1L, LOG, ActionCategory.ACTION, request, response);
            final List<QuadLabels> results = labelsService.getLabels(action, patterns);
            final ArrayNode resultNodeList = OBJECT_MAPPER.createArrayNode();
            results.forEach(r -> resultNodeList.add(r.toJSONNode()));
            final ObjectNode resultNode = OBJECT_MAPPER.createObjectNode();
            resultNode.set("results", resultNodeList);
            processResponse(response, resultNode);
        } catch (SmartCacheGraphException ex) {
            handleError(response, OBJECT_MAPPER.createObjectNode(), HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            handleError(response, OBJECT_MAPPER.createObjectNode(), HttpServletResponse.SC_BAD_REQUEST, "Missing or invalid request body content");
        } catch (IOException ioex) {
            // Anything left is a transport-level failure reading the request body (for example the client
            // disconnecting mid-request), not malformed JSON - that is the JsonProcessingException above.
            // Report it as a bad request on a best-effort basis rather than letting it escape the servlet.
            LOG.warn("Failed to read request body", ioex);
            handleError(response, OBJECT_MAPPER.createObjectNode(), HttpServletResponse.SC_BAD_REQUEST,
                        "Unable to read request body");
        } catch (ActionErrorException ex) {
            // ABAC rejects requests it can't evaluate, e.g. a 403 for a user with no attributes, which would otherwise
            // be reported as a 500
            handleError(response, OBJECT_MAPPER.createObjectNode(), ex.getRC(), ex.getMessage());
        }
    }
}
