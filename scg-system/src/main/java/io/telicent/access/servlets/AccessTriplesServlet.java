package io.telicent.access.servlets;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.telicent.access.AccessTriplesResults;
import io.telicent.access.services.AccessQueryService;
import io.telicent.model.JsonTriple;
import io.telicent.model.JsonTriples;
import io.telicent.model.TypedObject;
import io.telicent.utils.SmartCacheGraphException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.fuseki.servlets.HttpAction;
import org.apache.jena.fuseki.system.ActionCategory;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.kafka.FusekiKafka;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;
import static io.telicent.utils.ServletUtils.handleError;
import static io.telicent.utils.ServletUtils.processResponse;

public class AccessTriplesServlet extends HttpServlet {

    private final static Logger LOG = FusekiKafka.LOG;

    private final AccessQueryService queryService;

    public AccessTriplesServlet(AccessQueryService queryService) {
        this.queryService = queryService;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        boolean requireAllVisible = isAllVisibleRequired(request);
        try (final InputStream inputStream = request.getInputStream()) {
            final JsonTriples query = OBJECT_MAPPER.readValue(inputStream, JsonTriples.class);
            final List<Triple> requestedTriples = getTripleList(query);
            final HttpAction action = new HttpAction(1L, LOG, ActionCategory.ACTION, request, response);
            final int requestedTriplesCount = query.triples.size();
            final int visibleTriplesCount = queryService.getVisibleTriplesCount(action, requestedTriples);
            final int notVisibleCount = requestedTriplesCount - visibleTriplesCount;
            if (notVisibleCount == requestedTriplesCount) {
                createResponse(response, query, false);
            } else if (notVisibleCount > 0 && requireAllVisible) {
                createResponse(response, query, false);
            } else {
                createResponse(response, query, true);
            }
        } catch (SmartCacheGraphException ex) {
            handleError(response, OBJECT_MAPPER.createObjectNode(), HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
        } catch (JsonProcessingException jpex) {
            handleError(response, OBJECT_MAPPER.createObjectNode(), HttpServletResponse.SC_BAD_REQUEST, "Unable to interpret JSON request");
        }
    }

    private void createResponse(HttpServletResponse response, JsonTriples query, boolean hasVisibility) {
        final AccessTriplesResults results = new AccessTriplesResults(query.triples, hasVisibility);
        processResponse(response, OBJECT_MAPPER.valueToTree(results));
    }

    private boolean isAllVisibleRequired(HttpServletRequest request) {
        String allParam = request.getParameter("all");
        if (allParam == null || allParam.isEmpty()) {
            return true; // default value
        }
        return Boolean.parseBoolean(allParam);
    }

    private List<Triple> getTripleList(JsonTriples triplesRequest) throws SmartCacheGraphException {
        try {
            final List<Triple> triples = new ArrayList<>();
            for (JsonTriple accessTriple : triplesRequest.triples) {
                final Node s = NodeFactory.createURI(Objects.requireNonNull(accessTriple.subject));
                final Node p = NodeFactory.createURI(Objects.requireNonNull(accessTriple.predicate));
                final Node o;
                if (accessTriple.object.value.isEmpty()) {
                    throw new SmartCacheGraphException("Triple object value cannot be empty");
                } else {
                    if (accessTriple.object.language == null || accessTriple.object.language.isEmpty()) {
                        // no language so either a typed literal or a URI
                        if (accessTriple.object.dataType == null || accessTriple.object.dataType.isEmpty()) {
                            // no datatype so value must be a URI
                            final URI objectUri = URI.create(Objects.requireNonNull(accessTriple.object.value));
                            o = NodeFactory.createURI(objectUri.toString());
                        } else {
                            // must be a typed literal (this could include a literal URI value)
                            final TypedObject typedObject = Objects.requireNonNull(TypedObject.from(accessTriple.object));
                            o = NodeFactory.createLiteralDT(typedObject.value, typedObject.datatype);
                        }
                    } else if (accessTriple.object.dataType == null || accessTriple.object.dataType.isEmpty()) {
                        final TypedObject typedObject = Objects.requireNonNull(TypedObject.from(accessTriple.object));
                        o = NodeFactory.createLiteralLang(typedObject.value, typedObject.language);
                    } else {
                        throw new SmartCacheGraphException("Cannot have both language and dataType in request object");
                    }
                }
                triples.add(Triple.create(s, p, o));
            }
            return triples;
        } catch (NullPointerException npex) {
            throw new SmartCacheGraphException("Unable to process request as missing required values");
        } catch (IllegalArgumentException iaex){
            throw new SmartCacheGraphException(iaex.getMessage());
        }
    }
}
