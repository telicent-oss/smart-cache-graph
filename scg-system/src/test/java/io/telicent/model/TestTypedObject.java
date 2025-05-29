package io.telicent.model;

import io.telicent.utils.SmartCacheGraphException;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.datatypes.xsd.impl.XMLLiteralType;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.XSD;
import org.junit.jupiter.api.Test;

import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;
import static org.junit.jupiter.api.Assertions.*;

public class TestTypedObject {

    @Test
    public void testXsdInteger() throws Exception {
        final JsonTripleObject tripleObject = OBJECT_MAPPER.readValue(
                getJsonString("xsd:integer", "123"), JsonTripleObject.class);
        final TypedObject result = TypedObject.from(tripleObject);
        assertNotNull(result);
        assertEquals(result.datatype.getURI(), XSDDatatype.XSDinteger.getURI());
    }

    @Test
    public void testXsdNonNegativeInteger() throws Exception {
        final JsonTripleObject tripleObject = OBJECT_MAPPER.readValue(
                getJsonString("xsd:nonNegativeInteger", "123"), JsonTripleObject.class);
        final TypedObject result = TypedObject.from(tripleObject);
        assertNotNull(result);
        assertEquals(XSDDatatype.XSDnonNegativeInteger.getURI(), result.datatype.getURI());
    }

    @Test
    public void testXsdString() throws Exception {
        final JsonTripleObject tripleObject = OBJECT_MAPPER.readValue(
                getJsonString("xsd:string", "123"), JsonTripleObject.class);
        final TypedObject result = TypedObject.from(tripleObject);
        assertNotNull(result);
        assertEquals(XSDDatatype.XSDstring.getURI(), result.datatype.getURI());
    }

    @Test
    public void testXsdStringFullUri() throws Exception {
        final JsonTripleObject tripleObject = OBJECT_MAPPER.readValue(
                getJsonString(XSD.NS + "string", "123"), JsonTripleObject.class);
        final TypedObject result = TypedObject.from(tripleObject);
        assertNotNull(result);
        assertEquals(XSDDatatype.XSDstring.getURI(), result.datatype.getURI());
    }

    @Test
    public void testXsdAnyUri() throws Exception {
        final JsonTripleObject tripleObject = OBJECT_MAPPER.readValue(
                getJsonString("xsd:anyURI", "123"), JsonTripleObject.class);
        final TypedObject result = TypedObject.from(tripleObject);
        assertNotNull(result);
        assertEquals(XSDDatatype.XSDanyURI.getURI(), result.datatype.getURI());
    }

    @Test
    public void testXsdDate() throws Exception {
        final JsonTripleObject tripleObject = OBJECT_MAPPER.readValue(
                getJsonString("xsd:date", "2025-04-03"), JsonTripleObject.class);
        final TypedObject result = TypedObject.from(tripleObject);
        assertNotNull(result);
        assertEquals(XSDDatatype.XSDdate.getURI(), result.datatype.getURI());
    }

    @Test
    public void testXsdDateTime() throws Exception {
        final JsonTripleObject tripleObject = OBJECT_MAPPER.readValue(
                getJsonString("xsd:dateTime", "2025-04-03T14:00:00Z"), JsonTripleObject.class);
        final TypedObject result = TypedObject.from(tripleObject);
        assertNotNull(result);
        assertEquals(XSDDatatype.XSDdateTime.getURI(), result.datatype.getURI());
    }

    @Test
    public void testUnknownUnknown() throws Exception {
        final JsonTripleObject tripleObject = OBJECT_MAPPER.readValue(
                getJsonString("unknown:unknown", "123"), JsonTripleObject.class);
        Exception exception = assertThrows(SmartCacheGraphException.class, () -> TypedObject.from(tripleObject));
        assertEquals("Unknown data type: unknown:unknown", exception.getMessage(), "Unexpected exception message");
    }

    @Test
    public void testUnknownHttpUri() throws Exception {
        final JsonTripleObject tripleObject = OBJECT_MAPPER.readValue(
                getJsonString("http://example.org#unknown", "123"), JsonTripleObject.class);
        Exception exception = assertThrows(SmartCacheGraphException.class, () -> TypedObject.from(tripleObject));
        assertEquals("Unknown data type URI: http://example.org#unknown", exception.getMessage(), "Unexpected exception message");
    }

    /* The following test passes because, by default, Jena allow silent registration of unknown datatypes:
    /* https://jena.apache.org/documentation/javadoc/jena/org.apache.jena.core/org/apache/jena/shared/impl/JenaParameters.html#enableSilentAcceptanceOfUnknownDatatypes).
     */
    @Test
    public void testXsdUnknown() throws Exception {
        final JsonTripleObject tripleObject = OBJECT_MAPPER.readValue(
                getJsonString("xsd:unknown", "123"), JsonTripleObject.class);
        final TypedObject result = TypedObject.from(tripleObject);
        assertNotNull(result);
        assertEquals(XSD.NS + "unknown", result.datatype.getURI());
    }

    @Test
    public void testRdfUnknown() throws Exception {
        final JsonTripleObject tripleObject = OBJECT_MAPPER.readValue(
                getJsonString("rdf:unknown", "123"), JsonTripleObject.class);
        Exception exception = assertThrows(SmartCacheGraphException.class, () -> TypedObject.from(tripleObject));
        assertEquals("Unknown data type: rdf:unknown", exception.getMessage(), "Unexpected exception message");
    }


    @Test
    public void testRdfXmlLiteral() throws Exception {
        final JsonTripleObject tripleObject = OBJECT_MAPPER.readValue(
                getJsonString("rdf:XMLLiteral", "<test>test</test>"), JsonTripleObject.class);
        final TypedObject result = TypedObject.from(tripleObject);
        assertNotNull(result);
        assertEquals(XMLLiteralType.rdfXMLLiteral.getURI(), result.datatype.getURI());
    }

    @Test
    public void testRdfXmlLiteralFullUri() throws Exception {
        final JsonTripleObject tripleObject = OBJECT_MAPPER.readValue(
                getJsonString(RDF.uri + "XMLLiteral", "<test>test</test>"), JsonTripleObject.class);
        final TypedObject result = TypedObject.from(tripleObject);
        assertNotNull(result);
        assertEquals(XMLLiteralType.rdfXMLLiteral.getURI(), result.datatype.getURI());
    }

    private String getJsonString(final String dataType, final String value) {
        return """
                {
                  "dataType":"%s",
                  "value":"%s"
                }""".formatted(dataType, value);
    }


}
