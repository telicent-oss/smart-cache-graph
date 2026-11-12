package io.telicent.labels;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.telicent.smart.cache.security.data.labels.SecurityLabels;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.core.Quad;

import java.util.List;

import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;

public class QuadLabels {

    public QuadLabels(Quad quad, SecurityLabels<?> label){
        this.graph = quad.getGraph();
        this.triple = quad.asTriple();
        this.label = label;
    }

    public SecurityLabels<?> label;
    public Triple triple;
    public Node graph;

    public ObjectNode toJSONNode() {
        final ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("subject", triple.getSubject().toString());
        node.put("predicate", triple.getPredicate().toString());
        node.put("object", triple.getObject().toString());
        // Default graph is implied when graph is absent
        if (!Quad.isDefaultGraph(graph)) {
            node.put("graph", graph.toString());
        }
        final ArrayNode labelNode = OBJECT_MAPPER.createArrayNode();
        if (label != null) {
            labelNode.add(label.toDebugString());
        }
        node.set("labels", labelNode);
        return node;
    }

    /**
     * Builds the labels endpoints' response body, {@code {"results": [...]}}, from the quads found
     *
     * @param results Quads and their labels
     * @return Response body
     */
    public static ObjectNode toResultsNode(List<QuadLabels> results) {
        final ArrayNode resultNodeList = OBJECT_MAPPER.createArrayNode();
        results.forEach(r -> resultNodeList.add(r.toJSONNode()));
        final ObjectNode resultNode = OBJECT_MAPPER.createObjectNode();
        resultNode.set("results", resultNodeList);
        return resultNode;
    }
}
