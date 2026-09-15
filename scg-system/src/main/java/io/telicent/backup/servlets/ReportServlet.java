package io.telicent.backup.servlets;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.telicent.backup.services.DatasetBackupService;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static io.telicent.backup.utils.BackupUtils.*;
import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;
import static io.telicent.utils.ServletUtils.processResponse;

public class ReportServlet extends HttpServlet {

    private final DatasetBackupService backupService;

    public ReportServlet(final DatasetBackupService backupService) {
        this.backupService = backupService;
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) {
        try {
            final String pathInfo = request.getPathInfo();
            if (pathInfo == null || !pathInfo.startsWith("/")) {
                throw new IllegalArgumentException("Invalid report path");
            }
            final String[] pathElems = pathInfo.substring(1).split("/");
            if (pathElems.length != 2) {
                throw new IllegalArgumentException("Invalid report path");
            }
            final String backupId = requireSafePathComponent(pathElems[0], "backup-id");
            final String datasetName = requireSafePathComponent(pathElems[1], "dataset-name");
            final ObjectNode report = backupService.getReport(backupId, datasetName, response);
            processResponse(response, report);
        } catch (Exception exception) {
            final ObjectNode resultNode = OBJECT_MAPPER.createObjectNode();
            handleError(response, resultNode, exception);
        }
    }

}
