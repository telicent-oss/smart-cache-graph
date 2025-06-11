package io.telicent.labels;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.telicent.jena.abac.labels.Label;
import org.apache.jena.graph.Triple;

import java.util.List;

import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;

public class TripleLabels {

    public TripleLabels(Triple triple, List<Label> labels){
        this.triple = triple;
        this.labels = labels;
    }

    public List<Label> labels;
    public Triple triple;

    public ObjectNode toJSONNode() {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("subject", triple.getSubject().toString());
        node.put("predicate", triple.getPredicate().toString());
        node.put("object", triple.getObject().toString());
        ArrayNode labelNode = OBJECT_MAPPER.createArrayNode();
        labels.forEach(l -> labelNode.add(l.getText()));
        node.set("labels", labelNode);
        return node;
    }
}
