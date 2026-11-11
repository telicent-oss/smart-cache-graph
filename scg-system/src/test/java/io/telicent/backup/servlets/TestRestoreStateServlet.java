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

import io.telicent.core.RestoreMarkerStore;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.tdb2.store.DatasetGraphSwitchable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the endpoint that reports the record of a restore that did not complete.
 * <p>
 * Lives in the servlet package because doGet is protected.
 */
class TestRestoreStateServlet {

    private static final String DATASET_NAME = "/ds";

    @TempDir
    private Path workDir;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private ServletOutputStream output;
    private DatasetGraphSwitchable dataset;

    @BeforeEach
    void setup() throws IOException {
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        output = mock(ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(output);

        final Path container = Files.createDirectories(workDir.resolve("dsg"));
        Files.createDirectories(container.resolve("Data-0001"));
        dataset = new DatasetGraphSwitchable(container, null, DatasetGraphFactory.createTxnMem());
    }

    @AfterEach
    void cleanup() {
        // A successful restore is the only thing that removes a marker, so it is also how a test tidies up after itself
        RestoreMarkerStore.complete(dataset, true, null);
    }

    @Test
    void givenIncompleteRestore_whenListingRestoreState_thenItIsReported() {
        quarantine();
        when(request.getPathInfo()).thenReturn(null);

        new RestoreStateServlet().doGet(request, response);

        final String body = body();
        assertTrue(body.contains("\"dataset-name\" : \"/ds\""), body);
        assertTrue(body.contains("\"state\" : \"INCOMPLETE\""), body);
        assertTrue(body.contains("\"serving\" : false"), body);
        assertTrue(body.contains("\"backup-id\" : \"14\""), body);
        assertTrue(body.contains("\"completed-modules\" : [ \"tdb\" ]"), body);
        verify(response, never()).setStatus(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test
    void givenIncompleteRestore_whenAskingForThatDataset_thenItIsReported() {
        quarantine();
        when(request.getPathInfo()).thenReturn("/ds");

        new RestoreStateServlet().doGet(request, response);

        assertTrue(body().contains("\"state\" : \"INCOMPLETE\""), body());
    }

    @Test
    void givenNoIncompleteRestore_whenAskingForADataset_then404() {
        when(request.getPathInfo()).thenReturn("/ds");

        new RestoreStateServlet().doGet(request, response);

        verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
    }

    /**
     * The endpoint is read-only by design: running the restore again is the only supported way out of a quarantine,
     * so there must be no write operation here that puts a dataset back into service without restoring it.
     */
    @Test
    void givenQuarantinedDataset_whenPosting_thenTheRequestIsRefusedAndTheQuarantineStands()
            throws IOException, ServletException {
        quarantine();
        when(request.getMethod()).thenReturn("POST");
        when(request.getProtocol()).thenReturn("HTTP/1.1");
        when(request.getPathInfo()).thenReturn("/clear/ds");

        new RestoreStateServlet().service(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_METHOD_NOT_ALLOWED), anyString());
        assertTrue(RestoreMarkerStore.quarantine(dataset).isPresent(), "The quarantine must stand");
        assertTrue(Files.isRegularFile(RestoreMarkerStore.markerFile(dataset).toPath()),
                   "The record must survive, so a restart still withholds the dataset");
    }

    /**
     * Leaves the dataset as a service that died mid-restore would: a marker on disk, picked up at startup.
     */
    private void quarantine() {
        RestoreMarkerStore.begin(dataset, DATASET_NAME, "14");
        RestoreMarkerStore.moduleRestored(dataset, "tdb");
        // The service dies here; the next startup finds the marker and quarantines the dataset
        RestoreMarkerStore.recover(dataset, DATASET_NAME);
        assertTrue(RestoreMarkerStore.quarantine(dataset).isPresent());
    }

    private String body() {
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        try {
            verify(output).print(captor.capture());
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        return captor.getValue();
    }
}
