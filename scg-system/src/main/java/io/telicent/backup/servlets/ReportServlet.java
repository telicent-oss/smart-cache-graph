package io.telicent.backup.servlets;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.telicent.backup.services.DatasetBackupService;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static io.telicent.backup.utils.BackupUtils.*;

public class ReportServlet extends HttpServlet {

    private final DatasetBackupService backupService;

    public ReportServlet(final DatasetBackupService backupService) {
        this.backupService = backupService;
    }

    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) {
        final ObjectNode resultNode = MAPPER.createObjectNode();
        try {
            final String pathInfo = request.getPathInfo();
            final String[] pathElems = pathInfo.split("/");
            resultNode.set("validate-id", backupService.getReport(pathElems[1], pathElems[2]));
            processResponse(response,resultNode);
        } catch (Exception exception) {
            handleError(response, resultNode, exception);
        }
    }

}
