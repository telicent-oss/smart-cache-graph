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

import io.telicent.LibTestsSCG;
import io.telicent.backup.services.DatasetBackupService;
import io.telicent.backup.services.DatasetBackupService_Test;
import io.telicent.core.SmartCacheGraph;
import io.telicent.jena.abac.core.Attributes;
import io.telicent.smart.cache.configuration.Configurator;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.cmds.FusekiMain;
import org.apache.jena.fuseki.main.sys.FusekiModule;
import org.apache.jena.fuseki.main.sys.FusekiModules;
import org.apache.jena.fuseki.server.DataAccessPointRegistry;
import org.apache.jena.fuseki.system.FusekiLogging;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import static io.telicent.TestJwtServletAuth.makeAuthGETCallWithPath;
import static io.telicent.TestJwtServletAuth.makeAuthPOSTCallWithPath;
import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;
import static org.apache.jena.graph.Graph.emptyGraph;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;

public class TestBackupData {

    private static FusekiServer server;

    private FMod_BackupData testModule;

    private DatasetBackupService mockService;

    @BeforeEach
    public void createAndSetupServerDetails() throws Exception {
        mockService = mock(DatasetBackupService.class);
        LibTestsSCG.setupAuthentication();
        LibTestsSCG.disableInitialCompaction();
        LibTestsSCG.enableBackups();
        FusekiLogging.setLogging();
        Attributes.buildStore(emptyGraph);
        testModule = new FMod_BackupData_Test();
    }

    @AfterEach
    void clearDown() throws Exception {
        if (server != null) {
            server.stop();
        }
        LibTestsSCG.teardownAuthentication();
        Configurator.reset();
        reset(mockService);
    }

    private FusekiModules generateModulesAndReplaceWithTestModule() {
        List<FusekiModule> originalModules = SmartCacheGraph.modules().asList();
        List<FusekiModule> replacedModules = new ArrayList<>();
        for (FusekiModule module : originalModules) {
            if (module instanceof FMod_BackupData) {
                replacedModules.add(testModule);
            } else {
                replacedModules.add(module);
            }
        }
        return FusekiModules.create(replacedModules);
    }

    private FusekiServer buildServer(String... args) {
        return FusekiMain
                .builder(args)
                .fusekiModules(generateModulesAndReplaceWithTestModule())
                .build().start();
    }

    @Test
    public void test_name() {
        // given
        testModule = new FMod_BackupData();
        // when
        // then
        assertFalse(testModule.name().isEmpty());
    }

    @Test
    public void test_unrecognised_path() {
        // given
        testModule = new FMod_BackupData();
        server = buildServer("--port=0", "--empty");
        // when
        HttpResponse<InputStream> createBackupResponse = makeAuthPOSTCallWithPath(server, "$/backups/does_not_work/", "test");
        // then
        assertEquals(404, createBackupResponse.statusCode());
    }

    @Test
    public void test_disabled() {
        // given
        LibTestsSCG.disableBackups();
        testModule = new FMod_BackupData();
        server = buildServer("--port=0", "--empty");
        // when
        HttpResponse<InputStream> createBackupResponse = makeAuthPOSTCallWithPath(server, "$/backups/create/", "test");
        // then
        assertEquals(404, createBackupResponse.statusCode());
    }


    @Test
    public void test_createBackup_emptyGraph() {
        // given
        server = buildServer("--port=0", "--empty");
        // when
        HttpResponse<InputStream> createBackupResponse = makeAuthPOSTCallWithPath(server, "$/backups/create/", "test");
        // then
        assertEquals(200, createBackupResponse.statusCode());
        // for debugging
//        debug(createBackupResponse);
    }


    @Test
    public void test_listBackups_emptyGraph() {
        // given
        server = buildServer("--port=0", "--empty");
        // when
        HttpResponse<InputStream> createBackupResponse = makeAuthGETCallWithPath(server, "$/backups/list", "test");
        // then
//        debug(createBackupResponse);
        assertEquals(200, createBackupResponse.statusCode());
    }

    @Test
    public void test_deleteBackup_emptyGraph() {
        // given
        server = buildServer("--port=0", "--empty");
        // when
        HttpResponse<InputStream> createBackupResponse = makeAuthPOSTCallWithPath(server, "$/backups/delete", "test");
        // then
//        debug(createBackupResponse);
        assertEquals(200, createBackupResponse.statusCode());
    }

    @Test
    public void test_restoreBackup_emptyGraph_no_arg() {
        // given
        server = buildServer("--port=0", "--empty");
        // when
        HttpResponse<InputStream> createBackupResponse = makeAuthPOSTCallWithPath(server, "$/backups/create/", "test");
        HttpResponse<InputStream> createRestoreResponse = makeAuthPOSTCallWithPath(server, "$/backups/restore", "test");
        // then
        //debug(createBackupResponse);
        //debug(createRestoreResponse);
        assertEquals(200, createBackupResponse.statusCode());
        assertEquals(200, createRestoreResponse.statusCode());
    }

    @Test
    public void test_restoreBackup_emptyGraph() {
        // given
        server = buildServer("--port=0", "--empty");
        // when
        HttpResponse<InputStream> createBackupResponse = makeAuthPOSTCallWithPath(server, "$/backups/restore/1", "test");
        // then
        //debug(createBackupResponse);
        assertEquals(200, createBackupResponse.statusCode());
    }

    @Test
    public void test_createBackup_error() {
        // given
        testModule = new FMod_BackupData_Null();
        server = buildServer("--port=0", "--empty");
        // when
        HttpResponse<InputStream> createBackupResponse = makeAuthPOSTCallWithPath(server, "$/backups/create", "test");
        // then
//        debug(createBackupResponse);
        assertEquals(500, createBackupResponse.statusCode());
    }


    @Test
    public void test_listBackups_error() {
        // given
        testModule = new FMod_BackupData_Null();
        server = buildServer("--port=0", "--empty");
        // when
        HttpResponse<InputStream> createBackupResponse = makeAuthGETCallWithPath(server, "$/backups/list", "test");
        // then
//        debug(createBackupResponse);
        assertEquals(500, createBackupResponse.statusCode());
    }

    @Test
    public void test_deleteBackup_error() {
        // given
        testModule = new FMod_BackupData_Null();
        server = buildServer("--port=0", "--empty");
        // when
        HttpResponse<InputStream> createBackupResponse = makeAuthPOSTCallWithPath(server, "$/backups/delete", "test");
        // then
//        debug(createBackupResponse);
        assertEquals(500, createBackupResponse.statusCode());
    }

    @Test
    public void test_restoreBackup_error() {
        // given
        testModule = new FMod_BackupData_Null();
        server = buildServer("--port=0", "--empty");
        // when
        HttpResponse<InputStream> createBackupResponse = makeAuthPOSTCallWithPath(server, "$/backups/restore", "test");
        // then
//        debug(createBackupResponse);
        assertEquals(500, createBackupResponse.statusCode());
    }

    /**
     * Debugging method for outputting response to std:out
     * @param response generated response
     */
    private void debug(HttpResponse<InputStream> response) {
        System.out.println(convertToJSON(response));
    }

    /**
     * Obtain the JSON String from the HTTP Response
     * @param response the response returned
     * @return a JSON string
     */
    private String convertToJSON(HttpResponse<InputStream> response) {
        try {
            InputStream inputStream = response.body();
            InputStreamReader reader = new InputStreamReader(inputStream);
            Object jsonObject = OBJECT_MAPPER.readValue(reader, Object.class);
            return OBJECT_MAPPER.writeValueAsString(jsonObject);
        }catch (IOException e) {
            return e.getMessage();
        }
    }

    /**
     * Extension of the Backup Module for testing purposes.
     * Uses a test instance of actual back up service.
     */
    public class FMod_BackupData_Test extends FMod_BackupData {

        @Override
        DatasetBackupService getBackupService(DataAccessPointRegistry dapRegistry) {
            return new DatasetBackupService_Test(dapRegistry);
        }
    }

    /**
     * Extension of the Backup Module for testing purposes.
     * Causes a null pointer exception to be thrown.
     */
    public class FMod_BackupData_Null extends FMod_BackupData {

        @Override
        DatasetBackupService getBackupService(DataAccessPointRegistry dapRegistry) {
            return null;
        }
    }

    /**
     * Extension of the Backup Module for testing purposes.
     * Allows the underlying service to be mocked
     */
    public class FMod_BackupData_Mock extends FMod_BackupData {

        @Override
        DatasetBackupService getBackupService(DataAccessPointRegistry dapRegistry) {
            return mockService;
        }
    }
}