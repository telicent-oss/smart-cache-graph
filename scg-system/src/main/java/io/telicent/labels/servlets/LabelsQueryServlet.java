package io.telicent.labels.servlets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.telicent.labels.QuadLabels;
import io.telicent.labels.services.LabelsQueryService;
import io.telicent.model.JsonQuad;
import io.telicent.model.JsonQuads;
import io.telicent.utils.SmartCacheGraphException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

public class LabelsQueryServlet extends HttpServlet {

    private static final Logger LOG = FusekiKafka.LOG;

    private static final String HTTP = "http://";
    private static final String HTTPS = "https://";
    private static final String WILDCARD = "*";
    private static final String QUADS = "quads";
    private static final String TRIPLES = "triples";

    private final LabelsQueryService queryService;

    public LabelsQueryServlet(LabelsQueryService queryService) {
        this.queryService = queryService;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        try {
            List<Quad> quadQueryList = obtainQuadQueries(request);
            ObjectNode resultNode = OBJECT_MAPPER.createObjectNode();
            resultNode.set("results", processQueryList(quadQueryList));
            processResponse(response, resultNode);
        } catch (SmartCacheGraphException ex) {
            handleError(response, OBJECT_MAPPER.createObjectNode(), HttpServletResponse.SC_BAD_REQUEST, "Unable to interpret JSON request");
        }
    }

    ArrayNode processQueryList(List<Quad> quadQueryList) {
        ArrayNode resultNodeList = OBJECT_MAPPER.createArrayNode();
        quadQueryList.forEach(quad -> {
            List<QuadLabels> results = processQuad(quad);
            results.forEach(r -> resultNodeList.add(r.toJSONNode()));
        });

        return resultNodeList;
    }

    List<QuadLabels> processQuad(Quad quad) {
        if (isWildcardQuad(quad)) {
            return queryService.queryDSGAndLabelStore(quad);
        } else {
            return queryService.queryOnlyLabelStore(quad);
        }
    }

    public static boolean isWildcardQuad(Quad quad) {
        return Node.ANY.equals(quad.getGraph()) || isWildcardTriple(quad.asTriple());
    }

    public static boolean isWildcardTriple(Triple triple) {
        if (Node.ANY.equals(triple.getSubject())) {
            return true;
        } else if (Node.ANY.equals(triple.getPredicate())) {
            return true;
        } else return Node.ANY.equals(triple.getObject());
    }

    private List<Quad> obtainQuadQueries(HttpServletRequest request) throws SmartCacheGraphException {
        List<Quad> quadList = new ArrayList<>();
        try (final InputStream inputStream = request.getInputStream()) {
            JsonNode rootNode = OBJECT_MAPPER.readTree(inputStream);
            final boolean hasQuads = rootNode != null && rootNode.has(QUADS);
            final boolean hasTriples = rootNode != null && rootNode.has(TRIPLES);
            if (hasQuads && hasTriples) {
                final String message = "Invalid JSON format: Provide either a 'quads' or a 'triples' array, not both.";
                LOG.warn(message);
                throw new SmartCacheGraphException(message);
            }
            // root node can be either 'triples' or 'quads'
            final JsonNode triples = hasTriples ? rootNode.get(TRIPLES) : null;
            final JsonNode queries = hasQuads ? rootNode.get(QUADS) : triples ;
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
        } catch (IOException | IllegalArgumentException exception) {
            LOG.warn("Failed to parse labels query request", exception);
            throw new SmartCacheGraphException(exception.getMessage());
        }
        return quadList;
    }

    private Quad getQuad(JsonQuad tripleQuery) throws SmartCacheGraphException {
        if (tripleQuery == null || tripleQuery.subject == null || tripleQuery.predicate == null
                || tripleQuery.object == null || tripleQuery.object.value == null) {
            throw new SmartCacheGraphException("Invalid JSON format: Incomplete triple.");
        }
        final Node s = getWildcardOrURI(tripleQuery.subject);
        final Node p = getWildcardOrURI(tripleQuery.predicate);
        final Node o = getObjectNode(tripleQuery.object.value);
        return Quad.create(getGraphNode(tripleQuery.graph), Triple.create(s, p, o));
    }

    private Node getGraphNode(String graph) {
        if (graph == null) {
            return Quad.defaultGraphIRI;
        }
        return getWildcardOrURI(graph);
    }

    private Node getWildcardOrURI(String object) {
        if (WILDCARD.equals(object)) {
            return Node.ANY;
        }
        return NodeFactory.createURI(object);
    }

    private Node getObjectNode(String object) {
        if (WILDCARD.equals(object)) {
            return Node.ANY;
        } else if (object.startsWith(HTTP) || object.startsWith(HTTPS)) {
            return NodeFactory.createURI(object);
        } else {
            return NodeFactory.createLiteralByValue(object);
        }
    }

}
