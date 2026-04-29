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
import io.telicent.backup.services.BackupJobManager;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.atlas.lib.DateTimeUtils;

import static io.telicent.backup.utils.BackupConstants.DATE_FORMAT;
import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;
import static io.telicent.utils.ServletUtils.processResponse;

public class BackupJobsServlet extends HttpServlet {

    private final BackupJobManager jobManager;

    public BackupJobsServlet(final BackupJobManager jobManager) {
        this.jobManager = jobManager;
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) {
        final ObjectNode resultNode = OBJECT_MAPPER.createObjectNode();
        resultNode.put("date", DateTimeUtils.nowAsString(DATE_FORMAT));
        final String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.isBlank() || "/".equals(pathInfo)) {
            resultNode.set("jobs", this.jobManager.listJobs());
            processResponse(response, resultNode);
            return;
        }

        final String jobId = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        this.jobManager.getJob(jobId).ifPresentOrElse(job -> {
            resultNode.set("job", job);
            processResponse(response, resultNode);
        }, () -> {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resultNode.put("error", "Unknown backup job id: " + jobId);
            processResponse(response, resultNode);
        });
    }
}
