package io.telicent.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.telicent.LibTestsSCG;
import io.telicent.jena.abac.core.Attributes;
import io.telicent.smart.cache.configuration.Configurator;
import io.telicent.smart.cache.configuration.sources.PropertiesSource;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.cmds.FusekiMain;
import org.apache.jena.fuseki.main.sys.FusekiModule;
import org.apache.jena.fuseki.main.sys.FusekiModules;
import org.apache.jena.fuseki.mgt.Backup;
import org.apache.jena.fuseki.system.FusekiLogging;
import org.apache.jena.riot.WebContent;
import org.apache.jena.sparql.core.DatasetGraph;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.testcontainers.shaded.org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static io.telicent.TestJwtServletAuth.makeAuthPOSTCallWithPath;
import static io.telicent.TestSmartCacheGraphIntegration.DIR;
import static io.telicent.core.FMod_DatasetBackups.ENV_BACKUPS_DIR;
import static org.apache.jena.graph.Graph.emptyGraph;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * A test class that removes the underlying call to Jena's Backup.backup()
 */
public class TestDatasetBackups {
    public static class TestMod_DatasetBackups extends FMod_DatasetBackups {
        public int backupInvocations = 0;

        @Override
        public void backup(DatasetGraph dsg, String backupFile) {
            // NO-OP
            ++backupInvocations;
        }

        public int getBackupInvocations() {
            return backupInvocations;
        }

        public void resetInvocations() {
            backupInvocations = 0;
        }
    }

    private static FusekiServer server;

    private static final TestMod_DatasetBackups testModule = new TestMod_DatasetBackups();

    @BeforeEach
    public void createAndSetupServerDetails() throws Exception {
        LibTestsSCG.setupAuthentication();
        LibTestsSCG.disableInitialCompaction();
        LibTestsSCG.enableBackups();
        FusekiLogging.setLogging();
        Attributes.buildStore(emptyGraph);
    }

    private static FusekiModules generateModulesAndReplaceWithTestModule() {
        List<FusekiModule> originalModules = SmartCacheGraph.modules().asList();
        List<FusekiModule> replacedModules = new ArrayList<>();
        for (FusekiModule module : originalModules) {
            if (module instanceof FMod_DatasetBackups) {
                replacedModules.add(testModule);
            } else {
                replacedModules.add(module);
            }
        }
        return FusekiModules.create(replacedModules);
    }

    @AfterEach
    void clearDown() throws Exception {
        if (server != null) {
            server.stop();
        }
        LibTestsSCG.teardownAuthentication();
        Configurator.reset();
    }

    private FusekiServer buildServer(String... args) {
        testModule.resetInvocations();
        return FusekiMain
                .builder(args)
                .fusekiModules(generateModulesAndReplaceWithTestModule())
                .build().start();
    }

    @Test
    public void test_backup_emptyGraph() {
        // given
        server = buildServer("--port=0", "--empty");
        // when
        HttpResponse<InputStream> compactResponse = makeAuthPOSTCallWithPath(server, "$/backups", "test");
        // then
        assertEquals(200, compactResponse.statusCode());
        assertEquals(0, testModule.getBackupInvocations());
    }

    @Test
    public void test_backup_mem_no_dsg_in_path() {
        // given
        List<String> arguments = List.of("--port=0", "--mem", "/dataendpoint");
        // when
        server = buildServer(arguments.toArray(new String[0]));
        // when
        HttpResponse<InputStream> compactResponse = makeAuthPOSTCallWithPath(server, "$/backups/rubbish", "test");
        // then
        assertEquals(200, compactResponse.statusCode());
        assertEquals(0, testModule.getBackupInvocations());
    }

    @Test
    public void test_backup_mem_mismatch_dsg_in_path() {
        // given
        List<String> arguments = List.of("--port=0", "--mem", "/dataendpoint");
        // when
        server = buildServer(arguments.toArray(new String[0]));
        // when
        HttpResponse<InputStream> compactResponse = makeAuthPOSTCallWithPath(server, "$/backups/missingDataEndpoint", "test");
        // then
        assertEquals(200, compactResponse.statusCode());
        assertEquals(0, testModule.getBackupInvocations());
    }


    @Test
    public void test_backup_mem_happy_path() throws IOException {
        // given

        String expectedDir = "/backups";
        String expectedDataset = "/dataendpoint";
        List<String> arguments = List.of("--port=0", "--mem", "/dataendpoint");
        // when
        server = buildServer(arguments.toArray(new String[0]));
        // when
        HttpResponse<InputStream> compactResponse = makeAuthPOSTCallWithPath(server, "$/backups/dataendpoint", "test");
        // then
        assertEquals(200, compactResponse.statusCode());
        String actualBody = IOUtils.toString(compactResponse.body(), StandardCharsets.UTF_8);
        assertNotNull(actualBody);
        assertTrue(actualBody.contains(expectedDir + expectedDataset), "Actual body does not match expectation: " + actualBody);
        assertEquals(1, testModule.getBackupInvocations());
    }

    @Test
    public void test_backup_happy_path_with_specified_dir() throws IOException {
        // set-up
        FMod_DatasetBackups.dirBackups = null;
        Path tempFolder = Files.createTempDirectory("temp-folder");
        String expectedPath = tempFolder.toAbsolutePath().toString();

        String expectedDataset = "/dataendpoint";
        Properties properties = new Properties();
        properties.put(ENV_BACKUPS_DIR, expectedPath);
        Configurator.addSource(new PropertiesSource(properties));
        // given
        List<String> arguments = List.of("--port=0", "--mem", "/dataendpoint");
        server = buildServer(arguments.toArray(new String[0]));
        // when
        HttpResponse<InputStream> compactResponse = makeAuthPOSTCallWithPath(server, "$/backups/dataendpoint", "test");
        // then
        assertEquals(200, compactResponse.statusCode());
        String actualBody = IOUtils.toString(compactResponse.body(), StandardCharsets.UTF_8);
        assertNotNull(actualBody);
        assertTrue(actualBody.contains(expectedPath + expectedDataset), "Actual body: " + actualBody + " does not match expectation: " + expectedPath + expectedDataset);
        assertEquals(1, testModule.getBackupInvocations());

        // clean-up
        FMod_DatasetBackups.dirBackups = null;
    }

    @Test
    public void test_backup_happy_path_persisted_dsg() throws IOException {
        // given
        String expectedDir = "/backups";
        String expectedDataset = "/knowledge";
        String configFile = "config-persistent.ttl";
        server = buildServer("--port=0", "--conf", DIR + "/" + configFile);
        // when
        HttpResponse<InputStream> compactResponse = makeAuthPOSTCallWithPath(server, "$/backups/knowledge", "test");
        // then
        assertEquals(200, compactResponse.statusCode());
        String actualBody = IOUtils.toString(compactResponse.body(), StandardCharsets.UTF_8);
        assertNotNull(actualBody);
        assertTrue(actualBody.contains(expectedDir + expectedDataset), "Actual body does not match expectation: " + actualBody);
        assertEquals(1, testModule.getBackupInvocations());
    }

    @Test
    public void test_backup_happy_path_all() throws IOException {
        // given
        String expectedDir = "/backups";
        String expectedDataset1 = "/ds2";
        String expectedDataset2 = "/ds1";
        String configFile = "config-simple-multiple-endpoints.ttl";
        server = buildServer("--port=0", "--conf", DIR + "/" + configFile);
        // when
        HttpResponse<InputStream> compactResponse = makeAuthPOSTCallWithPath(server, "$/backups", "test");
        // then
        assertEquals(200, compactResponse.statusCode());
        ObjectMapper mapper = new ObjectMapper();
        List<String> resultList = mapper.readValue(compactResponse.body(), new TypeReference<List<String>>() {
        });
        assertEquals(2, resultList.size());
        assertTrue(resultList.get(0).contains(expectedDir + expectedDataset1), "Actual body does not match expectation: " + resultList.get(0));
        assertTrue(resultList.get(1).contains(expectedDir + expectedDataset2), "Actual body does not match expectation: " + resultList.get(1));
        assertEquals(2, testModule.getBackupInvocations());
    }

    @Test
    public void test_backup_calls_underlying_backup() {
        // set-up
        MockedStatic<Backup> mockBackup = mockStatic(Backup.class);
        mockBackup.when(() -> Backup.backup(any(), any(), any())).thenAnswer(invocationOnMock -> null);
        // given
        FMod_DatasetBackups fModDatasetBackups = new FMod_DatasetBackups();
        // when
        fModDatasetBackups.backup(null, null);
        // then
        mockBackup.verify(() -> Backup.backup(any(), any(), any()));
        // clean-up
        mockBackup.close();
    }

    @Test
    public void test_processResponse_success() throws IOException {
        // set-up
        HttpServletResponse mockResponse = mock(HttpServletResponse.class);
        ServletOutputStream mockOutputStream = mock(ServletOutputStream.class);
        when(mockResponse.getOutputStream()).thenReturn(mockOutputStream);

        // given
        ArrayList<String> backedUpFiles = new ArrayList<>();
        backedUpFiles.add("file1.txt");
        backedUpFiles.add("file2.txt");

        // when
        FMod_DatasetBackups.processResponse(mockResponse, backedUpFiles);

        // then
        verify(mockResponse).setContentLength(anyInt());
        verify(mockResponse).setContentType(WebContent.contentTypeJSON);
        verify(mockResponse).setCharacterEncoding(WebContent.charsetUTF8);
        verify(mockOutputStream).print(anyString());
    }

    @Test
    public void test_processResponse_jsonProcessingException() throws IOException {
        // given
        HttpServletResponse mockResponse = mock(HttpServletResponse.class);
        ServletOutputStream mockOutputStream = mock(ServletOutputStream.class);
        when(mockResponse.getOutputStream()).thenReturn(mockOutputStream);

        ArrayList<String> backedUpFiles = new ArrayList<>();
        ObjectMapper mockMapper = mock(ObjectMapper.class);
        when(mockMapper.writeValueAsString(backedUpFiles)).thenThrow(JsonProcessingException.class);
        FMod_DatasetBackups.MAPPER = mockMapper;

        // when
        FMod_DatasetBackups.processResponse(mockResponse, backedUpFiles);

        // then
        verify(mockResponse).setStatus(HttpServletResponse.SC_BAD_REQUEST);

        // clean-up
        FMod_DatasetBackups.MAPPER = new ObjectMapper();
    }

    @Test
    public void test_processResponse_ioException() throws IOException {
        // set-up
        HttpServletResponse mockResponse = mock(HttpServletResponse.class);
        ServletOutputStream mockOutputStream = mock(ServletOutputStream.class);
        when(mockResponse.getOutputStream()).thenReturn(mockOutputStream);

        // given
        ArrayList<String> backedUpFiles = new ArrayList<>();
        doThrow(IOException.class).when(mockOutputStream).print(anyString());

        // when
        FMod_DatasetBackups.processResponse(mockResponse, backedUpFiles);

        // then
        verify(mockResponse).setStatus(HttpServletResponse.SC_UNPROCESSABLE_CONTENT);
    }


    @Test
    public void test_getDirBackups_NullProperty_UsesDefault() {
        // given
        String currentPwd = System.getenv("PWD");
        Configurator.reset();

        // when
        Path result = FMod_DatasetBackups.getDirBackups();

        // then
        assertEquals(Path.of(currentPwd + "/backups"), result);

    }

    @Test
    public void test_getDirBackups_withValidDirectoryButCreatingFails() {
        // set-up
        // reset config
        Configurator.reset();

        String expectedPath = System.getenv("PWD") + "/backups";
        String badPath = "/cannotCreate";

        // given
        Properties properties = new Properties();
        properties.put(ENV_BACKUPS_DIR, badPath);
        Configurator.addSource(new PropertiesSource(properties));

        // when
        Path result = FMod_DatasetBackups.getDirBackups();

        // then
        assertEquals(Path.of(expectedPath), result);
    }

    @Test
    public void test_getDirBackups_withValidDirectoryThatNeedsCreating() throws IOException {
        // set-up
        // reset config
        Configurator.reset();

        String expectedPath = System.getenv("PWD") + "/custom";

        // given
        Properties properties = new Properties();
        properties.put(ENV_BACKUPS_DIR, expectedPath);
        Configurator.addSource(new PropertiesSource(properties));

        // when
        Path result = FMod_DatasetBackups.getDirBackups();

        // then
        assertEquals(Path.of(expectedPath), result);

        // clean-up
        assertTrue(Files.deleteIfExists(Path.of(expectedPath)));
    }

    @Test
    public void test_getDirBackups_withValidDirectoryThatAlreadyExists() throws IOException {
        // set-up
        // reset config
        Configurator.reset();

        // create a temporary directory, which exists
        Path tempFolder = Files.createTempDirectory("test-temp-folder");
        String expectedPath = tempFolder.toAbsolutePath().toString();

        // given
        Properties properties = new Properties();
        properties.put(ENV_BACKUPS_DIR, tempFolder.toString());
        Configurator.addSource(new PropertiesSource(properties));

        // when
        Path result = FMod_DatasetBackups.getDirBackups();

        // then
        assertEquals(Path.of(expectedPath), result);
    }

    @Test
    public void test_chooseFileName_containsSlash() {
        // given
        String datasetName = "example/dataset";
        String pwd = System.getenv("PWD");
        String expectedFilenameStarts = pwd + "/backups/example_dataset_";

        // when
        String result = FMod_DatasetBackups.chooseFileName(datasetName);

        // then
        assertTrue(result.startsWith(expectedFilenameStarts));
    }

    @Test
    public void test_datasetNotRequested() {
        // Null requestName
        assertTrue(FMod_DatasetBackups.datasetNotRequested(null), "Should return true for null requestName");

        // Empty requestName (after trimming)
        assertTrue(FMod_DatasetBackups.datasetNotRequested("   "), "Should return true for empty requestName after trimming");

        // requestName equals "/"
        assertTrue(FMod_DatasetBackups.datasetNotRequested("/"), "Should return true for requestName equal to '/'");

        // Valid requestName (not null, not empty, not "/")
        assertFalse(FMod_DatasetBackups.datasetNotRequested("dataset1"), "Should return false for a valid requestName");
    }
}