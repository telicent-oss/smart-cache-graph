package io.telicent.labels.servlets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.telicent.labels.LabelsQuery;
import io.telicent.labels.TripleLabels;
import io.telicent.labels.services.LabelsQueryService;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static io.telicent.backup.utils.BackupUtils.processResponse;

public class LabelsQueryServlet extends HttpServlet {

    private final static String HTTP = "http://";
    private final static String HTTPS = "https://";
    private final static String WILDCARD = "*";

    private final LabelsQueryService queryService;

    public static ObjectMapper MAPPER = new ObjectMapper();

    public LabelsQueryServlet(LabelsQueryService queryService) {
        this.queryService = queryService;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        List<Triple> tripleQueryList = obtainTripleQueries(request);
        ObjectNode resultNode = MAPPER.createObjectNode();
        resultNode.set("results", processQueryList(tripleQueryList));
        processResponse(response, resultNode);
    }

    ArrayNode processQueryList(List<Triple> tripleQueryList) {
        ArrayNode resultNodeList = MAPPER.createArrayNode();
        tripleQueryList.forEach(triple -> {
            List<TripleLabels> results = processTriple(triple);
            resultNodeList.add(populateNodeWithResults(results));
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

    ArrayNode populateNodeWithResults(List<TripleLabels> tripleLabels) {
        ArrayNode resultNode = MAPPER.createArrayNode();
        tripleLabels.forEach(tripleLabel -> {
            resultNode.add(tripleLabel.toJSONNode());
        });
        return resultNode;
    }

    public static boolean isWildcardTriple(Triple triple) {
        if (Node.ANY.equals(triple.getSubject())) {
            return true;
        } else if (Node.ANY.equals(triple.getPredicate())) {
            return true;
        } else return Node.ANY.equals(triple.getObject());
    }

    List<Triple> obtainTripleQueries(HttpServletRequest request) {
        List<Triple> tripleList = new ArrayList<>();
        try (final InputStream inputStream = request.getInputStream()) {
                JsonNode rootNode = MAPPER.readTree(inputStream); // Read into a JsonNode
                if (rootNode.isArray()) {
                    for (JsonNode node : rootNode) {
                        LabelsQuery query = MAPPER.convertValue(node, LabelsQuery.class); // Convert each node
                        tripleList.add(getTriple(query));
                    }
                } else if (rootNode.isObject()) {
                    LabelsQuery query = MAPPER.convertValue(rootNode, LabelsQuery.class); // Convert the root node
                    tripleList.add(getTriple(query));
                } else {
                    // IGNORE?
                    System.err.println("Invalid JSON format: Must be an object or an array.");
                }

            } catch (IOException exception) {
                // Handle I/O errors
                exception.printStackTrace(); // Replace with proper logging
                // ... appropriate error handling ...
            }
        return tripleList;
    }

    List<Triple> obtainTripleQueries2(HttpServletRequest request) {
        List<Triple> tripleList = new ArrayList<>();
        try (final InputStream inputStream = request.getInputStream()) {
            try {
                LabelsQuery json = MAPPER.readValue(inputStream, LabelsQuery.class);
                tripleList.add(getTriple(json));
            } catch (MismatchedInputException exception) {
                LabelsQuery[] queryArray = MAPPER.readValue(inputStream, LabelsQuery[].class);
                for (LabelsQuery query : queryArray) {
                    tripleList.add(getTriple(query));
                }
            }
        } catch (IOException exception) {
            // do nothing
        }
        return tripleList;
    }

    private static void processResponseTriple(HttpServletResponse response, Triple incomingTriple, TripleLabels labels) {
        ObjectNode resultNode = MAPPER.createObjectNode();
        resultNode.put("asString", incomingTriple.toString());
        resultNode.put("querySubject", incomingTriple.getSubject().toString());
        resultNode.put("queryPredicate", incomingTriple.getPredicate().toString());
        resultNode.put("queryObject", incomingTriple.getObject().toString());
        ArrayNode labelNode = MAPPER.createArrayNode();
        labels.labels.forEach(labelNode::add);
        resultNode.set("labels", labelNode);
        processResponse(response, resultNode);
    }

    private Triple getTriple(LabelsQuery tripleQuery) {
        final Node s = getWildcardOrURI(tripleQuery.subject);
        final Node p = getWildcardOrURI(tripleQuery.predicate);
        final Node o = getObjectNode(tripleQuery.object);
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
