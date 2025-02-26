package io.telicent.labels;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.jena.graph.Triple;

import java.util.List;

public class TripleLabels {

    public static ObjectMapper MAPPER = new ObjectMapper();

    public TripleLabels(Triple triple, List<String> labels){
        this.triple = triple;
        this.labels = labels;
    }

    public List<String> labels;
    public Triple triple;

    public ObjectNode toJSONNode() {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("subject", triple.getSubject().toString());
        node.put("predicate", triple.getPredicate().toString());
        node.put("object", triple.getObject().toString());
        ArrayNode labelNode = MAPPER.createArrayNode();
        labels.forEach(labelNode::add);
        node.set("labels", labelNode);
        return node;
    }
}
