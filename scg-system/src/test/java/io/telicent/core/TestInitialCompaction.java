package io.telicent.core;

import com.fasterxml.jackson.core.type.TypeReference;
import io.telicent.LibTestsSCG;
import io.telicent.backup.TestBackupData;
import io.telicent.jena.abac.ABAC;
import io.telicent.jena.abac.SysABAC;
import io.telicent.jena.abac.attributes.syntax.AEX;
import io.telicent.jena.abac.core.Attributes;
import io.telicent.jena.abac.core.AttributesStoreLocal;
import io.telicent.jena.abac.core.DatasetGraphABAC;
import io.telicent.jena.abac.labels.Labels;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.Strings;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.servlets.ActionErrorException;
import org.apache.jena.fuseki.system.FusekiLogging;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.tdb1.TDB1Factory;
import org.apache.jena.tdb1.base.file.Location;
import org.apache.jena.tdb2.DatabaseMgr;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.system.Txn;
import io.telicent.smart.cache.configuration.Configurator;
import io.telicent.smart.cache.configuration.sources.PropertiesSource;
import org.apache.jena.tdb2.store.DatasetGraphSwitchable;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.*;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.telicent.TestJwtServletAuth.*;
import static io.telicent.TestSmartCacheGraphIntegration.launchServer;
import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;
import static io.telicent.core.FMod_InitialCompaction.compactLabels;
import static org.apache.jena.graph.Graph.emptyGraph;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class TestInitialCompaction {
    private static final MockedStatic<DatabaseMgr> mockDatabaseMgr =
            mockStatic(DatabaseMgr.class, Mockito.CALLS_REAL_METHODS);
    private static FusekiServer server;

    private static final String[] PERSISTENT_DB_DIRS = { "knowledgeLegacy", "knowledgeModern" };
    /**
     * Per-test compaction settings, cleared after each test
     */
    private static final Properties COMPACTION_CONFIG = new Properties();

    @BeforeAll
    static void createAndSetupServerDetails() throws Exception {
        LibTestsSCG.setupAuthentication();
        Configurator.addSource(new PropertiesSource(COMPACTION_CONFIG));
        FusekiLogging.setLogging();
        Attributes.buildStore(emptyGraph);

        removePreviousCompactionResults();
    }

    @AfterEach
    void clearDown() {
        mockDatabaseMgr.clearInvocations();
        mockDatabaseMgr.reset();
        FMod_InitialCompaction.SIZES.clear();
        FMod_InitialCompaction.CURRENT_COMPACTIONS.clear();
        FMod_InitialCompaction.ingestPause = new DatasetIngestPause();
        FMod_InitialCompaction.usableSpace = File::getUsableSpace;
        DatasetMaintenanceRegistry.ACTIVE_MAINTENANCE.clear();
        COMPACTION_CONFIG.clear();
        removePreviousCompactionResults();
        if (server != null) {
            server.stop();
        }

    }

    @AfterAll
    public static void teardown() throws Exception {
        // NB - Have to close any open labels stores otherwise we can interfere with other tests that use the same
        //      configuration file
        LibTestsSCG.closeAndClearLabelsStores();
        stopServer();
    }

    private static void removePreviousCompactionResults() {
        for (String db : PERSISTENT_DB_DIRS) {
            File previousCompactionSize =
                    Path.of("target", "databases", db, ".last-compaction").toFile();
            if (previousCompactionSize.exists()) {
                previousCompactionSize.delete();
            }
            File previousCompactionStatus =
                    Path.of("target", "databases", db, ".compaction-status").toFile();
            if (previousCompactionStatus.exists()) {
                previousCompactionStatus.delete();
            }
        }
    }

    static void stopServer() throws Exception {
        if (server != null) {
            server.stop();
        }
        LibTestsSCG.teardownAuthentication();
        mockDatabaseMgr.close();
    }

    @Test
    public void test_emptyGraph() {
        // given, when
        server = SmartCacheGraph.construct("--port=0", "--empty").start();
        // then
        assertNotNull(server.serverURL());
        mockDatabaseMgr.verify(() -> DatabaseMgr.compact(any(), anyBoolean()), times(0));
    }

    @Test
    public void test_authMemoryDataset() {
        // given
        String configFile = "config-no-hierarchies.ttl";
        // when
        server = launchServer(configFile);
        // then
        assertNotNull(server.serverURL());
        mockDatabaseMgr.verify(() -> DatabaseMgr.compact(any(), anyBoolean()), times(0));
    }

    @Test
    @Disabled("Flaky/insufficiently isolated test - works reliably locally but fails on GitHub Actions")
    public void test_persistentDataset_sizeSame_ignoredSecondCall() {
        // given
        mockDatabaseMgr.when(() -> DatabaseMgr.compact(any(), anyBoolean())).thenAnswer(invocationOnMock -> null);
        String configFile = "config-persistent.ttl";
        // when
        server = launchServer(configFile);
        // then
        assertNotNull(server.serverURL());
        mockDatabaseMgr.verify(() -> DatabaseMgr.compact(any(), anyBoolean()), times(1));
        mockDatabaseMgr.clearInvocations();
        // when
        HttpResponse<InputStream> compactResponse =
                makeAuthCallWithCustomToken(server, "$/compactall", LibTestsSCG.tokenForUser("admin", "knowledge"),
                                            "POST");
        // then
        assertEquals(200, compactResponse.statusCode());
        mockDatabaseMgr.verify(() -> DatabaseMgr.compact(any(), anyBoolean()), times(0));
    }

    @Test
    @Disabled("Flaky/insufficiently isolated test - works reliably locally but fails on GitHub Actions")
    public void test_persistentDataset_dictionaryStore_sizeSame_ignoredSecondCall() {
        // given
        mockDatabaseMgr.when(() -> DatabaseMgr.compact(any(), anyBoolean())).thenAnswer(invocationOnMock -> null);
        String configFile = "config-persistent-rocks-dictionary.ttl";
        // when
        server = launchServer(configFile);
        // then
        assertNotNull(server.serverURL());
        mockDatabaseMgr.verify(() -> DatabaseMgr.compact(any(), anyBoolean()), times(1));
        mockDatabaseMgr.clearInvocations();
        // when
        HttpResponse<InputStream> compactResponse =
                makeAuthCallWithCustomToken(server, "$/compactall", LibTestsSCG.tokenForUser("admin", "knowledge"),
                                            "POST");
        // then
        assertEquals(200, compactResponse.statusCode());
        mockDatabaseMgr.verify(() -> DatabaseMgr.compact(any(), anyBoolean()), times(0));
    }

    @Test
    @Disabled("Flaky/insufficiently isolated test - works reliably locally but fails on GitHub Actions")
    public void test_persistentDataset_sizeDifferent_makeSecondCall() {
        // given
        mockDatabaseMgr.when(() -> DatabaseMgr.compact(any(), anyBoolean())).thenAnswer(invocationOnMock -> null);
        String configFile = "config-persistent.ttl";
        // when
        server = launchServer(configFile);
        // then
        assertNotNull(server.serverURL());
        mockDatabaseMgr.verify(() -> DatabaseMgr.compact(any(), anyBoolean()), times(1));
        // when
        FMod_InitialCompaction.SIZES.put("/knowledge", 500L);
        HttpResponse<InputStream> compactResponse =
                makeAuthCallWithCustomToken(server, "$/compactall", LibTestsSCG.tokenForUser("admin", "knowledge"),
                                            "POST");
        // then
        assertEquals(200, compactResponse.statusCode());
    }


    @Test
    public void test_name() {
        // given
        FMod_InitialCompaction fModInitialCompaction = new FMod_InitialCompaction();
        // when
        String name = fModInitialCompaction.name();
        // then
        assertNotNull(name);
    }

    @Test
    @SuppressWarnings("removal")
    public void test_TDB1_DSG() {
        // given
        DatasetGraph tdb1_DG = TDB1Factory.createDatasetGraph(Location.mem());
        // when
        DatasetGraph actualDSG = FMod_InitialCompaction.getTDB2(tdb1_DG);
        // then
        assertNull(actualDSG);
    }

    @Test
    public void test_ABACDSG_wrapped_memGraph() {
        // given
        DatasetGraphABAC dsgABAC = ABAC.authzDataset(DatasetGraphFactory.createTxnMem(),
                                                     AEX.strALLOW,
                                                     Labels.createLabelsStoreMem(),
                                                     SysABAC.denyLabel,
                                                     new AttributesStoreLocal());
        // when
        DatasetGraph actualDSG = FMod_InitialCompaction.getTDB2(dsgABAC);
        // then
        assertNull(actualDSG);
    }

    @Test
    public void test_ABACDSG_wrapped_persistedGraph() {
        // given
        DatasetGraphSwitchable dsgPersists =
                new DatasetGraphSwitchable(Path.of("./"), null, DatasetGraphFactory.createTxnMem());
        DatasetGraphABAC dsgABAC = ABAC.authzDataset(dsgPersists,
                                                     AEX.strALLOW,
                                                     Labels.createLabelsStoreMem(),
                                                     SysABAC.denyLabel,
                                                     new AttributesStoreLocal());
        // when
        DatasetGraph actualDSG = FMod_InitialCompaction.getTDB2(dsgABAC);
        // then
        assertNotNull(actualDSG);
    }

    @Test
    public void test_noServices() {
        // given
        server = SmartCacheGraph.construct("--port=0", "--empty").start();
        FMod_InitialCompaction fModInitialCompaction = new FMod_InitialCompaction();
        // when
        fModInitialCompaction.serverBeforeStarting(server);
        // then
        mockDatabaseMgr.verify(() -> DatabaseMgr.compact(any(), anyBoolean()), times(0));
    }

    @Test
    public void test_wrongServices() {
        // given
        server = SmartCacheGraph.construct("--port=0", "--empty").start();
        FMod_InitialCompaction fModInitialCompaction = new FMod_InitialCompaction();
        fModInitialCompaction.prepare(null, Set.of("Missing", "NotThere"), null);
        // when
        fModInitialCompaction.serverAfterStarting(server);
        // then
        mockDatabaseMgr.verify(() -> DatabaseMgr.compact(any(), anyBoolean()), times(0));
    }

    @Test
    public void test_serverAfterStarting_internalError_doesNotPropagate() throws IOException {
        // given
        mockDatabaseMgr.when(() -> DatabaseMgr.compact(any(), anyBoolean()))
                       .thenThrow(new InternalError("unsafe memory access"));
        server = FusekiServer.create().port(0).add("test", createPersistentSwitchableDataset()).build().start();
        FMod_InitialCompaction fModInitialCompaction = new FMod_InitialCompaction();
        fModInitialCompaction.prepare(null, Set.of("test"), null);

        // when / then
        assertDoesNotThrow(() -> fModInitialCompaction.serverAfterStarting(server));
        mockDatabaseMgr.verify(() -> DatabaseMgr.compact(any(), anyBoolean()), times(1));
    }

    @Test
    public void test_databaseSize_handlesInvalidInput() {
        // given
        DatasetGraph emptyDsg = DatasetGraphFactory.create();
        long expectedSize = -1;
        // when
        long actualSize = FMod_InitialCompaction.findDatabaseSize(emptyDsg);
        // then
        assertEquals(expectedSize, actualSize);
    }

    @Test
    public void test_databaseSize_invalidData() {
        // given
        DatasetGraphSwitchable mockDSG = mock(DatasetGraphSwitchable.class);
        when(mockDSG.getContainerPath()).thenReturn(Path.of("/doesnotexist/tmp.txt"));
        long expectedSize = -1;
        // when
        long actualSize = FMod_InitialCompaction.findDatabaseSize(mockDSG);
        // then
        assertEquals(expectedSize, actualSize);
    }

    @Test
    public void givenCompactionResultsFile_whenLoadingPreviousSize_thenCorrect() throws IOException {
        // Given
        File tempDir = Files.createTempDirectory("test").toFile();
        DatasetGraphSwitchable mockDSG = mock(DatasetGraphSwitchable.class);
        when(mockDSG.getContainerPath()).thenReturn(Path.of(tempDir.getAbsolutePath()));
        File previousSizeFile = FMod_InitialCompaction.getPreviousCompactionResultFile(mockDSG);
        try (OutputStream output = new FileOutputStream(previousSizeFile)) {
            output.write(Long.toString(123456789L).getBytes(StandardCharsets.UTF_8));
        }

        // When
        long previousSize = FMod_InitialCompaction.findPreviousCompactionSize(mockDSG);

        // Then
        Assertions.assertEquals(123456789L, previousSize);
    }

    @Test
    public void givenCompactionIndicatorFile_whenLoadingPreviousIndicator_thenCorrect() throws IOException {
        // Given
        File tempDir = Files.createTempDirectory("test").toFile();
        DatasetGraphSwitchable mockDSG = mock(DatasetGraphSwitchable.class);
        when(mockDSG.getContainerPath()).thenReturn(Path.of(tempDir.getAbsolutePath()));
        File indicatorFile = FMod_InitialCompaction.getCompactionIndicatorFile(mockDSG);
        Properties properties = new Properties();
        properties.setProperty("state", FMod_InitialCompaction.CompactionIndicatorState.FAILED.name());
        properties.setProperty("dataset", "/knowledge");
        properties.setProperty("startedAt", "2026-03-09T07:04:11Z");
        properties.setProperty("updatedAt", "2026-03-09T07:14:27Z");
        properties.setProperty("sizeBefore", "17924117568");
        properties.setProperty("sizeAfter", "-1");
        properties.setProperty("error", "java.lang.InternalError: unsafe memory access");
        try (OutputStream output = new FileOutputStream(indicatorFile)) {
            properties.store(output, "test");
        }

        // When
        Optional<FMod_InitialCompaction.CompactionIndicator> indicator =
                FMod_InitialCompaction.findPreviousCompactionIndicator(mockDSG);

        // Then
        assertTrue(indicator.isPresent());
        assertEquals(FMod_InitialCompaction.CompactionIndicatorState.FAILED, indicator.get().state());
        assertEquals("/knowledge", indicator.get().datasetName());
        assertEquals(17924117568L, indicator.get().sizeBefore());
        assertEquals("java.lang.InternalError: unsafe memory access", indicator.get().error());
    }

    @Test
    public void givenNoCompactionResultsFile_whenLoadingPreviousSize_thenDefaultValueReturned() throws IOException {
        // Given
        File tempDir = Files.createTempDirectory("test").toFile();
        DatasetGraphSwitchable mockDSG = mock(DatasetGraphSwitchable.class);
        when(mockDSG.getContainerPath()).thenReturn(Path.of(tempDir.getAbsolutePath(), "/Data-0001"));

        // When
        long previousSize = FMod_InitialCompaction.findPreviousCompactionSize(mockDSG);

        // Then
        Assertions.assertEquals(-1, previousSize);
    }

    @Test
    public void givenMalformedCompactionIndicatorFile_whenLoadingPreviousIndicator_thenEmptyReturned() throws
            IOException {
        // Given
        File tempDir = Files.createTempDirectory("test").toFile();
        DatasetGraphSwitchable mockDSG = mock(DatasetGraphSwitchable.class);
        when(mockDSG.getContainerPath()).thenReturn(Path.of(tempDir.getAbsolutePath()));
        File indicatorFile = FMod_InitialCompaction.getCompactionIndicatorFile(mockDSG);
        try (OutputStream output = new FileOutputStream(indicatorFile)) {
            output.write("state=NOT_A_REAL_STATE\n".getBytes(StandardCharsets.UTF_8));
        }

        // When
        Optional<FMod_InitialCompaction.CompactionIndicator> indicator =
                FMod_InitialCompaction.findPreviousCompactionIndicator(mockDSG);

        // Then
        assertTrue(indicator.isEmpty());
    }

    @Test
    public void givenMalformedCompactionResultsFile_whenLoadingPreviousSize_thenDefaultValueReturned() throws
            IOException {
        // Given
        File tempDir = Files.createTempDirectory("test").toFile();
        DatasetGraphSwitchable mockDSG = mock(DatasetGraphSwitchable.class);
        when(mockDSG.getContainerPath()).thenReturn(Path.of(tempDir.getAbsolutePath()));
        File previousSizeFile = FMod_InitialCompaction.getPreviousCompactionResultFile(mockDSG);
        try (OutputStream output = new FileOutputStream(previousSizeFile)) {
            output.write("bad data".getBytes(StandardCharsets.UTF_8));
        }

        // When
        long previousSize = FMod_InitialCompaction.findPreviousCompactionSize(mockDSG);

        // Then
        Assertions.assertEquals(-1, previousSize);
    }

    @Test
    public void test_humanReadableSize_handlesInvalidInput() {
        // given
        String expectedSize = "Unknown";
        // when
        String actualSize = FMod_InitialCompaction.humanReadableSize(-5000);
        // then
        assertEquals(expectedSize, actualSize);
    }

    @Test
    public void test_compactWithExistingLock() throws IOException {
        // These exercise the exclusive (non-RO) compaction path
        COMPACTION_CONFIG.put(FMod_InitialCompaction.COMPACTION_RO, "false");
        // given
        DatasetGraphSwitchable dsgPersists = createPersistentSwitchableDataset();
        DatasetGraphSwitchable mockedDsg = spy(dsgPersists);

        when(mockedDsg.tryExclusiveMode(false)).thenReturn(false);
        server = SmartCacheGraph.smartCacheGraphBuilder().port(0).add("test", mockedDsg).build().start();

        // when
        HttpResponse<InputStream> compactResponse =
                makeAuthCallWithCustomToken(server, "$/compactall", LibTestsSCG.tokenForUser("test", "test"),
                                            "POST");
        // then
        assertEquals(200, compactResponse.statusCode());
        String body = IOUtils.toString(compactResponse.body(), StandardCharsets.UTF_8);
        assertTrue(Strings.CI.contains(body, "\"status\":\"ok\""));
        assertTrue(Strings.CI.contains(body, "SKIPPED_LOCK_CONTENTION"));
        mockDatabaseMgr.verify(() -> DatabaseMgr.compact(any(), anyBoolean()), times(0));
        verify(mockedDsg, times(0)).finishExclusiveMode();
    }

    @Test
    public void test_compactOne_withExistingLockReturnsSummary() throws IOException {
        // These exercise the exclusive (non-RO) compaction path
        COMPACTION_CONFIG.put(FMod_InitialCompaction.COMPACTION_RO, "false");
        // given
        DatasetGraphSwitchable dsgPersists = createPersistentSwitchableDataset();
        DatasetGraphSwitchable mockedDsg = spy(dsgPersists);

        when(mockedDsg.tryExclusiveMode(false)).thenReturn(false);
        server = SmartCacheGraph.smartCacheGraphBuilder().port(0).add("test", mockedDsg).build().start();

        // when
        HttpResponse<InputStream> compactResponse =
                makeAuthCallWithCustomToken(server, "$/compact/test",
                                            tokenForUserWithCompactPermissions("test", "test"), "POST");

        // then
        assertEquals(200, compactResponse.statusCode());
        String body = IOUtils.toString(compactResponse.body(), StandardCharsets.UTF_8);
        assertTrue(Strings.CI.contains(body, "\"status\":\"ok\""));
        assertTrue(Strings.CI.contains(body, "SKIPPED_LOCK_CONTENTION"));
        mockDatabaseMgr.verify(() -> DatabaseMgr.compact(any(), anyBoolean()), times(0));
        verify(mockedDsg, times(0)).finishExclusiveMode();
    }

    @Test
    public void test_compactOne_failureReturnsError() throws IOException {
        // These exercise the exclusive (non-RO) compaction path
        COMPACTION_CONFIG.put(FMod_InitialCompaction.COMPACTION_RO, "false");
        // given
        DatasetGraphSwitchable dsgPersists = createPersistentSwitchableDataset();
        DatasetGraphSwitchable mockedDsg = spy(dsgPersists);
        when(mockedDsg.tryExclusiveMode(false)).thenReturn(false);
        server = SmartCacheGraph.smartCacheGraphBuilder().port(0).add("test", mockedDsg).build().start();
        when(mockedDsg.tryExclusiveMode(false)).thenThrow(new RuntimeException("failure"));

        // when
        HttpResponse<InputStream> compactResponse =
                makeAuthCallWithCustomToken(server, "$/compact/test",
                                            tokenForUserWithCompactPermissions("test", "test"), "POST");

        // then
        assertEquals(500, compactResponse.statusCode());
        String body = IOUtils.toString(compactResponse.body(), StandardCharsets.UTF_8);
        assertTrue(Strings.CI.contains(body, "Compaction failed for dataset"));
        assertTrue(Strings.CI.contains(body, "failure"));
    }

    @Test
    public void test_compactOne_internalErrorReturnsError() throws IOException {
        // given
        mockDatabaseMgr.when(() -> DatabaseMgr.compact(any(), anyBoolean()))
                       .thenThrow(new InternalError("unsafe memory access"));
        DatasetGraphSwitchable dsgPersists = createPersistentSwitchableDataset();
        server = SmartCacheGraph.smartCacheGraphBuilder().port(0).add("test", dsgPersists).build().start();

        // when
        HttpResponse<InputStream> compactResponse =
                makeAuthCallWithCustomToken(server, "$/compact/test",
                                            tokenForUserWithCompactPermissions("test", "test"), "POST");

        // then
        assertEquals(500, compactResponse.statusCode());
        String body = IOUtils.toString(compactResponse.body(), StandardCharsets.UTF_8);
        assertTrue(Strings.CI.contains(body, "Compaction failed for dataset"));
        Optional<FMod_InitialCompaction.CompactionIndicator> indicator =
                FMod_InitialCompaction.findPreviousCompactionIndicator(dsgPersists);
        assertTrue(indicator.isPresent());
        assertEquals(FMod_InitialCompaction.CompactionIndicatorState.FAILED, indicator.get().state());
        assertNotNull(indicator.get().error());
    }

    @Test
    public void test_compactOne_async_happyPath() throws IOException {
        mockDatabaseMgr.when(() -> DatabaseMgr.compact(any(), anyBoolean())).thenAnswer(invocationOnMock -> null);
        DatasetGraphSwitchable dsgPersists = createPersistentSwitchableDataset();
        server = SmartCacheGraph.smartCacheGraphBuilder().port(0).add("test", dsgPersists).build().start();
        String token = tokenForUserWithCompactPermissions("test", "test");

        HttpResponse<InputStream> submitResponse =
                makeAuthCallWithCustomToken(server, "$/compact/test?async=true", token, "POST");

        assertEquals(202, submitResponse.statusCode());
        Map<String, Object> submit = convertToMap(submitResponse);
        assertEquals("COMPACT_DATASET", submit.get("operation"));
        String statusPath = (String) submit.get("status-path");
        assertTrue(statusPath.contains("/$/compaction/jobs/test/"));

        Map<String, Object> job = waitForJob(statusPath, token);
        assertEquals("SUCCEEDED", job.get("status"));
        assertEquals(200, job.get("http-status"));
        Map<?, ?> result = asMap(job.get("result"));
        assertEquals("ok", result.get("status"));
        Map<?, ?> datasets = asMap(result.get("datasets"));
        assertTrue(Set.of("COMPACTED", "SKIPPED_ALREADY_COMPACTED", "SKIPPED_PREVIOUSLY_COMPACTED")
                      .contains(datasets.get("/test")));
    }

    @Test
    public void test_compactOne_async_unknownJobId() {
        server = SmartCacheGraph.smartCacheGraphBuilder().port(0).add("test", DatasetGraphFactory.createTxnMem()).build().start();

        HttpResponse<InputStream> jobResponse =
                makeAuthCallWithCustomToken(server, "$/compaction/jobs/test/unknown-job-id",
                                            tokenForUserWithCompactPermissions("test", "test"), "GET");

        assertEquals(404, jobResponse.statusCode());
        Map<String, Object> responseMap = convertToMap(jobResponse);
        assertEquals("Unknown compaction job id: unknown-job-id", responseMap.get("error"));
    }

    @Test
    public void test_compactOne_async_listJobs() throws IOException {
        mockDatabaseMgr.when(() -> DatabaseMgr.compact(any(), anyBoolean())).thenAnswer(invocationOnMock -> null);
        DatasetGraphSwitchable dsgPersists = createPersistentSwitchableDataset();
        server = SmartCacheGraph.smartCacheGraphBuilder().port(0).add("test", dsgPersists).build().start();
        String token = tokenForUserWithCompactPermissions("test", "test");

        HttpResponse<InputStream> submitResponse =
                makeAuthCallWithCustomToken(server, "$/compact/test?async=true", token, "POST");
        Map<String, Object> submit = convertToMap(submitResponse);
        String statusPath = (String) submit.get("status-path");
        waitForJob(statusPath, token);

        HttpResponse<InputStream> listResponse =
                makeAuthCallWithCustomToken(server, "$/compaction/jobs/test", token, "GET");

        assertEquals(200, listResponse.statusCode());
        Map<String, Object> responseMap = convertToMap(listResponse);
        List<?> jobs = asList(responseMap.get("jobs"));
        assertFalse(jobs.isEmpty());
    }

    @Test
    public void test_compactAll_mixedOutcomesReturnsSummary() throws IOException {
        // These exercise the exclusive (non-RO) compaction path
        COMPACTION_CONFIG.put(FMod_InitialCompaction.COMPACTION_RO, "false");
        // Given
        DatasetGraphSwitchable dsgPersists = createPersistentSwitchableDataset();
        DatasetGraphSwitchable mockedDsg = spy(dsgPersists);
        DatasetGraph memDsg = DatasetGraphFactory.createTxnMem();
        when(mockedDsg.tryExclusiveMode(false)).thenReturn(false);
        server = SmartCacheGraph.smartCacheGraphBuilder()
                                .port(0)
                                .add("test", mockedDsg)
                                .add("mem", memDsg)
                                .build()
                                .start();

        // When
        HttpResponse<InputStream> compactResponse = makeAuthCallWithCustomToken(server, "$/compactall",
                                                                                tokenForUserWithCompactPermissions(
                                                                                        "test", "test", "mem"),
                                                                                "POST");

        // Then
        assertEquals(200, compactResponse.statusCode());
        String body = IOUtils.toString(compactResponse.body(), StandardCharsets.UTF_8);
        assertTrue(Strings.CI.contains(body, "\"status\":\"ok\""));
        assertTrue(Strings.CI.contains(body, "SKIPPED_LOCK_CONTENTION"));
        assertTrue(Strings.CI.contains(body, "SKIPPED_NOT_TDB2"));
    }

    @Test
    public void test_compactAll_failureInDatasetReturnsError() throws IOException {
        // These exercise the exclusive (non-RO) compaction path
        COMPACTION_CONFIG.put(FMod_InitialCompaction.COMPACTION_RO, "false");
        // Given
        DatasetGraphSwitchable dsgPersists = createPersistentSwitchableDataset();
        DatasetGraphSwitchable mockedDsg = spy(dsgPersists);
        when(mockedDsg.tryExclusiveMode(false)).thenReturn(false);
        server = SmartCacheGraph.smartCacheGraphBuilder().port(0).add("test", mockedDsg).build().start();
        when(mockedDsg.tryExclusiveMode(false)).thenThrow(new RuntimeException("failure"));

        // When
        HttpResponse<InputStream> compactResponse = makeAuthCallWithCustomToken(server, "$/compactall",
                                                                                tokenForUserWithCompactPermissions(
                                                                                        "test", "test"),
                                                                                "POST");

        // Then
        assertEquals(500, compactResponse.statusCode());
        String body = IOUtils.toString(compactResponse.body(), StandardCharsets.UTF_8);
        assertTrue(Strings.CI.contains(body, "Compaction failed for one or more datasets"));
        assertTrue(Strings.CI.contains(body, "failure"));
    }

    @Test
    public void test_compactAll_internalErrorInDatasetReturnsError() throws IOException {
        // Given
        mockDatabaseMgr.when(() -> DatabaseMgr.compact(any(), anyBoolean()))
                       .thenThrow(new InternalError("unsafe memory access"));
        DatasetGraphSwitchable dsgPersists = createPersistentSwitchableDataset();
        server = SmartCacheGraph.smartCacheGraphBuilder().port(0).add("test", dsgPersists).build().start();

        // When
        HttpResponse<InputStream> compactResponse = makeAuthCallWithCustomToken(server, "$/compactall",
                                                                                tokenForUserWithCompactPermissions(
                                                                                        "test", "test"),
                                                                                "POST");

        // Then
        assertEquals(500, compactResponse.statusCode());
        String body = IOUtils.toString(compactResponse.body(), StandardCharsets.UTF_8);
        assertTrue(Strings.CI.contains(body, "Compaction failed for one or more datasets"));
        Optional<FMod_InitialCompaction.CompactionIndicator> indicator =
                FMod_InitialCompaction.findPreviousCompactionIndicator(dsgPersists);
        assertTrue(indicator.isPresent());
        assertEquals(FMod_InitialCompaction.CompactionIndicatorState.FAILED, indicator.get().state());
        assertNotNull(indicator.get().error());
    }

    @Test
    public void test_compactAll_async_happyPath() throws IOException {
        mockDatabaseMgr.when(() -> DatabaseMgr.compact(any(), anyBoolean())).thenAnswer(invocationOnMock -> null);
        DatasetGraphSwitchable dsgPersists = createPersistentSwitchableDataset();
        server = SmartCacheGraph.smartCacheGraphBuilder().port(0).add("test", dsgPersists).build().start();
        String token = tokenForUserWithCompactPermissions("test", "test");

        HttpResponse<InputStream> submitResponse =
                makeAuthCallWithCustomToken(server, "$/compactall?async=true", token, "POST");

        assertEquals(202, submitResponse.statusCode());
        Map<String, Object> submit = convertToMap(submitResponse);
        assertEquals("COMPACT_ALL_DATASETS", submit.get("operation"));
        String statusPath = (String) submit.get("status-path");
        assertTrue(statusPath.contains("/$/compaction/jobs/all/"));

        Map<String, Object> job = waitForJob(statusPath, token);
        assertEquals("SUCCEEDED", job.get("status"));
        assertEquals(200, job.get("http-status"));
        Map<?, ?> result = asMap(job.get("result"));
        Map<?, ?> datasets = asMap(result.get("datasets"));
        assertTrue(Set.of("COMPACTED", "SKIPPED_ALREADY_COMPACTED", "SKIPPED_PREVIOUSLY_COMPACTED")
                      .contains(datasets.get("/test")));
    }

    @Test
    public void test_compactAll_async_listJobs() throws IOException {
        mockDatabaseMgr.when(() -> DatabaseMgr.compact(any(), anyBoolean())).thenAnswer(invocationOnMock -> null);
        DatasetGraphSwitchable dsgPersists = createPersistentSwitchableDataset();
        server = SmartCacheGraph.smartCacheGraphBuilder().port(0).add("test", dsgPersists).build().start();
        String token = tokenForUserWithCompactPermissions("test", "test");

        HttpResponse<InputStream> submitResponse =
                makeAuthCallWithCustomToken(server, "$/compactall?async=true", token, "POST");
        Map<String, Object> submit = convertToMap(submitResponse);
        String statusPath = (String) submit.get("status-path");
        waitForJob(statusPath, token);

        HttpResponse<InputStream> listResponse =
                makeAuthCallWithCustomToken(server, "$/compaction/jobs/all", token, "GET");

        assertEquals(200, listResponse.statusCode());
        Map<String, Object> responseMap = convertToMap(listResponse);
        List<?> jobs = asList(responseMap.get("jobs"));
        assertFalse(jobs.isEmpty());
    }

    @Test
    public void test_compactAll_happyPath() {
        // given
        mockDatabaseMgr.when(() -> DatabaseMgr.compact(any(), anyBoolean())).thenAnswer(invocationOnMock -> null);
        String configFile = "config-persistent.ttl";
        server = launchServer(configFile);
        // when
        HttpResponse<InputStream> compactResponse =
                makeAuthCallWithCustomToken(server, "$/compactall", LibTestsSCG.tokenForUser("test", "knowledge"),
                                            "POST");
        // then
        assertEquals(200, compactResponse.statusCode());
        assertDoesNotThrow(() -> {
            String body = IOUtils.toString(compactResponse.body(), StandardCharsets.UTF_8);
            assertTrue(Strings.CI.contains(body, "\"status\":\"ok\""));
        });
    }

    @Test
    public void test_compactOne_indicatorRemainsInProgressUntilExclusiveModeReleased() throws IOException {
        // given
        mockDatabaseMgr.when(() -> DatabaseMgr.compact(any(), anyBoolean())).thenAnswer(invocationOnMock -> null);
        DatasetGraphSwitchable dsgPersists = createPersistentSwitchableDataset();
        DatasetGraphSwitchable mockedDsg = spy(dsgPersists);
        when(mockedDsg.tryExclusiveMode(false)).thenReturn(true);
        doAnswer(invocation -> {
            Optional<FMod_InitialCompaction.CompactionIndicator> indicator =
                    FMod_InitialCompaction.findCurrentCompactionIndicator(mockedDsg);
            assertTrue(indicator.isPresent());
            assertEquals(FMod_InitialCompaction.CompactionIndicatorState.IN_PROGRESS, indicator.get().state());
            invocation.callRealMethod();
            return null;
        }).when(mockedDsg).finishExclusiveMode();
        server = SmartCacheGraph.smartCacheGraphBuilder().port(0).add("test", mockedDsg).build().start();

        // when
        HttpResponse<InputStream> compactResponse =
                makeAuthCallWithCustomToken(server, "$/compact/test",
                                            tokenForUserWithCompactPermissions("test", "test"), "POST");

        // then
        assertEquals(200, compactResponse.statusCode());
        Optional<FMod_InitialCompaction.CompactionIndicator> indicator =
                FMod_InitialCompaction.findCurrentCompactionIndicator(mockedDsg);
        assertTrue(indicator.isPresent());
        assertEquals(FMod_InitialCompaction.CompactionIndicatorState.SUCCEEDED, indicator.get().state());
        verify(mockedDsg, times(1)).finishExclusiveMode();
    }

    @Test
    public void givenWrongRoles_whenCompactAll_thenForbidden() throws IOException {
        // Given
        mockDatabaseMgr.when(() -> DatabaseMgr.compact(any(), anyBoolean())).thenAnswer(invocationOnMock -> null);
        String configFile = "config-persistent.ttl";
        server = launchServer(configFile);

        // When
        HttpResponse<InputStream> compactResponse =
                makeAuthCallWithCustomToken(server, "$/compactall", TestBackupData.tokenWithUserRoleOnly(), "POST");

        // Then
        assertEquals(403, compactResponse.statusCode());
        String error = IOUtils.toString(compactResponse.body(), StandardCharsets.UTF_8);
        assertTrue(Strings.CI.contains(error, "requires roles"));
    }

    @Test
    public void givenInsufficientPermissions_whenCompactAll_thenForbidden() throws IOException {
        // Given
        mockDatabaseMgr.when(() -> DatabaseMgr.compact(any(), anyBoolean())).thenAnswer(invocationOnMock -> null);
        String configFile = "config-persistent.ttl";
        server = launchServer(configFile);

        // When
        HttpResponse<InputStream> compactResponse =
                makeAuthCallWithCustomToken(server, "$/compactall", TestBackupData.tokenWithBackupReadPermission(),
                                            "POST");

        // Then
        assertEquals(403, compactResponse.statusCode());
        String error = IOUtils.toString(compactResponse.body(), StandardCharsets.UTF_8);
        assertTrue(Strings.CI.contains(error, "requires permissions"));
    }

    @Test
    @Disabled("Flaky/insufficiently isolated test - works reliably locally but fails on GitHub Actions")
    public void givenServer_whenPreviouslyCompacted_thenAskingToCompactAgainIsANoOp() {
        // Given
        mockDatabaseMgr.when(() -> DatabaseMgr.compact(any(), anyBoolean())).thenAnswer(invocationOnMock -> null);
        String configFile = "config-persistent.ttl";
        server = launchServer(configFile);

        // When
        HttpResponse<InputStream> compactResponse =
                makeAuthCallWithCustomToken(server, "$/compactall", LibTestsSCG.tokenForUser("admin", "knowledge"),
                                            "POST");
        assertEquals(200, compactResponse.statusCode());
        mockDatabaseMgr.verify(() -> DatabaseMgr.compact(any(), anyBoolean()), times(1));

        // Then
        assertEquals(200, compactResponse.statusCode());
        compactResponse =
                makeAuthCallWithCustomToken(server, "$/compactall", LibTestsSCG.tokenForUser("admin", "knowledge"),
                                            "POST");
        assertEquals(200, compactResponse.statusCode());
        mockDatabaseMgr.verify(() -> DatabaseMgr.compact(any(), anyBoolean()), times(1));
    }

    @Test
    @SuppressWarnings("removal")
    public void test_compactLabels_notABAC() throws Exception {
        // given
        DatasetGraph dsgNotABAC = DatasetGraphFactory.createTxnMem();
        // when
        // then
        compactLabels(dsgNotABAC);
    }

    @Test
    public void test_compactLabels_notRocksDB() throws Exception {
        // given
        DatasetGraphABAC dsgABAC = ABAC.authzDataset(DatasetGraphFactory.createTxnMem(),
                                                     AEX.strALLOW,
                                                     Labels.createLabelsStoreMem(),
                                                     SysABAC.denyLabel,
                                                     new AttributesStoreLocal());
        // when
        // then
        compactLabels(dsgABAC);
    }

    @Test
    public void test_configured_exception() {
        // given
        FMod_InitialCompaction fModInitialCompaction = new FMod_InitialCompaction();
        // when
        // then
        FusekiServer.Builder mockBuilder = mock(FusekiServer.Builder.class);

        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<HttpServlet> servletCaptor = ArgumentCaptor.forClass(HttpServlet.class);
        when(mockBuilder.addServlet(pathCaptor.capture(), servletCaptor.capture())).thenReturn(mockBuilder);

        fModInitialCompaction.configured(mockBuilder, null, null);

        verify(mockBuilder).addServlet(eq("/$/compactall"), any(HttpServlet.class));
        int compactAllIndex = pathCaptor.getAllValues().indexOf("/$/compactall");
        HttpServlet capturedServlet = servletCaptor.getAllValues().get(compactAllIndex);


        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getProtocol()).thenReturn("HTTP/1.1");

        HttpServletResponse response = mock(HttpServletResponse.class);
        assertThrows(ActionErrorException.class, () -> capturedServlet.service(request, response));
    }

    private static String tokenForUserWithCompactPermissions(String user, String... datasetNames) {
        List<String> compactPermissions = java.util.Arrays.stream(datasetNames)
                                                          .map(name -> "api." + name + ".compact")
                                                          .toList();
        return LibTestsSCG.tokenBuilder(user)
                          .claims(Map.of("roles", List.of("ADMIN_SYSTEM"),
                                         "permissions", compactPermissions))
                          .compact();
    }

    private Map<String, Object> convertToMap(HttpResponse<InputStream> response) {
        try {
            InputStreamReader reader = new InputStreamReader(response.body(), StandardCharsets.UTF_8);
            return OBJECT_MAPPER.readValue(reader, new TypeReference<>() {});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> waitForJob(String jobPath, String jwt) {
        String requestPath = jobPath.startsWith("/") ? jobPath.substring(1) : jobPath;
        for (int i = 0; i < 20; i++) {
            HttpResponse<InputStream> jobResponse = makeAuthCallWithCustomToken(server, requestPath, jwt, "GET");
            if (jobResponse.statusCode() != 200) {
                try {
                    fail("Unexpected status " + jobResponse.statusCode() + " for " + requestPath + ": "
                         + IOUtils.toString(jobResponse.body(), StandardCharsets.UTF_8));
                } catch (IOException e) {
                    fail("Unexpected status " + jobResponse.statusCode() + " for " + requestPath
                         + " and failed to read body: " + e.getMessage());
                }
            }
            Map<String, Object> responseMap = convertToMap(jobResponse);
            Map<String, Object> job = asTypedMap(responseMap.get("job"));
            if ("SUCCEEDED".equals(job.get("status")) || "FAILED".equals(job.get("status"))) {
                return job;
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Interrupted while waiting for async compaction job");
            }
        }
        fail("Timed out waiting for async compaction job completion");
        return null;
    }

    private static Map<?, ?> asMap(Object value) {
        assertInstanceOf(Map.class, value);
        return (Map<?, ?>) value;
    }

    private static Map<String, Object> asTypedMap(Object value) {
        assertInstanceOf(Map.class, value);
        @SuppressWarnings("unchecked")
        Map<String, Object> typed = (Map<String, Object>) value;
        return typed;
    }

    private static List<?> asList(Object value) {
        assertInstanceOf(List.class, value);
        return (List<?>) value;
    }

    @Test
    public void test_compactOne_whileCompactionInProgressIsSkipped() throws IOException {
        // given
        DatasetGraphSwitchable dsgPersists = createPersistentSwitchableDataset();
        DatasetGraphSwitchable mockedDsg = spy(dsgPersists);
        // A running compaction leaves a Data-NNNN-tmp directory alongside the current one
        Files.createDirectories(dsgPersists.getContainerPath().resolve("Data-0002-tmp"));
        Optional<DatasetMaintenanceRegistry.MaintenanceHandle> running =
                DatasetMaintenanceRegistry.begin(mockedDsg, "/test",
                                                 DatasetMaintenanceRegistry.MaintenanceOperation.COMPACTION);
        assertTrue(running.isPresent());
        server = SmartCacheGraph.smartCacheGraphBuilder().port(0).add("test", mockedDsg).build().start();

        try {
            // when
            HttpResponse<InputStream> compactResponse =
                    makeAuthCallWithCustomToken(server, "$/compact/test",
                                                tokenForUserWithCompactPermissions("test", "test"), "POST");

            // then
            assertEquals(200, compactResponse.statusCode());
            String body = IOUtils.toString(compactResponse.body(), StandardCharsets.UTF_8);
            assertTrue(Strings.CI.contains(body, "SKIPPED_MAINTENANCE_IN_PROGRESS"));
            mockDatabaseMgr.verify(() -> DatabaseMgr.compact(any(), anyBoolean()), times(0));
            verify(mockedDsg, never()).tryExclusiveMode(anyBoolean());
            assertFalse(FMod_InitialCompaction.getCompactionIndicatorFile(mockedDsg).exists());
        } finally {
            DatasetMaintenanceRegistry.end(running.get());
        }
    }

    @Test
    public void test_compactOne_maintenanceStartedAfterLockReleasesExclusiveMode() throws IOException {
        // given
        DatasetGraphSwitchable dsgPersists = createPersistentSwitchableDataset();
        DatasetGraphSwitchable mockedDsg = spy(dsgPersists);
        // Another maintenance operation registers between the in-progress check and the compaction starting
        List<DatasetMaintenanceRegistry.MaintenanceHandle> other = new java.util.ArrayList<>();
        doAnswer(invocation -> {
            DatasetMaintenanceRegistry.begin(mockedDsg, "/test", DatasetMaintenanceRegistry.MaintenanceOperation.BACKUP)
                                      .ifPresent(other::add);
            return invocation.callRealMethod();
        }).when(mockedDsg).tryExclusiveMode(false);
        server = SmartCacheGraph.smartCacheGraphBuilder().port(0).add("test", mockedDsg).build().start();

        try {
            // when
            HttpResponse<InputStream> compactResponse =
                    makeAuthCallWithCustomToken(server, "$/compact/test",
                                                tokenForUserWithCompactPermissions("test", "test"), "POST");

            // then
            assertEquals(200, compactResponse.statusCode());
            String body = IOUtils.toString(compactResponse.body(), StandardCharsets.UTF_8);
            assertTrue(Strings.CI.contains(body, "SKIPPED_MAINTENANCE_IN_PROGRESS"));
            mockDatabaseMgr.verify(() -> DatabaseMgr.compact(any(), anyBoolean()), times(0));
            verify(mockedDsg, times(1)).finishExclusiveMode();
            // Exclusive mode was released, so it can be taken again
            assertTrue(mockedDsg.tryExclusiveMode(false));
            mockedDsg.finishExclusiveMode();
        } finally {
            other.forEach(DatasetMaintenanceRegistry::end);
        }
    }

    @Test
    public void test_findDatabaseSize_withTmpDataDirectoryReturnsUnknown() throws IOException {
        // given
        DatasetGraphSwitchable dsg = createPersistentSwitchableDataset();
        Files.createDirectories(dsg.getContainerPath().resolve("Data-0002-tmp"));

        // when
        long size = FMod_InitialCompaction.findDatabaseSize(dsg);

        // then
        assertEquals(-1, size);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // RO Compaction (COMPACTION_RO, the default for requested compactions)
    // ---------------------------------------------------------------------------------------------------------------

    private static final String COMPACT_URL = "$/compact/test";

    private static DatasetGraphSwitchable createTdb2DatasetWithData() throws IOException {
        Path dir = Files.createTempDirectory("ro-compaction-tdb2");
        DatasetGraphSwitchable dsg = (DatasetGraphSwitchable) DatabaseMgr.connectDatasetGraph(dir.toString());
        // Several commits so there is something for compaction to reclaim
        for (int i = 0; i < 5; i++) {
            final int batch = i;
            Txn.executeWrite(dsg, () -> {
                for (int j = 0; j < 200; j++) {
                    dsg.getDefaultGraph().add(NodeFactory.createURI("urn:s" + batch + "_" + j),
                                              NodeFactory.createURI("urn:p"), NodeFactory.createLiteralString("o" + j));
                }
            });
        }
        return dsg;
    }

    private static void addMoreData(DatasetGraphSwitchable dsg) {
        for (int i = 0; i < 3; i++) {
            final int batch = i;
            Txn.executeWrite(dsg, () -> {
                for (int j = 0; j < 200; j++) {
                    dsg.getDefaultGraph().add(NodeFactory.createURI("urn:more" + batch + "_" + j),
                                              NodeFactory.createURI("urn:p"), NodeFactory.createLiteralString("x" + j));
                }
            });
        }
    }

    private static String compactAndGetBody() throws IOException {
        HttpResponse<InputStream> response =
                makeAuthCallWithCustomToken(server, COMPACT_URL, tokenForUserWithCompactPermissions("test", "test"),
                                            "POST");
        assertEquals(200, response.statusCode());
        return IOUtils.toString(response.body(), StandardCharsets.UTF_8);
    }

    /**
     * Replaces the ingest pauser with one that records what it is asked to do, optionally running an action while
     * "paused" and optionally never reaching a pause point
     */
    private static List<String> fakeIngestPause(Runnable whilePausing, boolean reachesPausePoint) {
        List<String> calls = new java.util.concurrent.CopyOnWriteArrayList<>();
        FMod_InitialCompaction.ingestPause = new DatasetIngestPause(List::of, name -> {
            calls.add("pause " + name);
            whilePausing.run();
        }, name -> calls.add("resume " + name), (name, timeout) -> reachesPausePoint);
        return calls;
    }

    @Test
    public void givenReaderActive_whenROCompaction_thenCompacts() throws Exception {
        // given
        DatasetGraphSwitchable dsg = createTdb2DatasetWithData();
        server = SmartCacheGraph.smartCacheGraphBuilder().port(0).add("test", dsg).build().start();
        // Startup compaction has just compacted the store; write more so a requested compaction has work to do
        addMoreData(dsg);
        java.util.concurrent.CountDownLatch readerStarted = new java.util.concurrent.CountDownLatch(1);
        // A long-running query: Jena's compaction lets it finish against the old copy before switching over
        java.util.concurrent.CompletableFuture<Void> reader = java.util.concurrent.CompletableFuture.runAsync(
                () -> Txn.executeRead(dsg, () -> {
                    readerStarted.countDown();
                    sleep(1500);
                }));
        assertTrue(readerStarted.await(10, java.util.concurrent.TimeUnit.SECONDS));

        // when
        String body = compactAndGetBody();

        // then
        assertTrue(Strings.CI.contains(body, "\"COMPACTED\""), body);
        reader.get(10, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(DatasetMaintenanceRegistry.ACTIVE_MAINTENANCE.isEmpty());
    }

    @Test
    public void givenReaderActiveAndRODisabled_whenCompaction_thenSkippedForLockContention() throws Exception {
        // given
        COMPACTION_CONFIG.put(FMod_InitialCompaction.COMPACTION_RO, "false");
        DatasetGraphSwitchable dsg = createTdb2DatasetWithData();
        server = SmartCacheGraph.smartCacheGraphBuilder().port(0).add("test", dsg).build().start();
        // Startup compaction has just compacted the store; write more so a requested compaction has work to do
        addMoreData(dsg);
        java.util.concurrent.CountDownLatch readerStarted = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CompletableFuture<Void> reader = java.util.concurrent.CompletableFuture.runAsync(
                () -> Txn.executeRead(dsg, () -> {
                    readerStarted.countDown();
                    await(release);
                }));
        assertTrue(readerStarted.await(10, java.util.concurrent.TimeUnit.SECONDS));

        try {
            // when
            String body = compactAndGetBody();

            // then
            assertTrue(Strings.CI.contains(body, "SKIPPED_LOCK_CONTENTION"), body);
        } finally {
            release.countDown();
            reader.get(10, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    @Test
    public void givenROCompaction_whenIngestPaused_thenReadsServedAndWritesRejectedUntilResumed() throws Exception {
        // given
        DatasetGraphSwitchable dsg = createTdb2DatasetWithData();
        server = SmartCacheGraph.smartCacheGraphBuilder().port(0).add("test", dsg).build().start();
        // Startup compaction has just compacted the store; write more so a requested compaction has work to do
        addMoreData(dsg);
        // Allowed to read and write the dataset, so any rejection comes from the availability filter
        String token = LibTestsSCG.tokenBuilder("test")
                                  .claims(Map.of("roles", List.of("USER", "ADMIN_SYSTEM"), "permissions",
                                                 List.of("api.test.read", "api.test.write", "api.test.compact")))
                                  .compact();
        Map<String, Integer> statusesWhilePaused = new java.util.concurrent.ConcurrentHashMap<>();
        List<String> calls = fakeIngestPause(() -> {
            // While the compaction is pausing ingest the maintenance operation is already registered
            assertEquals(DatasetMaintenanceRegistry.MaintenanceOperation.RO_COMPACTION,
                         DatasetMaintenanceRegistry.findCurrentMaintenance(dsg).orElseThrow().operation());
            statusesWhilePaused.put("query", makeAuthCallWithCustomToken(server,
                    "test/query?query=" + java.net.URLEncoder.encode("SELECT * { ?s ?p ?o } LIMIT 1",
                                                                    StandardCharsets.UTF_8), token, "GET")
                    .statusCode());
            statusesWhilePaused.put("update", makeAuthCallWithCustomToken(server,
                    "test/update?update=" + java.net.URLEncoder.encode("INSERT DATA { <urn:a> <urn:b> <urn:c> }",
                                                                      StandardCharsets.UTF_8), token, "POST")
                    .statusCode());
        }, true);

        // when
        String body = compactAndGetBody();

        // then
        assertTrue(Strings.CI.contains(body, "\"COMPACTED\""), body);
        assertEquals(List.of("pause /test", "resume /test"), calls);
        assertNotEquals(503, statusesWhilePaused.get("query"), "reads must be served during an RO Compaction");
        assertEquals(503, statusesWhilePaused.get("update"), "writes must be rejected during an RO Compaction");
        assertTrue(DatasetMaintenanceRegistry.ACTIVE_MAINTENANCE.isEmpty());
    }

    @Test
    public void givenIngestNeverReachesPausePoint_whenROCompaction_thenSkippedAndIngestResumed() throws Exception {
        // given
        DatasetGraphSwitchable dsg = createTdb2DatasetWithData();
        server = SmartCacheGraph.smartCacheGraphBuilder().port(0).add("test", dsg).build().start();
        // Startup compaction has just compacted the store; write more so a requested compaction has work to do
        addMoreData(dsg);
        List<String> calls = fakeIngestPause(() -> { }, false);
        Optional<FMod_InitialCompaction.CompactionIndicator> indicatorBefore =
                FMod_InitialCompaction.findPreviousCompactionIndicator(dsg);

        // when
        String body = compactAndGetBody();

        // then
        assertTrue(Strings.CI.contains(body, "SKIPPED_LOCK_CONTENTION"), body);
        assertEquals(List.of("pause /test", "resume /test"), calls);
        assertTrue(DatasetMaintenanceRegistry.ACTIVE_MAINTENANCE.isEmpty());
        // Nothing was started, so the indicator from the startup compaction is untouched
        assertEquals(indicatorBefore, FMod_InitialCompaction.findPreviousCompactionIndicator(dsg));
    }

    @Test
    public void givenWriterStillActive_whenROCompaction_thenSkippedAfterDrainTimeoutThenCompactsOnceItFinishes()
            throws Exception {
        // given
        COMPACTION_CONFIG.put(FMod_InitialCompaction.COMPACTION_WRITER_DRAIN_TIMEOUT, "PT0.5S");
        DatasetGraphSwitchable dsg = createTdb2DatasetWithData();
        server = SmartCacheGraph.smartCacheGraphBuilder().port(0).add("test", dsg).build().start();
        // Startup compaction has just compacted the store; write more so a requested compaction has work to do
        addMoreData(dsg);
        List<String> calls = fakeIngestPause(() -> { }, true);
        java.util.concurrent.CountDownLatch writerStarted = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CompletableFuture<Void> writer = java.util.concurrent.CompletableFuture.runAsync(
                () -> Txn.executeWrite(dsg, () -> {
                    dsg.getDefaultGraph().add(NodeFactory.createURI("urn:w"), NodeFactory.createURI("urn:p"),
                                              NodeFactory.createURI("urn:o"));
                    writerStarted.countDown();
                    await(release);
                }));
        assertTrue(writerStarted.await(10, java.util.concurrent.TimeUnit.SECONDS));

        // when
        String whileWriting;
        try {
            whileWriting = compactAndGetBody();
        } finally {
            release.countDown();
            writer.get(10, java.util.concurrent.TimeUnit.SECONDS);
        }
        addMoreData(dsg);
        String afterwards = compactAndGetBody();

        // then
        assertTrue(Strings.CI.contains(whileWriting, "SKIPPED_LOCK_CONTENTION"), whileWriting);
        assertTrue(Strings.CI.contains(afterwards, "\"COMPACTED\""), afterwards);
        assertEquals(List.of("pause /test", "resume /test", "pause /test", "resume /test"), calls);
        assertTrue(Txn.calculateRead(dsg, () -> dsg.getDefaultGraph().contains(NodeFactory.createURI("urn:w"),
                                                                              NodeFactory.createURI("urn:p"),
                                                                              NodeFactory.createURI("urn:o"))),
                   "the write that was in progress must survive the compaction");
    }

    @Test
    public void givenNotEnoughDiskSpace_whenCompaction_thenSkipped() throws Exception {
        // given
        FMod_InitialCompaction.usableSpace = f -> 0L;
        DatasetGraphSwitchable dsg = createTdb2DatasetWithData();
        server = SmartCacheGraph.smartCacheGraphBuilder().port(0).add("test", dsg).build().start();
        // Startup compaction has just compacted the store; write more so a requested compaction has work to do
        addMoreData(dsg);

        // when
        String body = compactAndGetBody();

        // then
        assertTrue(Strings.CI.contains(body, "SKIPPED_INSUFFICIENT_DISK_SPACE"), body);
        assertTrue(DatasetMaintenanceRegistry.ACTIVE_MAINTENANCE.isEmpty());
    }

    @Test
    public void givenDiskSpaceCheckDisabled_whenCompaction_thenCompactsDespiteLowSpace() throws Exception {
        // given
        FMod_InitialCompaction.usableSpace = f -> 0L;
        COMPACTION_CONFIG.put(FMod_InitialCompaction.COMPACTION_DISK_SPACE_CHECK, "false");
        DatasetGraphSwitchable dsg = createTdb2DatasetWithData();
        server = SmartCacheGraph.smartCacheGraphBuilder().port(0).add("test", dsg).build().start();
        // Startup compaction has just compacted the store; write more so a requested compaction has work to do
        addMoreData(dsg);

        // when
        String body = compactAndGetBody();

        // then
        assertTrue(Strings.CI.contains(body, "\"COMPACTED\""), body);
    }

    private static final long MB = 1024L * 1024, GB = 1024 * MB;

    @Test
    public void givenNoUsablePreviousCompaction_whenEstimating_thenEstimatedFromNodeData() {
        long expected = FMod_InitialCompaction.EMPTY_TDB2_SIZE + 20 * 900 * MB;
        // No previous compaction
        assertEquals(expected, FMod_InitialCompaction.estimateCompactedSize(72 * GB, -1, -1, 900 * MB));
        // The last compaction was at startup, of an (almost) empty store: as in the scale test, this must not be scaled
        // up from 192 MB, which would estimate far less than the copy needs
        assertEquals(expected, FMod_InitialCompaction.estimateCompactedSize(72 * GB, 192 * MB, 2 * MB, 900 * MB));
        // Node data size of the last compaction unknown
        assertEquals(expected, FMod_InitialCompaction.estimateCompactedSize(72 * GB, 2 * GB, -1, 900 * MB));
        // Never more than the current size
        assertEquals(10 * GB, FMod_InitialCompaction.estimateCompactedSize(10 * GB, -1, -1, 900 * MB));
    }

    @Test
    public void givenNodeDataSizeUnknown_whenEstimating_thenCurrentSizeIsUsed() {
        assertEquals(72 * GB, FMod_InitialCompaction.estimateCompactedSize(72 * GB, -1, -1, -1));
        assertEquals(72 * GB, FMod_InitialCompaction.estimateCompactedSize(72 * GB, 2 * GB, 500 * MB, -1));
    }

    @Test
    public void givenConfiguredNodeDataRatio_whenEstimatingWithoutPreviousCompaction_thenRatioUsed() {
        COMPACTION_CONFIG.put(FMod_InitialCompaction.COMPACTION_NODE_DATA_RATIO, "12.5");
        assertEquals(FMod_InitialCompaction.EMPTY_TDB2_SIZE + (long) (12.5 * 400 * MB),
                     FMod_InitialCompaction.estimateCompactedSize(72 * GB, -1, -1, 400 * MB));
        // Nonsensical ratios fall back to the default
        COMPACTION_CONFIG.put(FMod_InitialCompaction.COMPACTION_NODE_DATA_RATIO, "0");
        assertEquals(FMod_InitialCompaction.EMPTY_TDB2_SIZE + 20 * 400 * MB,
                     FMod_InitialCompaction.estimateCompactedSize(72 * GB, -1, -1, 400 * MB));
    }

    @Test
    public void givenBloatedStoreNeverCompactedWithData_whenCheckingDiskSpace_thenUsesNodeDataEstimate()
            throws IOException {
        // As in the page cache test: 60 GB on disk, 431 MB of node data (about 4 GB live), last compacted when empty,
        // 38 GB free. Falling back to the current size would need 66 GB and refuse to compact.
        DatasetGraphSwitchable dsg = createPersistentSwitchableDataset();
        FMod_InitialCompaction.usableSpace = f -> 38 * GB;
        assertTrue(FMod_InitialCompaction.hasEnoughDiskSpace(dsg, "/t", 60 * GB, 201_326_914L, 0, 431 * MB));
        // ... but still refuses if there isn't room for the node data estimate
        FMod_InitialCompaction.usableSpace = f -> 5 * GB;
        assertFalse(FMod_InitialCompaction.hasEnoughDiskSpace(dsg, "/t", 60 * GB, 201_326_914L, 0, 431 * MB));
    }

    @Test
    public void givenPreviousCompactionWithData_whenEstimating_thenScaledByNodeDataGrowthAndCappedAtCurrentSize() {
        // Compacted to 2 GB with 500 MB of node data; node data has since doubled
        assertEquals(4 * GB, FMod_InitialCompaction.estimateCompactedSize(97 * GB, 2 * GB, 500 * MB, 1000 * MB));
        // Node data unchanged (e.g. only repeats, relabels or deletes since): same size again
        assertEquals(2 * GB, FMod_InitialCompaction.estimateCompactedSize(97 * GB, 2 * GB, 500 * MB, 500 * MB));
        // Never more than the current size
        assertEquals(3 * GB, FMod_InitialCompaction.estimateCompactedSize(3 * GB, 2 * GB, 500 * MB, 1000 * MB));
    }

    @Test
    public void givenEstimate_whenCheckingDiskSpace_thenNeedsEstimatePlusTenPercent() throws IOException {
        DatasetGraphSwitchable dsg = createPersistentSwitchableDataset();
        // No previous compaction: needs the current size + 10%
        FMod_InitialCompaction.usableSpace = f -> 1_099L;
        assertFalse(FMod_InitialCompaction.hasEnoughDiskSpace(dsg, "/t", 1_000L, -1, -1, -1));
        FMod_InitialCompaction.usableSpace = f -> 1_100L;
        assertTrue(FMod_InitialCompaction.hasEnoughDiskSpace(dsg, "/t", 1_000L, -1, -1, -1));
        // Scaled: compacted to 2 GB with 500 MB node data, now 1000 MB node data -> 4 GB + 10%
        FMod_InitialCompaction.usableSpace = f -> 4 * GB + 4 * GB / 10 - 1;
        assertFalse(FMod_InitialCompaction.hasEnoughDiskSpace(dsg, "/t", 97 * GB, 2 * GB, 500 * MB, 1000 * MB));
        FMod_InitialCompaction.usableSpace = f -> 4 * GB + 4 * GB / 10;
        assertTrue(FMod_InitialCompaction.hasEnoughDiskSpace(dsg, "/t", 97 * GB, 2 * GB, 500 * MB, 1000 * MB));
        // Unknown current size: nothing to check
        FMod_InitialCompaction.usableSpace = f -> 0L;
        assertTrue(FMod_InitialCompaction.hasEnoughDiskSpace(dsg, "/t", -1L, 2 * GB, 500 * MB, 1000 * MB));
    }

    @Test
    public void givenCompaction_whenFinished_thenRecordsCompactedNodeDataSize() throws Exception {
        // given
        DatasetGraphSwitchable dsg = createTdb2DatasetWithData();
        server = SmartCacheGraph.smartCacheGraphBuilder().port(0).add("test", dsg).build().start();
        addMoreData(dsg);

        // when
        String body = compactAndGetBody();

        // then
        assertTrue(Strings.CI.contains(body, "\"COMPACTED\""), body);
        long nodeData = FMod_InitialCompaction.findNodeDataSize(dsg);
        assertTrue(nodeData > 0, "node data file should be found in the current database");
        assertEquals(nodeData, FMod_InitialCompaction.findPreviousCompactionNodeDataSize(dsg));
    }

    @Test
    public void givenNoNodeDataFile_whenFindingNodeDataSize_thenUnknown() throws IOException {
        DatasetGraphSwitchable dsg = createPersistentSwitchableDataset();
        // Has a Data-0001 directory but no node data file, and no previous compaction
        assertEquals(-1, FMod_InitialCompaction.findNodeDataSize(dsg));
        assertEquals(-1, FMod_InitialCompaction.findPreviousCompactionNodeDataSize(dsg));
    }

    @Test
    public void givenCompactionRunning_whenProgressIntervalElapses_thenProgressIsLoggedAndStallsWarned()
            throws Exception {
        // given
        COMPACTION_CONFIG.put(FMod_InitialCompaction.COMPACTION_PROGRESS_LOG_INTERVAL, "PT0.1S");
        DatasetGraphSwitchable dsg = createTdb2DatasetWithData();
        Files.createDirectories(dsg.getContainerPath().resolve("Data-0002-tmp"));
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(FMod_InitialCompaction.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        ch.qos.logback.classic.Level originalLevel = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.INFO);

        // when
        java.util.concurrent.ScheduledFuture<?> progress =
                FMod_InitialCompaction.startProgressLogging(dsg, "/t", java.time.Instant.now());
        try {
            waitUntil(() -> appender.list.stream().anyMatch(e -> e.getLevel() == ch.qos.logback.classic.Level.WARN));
        } finally {
            progress.cancel(false);
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
        }

        // then
        assertTrue(appender.list.stream().anyMatch(e -> e.getFormattedMessage().contains("Still compacting /t")));
        assertTrue(appender.list.stream()
                                .anyMatch(e -> e.getLevel() == ch.qos.logback.classic.Level.WARN
                                        && e.getFormattedMessage().contains("has not grown")));
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                fail("Condition not met within 10s");
            }
            Thread.sleep(50);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void await(java.util.concurrent.CountDownLatch latch) {
        try {
            assertTrue(latch.await(30, java.util.concurrent.TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static DatasetGraphSwitchable createPersistentSwitchableDataset() throws IOException {
        Path container = Files.createTempDirectory("compaction-test-dsg");
        Path dataDir = container.resolve("Data-0001");
        Files.createDirectories(dataDir);
        Files.writeString(dataDir.resolve("marker.txt"), "x", StandardCharsets.UTF_8);
        return new DatasetGraphSwitchable(container, null, DatasetGraphFactory.createTxnMem());
    }
}
