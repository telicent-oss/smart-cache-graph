package io.telicent.backup.servlets;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.telicent.backup.services.DatasetBackupService;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.atlas.lib.DateTimeUtils;

import java.io.BufferedReader;

import static io.telicent.backup.utils.BackupUtils.*;

public class ValidateServlet extends HttpServlet {
    private final DatasetBackupService backupService;

    public ValidateServlet(DatasetBackupService backupService) {
        this.backupService = backupService;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        ObjectNode resultNode = MAPPER.createObjectNode();
        try {
            String validateId = request.getPathInfo();
            try(BufferedReader reader = request.getReader()) {
                resultNode.put("validate-id", validateId);
                resultNode.put("date", DateTimeUtils.nowAsString("yyyy-MM-dd_HH-mm-ss"));
                resultNode.set("validate", backupService.validateBackup(validateId, reader));
                processResponse(response, resultNode);
            }
        } catch (Exception exception) {
            handleError(response, resultNode, exception);
        }
    }
}
