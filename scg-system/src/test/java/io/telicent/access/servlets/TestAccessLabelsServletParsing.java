package io.telicent.access.servlets;

import io.telicent.labels.LabelsQueryParser;
import io.telicent.labels.LabelsQueryParser.ObjectMode;
import io.telicent.utils.SmartCacheGraphException;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.sparql.core.Quad;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the request parsing of {@code /<dataset>/access/labels}, in particular the handling of the object's
 * {@code dataType} and {@code language}.
 */
public class TestAccessLabelsServletParsing {

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

    private static Node parseObject(String object) throws Exception {
        final List<Quad> quads = LabelsQueryParser.parse(stream(request(object)), ObjectMode.TYPED);
        assertEquals(1, quads.size());
        return quads.getFirst().getObject();
    }

    private static ByteArrayInputStream stream(String body) {
        return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void givenPrefixedDataType_whenParsing_thenTypedLiteral() throws Exception {
        assertEquals(NodeFactory.createLiteralDT("8799800", XSDDatatype.XSDinteger),
                     parseObject("{ \"value\": \"8799800\", \"dataType\": \"xsd:integer\" }"));
    }

    @Test
    public void givenFullDataTypeUri_whenParsing_thenTypedLiteral() throws Exception {
        assertEquals(NodeFactory.createLiteralDT("8799800", XSDDatatype.XSDinteger), parseObject(
                "{ \"value\": \"8799800\", \"dataType\": \"http://www.w3.org/2001/XMLSchema#integer\" }"));
    }

    @Test
    public void givenAnyUriDataType_whenParsing_thenLiteralRatherThanUri() throws Exception {
        assertEquals(NodeFactory.createLiteralDT("https://www.london.gov.uk/", XSDDatatype.XSDanyURI),
                     parseObject("{ \"value\": \"https://www.london.gov.uk/\", \"dataType\": \"xsd:anyURI\" }"));
    }

    @Test
    public void givenLanguage_whenParsing_thenLanguageTaggedLiteral() throws Exception {
        assertEquals(NodeFactory.createLiteralLang("London", "en"),
                     parseObject("{ \"value\": \"London\", \"language\": \"en\" }"));
    }

    @Test
    public void givenNoDataTypeOrLanguage_whenParsing_thenUriOrStringLiteral() throws Exception {
        assertEquals(NodeFactory.createURI("http://example.org/o"),
                     parseObject("{ \"value\": \"http://example.org/o\" }"));
        assertEquals(NodeFactory.createURI("https://example.org/o"),
                     parseObject("{ \"value\": \"https://example.org/o\" }"));
        assertEquals(NodeFactory.createLiteralString("8799800"), parseObject("{ \"value\": \"8799800\" }"));
        assertEquals(NodeFactory.createLiteralString("8799800"),
                     parseObject("{ \"value\": \"8799800\", \"dataType\": \"\" }"));
        assertEquals(Node.ANY, parseObject("{ \"value\": \"*\" }"));
    }

    @Test
    public void givenWildcardWithDataType_whenParsing_thenWildcard() throws Exception {
        assertEquals(Node.ANY, parseObject("{ \"value\": \"*\", \"dataType\": \"xsd:integer\" }"));
    }

    @Test
    public void givenDataTypeAndLanguage_whenParsing_thenRejected() {
        assertThrows(SmartCacheGraphException.class, () -> parseObject(
                "{ \"value\": \"London\", \"dataType\": \"xsd:string\", \"language\": \"en\" }"));
    }

    @Test
    public void givenUnknownDataType_whenParsing_thenRejected() {
        assertThrows(SmartCacheGraphException.class,
                     () -> parseObject("{ \"value\": \"1\", \"dataType\": \"ex:unknown\" }"));
        assertThrows(SmartCacheGraphException.class,
                     () -> parseObject("{ \"value\": \"1\", \"dataType\": \"http://example.org/unknown\" }"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "xsd", "xsd:", ":", "rdf:", "http://" })
    public void givenMalformedDataType_whenParsing_thenRejected(String dataType) {
        assertThrows(SmartCacheGraphException.class,
                     () -> parseObject("{ \"value\": \"1\", \"dataType\": \"" + dataType + "\" }"));
    }

    @Test
    public void givenMalformedJson_whenParsing_thenIOException() {
        assertThrows(IOException.class, () -> LabelsQueryParser.parse(stream("not json"), ObjectMode.TYPED));
    }
}
