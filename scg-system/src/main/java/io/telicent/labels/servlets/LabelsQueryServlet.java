package io.telicent.labels.servlets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.telicent.labels.TripleLabels;
import io.telicent.labels.services.LabelsQueryService;
import io.telicent.model.JsonTriple;
import io.telicent.model.JsonTriples;
import io.telicent.utils.SmartCacheGraphException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.kafka.FusekiKafka;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;
import static io.telicent.utils.ServletUtils.handleError;
import static io.telicent.utils.ServletUtils.processResponse;

public class LabelsQueryServlet extends HttpServlet {

    private final static Logger LOG = FusekiKafka.LOG;

    private final static String HTTP = "http://";
    private final static String HTTPS = "https://";
    private final static String WILDCARD = "*";

    private final LabelsQueryService queryService;

    public LabelsQueryServlet(LabelsQueryService queryService) {
        this.queryService = queryService;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        try {
            List<Triple> tripleQueryList = obtainTripleQueries(request);
            ObjectNode resultNode = OBJECT_MAPPER.createObjectNode();
            resultNode.set("results", processQueryList(tripleQueryList));
            processResponse(response, resultNode);
        } catch (SmartCacheGraphException ex) {
            handleError(response, OBJECT_MAPPER.createObjectNode(), HttpServletResponse.SC_BAD_REQUEST, "Unable to interpret JSON request");
        }
    }

    ArrayNode processQueryList(List<Triple> tripleQueryList) {
        ArrayNode resultNodeList = OBJECT_MAPPER.createArrayNode();
        tripleQueryList.forEach(triple -> {
            List<TripleLabels> results = processTriple(triple);
            results.forEach(tripleLabel -> resultNodeList.add(tripleLabel.toJSONNode()));
        });

        return resultNodeList;
    }

    List<TripleLabels> processTriple(Triple triple) {
        if (isWildcardTriple(triple)) {
            return queryService.queryDSGAndLabelStore(triple);
        } else {
            return queryService.queryOnlyLabelStore(triple);
        }
    }

    public static boolean isWildcardTriple(Triple triple) {
        if (Node.ANY.equals(triple.getSubject())) {
            return true;
        } else if (Node.ANY.equals(triple.getPredicate())) {
            return true;
        } else return Node.ANY.equals(triple.getObject());
    }

    private List<Triple> obtainTripleQueries(HttpServletRequest request) throws SmartCacheGraphException {
        List<Triple> tripleList = new ArrayList<>();
        try (final InputStream inputStream = request.getInputStream()) {
            JsonNode rootNode = OBJECT_MAPPER.readTree(inputStream);
            if (rootNode.has("triples") && rootNode.get("triples").isArray()) {
                JsonTriples queryRequest = OBJECT_MAPPER.convertValue(rootNode, JsonTriples.class);
                for (JsonTriple query : queryRequest.triples) {
                    tripleList.add(getTriple(query));
                }
            } else {
                final String message = "Invalid JSON format: Missing 'triples' array.";
                LOG.warn(message);
                throw new SmartCacheGraphException(message);
            }
        } catch (IOException | IllegalArgumentException exception) {
            LOG.warn("Failed to parse labels query request", exception);
            throw new SmartCacheGraphException(exception.getMessage());
        }
        return tripleList;
    }

    private Triple getTriple(JsonTriple tripleQuery) {
        final Node s = getWildcardOrURI(tripleQuery.subject);
        final Node p = getWildcardOrURI(tripleQuery.predicate);
        final Node o = getObjectNode(tripleQuery.object.value);
        return Triple.create(s, p, o);
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
