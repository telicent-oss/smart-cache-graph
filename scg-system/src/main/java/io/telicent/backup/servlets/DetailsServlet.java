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

import static io.telicent.backup.utils.BackupConstants.DATE_FORMAT;
import static io.telicent.backup.utils.BackupUtils.handleError;
import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;
import static io.telicent.utils.ServletUtils.processResponse;

/**
 * Servlet class responsible for providing a detailed description of a given backup.
 */
public class DetailsServlet extends HttpServlet {
    private final DatasetBackupService backupService;
    public DetailsServlet(DatasetBackupService backupService) {
        this.backupService = backupService;
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) {
        ObjectNode resultNode = OBJECT_MAPPER.createObjectNode();
        try {
            String backupId = request.getPathInfo();
            if (backupId.startsWith("/")) {
                backupId = backupId.substring(1);
            }
            resultNode.put("backup-id", backupId);
            resultNode.put("date", DateTimeUtils.nowAsString(DATE_FORMAT));
            resultNode.set("details", backupService.getDetails(backupId));
            processResponse(response, resultNode);
        } catch (Exception exception) {
            handleError(response, resultNode, exception);
        }
    }
}
