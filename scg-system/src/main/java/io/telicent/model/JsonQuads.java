package io.telicent.model;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;

public class JsonQuads {

    /**
     * The quads in the request, supplied under either a {@code quads} or a {@code triples} key.
     */
    @JsonAlias("triples")
    public List<JsonQuad> quads;
}
