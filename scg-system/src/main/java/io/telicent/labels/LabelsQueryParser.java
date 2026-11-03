package io.telicent.labels;

import com.fasterxml.jackson.databind.JsonNode;
import io.telicent.model.JsonQuad;
import io.telicent.model.JsonQuads;
import io.telicent.model.JsonTripleObject;
import io.telicent.model.TypedObject;
import io.telicent.utils.SmartCacheGraphException;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.kafka.FusekiKafka;
import org.apache.jena.sparql.core.Quad;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;

/**
 * Parses labels query request bodies, as accepted by {@code /$/labels/<dataset>} and
 * {@code /<dataset>/access/labels}, into the quad patterns to query.
 */
public final class LabelsQueryParser {

    private static final Logger LOG = FusekiKafka.LOG;

    private static final String HTTP = "http://";
    private static final String HTTPS = "https://";
    private static final String WILDCARD = "*";
    private static final String QUADS = "quads";
    private static final String TRIPLES = "triples";

    /**
     * How the object of each requested triple is interpreted
     */
    public enum ObjectMode {
        /**
         * Objects starting with {@code http://} or {@code https://} are URIs and anything else is a string literal,
         * {@code object.dataType} and {@code object.language} are ignored
         */
        PLAIN,
        /**
         * As {@link #PLAIN} unless {@code object.dataType} or {@code object.language} is given, in which case the
         * object is a typed or language tagged literal. Giving both is rejected.
         */
        TYPED
    }

    private LabelsQueryParser() {
    }

    /**
     * Parses a labels query request body into the quad patterns to query.
     *
     * @param inputStream Request body
     * @param objectMode  How objects are interpreted
     * @return Quad patterns, with {@link Node#ANY} for any wildcard ({@code *}) values
     * @throws SmartCacheGraphException Thrown if the request is well-formed JSON but not a valid labels query, including,
     *                                  for {@link ObjectMode#TYPED}, an unknown data type or both a data type and a
     *                                  language
     * @throws IOException              Thrown if the request body can't be read or isn't well-formed JSON
     * @throws IllegalArgumentException Thrown if the request entries can't be converted to the expected structure
     */
    public static List<Quad> parse(InputStream inputStream, ObjectMode objectMode)
            throws SmartCacheGraphException, IOException {
        final List<Quad> quadList = new ArrayList<>();
        final JsonNode rootNode = OBJECT_MAPPER.readTree(inputStream);
        final boolean hasQuads = rootNode != null && rootNode.has(QUADS);
        final boolean hasTriples = rootNode != null && rootNode.has(TRIPLES);
        if (hasQuads && hasTriples) {
            final String message = "Invalid JSON format: Provide either a 'quads' or a 'triples' array, not both.";
            LOG.warn(message);
            throw new SmartCacheGraphException(message);
        }
        // root node can be either 'triples' or 'quads'
        final JsonNode triples = hasTriples ? rootNode.get(TRIPLES) : null;
        final JsonNode queries = hasQuads ? rootNode.get(QUADS) : triples;
        if (queries != null && queries.isArray()) {
            JsonQuads queryRequest = OBJECT_MAPPER.convertValue(rootNode, JsonQuads.class);
            for (JsonQuad query : queryRequest.quads) {
                quadList.add(getQuad(query, objectMode));
            }
        } else {
            final String message = "Invalid JSON format: Missing 'quads' or 'triples' array.";
            LOG.warn(message);
            throw new SmartCacheGraphException(message);
        }
        return quadList;
    }

    private static Quad getQuad(JsonQuad tripleQuery, ObjectMode objectMode) throws SmartCacheGraphException {
        if (tripleQuery == null || tripleQuery.subject == null || tripleQuery.predicate == null
                || tripleQuery.object == null || tripleQuery.object.value == null) {
            throw new SmartCacheGraphException("Invalid JSON format: Incomplete triple.");
        }
        final Node s = getWildcardOrURI(tripleQuery.subject);
        final Node p = getWildcardOrURI(tripleQuery.predicate);
        final Node o = objectMode == ObjectMode.TYPED ? getTypedObjectNode(tripleQuery.object)
                                                      : getPlainObjectNode(tripleQuery.object.value);
        return Quad.create(getGraphNode(tripleQuery.graph), Triple.create(s, p, o));
    }

    private static Node getGraphNode(String graph) {
        if (graph == null) {
            return Quad.defaultGraphIRI;
        }
        return getWildcardOrURI(graph);
    }

    private static Node getWildcardOrURI(String object) {
        if (WILDCARD.equals(object)) {
            return Node.ANY;
        }
        return NodeFactory.createURI(object);
    }

    private static Node getPlainObjectNode(String object) {
        if (WILDCARD.equals(object)) {
            return Node.ANY;
        } else if (object.startsWith(HTTP) || object.startsWith(HTTPS)) {
            return NodeFactory.createURI(object);
        } else {
            return NodeFactory.createLiteralByValue(object);
        }
    }

    private static Node getTypedObjectNode(JsonTripleObject object) throws SmartCacheGraphException {
        final boolean hasLanguage = object.language != null && !object.language.isEmpty();
        final boolean hasDataType = object.dataType != null && !object.dataType.isEmpty();
        if (WILDCARD.equals(object.value) || (!hasLanguage && !hasDataType)) {
            return getPlainObjectNode(object.value);
        }
        if (hasLanguage && hasDataType) {
            throw new SmartCacheGraphException("Cannot have both language and dataType in request object");
        }
        final TypedObject typedObject = TypedObject.from(object);
        if (hasLanguage) {
            return NodeFactory.createLiteralLang(typedObject.value, typedObject.language);
        }
        return NodeFactory.createLiteralDT(typedObject.value, typedObject.datatype);
    }
}
