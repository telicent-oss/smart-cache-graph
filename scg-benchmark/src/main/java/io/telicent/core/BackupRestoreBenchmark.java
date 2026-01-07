package io.telicent.core;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.telicent.backup.services.DatasetBackupService;
import io.telicent.backup.utils.BackupUtils;
import io.telicent.jena.abac.AttributeValueSet;
import io.telicent.jena.abac.core.AttributesStoreLocal;
import io.telicent.jena.abac.core.DatasetGraphABAC;
import io.telicent.jena.abac.labels.Label;
import io.telicent.jena.abac.labels.Labels;
import io.telicent.jena.abac.labels.LabelsStore;
import io.telicent.jena.abac.labels.LabelsStoreRocksDB;
import io.telicent.jena.abac.labels.StoreFmtByString;
import io.telicent.smart.cache.configuration.Configurator;
import io.telicent.smart.cache.configuration.sources.PropertiesSource;
import org.apache.jena.fuseki.server.DataAccessPoint;
import org.apache.jena.fuseki.server.DataAccessPointRegistry;
import org.apache.jena.fuseki.server.DataService;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.system.Txn;
import org.apache.jena.tdb2.TDB2Factory;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 1, time = 5)
@Measurement(iterations = 3, time = 10)
@Fork(1)
@State(Scope.Benchmark)
public class BackupRestoreBenchmark {

    @Param({"1000", "10000"})
    private int tripleCount;

    private DatasetGraphABAC datasetGraph;
    private DataService dataService;
    private DatasetBackupService backupService;
    private String restoreId;
    private Path backupRoot;
    private Path tdbDir;
    private Path labelsDir;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        backupRoot = Path.of("target", "benchmark-backups-" + UUID.randomUUID());
        tdbDir = Path.of("target", "benchmark-tdb-" + UUID.randomUUID());
        labelsDir = Path.of("target", "benchmark-labels-" + UUID.randomUUID());
        Files.createDirectories(backupRoot);
        Files.createDirectories(tdbDir);
        Files.createDirectories(labelsDir);

        Properties props = new Properties();
        props.put(BackupUtils.ENV_BACKUPS_DIR, backupRoot.toString());
        Configurator.addSource(new PropertiesSource(props));
        BackupUtils.dirBackups = null;

        DatasetGraph base = TDB2Factory.connectDataset(tdbDir.toString()).asDatasetGraph();
        LabelsStore labelsStore = Labels.createLabelsStoreRocksDB(
                labelsDir.toFile(),
                LabelsStoreRocksDB.LabelMode.Overwrite,
                null,
                new StoreFmtByString()
        );
        AttributesStoreLocal store = new AttributesStoreLocal();
        store.put("user", AttributeValueSet.of("A"));
        datasetGraph = new DatasetGraphABAC(base, "attributes", labelsStore, Label.fromText("DEFAULT"), store);

        Txn.executeWrite(datasetGraph, () -> {
            for (int i = 0; i < tripleCount; i++) {
                Triple triple = Triple.create(
                        NodeFactory.createURI("urn:s:" + i),
                        NodeFactory.createURI("urn:p:" + (i % 23)),
                        NodeFactory.createLiteralString("o-" + i)
                );
                datasetGraph.getDefaultGraph().add(triple);
                datasetGraph.labelsStore().add(triple.getSubject(), triple.getPredicate(), triple.getObject(), Label.fromText("A"));
            }
        });

        dataService = DataService.newBuilder(datasetGraph).build();
        dataService.goActive();

        DataAccessPointRegistry registry = new DataAccessPointRegistry();
        DataAccessPoint dap = new DataAccessPoint("bench", dataService);
        registry.register(dap);
        backupService = new DatasetBackupService(registry);

        ObjectNode response = OBJECT_MAPPER.createObjectNode();
        backupService.backupDataset(null, response);
        restoreId = response.get("backup-id").asText();
    }

    @TearDown(Level.Trial)
    public void tearDown() throws Exception {
        if (datasetGraph != null) {
            datasetGraph.close();
        }
        if (dataService != null) {
            dataService.shutdown();
        }
        deleteTree(backupRoot);
        deleteTree(tdbDir);
        deleteTree(labelsDir);
    }

    @Benchmark
    public void benchmarkBackupDataset(Blackhole bh) {
        ObjectNode response = OBJECT_MAPPER.createObjectNode();
        backupService.backupDataset(null, response);
        bh.consume(response.get("backup-id"));
    }

    @Benchmark
    public void benchmarkRestoreDataset(Blackhole bh) throws Exception {
        ObjectNode response = OBJECT_MAPPER.createObjectNode();
        backupService.restoreDatasets(restoreId, response);
        bh.consume(response.size());
    }

    private void deleteTree(Path root) throws Exception {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
