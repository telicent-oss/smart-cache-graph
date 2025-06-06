package io.telicent.access.servlets;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.telicent.access.AccessQuery;
import io.telicent.access.AccessQueryResults;
import io.telicent.access.services.AccessQueryService;
import io.telicent.model.JsonTripleObject;
import io.telicent.utils.SmartCacheGraphException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.fuseki.servlets.HttpAction;
import org.apache.jena.fuseki.system.ActionCategory;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.kafka.FusekiKafka;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;
import static io.telicent.utils.ServletUtils.handleError;
import static io.telicent.utils.ServletUtils.processResponse;

public class AccessQueryServlet extends HttpServlet {

    private final static Logger LOG = FusekiKafka.LOG;

    private final AccessQueryService queryService;

    public AccessQueryServlet(AccessQueryService queryService) {
        this.queryService = queryService;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try (final InputStream inputStream = request.getInputStream()) {
            final AccessQuery query = OBJECT_MAPPER.readValue(inputStream, AccessQuery.class);
            final Triple accessQueryTriple = getAccessQueryTriple(query);
            final HttpAction action = new HttpAction(1L, LOG, ActionCategory.ACTION, request, response);
            final List<Triple> triples = queryService.getTriples(action, accessQueryTriple);
            final List<JsonTripleObject> results = new ArrayList<>();
            for (Triple triple : triples) {
                final Node tripleObject = triple.getObject();
                if (tripleObject.isURI()) {
                    final JsonTripleObject jsonTripleObject = new JsonTripleObject(
                            XSDDatatype.XSDanyURI.getURI(),
                            tripleObject.getURI());
                    results.add(jsonTripleObject);
                } else {
                    final JsonTripleObject jsonTripleObject = new JsonTripleObject(
                            tripleObject.getLiteralDatatype().getURI(),
                            tripleObject.getLiteralValue().toString());
                    results.add(jsonTripleObject);
                }
            }
            final AccessQueryResults queryResults = new AccessQueryResults(query, (results.isEmpty() ? null : results));
            processResponse(response, OBJECT_MAPPER.valueToTree(queryResults));
        } catch (SmartCacheGraphException ex) {
            handleError(response, OBJECT_MAPPER.createObjectNode(), HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
        } catch (JsonProcessingException ex) {
            handleError(response, OBJECT_MAPPER.createObjectNode(), HttpServletResponse.SC_BAD_REQUEST, "Missing or invalid request body content");
        }
    }

    private Triple getAccessQueryTriple(AccessQuery accessQuery) throws SmartCacheGraphException {
        try {
            final Node s = NodeFactory.createURI(Objects.requireNonNull(accessQuery.subject));
            final Node p = NodeFactory.createURI(Objects.requireNonNull(accessQuery.predicate));
            final Node o = Node.ANY;
            return Triple.create(s, p, o);
        } catch (NullPointerException npex) {
            throw new SmartCacheGraphException("Unable to process request as missing required values");
        }
    }

}
