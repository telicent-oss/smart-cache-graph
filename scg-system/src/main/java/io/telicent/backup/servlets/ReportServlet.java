package io.telicent.backup.servlets;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.telicent.backup.services.DatasetBackupService;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static io.telicent.backup.utils.BackupUtils.*;

public class ReportServlet extends HttpServlet {

    private final DatasetBackupService backupService;

    public ReportServlet(DatasetBackupService backupService) {
        this.backupService = backupService;
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        ObjectNode resultNode = MAPPER.createObjectNode();
        try {
            String pathInfo = request.getPathInfo();
            String[] pathElems = pathInfo.split("/");
            resultNode.set("validate-id", backupService.getReport(pathElems[1], pathElems[2]));
            processResponse(response,resultNode);
        } catch (Exception exception) {
            handleError(response, resultNode, exception);
        }
    }

}
