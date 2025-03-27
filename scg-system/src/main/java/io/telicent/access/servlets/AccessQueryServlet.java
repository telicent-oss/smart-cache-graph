package io.telicent.access.servlets;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.telicent.access.AccessQuery;
import io.telicent.access.AccessQueryResults;
import io.telicent.access.services.AccessQueryService;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.fuseki.servlets.HttpAction;
import org.apache.jena.fuseki.system.ActionCategory;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.kafka.FusekiKafka;
import org.apache.jena.riot.WebContent;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class AccessQueryServlet extends HttpServlet {

    private final static Logger LOG = FusekiKafka.LOG;

    public static ObjectMapper MAPPER = new ObjectMapper();

    private final AccessQueryService queryService;

    public AccessQueryServlet(AccessQueryService queryService) {
        this.queryService = queryService;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try (final InputStream inputStream = request.getInputStream()) {
            final AccessQuery query = MAPPER.readValue(inputStream, AccessQuery.class);
            final Triple accessQueryTriple = getAccessQueryTriple(query);
            final HttpAction action = new HttpAction(1L, LOG, ActionCategory.ACTION, request, response);
            final List<Triple> triples = queryService.getTriples(action, accessQueryTriple);
            final List<String> results = new ArrayList<>();
            for (Triple triple : triples) {
                results.add(triple.getObject().toString());
            }
            final AccessQueryResults queryResults = new AccessQueryResults(query, (results.isEmpty() ? null : results));
            processResponse(response, queryResults);
        }
    }

    private static void processResponse(HttpServletResponse response, AccessQueryResults results) {
        String jsonOutput;
        try (ServletOutputStream out = response.getOutputStream()) {
            jsonOutput = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(results);
            response.setContentLength(jsonOutput.length());
            response.setContentType(WebContent.contentTypeJSON);
            response.setCharacterEncoding(WebContent.charsetUTF8);
            out.print(jsonOutput);
        } catch (IOException ex) {
            response.setStatus(HttpServletResponse.SC_UNPROCESSABLE_CONTENT);
        }
    }

    private Triple getAccessQueryTriple(AccessQuery accessQuery) {
        final Node s = NodeFactory.createURI(accessQuery.subject);
        final Node p = NodeFactory.createURI(accessQuery.predicate);
        final Node o = Node.ANY;
        return Triple.create(s, p, o);
    }

}
