package io.telicent.model;

import io.telicent.utils.SmartCacheGraphException;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.datatypes.xsd.impl.XMLLiteralType;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.XSD;

import java.util.Objects;

import static io.telicent.utils.ServletUtils.*;

public class TypedObject {
    public RDFDatatype datatype;
    public String value;
    public String language;

    public TypedObject(RDFDatatype datatype, String value){
        this.datatype = datatype;
        this.value = value;
    }

    public TypedObject(RDFDatatype datatype, String value, String language) {
        this.datatype = datatype;
        this.value = value;
        this.language = language;
    }

    /**
     * Takes a JSON RDF triple object representation and attempts to create a object encapsulating the RDFDataType that
     * can be understood by Apache Jena
     *
     * @param jsonTripleObject the JSON representation of the RDF object
     * @return the TypedObject
     * @throws SmartCacheGraphException if unable to resolve the RDFDataType
     */
    public static TypedObject from(final JsonTripleObject jsonTripleObject) throws SmartCacheGraphException {
        if(jsonTripleObject.language != null && !jsonTripleObject.language.isEmpty()) {
            return new TypedObject(RDF.dtLangString, jsonTripleObject.value, jsonTripleObject.language);
        } else if (jsonTripleObject.dataType.startsWith(HTTP) || jsonTripleObject.dataType.startsWith(HTTPS)) {
            return getTypedObjectFromUriValue(jsonTripleObject);
        } else {
            final String[] tokens = jsonTripleObject.dataType.split(":");
            return getTypedObjectFromPrefixedValue(jsonTripleObject, tokens);
        }
    }

    private static TypedObject getTypedObjectFromPrefixedValue(JsonTripleObject jsonTripleObject, String[] tokens) throws SmartCacheGraphException {
        try {
            if (tokens[0].equals("xsd")) {
                final RDFDatatype dt = TypeMapper.getInstance().getSafeTypeByName(XSD.NS + tokens[1]);
                Objects.requireNonNull(dt);
                return new TypedObject(dt, jsonTripleObject.value);
            } else if (tokens[0].equals("rdf")) {
                if (tokens[1].equals("XMLLiteral")) {
                    final RDFDatatype dt = TypeMapper.getInstance().getSafeTypeByName(XMLLiteralType.XMLLiteralTypeURI);
                    return new TypedObject(dt, jsonTripleObject.value);
                } else {
                    throw new SmartCacheGraphException("Unknown data type: " + jsonTripleObject.dataType);
                }
            } else {
                throw new SmartCacheGraphException("Unknown data type: " + jsonTripleObject.dataType);
            }
        } catch (NullPointerException nex) {
            throw new SmartCacheGraphException("Unknown data type: " + jsonTripleObject.dataType);
        }
    }

    private static TypedObject getTypedObjectFromUriValue(JsonTripleObject jsonTripleObject) throws SmartCacheGraphException {
        String dataTypeUri = jsonTripleObject.dataType;
        if (dataTypeUri.startsWith(XSD.NS)) {
            final RDFDatatype dt = TypeMapper.getInstance().getSafeTypeByName(dataTypeUri);
            Objects.requireNonNull(dt);
            return new TypedObject(dt, jsonTripleObject.value);
        } else if (dataTypeUri.startsWith(RDF.uri)) {
            if (dataTypeUri.substring(RDF.uri.length()).equals("XMLLiteral")) {
                final RDFDatatype dt = TypeMapper.getInstance().getSafeTypeByName(XMLLiteralType.rdfXMLLiteral.getURI());
                return new TypedObject(dt, jsonTripleObject.value);
            } else {
                throw new SmartCacheGraphException("Unknown data type URI: " + dataTypeUri);
            }
        } else {
            throw new SmartCacheGraphException("Unknown data type URI: " + dataTypeUri);
        }
    }

}
