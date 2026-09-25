package io.telicent.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * A triple with an optional graph. An absent graph means the default graph.
 */
public class JsonQuad extends JsonTriple {
    @JsonInclude(Include.NON_NULL)
    public String graph;
}
