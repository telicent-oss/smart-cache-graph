package io.telicent.labels;

import io.telicent.labels.LabelsQueryParser.ObjectMode;
import io.telicent.utils.SmartCacheGraphException;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.sparql.core.Quad;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link LabelsQueryParser} in {@link ObjectMode#PLAIN}, as used by {@code /$/labels/<dataset>}, which ignores the
 * object's {@code dataType} and {@code language}. {@link ObjectMode#TYPED} is covered by
 * {@code TestAccessLabelsServletParsing}.
 */
public class TestLabelsQueryParser {

    private static String request(String object) {
        return """
                {
                  "triples": [ {
                    "subject": "http://example.org/s",
                    "predicate": "http://example.org/p",
                    "object": %s
                  } ]
                }""".formatted(object);
    }

    private static List<Quad> parse(String body) throws Exception {
        return LabelsQueryParser.parse(stream(body), ObjectMode.PLAIN);
    }

    private static Node parseObject(String object) throws Exception {
        final List<Quad> quads = parse(request(object));
        assertEquals(1, quads.size());
        return quads.getFirst().getObject();
    }

    private static ByteArrayInputStream stream(String body) {
        return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void givenNoDataTypeOrLanguage_whenParsing_thenUriOrStringLiteral() throws Exception {
        assertEquals(NodeFactory.createURI("http://example.org/o"),
                     parseObject("{ \"value\": \"http://example.org/o\" }"));
        assertEquals(NodeFactory.createURI("https://example.org/o"),
                     parseObject("{ \"value\": \"https://example.org/o\" }"));
        assertEquals(NodeFactory.createLiteralString("8799800"), parseObject("{ \"value\": \"8799800\" }"));
        assertEquals(Node.ANY, parseObject("{ \"value\": \"*\" }"));
    }

    @Test
    public void givenDataType_whenParsing_thenIgnored() throws Exception {
        assertEquals(NodeFactory.createLiteralString("8799800"),
                     parseObject("{ \"value\": \"8799800\", \"dataType\": \"xsd:integer\" }"));
        assertEquals(NodeFactory.createURI("https://www.london.gov.uk/"),
                     parseObject("{ \"value\": \"https://www.london.gov.uk/\", \"dataType\": \"xsd:anyURI\" }"));
    }

    @Test
    public void givenLanguage_whenParsing_thenIgnored() throws Exception {
        assertEquals(NodeFactory.createLiteralString("London"),
                     parseObject("{ \"value\": \"London\", \"language\": \"en\" }"));
    }

    @Test
    public void givenDataTypeAndLanguage_whenParsing_thenNotRejected() throws Exception {
        assertEquals(NodeFactory.createLiteralString("London"), parseObject(
                "{ \"value\": \"London\", \"dataType\": \"xsd:string\", \"language\": \"en\" }"));
    }

    @Test
    public void givenUnknownDataType_whenParsing_thenNotRejected() throws Exception {
        assertEquals(NodeFactory.createLiteralString("1"),
                     parseObject("{ \"value\": \"1\", \"dataType\": \"ex:unknown\" }"));
    }

    @Test
    public void givenGraph_whenParsing_thenDefaultWildcardOrNamedGraph() throws Exception {
        final List<Quad> quads = parse("""
                {
                  "quads": [
                    { "subject": "*", "predicate": "*", "object": { "value": "*" } },
                    { "subject": "*", "predicate": "*", "object": { "value": "*" }, "graph": "*" },
                    { "subject": "*", "predicate": "*", "object": { "value": "*" }, "graph": "http://example.org/g" }
                  ]
                }""");
        assertEquals(3, quads.size());
        assertEquals(Quad.defaultGraphIRI, quads.get(0).getGraph());
        assertEquals(Node.ANY, quads.get(1).getGraph());
        assertEquals(NodeFactory.createURI("http://example.org/g"), quads.get(2).getGraph());
        assertEquals(Node.ANY, quads.get(0).getSubject());
        assertEquals(Node.ANY, quads.get(0).getPredicate());
    }

    @Test
    public void givenQuadsAndTriples_whenParsing_thenRejected() {
        assertThrows(SmartCacheGraphException.class, () -> parse("{ \"quads\": [], \"triples\": [] }"));
    }

    @Test
    public void givenNoQueries_whenParsing_thenRejected() {
        assertThrows(SmartCacheGraphException.class, () -> parse("{}"));
        assertThrows(SmartCacheGraphException.class, () -> parse("{ \"triples\": {} }"));
    }

    @Test
    public void givenIncompleteTriple_whenParsing_thenRejected() {
        assertThrows(SmartCacheGraphException.class,
                     () -> parse("{ \"triples\": [ { \"subject\": \"http://example.org/s\" } ] }"));
    }

    @Test
    public void givenMalformedJson_whenParsing_thenIOException() {
        assertThrows(IOException.class, () -> parse("not json"));
    }
}
