package io.telicent.core.queryTracker;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.web.HttpSC;

import java.io.IOException;

import static io.telicent.core.queryTracker.QueryMapper.*;
import static org.apache.jena.riot.WebContent.charsetUTF8;

public class QueryMapEndpoint extends HttpServlet {

    public QueryMapEndpoint() {
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setCharacterEncoding(charsetUTF8);
        response.setContentType("text/plain");
        response.setStatus(HttpSC.OK_200);
        StringBuilder builder = new StringBuilder();
        builder.append("Running Queries:\n");
        queryMapper.forEach((id,exec) -> builder.append(id).append(" : ").append(exec.getQuery().toString()).append("\n"));

        builder.append("Aborted Queries:\n");
        abortMapper.forEach((id,exec) -> builder.append(id).append(" : ").append(exec.getQuery().toString()).append("\n"));

        builder.append("Closed Queries:\n");
        closedMapper.forEach((id,exec) -> builder.append(id).append(" : ").append(exec.getQuery().toString()).append("\n"));
        response.getOutputStream().print(builder.toString());
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpSC.OK_200);
        String idString = request.getParameter("ID");
        if (idString == null) {
            response.setStatus(HttpSC.BAD_REQUEST_400);
            response.getWriter().println("Missing required parameter: ID");
            return;
        }
        try {
            long queryId = Long.parseLong(idString);
            abort(queryId);
            response.getWriter().println("Aborting query: " + idString);
        } catch (NumberFormatException e) {
            response.setStatus(HttpSC.BAD_REQUEST_400);
            response.getWriter().println("Invalid ID format: " + idString);
        }
    }
}
