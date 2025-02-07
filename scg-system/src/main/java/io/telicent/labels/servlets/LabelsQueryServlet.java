package io.telicent.labels.servlets;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.telicent.labels.TripleLabels;
import io.telicent.labels.LabelsQuery;
import io.telicent.labels.services.LabelsQueryService;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.riot.WebContent;

import java.io.IOException;
import java.io.InputStream;

public class LabelsQueryServlet extends HttpServlet {

    private final LabelsQueryService queryService;

    public static ObjectMapper MAPPER = new ObjectMapper();

    public LabelsQueryServlet(LabelsQueryService queryService) {
        this.queryService = queryService;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try (final InputStream inputStream = request.getInputStream()) {
            final LabelsQuery json = MAPPER.readValue(inputStream, LabelsQuery.class);
            final Triple triple = getTriple(json);
            final TripleLabels labels = queryService.queryLabelStore(triple);
            processResponse(response, labels);
        }
    }

    private static void processResponse(HttpServletResponse response, TripleLabels labels) {
        String jsonOutput;
        try (ServletOutputStream out = response.getOutputStream()) {
            jsonOutput = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(labels);
            response.setContentLength(jsonOutput.length());
            response.setContentType(WebContent.contentTypeJSON);
            response.setCharacterEncoding(WebContent.charsetUTF8);
            out.print(jsonOutput);
        } catch (IOException ex) {
            response.setStatus(HttpServletResponse.SC_UNPROCESSABLE_CONTENT);
        }
    }

    private Triple getTriple(LabelsQuery tripleQuery) {
        final Node s = NodeFactory.createURI(tripleQuery.subject);
        final Node p = NodeFactory.createURI(tripleQuery.predicate);
        final Node o = getObjectNode(tripleQuery.object);
        return Triple.create(s,p,o);
    }

    private Node getObjectNode(String object) {
        if(object.startsWith("http://")||object.startsWith("https://") ){
            return NodeFactory.createURI(object);
        } else {
            return NodeFactory.createLiteralByValue(object);
        }
    }

}
