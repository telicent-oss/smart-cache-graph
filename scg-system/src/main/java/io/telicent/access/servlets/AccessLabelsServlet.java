package io.telicent.access.servlets;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.telicent.access.services.AccessLabelsService;
import io.telicent.labels.QuadLabels;
import io.telicent.model.JsonQuad;
import io.telicent.model.JsonQuads;
import io.telicent.model.JsonTripleObject;
import io.telicent.model.TypedObject;
import io.telicent.utils.SmartCacheGraphException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.fuseki.servlets.ActionErrorException;
import org.apache.jena.fuseki.servlets.HttpAction;
import org.apache.jena.fuseki.system.ActionCategory;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.kafka.FusekiKafka;
import org.apache.jena.sparql.core.Quad;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
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

    private static final String HTTP = "http://";
    private static final String HTTPS = "https://";
    private static final String WILDCARD = "*";
    private static final String QUADS = "quads";
    private static final String TRIPLES = "triples";

    private final AccessLabelsService labelsService;

    public AccessLabelsServlet(AccessLabelsService labelsService) {
        this.labelsService = labelsService;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        try (final InputStream inputStream = request.getInputStream()) {
            final List<Quad> patterns = parseQuadQueries(inputStream);
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

    /**
     * Parses a request body, in the same format as {@code /$/labels/<dataset>}, into the quad patterns to query.
     * <p>
     * Objects starting with {@code http://} or {@code https://} are URIs and anything else is a string literal, as for
     * {@code /$/labels/<dataset>}. Unlike that endpoint, {@code object.dataType} and {@code object.language} are
     * honoured, so typed and language tagged literals can be queried. These queries are evaluated against the dataset
     * itself, rather than only the labels store, so the object must match the stored term exactly.
     * </p>
     *
     * @param inputStream Request body
     * @return Quad patterns, with {@link Node#ANY} for any wildcard ({@code *}) values
     * @throws SmartCacheGraphException Thrown if the request is well-formed JSON but not a valid labels query, including
     *                                  an unknown data type or both a data type and a language
     * @throws IOException              Thrown if the request body can't be read or isn't well-formed JSON
     * @throws IllegalArgumentException Thrown if the request entries can't be converted to the expected structure
     */
    static List<Quad> parseQuadQueries(InputStream inputStream) throws SmartCacheGraphException, IOException {
        final List<Quad> quadList = new ArrayList<>();
        final JsonNode rootNode = OBJECT_MAPPER.readTree(inputStream);
        final boolean hasQuads = rootNode != null && rootNode.has(QUADS);
        final boolean hasTriples = rootNode != null && rootNode.has(TRIPLES);
        if (hasQuads && hasTriples) {
            final String message = "Invalid JSON format: Provide either a 'quads' or a 'triples' array, not both.";
            LOG.warn(message);
            throw new SmartCacheGraphException(message);
        }
        // root node can be either 'triples' or 'quads'
        final JsonNode triples = hasTriples ? rootNode.get(TRIPLES) : null;
        final JsonNode queries = hasQuads ? rootNode.get(QUADS) : triples;
        if (queries != null && queries.isArray()) {
            JsonQuads queryRequest = OBJECT_MAPPER.convertValue(rootNode, JsonQuads.class);
            for (JsonQuad query : queryRequest.quads) {
                quadList.add(getQuad(query));
            }
        } else {
            final String message = "Invalid JSON format: Missing 'quads' or 'triples' array.";
            LOG.warn(message);
            throw new SmartCacheGraphException(message);
        }
        return quadList;
    }

    private static Quad getQuad(JsonQuad tripleQuery) throws SmartCacheGraphException {
        if (tripleQuery == null || tripleQuery.subject == null || tripleQuery.predicate == null
                || tripleQuery.object == null || tripleQuery.object.value == null) {
            throw new SmartCacheGraphException("Invalid JSON format: Incomplete triple.");
        }
        final Node s = getWildcardOrURI(tripleQuery.subject);
        final Node p = getWildcardOrURI(tripleQuery.predicate);
        final Node o = getObjectNode(tripleQuery.object);
        return Quad.create(getGraphNode(tripleQuery.graph), Triple.create(s, p, o));
    }

    private static Node getGraphNode(String graph) {
        if (graph == null) {
            return Quad.defaultGraphIRI;
        }
        return getWildcardOrURI(graph);
    }

    private static Node getWildcardOrURI(String object) {
        if (WILDCARD.equals(object)) {
            return Node.ANY;
        }
        return NodeFactory.createURI(object);
    }

    private static Node getObjectNode(JsonTripleObject object) throws SmartCacheGraphException {
        final boolean hasLanguage = object.language != null && !object.language.isEmpty();
        final boolean hasDataType = object.dataType != null && !object.dataType.isEmpty();
        if (WILDCARD.equals(object.value)) {
            return Node.ANY;
        }
        if (!hasLanguage && !hasDataType) {
            if (object.value.startsWith(HTTP) || object.value.startsWith(HTTPS)) {
                return NodeFactory.createURI(object.value);
            }
            return NodeFactory.createLiteralByValue(object.value);
        }
        if (hasLanguage && hasDataType) {
            throw new SmartCacheGraphException("Cannot have both language and dataType in request object");
        }
        final TypedObject typedObject = TypedObject.from(object);
        if (hasLanguage) {
            return NodeFactory.createLiteralLang(typedObject.value, typedObject.language);
        }
        return NodeFactory.createLiteralDT(typedObject.value, typedObject.datatype);
    }
}
