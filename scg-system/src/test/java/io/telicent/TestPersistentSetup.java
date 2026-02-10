/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.telicent;

import static io.telicent.LibTestsSCG.tokenHeader;
import static io.telicent.LibTestsSCG.tokenHeaderValue;
import static org.apache.jena.atlas.lib.Lib.concatPaths;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import io.telicent.core.MainSmartCacheGraph;
import io.telicent.core.SmartCacheGraphSink;
import io.telicent.jena.abac.AttributeValueSet;
import io.telicent.jena.abac.SysABAC;
import io.telicent.jena.abac.attributes.Attribute;
import io.telicent.jena.abac.attributes.ValueTerm;
import io.telicent.jena.abac.core.Attributes;
import io.telicent.jena.abac.core.AttributesStore;
import io.telicent.jena.abac.core.DatasetGraphABAC;
import io.telicent.smart.cache.configuration.Configurator;
import io.telicent.smart.cache.payloads.RdfPayload;
import io.telicent.smart.cache.sources.Event;
import io.telicent.smart.cache.sources.memory.SimpleEvent;
import org.apache.jena.atlas.io.IO;
import org.apache.jena.atlas.lib.FileOps;
import org.apache.jena.fuseki.kafka.FKLib;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.system.FusekiLogging;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFWriter;
import org.apache.jena.riot.web.HttpNames;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.exec.RowSet;
import org.apache.jena.sparql.exec.RowSetOps;
import org.apache.jena.sparql.exec.RowSetRewindable;
import org.apache.jena.sparql.exec.http.QueryExecHTTPBuilder;
import org.apache.jena.system.Txn;
import org.apache.kafka.common.utils.Bytes;
import org.junit.jupiter.api.*;

/**
 * Testing of the persistent setup
 */
public class TestPersistentSetup {

    private static String testArea = "target/databases/";

    @BeforeAll
    public static void beforeAll() throws Exception {
        LibTestsSCG.setupAuthentication();
        LibTestsSCG.disableInitialCompaction();
        FusekiLogging.setLogging();
        FileOps.ensureDir(testArea);
    }

    @BeforeEach
    public void beforeEach() {
        FileOps.clearAll(testArea);
    }

    @AfterAll
    public static void afterAll() {
        FileOps.clearAll(testArea);
        Configurator.reset();
    }

    @Test
    public void persistentSetup() {

        //CxtABAC.systemTrace(Track.DEBUG);
        final String DIR = "src/test/files/";
        final String CONFIG = concatPaths(DIR, "config-persistent.ttl");

        final String FILES = "src/test/files/Data";
        final String TOPIC = "knowledge";

        // Load the attributes: this copy is only used for printing information
        AttributesStore attributeStore = Attributes.readAttributesStore(concatPaths(DIR, "attribute-store.ttl"), null);

        FusekiServer server = MainSmartCacheGraph.build("--port=0", "--config", CONFIG);

        // ---- Internal setup
        // -- DatasetGraphABAC and base dataset
        final DatasetGraph dsg = server.getDataAccessPointRegistry().get("/knowledge").getDataService().getDataset();
        final DatasetGraphABAC dsgz = (DatasetGraphABAC) dsg;
        final DatasetGraph dsgBase = dsgz.getBase();

        // Add connectors in such a way we can manually inject requests.
        SmartCacheGraphSink sink = new SmartCacheGraphSink(dsgz);

        try {
            server.start();
            String queryURL = server.datasetURL("/knowledge") + "/sparql";

            // Batch ???

            // Send files to the FKProcessor
            sendFile(dsgz, sink, concatPaths(FILES, "data-test-1.ttl"),
                     Map.of(SysABAC.hSecurityLabel, "clearance=ordinary", HttpNames.hContentType,
                            Lang.TTL.getHeaderString()));
            sendFile(dsgz, sink, concatPaths(FILES, "data-test-2.ttl"),
                     Map.of(SysABAC.hSecurityLabel, "clearance=secret", HttpNames.hContentType,
                            Lang.TTL.getHeaderString()));

            if (false) {
                dump(dsgz);
            }

            // User 'user1' can see "ordinary", "secret"
            query(queryURL, 2, "user1", "SELECT * { ?s ?p ?o}", attributeStore);
            // User 'public' - "no label" not present.
            query(queryURL, 0, "public", "SELECT * { ?s ?p ?o}", attributeStore);

            // Send more files to the FKProcessor
            sendFile(dsgz, sink, concatPaths(FILES, "data-test-3.ttl"),
                     Map.of(SysABAC.hSecurityLabel, "clearance=top-secret", HttpNames.hContentType,
                            Lang.TTL.getHeaderString()));
            sendFile(dsgz, sink, concatPaths(FILES, "data-test-4.ttl"),
                     Map.of(SysABAC.hSecurityLabel, "*", HttpNames.hContentType, Lang.TTL.getHeaderString()));

            // User 'user1' can see "no label", "ordinary", "secret"
            query(queryURL, 3, "user1", "SELECT * { ?s ?p ?o}", attributeStore);
            // User 'public' can see "no label".
            query(queryURL, 1, "public", "SELECT * { ?s ?p ?o}", attributeStore);


        } catch (Exception ex) {
            ex.printStackTrace();
            Assertions.fail("Unexpected error: " + ex.getMessage());
        } finally {
            server.stop();
        }
    }

    private void sendFile(DatasetGraph dsg, SmartCacheGraphSink sink, String path, Map<String, String> headers) throws
            IOException {
        byte[] data;
        try (InputStream input = IO.openFileBuffered(path)) {
            data = IO.readWholeFile(input);
        }
        Event<Bytes, RdfPayload> event = new SimpleEvent<>(TestSmartCacheGraphSink.toHeaders(headers), null,
                                                           RdfPayload.of(FKLib.ctForFile(path), data));
        Txn.executeWrite(dsg, () ->
                sink.send(event));
    }

    private void dump(DatasetGraphABAC dsgz) {
        final DatasetGraph dsgBase = dsgz.getBase();
        dsgz.executeRead(() -> {
            System.out.println("== Data");
            RDFWriter.source(dsgBase.getDefaultGraph()).lang(Lang.TTL).output(System.out);
//          System.out.println("== Labels");
//          // Assumes LabelsStore.forEach implemented.
//          RDFWriter.source(dsgz.labelsStore().asGraph()).lang(Lang.TTL).output(System.out);
            System.out.println("== User attributes");
            dsgz.attributesStore().users().forEach(u -> {
                AttributeValueSet a = dsgz.attributesStore().attributes(u);
                System.out.printf("%-10s %s\n", u, a);
            });
            System.out.println("==");
        });
    }

    private static void query(String URL, int expectedCount, String user, String queryString,
                              AttributesStore attributeStore) {
        if (false) {
            System.out.printf("User = %s\n", user);
            printAttrs(user, attributeStore);
        }

        String bearerToken = LibTestsSCG.tokenForUser(user, URL.split("/")[3]);
        tokenHeader();
        tokenHeaderValue(bearerToken);

        // == ABAC Query
        RowSet rs1 = QueryExecHTTPBuilder.service(URL)
                                         .query(queryString)
                                         .httpHeader(tokenHeader(), tokenHeaderValue(bearerToken))
                                         .select();

        RowSetRewindable rs2 = rs1.rewindable();
        //RowSetOps.out(rs2);
        rs2.reset();

        long actualCount = RowSetOps.count(rs2);
        assertEquals(expectedCount, actualCount, "ABAC Query count");
    }

    private static void printAttrs(String user, AttributesStore attributeStore) {
        AttributeValueSet avs = attributeStore.attributes(user);
        if (avs == null) {
            System.out.printf("    no such user\n");
            return;
        }
        if (avs.isEmpty()) {
            System.out.printf("    no attributes\n");
            return;
        }

        attributeStore.attributes(user).attributeValues((attributeValue) -> {
            Attribute a = attributeValue.attribute();
            ValueTerm vt = attributeValue.value();
            System.out.printf("    %s %s", a, vt);
            if (attributeStore.hasHierarchy(a)) {
                System.out.printf(" --");
                for (ValueTerm hvt : attributeStore.getHierarchy(a).values()) {
                    System.out.printf(" %s", hvt);
                    if (hvt.equals(vt)) {
                        break;
                    }
                }
            }
            System.out.println();
        });
    }
}
