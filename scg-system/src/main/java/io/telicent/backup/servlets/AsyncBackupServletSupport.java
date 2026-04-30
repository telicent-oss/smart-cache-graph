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

import io.telicent.backup.services.DatasetBackupService;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class AsyncBackupServletSupport {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncBackupServletSupport.class);
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        final Thread thread = new Thread(r, "scg-backup-async");
        thread.setDaemon(true);
        return thread;
    });

    private AsyncBackupServletSupport() {
    }

    static void processAsync(final DatasetBackupService backupService,
                             final HttpServletRequest request,
                             final HttpServletResponse response,
                             final boolean backup) {
        final AsyncContext asyncContext = request.startAsync();
        final String remoteUser = request.getRemoteUser();
        asyncContext.setTimeout(0);
        EXECUTOR.execute(() -> {
            try {
                final HttpServletRequest asyncRequest = (HttpServletRequest) asyncContext.getRequest();
                backupService.process(new HttpServletRequestWrapper(asyncRequest) {
                                          @Override
                                          public String getRemoteUser() {
                                              return remoteUser;
                                          }
                                      },
                                      (HttpServletResponse) asyncContext.getResponse(),
                                      backup);
            } catch (Throwable e) {
                LOG.error("Unhandled error processing {} request asynchronously", backup ? "backup" : "restore", e);
                final HttpServletResponse asyncResponse = (HttpServletResponse) asyncContext.getResponse();
                if (!asyncResponse.isCommitted()) {
                    try {
                        asyncResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
                    } catch (IOException ioException) {
                        LOG.warn("Failed to write async error response", ioException);
                    }
                }
            } finally {
                asyncContext.complete();
            }
        });
    }
}
