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
package io.telicent.backup;

import io.telicent.backup.servlets.*;
import io.telicent.backup.services.DatasetBackupService;
import io.telicent.smart.cache.configuration.Configurator;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiAutoModule;
import org.apache.jena.fuseki.server.DataAccessPointRegistry;
import org.apache.jena.rdf.model.Model;

/**
 * Fuseki module responsible for the creating, restoring and administration of backup-data from the server.
 */
public class FMod_BackupData implements FusekiAutoModule {

    /**
     * Configuration flag to enable/disable functionality.
     */
    public static final String ENABLE_BACKUPS = "ENABLE_BACKUPS";

    @Override
    public String name() {
        return "Dataset Backups";
    }

    DatasetBackupService getBackupService(DataAccessPointRegistry dapRegistry) {
        return new DatasetBackupService(dapRegistry);
    }

    @Override
    public void configured(FusekiServer.Builder serverBuilder, DataAccessPointRegistry dapRegistry, Model configModel) {
        if (isBackupEnabled()) {
            DatasetBackupService backupService = getBackupService(dapRegistry);
            serverBuilder.addServlet("/$/backups/create/*", new BackupServlet(backupService));
            serverBuilder.addServlet("/$/backups/list/*", new ListBackupsServlet(backupService));
            serverBuilder.addServlet("/$/backups/restore/*", new RestoreServlet(backupService));
            serverBuilder.addServlet("/$/backups/delete/*", new DeleteServlet(backupService));
        }
    }

    private static boolean isBackupEnabled() {
        return Configurator.get(FMod_BackupData.ENABLE_BACKUPS, Boolean::parseBoolean, false);
    }
}
