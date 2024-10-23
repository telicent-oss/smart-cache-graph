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

package io.telicent;

import io.telicent.jena.abac.fuseki.SysFusekiABAC;
import io.telicent.smart.cache.configuration.Configurator;
import org.apache.jena.atlas.logging.FmtLog;
import org.apache.jena.cmd.CmdException;
import org.apache.jena.fuseki.Fuseki;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.system.FusekiLogging;
import org.apache.jena.http.HttpOp;
import org.apache.jena.riot.SysRIOT;
import org.apache.jena.sparql.ARQException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static io.telicent.core.SmartCacheGraph.construct;
import static org.junit.jupiter.api.Assertions.*;

/*
* Test SCG usage of varying configuration parameters.
*/
class TestMainSmartCacheGraph {

    private static final String DIR = "src/test/files";
    private FusekiServer server;

    @BeforeEach
    void setUp() throws Exception {
        FusekiLogging.setLogging();
        SysFusekiABAC.init();
        LibTestsSCG.teardownAuthentication();
        LibTestsSCG.disableInitialCompaction();
    }

    @AfterEach
    void clearDown() {
        if (null != server) {
            server.stop();
        }
        Configurator.reset();
    }

    @Test
    void fail_empty_config() {
        assertThrows(CmdException.class, () -> construct(""));
    }

    @Test
    void fail_mem_missing_service_name() {
        assertThrows(CmdException.class, () -> construct("--mem"));
    }

    @Test
    void only_service_name() {
        assertThrows(CmdException.class, () -> construct("/endpoint"));
    }

    @Test
    void too_many_data_sets() {
        assertThrows(CmdException.class, () -> {
            // given
            List<String> arguments =
                    List.of("--mem", "--file=file", "--dataset=dataset", "--tdb=file", "--memtdb=file", "--config=file");
            // when
            // then
            construct(arguments.toArray(new String[0]));
        });
    }

    @Test
    void happy_path_simple_mem_config() {
        // given
        List<String> arguments = List.of("--port=0","--mem","/dataendpoint");
        // when
        server = construct(arguments.toArray(new String[0])).start();
        // then
        assertNotNull(server);
    }

    @Test
    void happy_empty() {
        // given
        List<String> arguments = List.of("--port=0", "--empty");
        // when
        server = construct(arguments.toArray(new String[0])).start();
        // then
        assertNotNull(server);
    }

    @Test
    void happy_localhost() {
        // given
        List<String> arguments = List.of("--localhost", "--mem", "/dataendpoint");
        // when
        server = construct(arguments.toArray(new String[0])).start();
        // then
        assertNotNull(server);
    }

    @Test
    void happy_simple_config() {
        // given
        List<String> arguments = List.of("--conf",DIR + "/config-simple.ttl");
        // when
        server = construct(arguments.toArray(new String[0])).start();
        // then
        assertNotNull(server);
    }

    @Test
    void fail_wrong_format() {
        assertThrows(ARQException.class, () -> {
            // given
            List<String> arguments = List.of("--conf", DIR + "/wrong-format.ttl");
            // when
            LibTestsSCG.withLevel(SysRIOT.getLogger(), "FATAL",
                    () -> {
                        server = construct(arguments.toArray(new String[0])).start();
                        assertNotNull(server);
                    });
            // then
        });
    }

    @Test
    void fail_missing_file() {
        assertThrows(CmdException.class, () -> {
            // given
            List<String> arguments = List.of("--conf", DIR + "/not.exists");
            // when
            server = construct(arguments.toArray(new String[0])).start();
            // then
            assertNotNull(server);
        });
    }


    @Test
    void endpoint_ping() {
        // given
        List<String> arguments = List.of("--port=0", "--empty");
        server = construct(arguments.toArray(new String[0])).start();
        // when
        String result = HttpOp.httpGetString("http://localhost:" + server.getHttpPort() + "/$/ping");
        // then
        assertNotNull(result);
    }

    @Test
    void endpoint_metrics_enabled() {
        // given
        List<String> arguments = List.of("--port=0", "--empty", "--metrics");
        server = construct(arguments.toArray(new String[0])).start();
        // when
        String result = HttpOp.httpGetString("http://localhost:" + server.getHttpPort() + "/$/metrics");
        // then
        assertNotNull(result);
    }

    @Test
    void endpoint_metrics_disabled() {
        // given
        List<String> arguments = List.of("--port=0", "--empty");
        server = construct(arguments.toArray(new String[0])).start();
        // when
        String result = HttpOp.httpGetString("http://localhost:" + server.getHttpPort() + "/$/metrics");
        // then
        assertNull(result);
    }

    @Test
    void endpoint_stats_disabled() {
        // given
        List<String> arguments = List.of("--port=0", "--empty");
        server = construct(arguments.toArray(new String[0])).start();
        // when
        String result = HttpOp.httpGetString("http://localhost:" + server.getHttpPort() + "/$/stats");
        // then
        assertNull(result);
    }

    @Test
    void endpoint_stats_enabled() {
        // given
        List<String> arguments = List.of("--port=0", "--empty", "--stats");
        server = construct(arguments.toArray(new String[0])).start();
        // when
        String result = HttpOp.httpGetString("http://localhost:" + server.getHttpPort() + "/$/stats");
        // then
        assertNotNull(result);
        assertEquals("{ \"datasets\" : { } }", result);
    }

    @Test
    void endpoint_tasks() {
        // given
        List<String> arguments = List.of("--port=0", "--empty");
        server = construct(arguments.toArray(new String[0])).start();
        // when
        String result = HttpOp.httpGetString("http://localhost:" + server.getHttpPort() + "/$/tasks");
        // then
        assertNull(result);
    }

    @Test
    void enable_modules() {
        // given
        List<String> arguments = List.of("--port=0", "--empty", "--modules=true");
        server = construct(arguments.toArray(new String[0])).start();
        // when
        HttpOp.httpGetString("http://localhost:" + server.getHttpPort() + "/$/stats");
        // then
        assertNotNull(server);
    }

    @Test
    void disable_modules() {
        // given
        List<String> arguments = List.of("--port=0", "--empty", "--modules=false");
        server = construct(arguments.toArray(new String[0])).start();
        // when
        HttpOp.httpGetString("http://localhost:" + server.getHttpPort() + "/$/stats");
        // then
        assertNotNull(server);
    }
    // XXX Sort out with TestSmartCacheGraph

    // Two datasets.

    // Update one, see others.

    // Update one with label. see ABAC others.

    // No SERVICE

    // Timeouts?

    // Format of Turtle.

}
