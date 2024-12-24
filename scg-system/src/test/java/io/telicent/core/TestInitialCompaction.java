package io.telicent.core;

import io.telicent.LibTestsSCG;
import io.telicent.jena.abac.ABAC;
import io.telicent.jena.abac.SysABAC;
import io.telicent.jena.abac.core.Attributes;
import io.telicent.jena.abac.core.AttributesStoreLocal;
import io.telicent.jena.abac.core.DatasetGraphABAC;
import io.telicent.jena.abac.labels.Labels;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.servlets.ActionErrorException;
import org.apache.jena.fuseki.system.FusekiLogging;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.tdb1.TDB1Factory;
import org.apache.jena.tdb1.base.file.Location;
import org.apache.jena.tdb2.DatabaseMgr;
import org.apache.jena.tdb2.store.DatasetGraphSwitchable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Set;

import static io.telicent.TestJwtServletAuth.makePOSTCallWithPath;
import static io.telicent.TestSmartCacheGraphIntegration.launchServer;
import static io.telicent.core.FMod_InitialCompaction.compactLabels;
import static org.apache.jena.graph.Graph.emptyGraph;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class TestInitialCompaction {
    private static final MockedStatic<DatabaseMgr> mockDatabaseMgr  = mockStatic(DatabaseMgr.class, Mockito.CALLS_REAL_METHODS);
    private static FusekiServer server;

    @BeforeAll
    static void createAndSetupServerDetails() throws Exception {
        LibTestsSCG.setupAuthentication();
        FusekiLogging.setLogging();
        Attributes.buildStore(emptyGraph);
    }

    @AfterEach
    void clearDown() {
       mockDatabaseMgr.clearInvocations();
       mockDatabaseMgr.reset();
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
    public void test_persistentDataset() {
        // given
        mockDatabaseMgr.when(() -> DatabaseMgr.compact(any(), anyBoolean())).thenAnswer(invocationOnMock -> null);
        mockDatabaseMgr.clearInvocations();
        String configFile = "config-persistent.ttl";
        // when
        server = launchServer(configFile);
        // then
        assertNotNull(server.serverURL());
        mockDatabaseMgr.verify(() -> DatabaseMgr.compact(any(), anyBoolean()), times(1));
    }

    @Test
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
        FMod_InitialCompaction.sizes.put("/knowledge", 500L);
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
                SysABAC.allowLabel,
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
        DatasetGraphSwitchable dsgPersists = new DatasetGraphSwitchable(Path.of("./"), null, DatasetGraphFactory.createTxnMem());
        DatasetGraphABAC dsgABAC = ABAC.authzDataset(dsgPersists,
                SysABAC.allowLabel,
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
    public void test_humanReadableSize_handlesInvalidInput() {
        // given
        String expectedSize = "Unknown";
        // when
        String actualSize = FMod_InitialCompaction.humanReadableSize(-5000);
        // then
        assertEquals(expectedSize, actualSize);
    }

    @Test
    public void test_compactWithExistingLock()  {
        // given
        DatasetGraphSwitchable dsgPersists = new DatasetGraphSwitchable(Path.of("./"), null, DatasetGraphFactory.createTxnMem());
        DatasetGraphSwitchable mockedDsg = Mockito.spy(dsgPersists);

        when(mockedDsg.tryExclusiveMode(false)).thenReturn(false);
        doNothing().when(mockedDsg).finishExclusiveMode();
        server = SmartCacheGraph.smartCacheGraphBuilder().add("test", mockedDsg).build().start();

        // when
        HttpResponse<InputStream> compactResponse = makePOSTCallWithPath(server, "$/compactall");
        // then
        assertEquals(200, compactResponse.statusCode());
        mockDatabaseMgr.verify(() -> DatabaseMgr.compact(any(), anyBoolean()), times(0));
    }

    @Test
    public void test_compactAll_happyPath() {
        // given
        mockDatabaseMgr.when(() -> DatabaseMgr.compact(any(), anyBoolean())).thenAnswer(invocationOnMock -> null);
        String configFile = "config-persistent.ttl";
        server = launchServer(configFile);
        // when
        HttpResponse<InputStream> compactResponse = makePOSTCallWithPath(server, "$/compactall");
        // then
        assertEquals(200, compactResponse.statusCode());
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
                SysABAC.allowLabel,
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
        assertThrows(ActionErrorException.class, () ->capturedServlet.service(request, response));
    }
}
