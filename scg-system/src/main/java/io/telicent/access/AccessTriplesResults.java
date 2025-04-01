package io.telicent.access;

import io.telicent.model.JsonTriple;

import java.util.List;

public class AccessTriplesResults {

    public List<JsonTriple> triples;

    public boolean visible;

    public AccessTriplesResults(List<JsonTriple> triples, boolean visible) {
        this.triples = triples;
        this.visible = visible;
    }
}
