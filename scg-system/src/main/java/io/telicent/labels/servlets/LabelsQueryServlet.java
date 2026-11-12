package io.telicent.labels.servlets;

import io.telicent.labels.LabelsQueryParser;
import io.telicent.labels.LabelsQueryParser.ObjectMode;
import io.telicent.labels.QuadLabels;
import io.telicent.labels.services.LabelsQueryService;
import io.telicent.utils.SmartCacheGraphException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.kafka.FusekiKafka;
import org.apache.jena.sparql.core.Quad;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;
import static io.telicent.utils.ServletUtils.handleError;
import static io.telicent.utils.ServletUtils.processResponse;

// S1948: Embedded Jetty does not serialise these servlets or replicate their injected service state.
@SuppressWarnings("java:S1948")
public class LabelsQueryServlet extends HttpServlet {

    private static final Logger LOG = FusekiKafka.LOG;

    private final LabelsQueryService queryService;

    public LabelsQueryServlet(LabelsQueryService queryService) {
        this.queryService = queryService;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        try {
            List<Quad> quadQueryList = obtainQuadQueries(request);
            processResponse(response, QuadLabels.toResultsNode(processQueryList(quadQueryList)));
        } catch (SmartCacheGraphException ex) {
            handleError(response, OBJECT_MAPPER.createObjectNode(), HttpServletResponse.SC_BAD_REQUEST, "Unable to interpret JSON request");
        }
    }

    List<QuadLabels> processQueryList(List<Quad> quadQueryList) {
        final List<QuadLabels> results = new ArrayList<>();
        quadQueryList.forEach(quad -> results.addAll(processQuad(quad)));
        return results;
    }

    List<QuadLabels> processQuad(Quad quad) {
        if (isWildcardQuad(quad)) {
            return queryService.queryDSGAndLabelStore(quad);
        } else {
            return queryService.queryOnlyLabelStore(quad);
        }
    }

    public static boolean isWildcardQuad(Quad quad) {
        return Node.ANY.equals(quad.getGraph()) || isWildcardTriple(quad.asTriple());
    }

    public static boolean isWildcardTriple(Triple triple) {
        if (Node.ANY.equals(triple.getSubject())) {
            return true;
        } else if (Node.ANY.equals(triple.getPredicate())) {
            return true;
        } else return Node.ANY.equals(triple.getObject());
    }

    private List<Quad> obtainQuadQueries(HttpServletRequest request) throws SmartCacheGraphException {
        try (final InputStream inputStream = request.getInputStream()) {
            return LabelsQueryParser.parse(inputStream, ObjectMode.PLAIN);
        } catch (IOException | IllegalArgumentException exception) {
            LOG.warn("Failed to parse labels query request", exception);
            throw new SmartCacheGraphException(exception.getMessage());
        }
    }

}
