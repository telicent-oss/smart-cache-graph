package io.telicent.backup.servlets;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.telicent.backup.services.DatasetBackupService;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.telicent.backup.utils.BackupUtils.*;

public class ValidateServlet extends HttpServlet {

    private final DatasetBackupService backupService;

    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    public ValidateServlet(DatasetBackupService backupService) {
        this.backupService = backupService;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        if (!RUNNING.getAndSet(true)) {
            try {
                if (request.getContentType().equals("text/turtle")) {
                    processRequest(request, response);
                } else {
                    final ObjectNode resultNode = MAPPER.createObjectNode();
                    handleError(response, resultNode, HttpServletResponse.SC_BAD_REQUEST, "Invalid content type: " + request.getContentType());
                }
            } catch (Exception exception) {
                final ObjectNode resultNode = MAPPER.createObjectNode();
                handleError(response, resultNode, exception);
            } finally {
                RUNNING.set(false);
            }
        } else {
            final ObjectNode resultNode = MAPPER.createObjectNode();
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            resultNode.put("error", "Validation already in progress");
            processResponse(response, resultNode);
        }
    }

    private void processRequest(
            final HttpServletRequest request,
            final HttpServletResponse response) throws IOException {
        final String pathInfo = request.getPathInfo();
        final String[] validateParams = pathInfo.substring(1).split("/");
        try (final InputStream inputStream = request.getInputStream()) {
            final ObjectNode resultNode = backupService.validateBackup(validateParams, inputStream, response);
            processResponse(response, resultNode);
        }
    }

}
