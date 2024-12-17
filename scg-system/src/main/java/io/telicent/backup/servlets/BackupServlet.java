/*
 *  Copyright (c) Telicent Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.telicent.backup.servlets;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.telicent.backup.services.DatasetBackupService;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.atlas.lib.DateTimeUtils;

import static io.telicent.backup.utils.BackupUtils.*;

/**
 * Servlet class responsible for the creation of backups.
 */
public class BackupServlet extends HttpServlet {
    private final DatasetBackupService backupService;

    public BackupServlet(DatasetBackupService backupService) {
        this.backupService = backupService;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        ObjectNode resultNode = MAPPER.createObjectNode();
        try {
            String datasetName = request.getPathInfo();
            resultNode.put("dataset", datasetName);
            resultNode.put("date", DateTimeUtils.nowAsString("yyyy-MM-dd_HH-mm-ss"));
            resultNode.set("backup", backupService.backupDataset(datasetName));
            processResponse(response, resultNode);
        } catch (Exception exception) {
            handleError(response, resultNode, exception);
        }
    }
}