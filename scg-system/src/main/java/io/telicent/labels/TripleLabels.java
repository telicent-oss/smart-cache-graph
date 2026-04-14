package io.telicent.labels;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.telicent.jena.abac.labels.Label;
import org.apache.jena.graph.Triple;

import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;

public class TripleLabels {

    public TripleLabels(Triple triple, Label label){
        this.triple = triple;
        this.label = label;
    }

    public Label label;
    public Triple triple;

    public ObjectNode toJSONNode() {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("subject", triple.getSubject().toString());
        node.put("predicate", triple.getPredicate().toString());
        node.put("object", triple.getObject().toString());
        ArrayNode labelNode = OBJECT_MAPPER.createArrayNode();
        if (label != null) {
            labelNode.add(label.getText());
        }
        node.set("labels", labelNode);
        return node;
    }
}
