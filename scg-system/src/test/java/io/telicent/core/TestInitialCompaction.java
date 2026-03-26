package io.telicent.core;

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
import static io.telicent.core.FMod_InitialCompaction.compactLabels;
import static org.apache.jena.graph.Graph.emptyGraph;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class TestInitialCompaction {
    private static final MockedStatic<DatabaseMgr> mockDatabaseMgr =
            mockStatic(DatabaseMgr.class, Mockito.CALLS_REAL_METHODS);
    private static FusekiServer server;

    @BeforeAll
    static void createAndSetupServerDetails() throws Exception {
        LibTestsSCG.setupAuthentication();
        FusekiLogging.setLogging();
        Attributes.buildStore(emptyGraph);

        removePreviousCompactionResults();
    }

    @AfterEach
    void clearDown() {
        mockDatabaseMgr.clearInvocations();
        mockDatabaseMgr.reset();
        FMod_InitialCompaction.SIZES.clear();
        removePreviousCompactionResults();
        if (server != null) {
            server.stop();
        }
    }

    private static void removePreviousCompactionResults() {
        File previousCompactionSize = Path.of("target", "databases", "knowledge", ".last-compaction").toFile();
        if (previousCompactionSize.exists()) {
            previousCompactionSize.delete();
        }
        File previousCompactionStatus = Path.of("target", "databases", "knowledge", ".compaction-status").toFile();
        if (previousCompactionStatus.exists()) {
            previousCompactionStatus.delete();
        }
    }

    @AfterAll
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
    @Disabled // Flaky test
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
        HttpResponse<InputStream> compactResponse = makePOSTCallWithPath(server, "$/compactall");
        // then
        assertEquals(200, compactResponse.statusCode());
        mockDatabaseMgr.verify(() -> DatabaseMgr.compact(any(), anyBoolean()), times(0));
    }

    @Test
    @Disabled
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
        HttpResponse<InputStream> compactResponse = makePOSTCallWithPath(server, "$/compactall");
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
    public void givenMalformedCompactionIndicatorFile_whenLoadingPreviousIndicator_thenEmptyReturned() throws IOException {
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
        // given
        DatasetGraphSwitchable dsgPersists = createPersistentSwitchableDataset();
        DatasetGraphSwitchable mockedDsg = Mockito.spy(dsgPersists);

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
        // given
        DatasetGraphSwitchable dsgPersists = createPersistentSwitchableDataset();
        DatasetGraphSwitchable mockedDsg = Mockito.spy(dsgPersists);

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
        // given
        DatasetGraphSwitchable dsgPersists = createPersistentSwitchableDataset();
        DatasetGraphSwitchable mockedDsg = Mockito.spy(dsgPersists);
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
    public void test_compactAll_mixedOutcomesReturnsSummary() throws IOException {
        // Given
        DatasetGraphSwitchable dsgPersists = createPersistentSwitchableDataset();
        DatasetGraphSwitchable mockedDsg = Mockito.spy(dsgPersists);
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
        // Given
        DatasetGraphSwitchable dsgPersists = createPersistentSwitchableDataset();
        DatasetGraphSwitchable mockedDsg = Mockito.spy(dsgPersists);
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
    public void givenWrongRoles_whenCompactAll_thenUnauthorized() throws IOException {
        // Given
        mockDatabaseMgr.when(() -> DatabaseMgr.compact(any(), anyBoolean())).thenAnswer(invocationOnMock -> null);
        String configFile = "config-persistent.ttl";
        server = launchServer(configFile);

        // When
        HttpResponse<InputStream> compactResponse =
                makeAuthCallWithCustomToken(server, "$/compactall", TestBackupData.tokenWithUserRoleOnly(), "POST");

        // Then
        assertEquals(401, compactResponse.statusCode());
        String error = IOUtils.toString(compactResponse.body(), StandardCharsets.UTF_8);
        assertTrue(Strings.CI.contains(error, "requires roles"));
    }

    @Test
    public void givenInsufficientPermissions_whenCompactAll_thenUnauthorized() throws IOException {
        // Given
        mockDatabaseMgr.when(() -> DatabaseMgr.compact(any(), anyBoolean())).thenAnswer(invocationOnMock -> null);
        String configFile = "config-persistent.ttl";
        server = launchServer(configFile);

        // When
        HttpResponse<InputStream> compactResponse =
                makeAuthCallWithCustomToken(server, "$/compactall", TestBackupData.tokenWithBackupReadPermission(),
                                            "POST");

        // Then
        assertEquals(401, compactResponse.statusCode());
        String error = IOUtils.toString(compactResponse.body(), StandardCharsets.UTF_8);
        assertTrue(Strings.CI.contains(error, "requires permissions"));
    }

    @Test
    @Disabled // NB - This test is flaky, disabling it for the time being
    public void givenServer_whenPreviouslyCompacted_thenAskingToCompactAgainIsANoOp() {
        // Given
        mockDatabaseMgr.when(() -> DatabaseMgr.compact(any(), anyBoolean())).thenAnswer(invocationOnMock -> null);
        String configFile = "config-persistent.ttl";
        server = launchServer(configFile);

        // When
        HttpResponse<InputStream> compactResponse = makePOSTCallWithPath(server, "$/compactall");
        assertEquals(200, compactResponse.statusCode());
        mockDatabaseMgr.verify(() -> DatabaseMgr.compact(any(), anyBoolean()), times(1));

        // Then
        assertEquals(200, compactResponse.statusCode());
        compactResponse = makePOSTCallWithPath(server, "$/compactall");
        mockDatabaseMgr.verify(() -> DatabaseMgr.compact(any(), anyBoolean()), times(1));
    }

    @Test
    public void test_compactLabels_notABAC() {
        // given
        DatasetGraph dsgNotABAC = TDB1Factory.createDatasetGraph(Location.mem());
        // when
        // then
        compactLabels(dsgNotABAC);
    }

    @Test
    public void test_compactLabels_notRocksDB() {
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

        ArgumentCaptor<HttpServlet> servletCaptor = ArgumentCaptor.forClass(HttpServlet.class);
        when(mockBuilder.addServlet(any(), servletCaptor.capture())).thenReturn(mockBuilder);

        fModInitialCompaction.configured(mockBuilder, null, null);

        verify(mockBuilder).addServlet(eq("/$/compactall"), any(HttpServlet.class));
        HttpServlet capturedServlet = servletCaptor.getValue();


        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");

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

    private static DatasetGraphSwitchable createPersistentSwitchableDataset() throws IOException {
        Path container = Files.createTempDirectory("compaction-test-dsg");
        Path dataDir = container.resolve("Data-0001");
        Files.createDirectories(dataDir);
        Files.writeString(dataDir.resolve("marker.txt"), "x", StandardCharsets.UTF_8);
        return new DatasetGraphSwitchable(container, null, DatasetGraphFactory.createTxnMem());
    }
}
