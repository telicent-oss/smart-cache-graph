package io.telicent.backup.servlets;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.telicent.backup.services.DatasetBackupService;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.atlas.lib.DateTimeUtils;

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
        final ObjectNode resultNode = MAPPER.createObjectNode();
        if(!RUNNING.getAndSet(true)) {
            try {
                if (request.getContentType().equals("text/turtle")) {
                    processRequest(request, response, resultNode);
                } else {
                    handleError(response, resultNode, HttpServletResponse.SC_BAD_REQUEST, "Invalid content type: " + request.getContentType());
                }
            } catch (Exception exception) {
                handleError(response, resultNode, exception);
            } finally {
                RUNNING.set(false);
            }
        } else {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            resultNode.put("error", "Validation already in progress");
            processResponse(response, resultNode);
        }

    }

    private void processRequest(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final ObjectNode resultNode) throws IOException {
        final String pathInfo = request.getPathInfo();
        final String[] validateParams = pathInfo.split("/");
        try (final InputStream inputStream = request.getInputStream()) {
            resultNode.put("validate-id", validateParams[0]);
            resultNode.put("date", DateTimeUtils.nowAsString("yyyy-MM-dd_HH-mm-ss"));
            resultNode.set("validate", backupService.validateBackup(validateParams, inputStream));
            processResponse(response, resultNode);
        }
    }

}
