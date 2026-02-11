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

import io.telicent.backup.services.DatasetBackupService;
import io.telicent.backup.servlets.*;
import io.telicent.model.KeyPair;
import io.telicent.smart.cache.configuration.Configurator;
import io.telicent.utils.SmartCacheGraphException;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiAutoModule;
import org.apache.jena.fuseki.server.DataAccessPointRegistry;
import org.apache.jena.rdf.model.Model;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.security.Security;
import java.util.Objects;
import java.util.Optional;

/**
 * Fuseki module responsible for the creating, restoring and administration of backup-data from the server.
 */
public class FMod_BackupData implements FusekiAutoModule {

    public static final Logger LOG = LoggerFactory.getLogger(FMod_BackupData.class);

    /**
     * Configuration flag to enable/disable functionality.
     */
    public static final String ENABLE_BACKUPS = "ENABLE_BACKUPS";

    private static final String BACKUPS_PUBLIC_KEY_URL = "BACKUPS_PUBLIC_KEY_URL";
    private static final String BACKUPS_PRIVATE_KEY_URL = "BACKUPS_PRIVATE_KEY_URL";
    private static final String BACKUPS_PASSKEY = "BACKUPS_PASSKEY";

    static {
        // Add Bouncy castle to JVM
        if (Objects.isNull(Security.getProvider(BouncyCastleProvider.PROVIDER_NAME))) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Override
    public String name() {
        return "Dataset Backups";
    }

    DatasetBackupService getBackupService(DataAccessPointRegistry dapRegistry) throws SmartCacheGraphException {
        try {
            final Optional<KeyPair> keyPairOption = getKeyPairOption();
            if (keyPairOption.isPresent()) {
                return new DatasetBackupService(dapRegistry, keyPairOption.get());
            } else {
                return new DatasetBackupService(dapRegistry);
            }

        } catch (IOException | PGPException | URISyntaxException ex) {
            throw new SmartCacheGraphException("Unable to initialise DatabaseBackupService due to " + ex.getMessage());
        }
    }

    @Override
    public void configured(FusekiServer.Builder serverBuilder, DataAccessPointRegistry dapRegistry, Model configModel) {
        if (isBackupEnabled()) {
            try {
                DatasetBackupService backupService = getBackupService(dapRegistry);
                serverBuilder.addServlet("/$/backups/create/*", new BackupServlet(backupService));
                serverBuilder.addServlet("/$/backups/list/*", new ListBackupsServlet(backupService));
                serverBuilder.addServlet("/$/backups/restore/*", new RestoreServlet(backupService));
                serverBuilder.addServlet("/$/backups/delete/*", new DeleteServlet(backupService));
                serverBuilder.addServlet("/$/backups/validate/*", new ValidateServlet(backupService));
                serverBuilder.addServlet("/$/backups/report/*", new ReportServlet(backupService));
                serverBuilder.addServlet("/$/backups/details/*", new DetailsServlet(backupService));
            } catch (SmartCacheGraphException ex) {
                LOG.warn("Database backups are not enabled", ex);
            }
        }
    }

    private static boolean isBackupEnabled() {
        return Configurator.get(FMod_BackupData.ENABLE_BACKUPS, Boolean::parseBoolean, false);
    }

    private static String getPublicKeyUrl() {
        return Configurator.get(new String[]{FMod_BackupData.BACKUPS_PUBLIC_KEY_URL}, "");
    }

    private static String getPrivateKeyUrl() {
        return Configurator.get(new String[]{FMod_BackupData.BACKUPS_PRIVATE_KEY_URL}, "");
    }

    private static String getPasskey() {
        return Configurator.get(new String[]{FMod_BackupData.BACKUPS_PASSKEY}, "");
    }

    private Optional<KeyPair> getKeyPairOption() throws MalformedURLException, URISyntaxException {
        if (!getPublicKeyUrl().isEmpty() && !getPrivateKeyUrl().isEmpty() && !getPasskey().isEmpty()) {
            try{
                return Optional.of(KeyPair.fromValues(getPrivateKeyUrl(), getPublicKeyUrl(), getPasskey()));
            } catch (IllegalArgumentException | MalformedURLException | URISyntaxException ex){
                LOG.error("Unable to read backup encryption key pair", ex);
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }

}
