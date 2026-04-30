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

import io.telicent.backup.services.BackupJobManager;
import io.telicent.backup.services.BackupOperationRequest;
import io.telicent.backup.services.DatasetBackupService;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;
import static io.telicent.utils.ServletUtils.processResponse;

/**
 * Servlet class responsible for the creation of backups.
 */
public class BackupServlet extends HttpServlet {
    private final DatasetBackupService backupService;
    private final BackupJobManager jobManager;

    public BackupServlet(DatasetBackupService backupService, BackupJobManager jobManager) {
        this.backupService = backupService;
        this.jobManager = jobManager;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        if (Boolean.parseBoolean(request.getParameter("async"))) {
            final BackupOperationRequest operationRequest;
            try {
                operationRequest = this.backupService.captureRequest(request);
            } catch (RuntimeException e) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                var resultNode = OBJECT_MAPPER.createObjectNode();
                resultNode.put("error", e.getMessage());
                processResponse(response, resultNode);
                return;
            }
            response.setStatus(HttpServletResponse.SC_ACCEPTED);
            processResponse(response,
                            this.jobManager.submit("CREATE_BACKUP",
                                                   "/$/backups/jobs/",
                                                   () -> this.backupService.execute(operationRequest, true, true)));
            return;
        }
        AsyncBackupServletSupport.processAsync(backupService, request, response, true);
    }
}
