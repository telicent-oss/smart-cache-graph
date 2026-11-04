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
package io.telicent.backup.services;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.telicent.backup.utils.BackupUtils;
import io.telicent.jena.abac.ABAC;
import io.telicent.jena.abac.SysABAC;
import io.telicent.jena.abac.core.DatasetGraphABAC;
import io.telicent.jena.abac.labels.Label;
import io.telicent.jena.abac.labels.StoreFmtByHash;
import io.telicent.jena.abac.labels.hashing.HasherUtil;
import io.telicent.jena.abac.labels.store.rocksdb.modern.DictionaryLabelStoreRocksDB;
import io.telicent.smart.cache.security.data.plugins.rdf.abac.RdfAbacPlugin;
import io.telicent.smart.cache.storage.BackupConfig;
import org.apache.jena.fuseki.server.DataAccessPoint;
import org.apache.jena.fuseki.server.DataAccessPointRegistry;
import org.apache.jena.fuseki.server.DataService;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.system.Txn;

import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

import static io.telicent.backup.utils.JsonFileUtils.OBJECT_MAPPER;

/**
 * Runs a complete {@link DatasetBackupService#restoreDataset(String, String, ObjectNode)} against a dataset whose
 * security labels live in a <strong>real</strong> RocksDB backed labels store.
 * <p>
 * This has a {@code main()} because the defect it exercises is a native use-after-free that aborts the whole JVM
 * rather than throwing: {@code DatasetBackupService} restores the labels store from inside a Jena write transaction on
 * the ABAC dataset, {@code DatasetGraphABAC} propagates that transaction into the labels store, and
 * {@code AbstractRocksDBStorage.restore()} then closes and reopens the RocksDB database underneath the still-open
 * transaction. The next use of that transaction dereferences freed native memory and the process dies with SIGABRT /
 * SIGSEGV inside {@code rocksdb::PessimisticTransactionDB::UnLock}. A crash like that takes the surefire fork with it,
 * so {@link TestRestoreWithRocksDbLabelStore} runs this in a child JVM and asserts the child exits cleanly.
 * </p>
 *
 * @see TestRestoreWithRocksDbLabelStore
 */
public final class RestoreWithRocksDbLabelStoreHarness {

    /**
     * Name of the dataset restored by this harness
     */
    public static final String DATASET_NAME = "rocks-labels-ds";

    private static final String DAP_NAME = "/" + DATASET_NAME;
    private static final int QUAD_COUNT = 50;

    private RestoreWithRocksDbLabelStoreHarness() {
    }

    private static Quad quad(int i) {
        return Quad.create(Quad.defaultGraphIRI,
                           NodeFactory.createURI("http://example.org/s" + i),
                           NodeFactory.createURI("http://example.org/p" + i),
                           NodeFactory.createURI("http://example.org/o" + i));
    }

    /**
     * Runs the restore. Exits non-zero, or dies on a signal, if the restore cannot complete.
     *
     * @param args Optionally a working directory to use, otherwise a temporary one is created
     * @throws Exception If the harness itself cannot be set up
     */
    public static void main(String[] args) throws Exception {
        final Path workDir = args.length > 0 ? Path.of(args[0]) : Files.createTempDirectory("scg-rocks-labels-restore");
        Files.createDirectories(workDir);

        final ObjectNode result = run(workDir);

        System.out.println("Restore completed without crashing: " + result.toPrettyString());
    }

    /**
     * Builds a dataset with a real RocksDB labels store, takes a backup of it, then restores that backup through the
     * production {@link DatasetBackupService} code path
     *
     * @param workDir Working directory for the labels database and the backups
     * @return The restore result
     * @throws Exception If the harness cannot be set up
     */
    public static ObjectNode run(Path workDir) throws Exception {
        final Path backupsDir = workDir.resolve("backups");
        Files.createDirectories(backupsDir);
        BackupUtils.dirBackups = backupsDir.toString();

        final File dbDir = workDir.resolve("labels.db").toFile();
        final var store = new DictionaryLabelStoreRocksDB(dbDir, new StoreFmtByHash(HasherUtil.createXX128Hasher()));
        final DatasetGraphABAC dsg =
                ABAC.authzDataset(DatasetGraphFactory.createTxnMem(), null, store, SysABAC.allowLabel, null);

        // Seed both the data and the labels so that the restore has real content to put back, and so that the labels
        // store holds enough state for the post-restore writes to touch freed memory
        Txn.executeWrite(dsg, () -> {
            for (int i = 0; i < QUAD_COUNT; i++) {
                dsg.add(quad(i));
                store.add(quad(i), Label.fromText("seed" + i));
            }
        });

        writeBackup(store, backupsDir.resolve("1").resolve(DATASET_NAME));

        final var registry = new DataAccessPointRegistry();
        registry.register(new DataAccessPoint(DAP_NAME, DataService.newBuilder().dataset(dsg).build()));

        final var service = new DatasetBackupService(registry, new RdfAbacPlugin());
        final ObjectNode result = OBJECT_MAPPER.createObjectNode();
        service.restoreDataset(backupsDir.resolve("1").toString(), DATASET_NAME, result);
        return result;
    }

    /**
     * Lays out a backup on disk in the shape {@code restoreDataset} expects, i.e. a {@code labels/} directory holding a
     * genuine RocksDB backup and a {@code tdb/} directory holding the gzipped N-Quads
     */
    private static void writeBackup(DictionaryLabelStoreRocksDB store, Path datasetDir) throws Exception {
        final Path labelsDir = datasetDir.resolve("labels");
        final Path tdbDir = datasetDir.resolve("tdb");
        Files.createDirectories(labelsDir);
        Files.createDirectories(tdbDir);

        store.backup(BackupConfig.builder().backupLocation(labelsDir.toString()).build());

        final StringBuilder quads = new StringBuilder();
        for (int i = 0; i < QUAD_COUNT; i++) {
            quads.append("<http://example.org/s").append(i).append("> ")
                 .append("<http://example.org/p").append(i).append("> ")
                 .append("<http://example.org/o").append(i).append("> .\n");
        }
        try (OutputStream os = Files.newOutputStream(tdbDir.resolve(DATASET_NAME + "_backup.nq.gz"));
             GZIPOutputStream gz = new GZIPOutputStream(os)) {
            gz.write(quads.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

}
