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
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the request-path handling of the backup servlets: the validation that rejects unusable or
 * unsafe path components before anything reaches {@link DatasetBackupService}, and the pass-through
 * for well-formed requests.
 * <p>
 * These live in the servlet package because doGet/doPost are protected.
 */
public class TestBackupServlets {

    private static final int SERVER_ERROR = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

    private DatasetBackupService backupService;
    private HttpServletRequest request;
    private HttpServletResponse response;

    @BeforeEach
    public void setup() throws IOException {
        backupService = mock(DatasetBackupService.class);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        when(response.getOutputStream()).thenReturn(mock(ServletOutputStream.class));
    }

    private static ObjectNode emptyResult() {
        return OBJECT_MAPPER.createObjectNode();
    }

    // ---------------------------------------------------------------- ReportServlet

    @Test
    @DisplayName("Report servlet rejects a request with no path")
    public void test_report_nullPathInfo() {
        when(request.getPathInfo()).thenReturn(null);

        new ReportServlet(backupService).doGet(request, response);

        verify(response).setStatus(SERVER_ERROR);
    }

    @Test
    @DisplayName("Report servlet rejects a path that is not rooted")
    public void test_report_pathInfoNotRooted() {
        when(request.getPathInfo()).thenReturn("no-leading-slash");

        new ReportServlet(backupService).doGet(request, response);

        verify(response).setStatus(SERVER_ERROR);
    }

    @Test
    @DisplayName("Report servlet requires both a backup id and a dataset name")
    public void test_report_wrongNumberOfPathElements() {
        when(request.getPathInfo()).thenReturn("/only-the-backup-id");

        new ReportServlet(backupService).doGet(request, response);

        verify(response).setStatus(SERVER_ERROR);
    }

    @Test
    @DisplayName("Report servlet rejects a traversal attempt in the backup id")
    public void test_report_unsafeBackupId() {
        when(request.getPathInfo()).thenReturn("/../etc");

        new ReportServlet(backupService).doGet(request, response);

        verify(response).setStatus(SERVER_ERROR);
    }

    @Test
    @DisplayName("Report servlet passes a well-formed request through to the service")
    public void test_report_happyPath() throws Exception {
        when(request.getPathInfo()).thenReturn("/12/ds");
        when(backupService.getReport(anyString(), anyString(), any())).thenReturn(emptyResult());

        new ReportServlet(backupService).doGet(request, response);

        verify(backupService).getReport("12", "ds", response);
        verify(response, never()).setStatus(SERVER_ERROR);
    }

    // ---------------------------------------------------------------- ValidateServlet

    @Test
    @DisplayName("Validate servlet rejects a content type it cannot parse")
    public void test_validate_wrongContentType() {
        when(request.getContentType()).thenReturn("application/json");

        new ValidateServlet(backupService).doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test
    @DisplayName("Validate servlet rejects a request with no path")
    public void test_validate_nullPathInfo() {
        when(request.getContentType()).thenReturn("text/turtle");
        when(request.getPathInfo()).thenReturn(null);

        new ValidateServlet(backupService).doPost(request, response);

        verify(response).setStatus(SERVER_ERROR);
    }

    @Test
    @DisplayName("Validate servlet rejects more path elements than it understands")
    public void test_validate_tooManyPathElements() {
        when(request.getContentType()).thenReturn("text/turtle");
        when(request.getPathInfo()).thenReturn("/12/ds/extra");

        new ValidateServlet(backupService).doPost(request, response);

        verify(response).setStatus(SERVER_ERROR);
    }

    @Test
    @DisplayName("Validate servlet rejects a traversal attempt in the dataset name")
    public void test_validate_unsafeDatasetName() {
        when(request.getContentType()).thenReturn("text/turtle");
        when(request.getPathInfo()).thenReturn("/12/..");

        new ValidateServlet(backupService).doPost(request, response);

        verify(response).setStatus(SERVER_ERROR);
    }

    @Test
    @DisplayName("Validate servlet accepts a backup id on its own, validating the whole backup")
    public void test_validate_backupIdOnly() throws IOException {
        final ServletInputStream body = mock(ServletInputStream.class);
        when(request.getContentType()).thenReturn("text/turtle");
        when(request.getPathInfo()).thenReturn("/12");
        when(request.getInputStream()).thenReturn(body);
        when(backupService.validateBackup(any(), any(), any())).thenReturn(emptyResult());

        new ValidateServlet(backupService).doPost(request, response);

        verify(backupService).validateBackup(new String[]{"12"}, body, response);
        verify(response, never()).setStatus(SERVER_ERROR);
    }

    @Test
    @DisplayName("Validate servlet accepts a backup id and dataset name")
    public void test_validate_backupIdAndDataset() throws IOException {
        final ServletInputStream body = mock(ServletInputStream.class);
        when(request.getContentType()).thenReturn("text/turtle");
        when(request.getPathInfo()).thenReturn("/12/ds");
        when(request.getInputStream()).thenReturn(body);
        when(backupService.validateBackup(any(), any(), any())).thenReturn(emptyResult());

        new ValidateServlet(backupService).doPost(request, response);

        verify(backupService).validateBackup(new String[]{"12", "ds"}, body, response);
        verify(response, never()).setStatus(SERVER_ERROR);
    }

    // ---------------------------------------------------------------- DeleteServlet

    @Test
    @DisplayName("Delete servlet with no path reports the failure in the body rather than as a server error")
    public void test_delete_noPathInfo() {
        when(request.getPathInfo()).thenReturn(null);
        when(backupService.deleteBackup(anyString())).thenReturn(emptyResult());

        new DeleteServlet(backupService).doPost(request, response);

        verify(backupService).deleteBackup("");
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    @DisplayName("Delete servlet strips the leading slash from the id")
    public void test_delete_stripsLeadingSlash() {
        when(request.getPathInfo()).thenReturn("/12");
        when(backupService.deleteBackup(anyString())).thenReturn(emptyResult());

        new DeleteServlet(backupService).doPost(request, response);

        verify(backupService).deleteBackup("12");
    }

    @Test
    @DisplayName("Delete servlet rejects a traversal attempt and never reaches the service")
    public void test_delete_unsafeId() {
        when(request.getPathInfo()).thenReturn("/../etc");

        new DeleteServlet(backupService).doPost(request, response);

        verify(response).setStatus(SERVER_ERROR);
        verify(backupService, never()).deleteBackup(anyString());
    }

    // ---------------------------------------------------------------- DetailsServlet

    @Test
    @DisplayName("Details servlet rejects a traversal attempt and never reaches the service")
    public void test_details_unsafeId() {
        when(request.getPathInfo()).thenReturn("/../etc");

        new DetailsServlet(backupService).doGet(request, response);

        verify(response).setStatus(SERVER_ERROR);
        verify(backupService, never()).getDetails(anyString());
    }

    @Test
    @DisplayName("Details servlet passes a well-formed id through to the service")
    public void test_details_happyPath() {
        when(request.getPathInfo()).thenReturn("/12");
        when(backupService.getDetails(anyString())).thenReturn(emptyResult());

        new DetailsServlet(backupService).doGet(request, response);

        verify(backupService).getDetails("12");
        verify(response, never()).setStatus(SERVER_ERROR);
    }
}
