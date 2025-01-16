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
        try {
            final String pathInfo = request.getPathInfo();
            final String[] pathElems = pathInfo.substring(1).split("/");
            final ObjectNode report = backupService.getReport(pathElems[0], pathElems[1], response);
            processResponse(response, report);
        } catch (Exception exception) {
            final ObjectNode resultNode = MAPPER.createObjectNode();
            handleError(response, resultNode, exception);
        }
    }

}
