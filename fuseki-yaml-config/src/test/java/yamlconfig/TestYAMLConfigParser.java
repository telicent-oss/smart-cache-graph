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

package yamlconfig;

import org.apache.jena.sys.JenaSystem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.scanner.ScannerException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static yamlconfig.ConfigConstants.log;


public class TestYAMLConfigParser {
    YAMLConfigParser ycp = new YAMLConfigParser();
    String loggerName = log.getName();
    org.apache.logging.log4j.core.Logger logger = (org.apache.logging.log4j.core.Logger) LogManager.getLogger(loggerName);

    @BeforeAll
    public static void before() {
        JenaSystem.init();
    }

    @Test
    public void nonExistingFileTest() {
        RuntimeException ex = assertThrows(RuntimeException.class,() -> {
            ycp.runYAMLParser("nonexistingfile.yaml");
        });
        assertEquals("java.io.UncheckedIOException: java.io.FileNotFoundException: nonexistingfile.yaml (No such file or directory)", ex.getMessage());
    }

    @Test
    public void noServerFieldTest() {
        RuntimeException ex = assertThrows(RuntimeException.class,() -> {
            ycp.runYAMLParser("src/test/files/yaml/wrong/config-no-server-field.yaml");
        });
        assertEquals("java.lang.IllegalArgumentException: 'server' field is missing", ex.getMessage());
    }

    @Test
    public void noServiceNameTest() {
        RuntimeException ex = assertThrows(RuntimeException.class,() -> {
            ycp.runYAMLParser("src/test/files/yaml/wrong/config-no-service-name.yaml");
        });
        assertEquals("java.lang.IllegalArgumentException: No name defined for service", ex.getMessage());
    }

    @Test
    public void noDbTypeTest() {
        RuntimeException ex = assertThrows(RuntimeException.class,() -> {
            ycp.runYAMLParser("src/test/files/yaml/wrong/config-no-dbtype.yaml");
        });
        assertEquals("java.lang.IllegalArgumentException: No database type for mem-db", ex.getMessage());
    }

    @Test
    public void emptyServicesTest() {
        Configurator.setLevel(logger.getName(), org.apache.logging.log4j.Level.WARN);
        TestLogAppender logAppender = TestLogAppender.createAndRegister();
        logger.addAppender(logAppender);

        ConfigStruct configToCheck = new ConfigStruct();
        Server serverToCheck = new Server("Fuseki server 1", emptyMap());
        Database database1 = new Database("mem-db", ConfigConstants.TIM, "", "", "", "", "", "", "", "", "false", "", "", "", "", "", emptyMap());
        Map<String, String> settings2 = new HashMap<String, String>();
        settings2.put( "tdb2:unionDefaultGraph", "true");
        Database database2 = new Database("tdb2-db", ConfigConstants.TDB2, "", "", "", "", "", "","DB2", "", "false", "", "", "", "", "", settings2);
        configToCheck.setServer(serverToCheck);
        List<Service> servicesToCheck = new ArrayList<>();
        configToCheck.setServices(servicesToCheck);
        List<Database> databasesTocCheck = new ArrayList<>();
        databasesTocCheck.add(database1);
        databasesTocCheck.add(database2);
        configToCheck.setDatabases(databasesTocCheck);
        configToCheck.setVersion("1.0");
        Map<String, Object> map = ycp.parseYAMLConfigToMap("src/test/files/yaml/warnings/config-empty-services.yaml");
        ConfigStruct config = ycp.mapToConfigStruct(map);
        assertEquals(config.toString(), configToCheck.toString());

        List<LogEvent> logEvents = logAppender.getLogEvents();
        try {
            assertEquals(1, logEvents.size());
            assertTrue(logEvents.get(0).getMessage().getFormattedMessage().contains("No services defined"));
        }
        finally {
            logger.removeAppender(logAppender);
        }

    }

    @Test
    public void emptyEndpointTest() {
        Configurator.setLevel(logger.getName(), org.apache.logging.log4j.Level.WARN);
        TestLogAppender logAppender = TestLogAppender.createAndRegister();
        logger.addAppender(logAppender);

        ConfigStruct configToCheck = new ConfigStruct();
        Server serverToCheck = new Server("Fuseki server 1", emptyMap());
        List<Endpoint> endpoints1 = new ArrayList<>();
        Service service1 = new Service("/ds", endpoints1, "mem-db");
        Database database1 = new Database("mem-db", ConfigConstants.TIM, "", "", "", "", "", "", "", "", "false", "", "", "", "", "", emptyMap());
        List<Endpoint> endpoints2 = new ArrayList<>();
        endpoints2.add(new Endpoint("sparql", "query", emptyMap()));
        Service service2 = new Service("/db2", endpoints2, "tdb2-db");
        Map<String, String> settings2 = new HashMap<String, String>();
        settings2.put( "tdb2:unionDefaultGraph", "true");
        Database database2 = new Database("tdb2-db", ConfigConstants.TDB2, "", "", "", "", "", "", "DB2", "", "false", "", "", "", "", "", settings2);
        configToCheck.setServer(serverToCheck);
        List<Service> servicesToCheck = new ArrayList<>();
        servicesToCheck.add(service1);
        servicesToCheck.add(service2);
        configToCheck.setServices(servicesToCheck);
        List<Database> databasesTocCheck = new ArrayList<>();
        databasesTocCheck.add(database1);
        databasesTocCheck.add(database2);
        configToCheck.setDatabases(databasesTocCheck);
        configToCheck.setVersion("1.0");
        Map<String, Object> map = ycp.parseYAMLConfigToMap("src/test/files/yaml/warnings/config-empty-endpoints.yaml");
        ConfigStruct config = ycp.mapToConfigStruct(map);
        assertEquals(config.toString(), configToCheck.toString());

        List<LogEvent> logEvents = logAppender.getLogEvents();
        try {
            assertEquals(1, logEvents.size());
            assertTrue(logEvents.get(0).getMessage().getFormattedMessage().contains("No endpoints for service /ds"));
        }
        finally {
            logger.removeAppender(logAppender);
        }
    }

    @Test
    public void ABACtdb2NoLabelsTest() {
        Configurator.setLevel(logger.getName(), org.apache.logging.log4j.Level.WARN);
        TestLogAppender logAppender = TestLogAppender.createAndRegister();
        logger.addAppender(logAppender);

        ConfigStruct configToCheck = new ConfigStruct();
        Server serverToCheck = new Server("Fuseki server simple", emptyMap());
        List<Endpoint> endpoints1 = new ArrayList<>();
        endpoints1.add(new Endpoint("", "query", emptyMap()));
        endpoints1.add(new Endpoint("upload", "upload", emptyMap()));
        Service service1 = new Service("ds", endpoints1, "abac-tdb2-db");
        Database database1 = new Database("abac-tdb2-db", ConfigConstants.ABAC, "abac/attribute-store.ttl", "", "", "", "", "", "", "dataset-under", "false", "", "", "", "", "", emptyMap());
        Database database2 = new Database("dataset-under", ConfigConstants.TDB2, "", "", "", "", "", "", "target/test-abac-DB", "", "false", "", "", "", "", "", emptyMap());
        configToCheck.setServer(serverToCheck);
        List<Service> servicesToCheck = new ArrayList<>();
        servicesToCheck.add(service1);
        configToCheck.setServices(servicesToCheck);
        List<Database> databasesTocCheck = new ArrayList<>();
        databasesTocCheck.add(database1);
        databasesTocCheck.add(database2);
        configToCheck.setDatabases(databasesTocCheck);
        configToCheck.setVersion("1.0");
        ConfigStruct config = ycp.runYAMLParser("src/test/files/yaml/warnings/config-abac-tdb2-no-labelsstore.yaml");
        assertEquals(config.toString(), configToCheck.toString());

        List<LogEvent> logEvents = logAppender.getLogEvents();
        try {
            assertEquals(1, logEvents.size());
            assertTrue(logEvents.get(0).getMessage().getFormattedMessage().contains("ABAC TDB2 database \"abac-tdb2-db\" is missing a labelsStore or a labels file."));
        }
        finally {
            logger.removeAppender(logAppender);
        }
    }

    @Test
    public void badYamlTest() {
        RuntimeException ex = assertThrows(ScannerException.class,() -> {
            ycp.runYAMLParser("src/test/files/yaml/wrong/config-bad-yaml.yaml");
        });
        assertEquals("while scanning a simple key\n" +
                " in 'reader', line 2, column 1:\n" +
                "    server\n" +
                "    ^\n" +
                "could not find expected ':'\n" +
                " in 'reader', line 3, column 7:\n" +
                "      name: \"Fuseki server 1\"\n" +
                "          ^\n", ex.getMessage());
    }

    @Test
    public void wrongSettingFormatTest() {
        RuntimeException ex = assertThrows(RuntimeException.class,() -> {
            ycp.runYAMLParser("src/test/files/yaml/wrong/config-wrong-value.yaml");
        });
        assertEquals("java.lang.IllegalArgumentException: The value of 'tdb2:unionDefaultGraph' in tdb2-db settings is not a boolean", ex.getMessage());
    }

    @Test
    public void dbMismatchTest0() {
        RuntimeException ex = assertThrows(RuntimeException.class,() -> {
            ycp.runYAMLParser("src/test/files/yaml/wrong/dbmismatch/config-db-mismatch1.yaml");
        });
        assertEquals("java.lang.NullPointerException: No databases defined", ex.getMessage());
    }

    @Test
    public void dbMismatchTest1() {
        RuntimeException ex = assertThrows(RuntimeException.class,() -> {
            ycp.runYAMLParser("src/test/files/yaml/wrong/dbmismatch/config-db-mismatch2.yaml");
        });
        assertEquals("java.lang.IllegalArgumentException: Mismatch between the databases referenced in services and the existing databases.", ex.getMessage());
    }

    @Test
    public void dbMismatchTest2() {
        RuntimeException ex = assertThrows(RuntimeException.class,() -> {
            ycp.runYAMLParser("src/test/files/yaml/wrong/dbmismatch/config-db-mismatch3.yaml");
        });
        assertEquals("java.lang.IllegalArgumentException: Mismatch between the databases referenced in services and the existing databases.", ex.getMessage());
    }

    @Test
    public void dbMismatchTest3() {
        ConfigStruct configToCheck = new ConfigStruct();
        Server serverToCheck = new Server("Fuseki server 1", emptyMap());
        Map<String, String> settings1 = new HashMap<String, String>();
        settings1.put("arq:queryTimeout", "1000,10000");
        List<Endpoint> endpoints1 = new ArrayList<>();
        endpoints1.add(new Endpoint("", "query", emptyMap()));
        endpoints1.add(new Endpoint("sparql", "query", settings1));
        endpoints1.add(new Endpoint("data-update", "update", emptyMap()));
        Service service1 = new Service("/ds", endpoints1, "mem-db");
        Database database1 = new Database("mem-db", ConfigConstants.TIM, "", "", "", "", "", "", "", "", "false", "", "", "", "", "", emptyMap());
        List<Endpoint> endpoints2 = new ArrayList<>();
        endpoints2.add(new Endpoint("sparql", "query", emptyMap()));
        Service service2 = new Service("/db2", endpoints2, "mem-db2");
        Database database2 = new Database("mem-db2", ConfigConstants.TIM, "", "", "", "", "", "", "", "", "false", "", "", "", "", "", emptyMap());
        Database database3 = new Database("mem-db2", ConfigConstants.TIM, "", "", "", "", "", "", "", "", "false", "", "", "", "", "", emptyMap());
        configToCheck.setServer(serverToCheck);
        List<Service> servicesToCheck = new ArrayList<>();
        servicesToCheck.add(service1);
        servicesToCheck.add(service2);
        configToCheck.setServices(servicesToCheck);
        List<Database> databasesTocCheck = new ArrayList<>();
        databasesTocCheck.add(database1);
        databasesTocCheck.add(database2);
        databasesTocCheck.add(database3);
        configToCheck.setDatabases(databasesTocCheck);
        configToCheck.setVersion("1.0");
        Map<String, Object> map = ycp.parseYAMLConfigToMap("src/test/files/yaml/wrong/dbmismatch/config-db-mismatch4.yaml");
        ConfigStruct config = ycp.mapToConfigStruct(map);
        assertEquals(config.toString(), configToCheck.toString());
    }

    @Test
    public void dbMismatchTest4() {
        RuntimeException ex = assertThrows(RuntimeException.class,() -> {
            ycp.runYAMLParser("src/test/files/yaml/wrong/dbmismatch/config-db-mismatch5.yaml");
        });
        assertEquals("java.lang.IllegalArgumentException: Mismatch between the databases referenced in services and the existing databases.", ex.getMessage());
    }

    @Test
    public void invalidDbTypeTest() {
        RuntimeException ex = assertThrows(RuntimeException.class,() -> {
            ycp.runYAMLParser("src/test/files/yaml/wrong/config-wrong-db-type.yaml");
        });
        assertEquals("java.lang.IllegalArgumentException: Unsupported database type TIMMMMMMM for database mem-db", ex.getMessage());
    }

    @Test
    public void invalidOperationTest() {
        RuntimeException ex = assertThrows(RuntimeException.class,() -> {
            ycp.runYAMLParser("src/test/files/yaml/wrong/config-wrong-operation.yaml");
        });
        assertEquals("java.lang.IllegalArgumentException: Operation wrong does not exist in the OperationRegistry", ex.getMessage());
    }

    @Test
    public void validConfigTest0() {
        ConfigStruct configToCheck = new ConfigStruct();
        Server serverToCheck = new Server("Fuseki server 1", emptyMap());
        Map<String, String> settings1 = new HashMap<String, String>();
        settings1.put("arq:queryTimeout", "1000,10000");
        List<Endpoint> endpoints1 = new ArrayList<>();
        endpoints1.add(new Endpoint("", "query", emptyMap()));
        endpoints1.add(new Endpoint("sparql", "query", settings1));
        endpoints1.add(new Endpoint("data-update", "update", emptyMap()));
        Service service1 = new Service("/ds", endpoints1, "mem-db");
        Database database1 = new Database("mem-db", ConfigConstants.TIM, "", "", "", "", "", "", "", "", "false", "", "", "", "", "", emptyMap());
        List<Endpoint> endpoints2 = new ArrayList<>();
        endpoints2.add(new Endpoint("sparql", "query", emptyMap()));
        Service service2 = new Service("/db2", endpoints2, "tdb2-db");
        Map<String, String> settings2 = new HashMap<String, String>();
        settings2.put( "tdb2:unionDefaultGraph", "true");
        Database database2 = new Database("tdb2-db", ConfigConstants.TDB2, "", "", "", "", "", "", "DB2", "", "false", "", "", "", "", "", settings2);
        configToCheck.setServer(serverToCheck);
        List<Service> servicesToCheck = new ArrayList<>();
        servicesToCheck.add(service1);
        servicesToCheck.add(service2);
        configToCheck.setServices(servicesToCheck);
        List<Database> databasesTocCheck = new ArrayList<>();
        databasesTocCheck.add(database1);
        databasesTocCheck.add(database2);
        configToCheck.setDatabases(databasesTocCheck);
        configToCheck.setVersion("1.0");
        Map<String, Object> map = ycp.parseYAMLConfigToMap("src/test/files/yaml/correct/config-nocomment.yaml");
        ConfigStruct config = ycp.mapToConfigStruct(map);
        assertEquals(config.toString(), configToCheck.toString());
    }

    @Test
    public void ABACTIMTest() {
        ConfigStruct configToCheck = new ConfigStruct();
        Server serverToCheck = new Server("Fuseki server simple", emptyMap());
        List<Endpoint> endpoints1 = new ArrayList<>();
        endpoints1.add(new Endpoint("", "query", emptyMap()));
        endpoints1.add(new Endpoint("upload", "upload", emptyMap()));
        Service service1 = new Service("ds", endpoints1, "abac-mem-db");
        Database database1 = new Database("abac-mem-db", ConfigConstants.ABAC, "abac/attribute-store.ttl", "", "", "", "", "", "", "dataset-under", "false", "", "", "", "", "", emptyMap());
        Map<String, String> settings2 = new HashMap<String, String>();
        settings2.put( "arq:queryTimeout", "100,100");
        Database database2 = new Database("dataset-under", ConfigConstants.TIM, "", "", "", "", "", "src/main/files/abac/data-and-labels.trig", "", "", "false", "", "", "", "", "", settings2);
        configToCheck.setServer(serverToCheck);
        List<Service> servicesToCheck = new ArrayList<>();
        servicesToCheck.add(service1);
        configToCheck.setServices(servicesToCheck);
        List<Database> databasesTocCheck = new ArrayList<>();
        databasesTocCheck.add(database1);
        databasesTocCheck.add(database2);
        configToCheck.setDatabases(databasesTocCheck);
        configToCheck.setVersion("1.0");
        Map<String, Object> map = ycp.parseYAMLConfigToMap("src/test/files/yaml/correct/abac/config-abac-tim.yaml");
        ConfigStruct config = ycp.mapToConfigStruct(map);
        assertEquals(config.toString(), configToCheck.toString());
    }

    @Test
    public void ABACTDB2Test() {
        ConfigStruct configToCheck = new ConfigStruct();
        Server serverToCheck = new Server("Fuseki server simple", emptyMap());
        List<Endpoint> endpoints1 = new ArrayList<>();
        endpoints1.add(new Endpoint("", "query", emptyMap()));
        endpoints1.add(new Endpoint("upload", "upload", emptyMap()));
        Service service1 = new Service("ds", endpoints1, "abac-tdb2-db");
        Database database1 = new Database("abac-tdb2-db", ConfigConstants.ABAC, "abac/attribute-store.ttl", "", "", "target/labels", "", "", "", "dataset-under", "false", "", "", "", "", "", emptyMap());
        Database database2 = new Database("dataset-under", ConfigConstants.TDB2, "", "", "", "", "", "", "target/test-abac-DB", "", "false", "", "", "", "", "", emptyMap());
        configToCheck.setServer(serverToCheck);
        List<Service> servicesToCheck = new ArrayList<>();
        servicesToCheck.add(service1);
        configToCheck.setServices(servicesToCheck);
        List<Database> databasesTocCheck = new ArrayList<>();
        databasesTocCheck.add(database1);
        databasesTocCheck.add(database2);
        configToCheck.setDatabases(databasesTocCheck);
        configToCheck.setVersion("1.0");
        Map<String, Object> map = ycp.parseYAMLConfigToMap("src/test/files/yaml/correct/abac/config-abac-tdb2.yaml");
        ConfigStruct config = ycp.mapToConfigStruct(map);
        assertEquals(config.toString(), configToCheck.toString());
    }

    @Test
    public void ABACattributesURLTest() {
        ConfigStruct configToCheck = new ConfigStruct();
        Server serverToCheck = new Server("Fuseki server simple", emptyMap());
        List<Endpoint> endpoints1 = new ArrayList<>();
        endpoints1.add(new Endpoint("", "query", emptyMap()));
        endpoints1.add(new Endpoint("upload", "upload", emptyMap()));
        Service service1 = new Service("ds", endpoints1, "abac-db");
        Database database1 = new Database("abac-db", ConfigConstants.ABAC, "", "http://localhost:3132/users/lookup/{user}", "","", "", "", "", "dataset-under", "false", "", "", "", "", "", emptyMap());
        Database database2 = new Database("dataset-under", ConfigConstants.TIM, "", "", "","", "", "src/main/files/abac/data-and-labels.trig", "", "", "false", "", "", "", "", "", emptyMap());
        configToCheck.setServer(serverToCheck);
        List<Service> servicesToCheck = new ArrayList<>();
        servicesToCheck.add(service1);
        configToCheck.setServices(servicesToCheck);
        List<Database> databasesTocCheck = new ArrayList<>();
        databasesTocCheck.add(database1);
        databasesTocCheck.add(database2);
        configToCheck.setDatabases(databasesTocCheck);
        configToCheck.setVersion("1.0");
        Map<String, Object> map = ycp.parseYAMLConfigToMap("src/test/files/yaml/correct/abac/config-abac-remote-attributes.yaml");
        ConfigStruct config = ycp.mapToConfigStruct(map);
        assertEquals(config.toString(), configToCheck.toString());
    }

    @Test
    public void noDatasetTest() {
        RuntimeException ex = assertThrows(RuntimeException.class,() -> {
            ycp.runYAMLParser("src/test/files/yaml/wrong/abac/config-no-dataset.yaml");
        });
        assertEquals("java.lang.IllegalArgumentException: ABAC database abac-mem-db is missing dataset", ex.getMessage());
    }

    @Test
    public void noAttributesTest() {
        RuntimeException ex = assertThrows(RuntimeException.class,() -> {
            ycp.runYAMLParser("src/test/files/yaml/wrong/abac/config-no-attributes.yaml");
        });
        assertEquals("java.lang.IllegalArgumentException: ABAC database abac-mem-db is missing attribute store", ex.getMessage());
    }

    @Test
    public void conflictingAttributesTest() {
        RuntimeException ex = assertThrows(RuntimeException.class,() -> {
            ycp.runYAMLParser("src/test/files/yaml/wrong/abac/config-conflicting-attributes.yaml");
        });
        assertEquals("java.lang.IllegalArgumentException: Both an in-memory and remote attribute store were specified for ABAC database \"abac-mem-db\". Only one is permitted.", ex.getMessage());
    }

    @Test
    public void ABAClabelsTest() {
        ConfigStruct configToCheck = new ConfigStruct();
        Server serverToCheck = new Server("Fuseki server simple", emptyMap());
        List<Endpoint> endpoints1 = new ArrayList<>();
        endpoints1.add(new Endpoint("", "query", emptyMap()));
        endpoints1.add(new Endpoint("upload", "upload", emptyMap()));
        Service service1 = new Service("ds", endpoints1, "abac-db");
        Database database1 = new Database("abac-db", ConfigConstants.ABAC, "abac/attribute-store.ttl", "", "abac/labels.ttl","", "", "", "", "dataset-under", "false", "", "", "", "", "", emptyMap());
        Database database2 = new Database("dataset-under", ConfigConstants.TIM, "", "", "","", "", "src/main/files/abac/data-no-labels.trig", "", "", "false", "", "", "", "", "", emptyMap());
        configToCheck.setServer(serverToCheck);
        List<Service> servicesToCheck = new ArrayList<>();
        servicesToCheck.add(service1);
        configToCheck.setServices(servicesToCheck);
        List<Database> databasesTocCheck = new ArrayList<>();
        databasesTocCheck.add(database1);
        databasesTocCheck.add(database2);
        configToCheck.setDatabases(databasesTocCheck);
        configToCheck.setVersion("1.0");
        Map<String, Object> map = ycp.parseYAMLConfigToMap("src/test/files/yaml/correct/abac/config-abac-labels-file.yaml");
        ConfigStruct config = ycp.mapToConfigStruct(map);
        assertEquals(config.toString(), configToCheck.toString());
    }

    @Test
    public void ABAClabelsStoreTest() {
        ConfigStruct configToCheck = new ConfigStruct();
        Server serverToCheck = new Server("Fuseki server simple", emptyMap());
        List<Endpoint> endpoints1 = new ArrayList<>();
        endpoints1.add(new Endpoint("", "query", emptyMap()));
        endpoints1.add(new Endpoint("upload", "upload", emptyMap()));
        Service service1 = new Service("ds", endpoints1, "abac-db");
        Database database1 = new Database("abac-db", ConfigConstants.ABAC, "abac/attribute-store.ttl", "", "","target/labels-store", "", "", "", "dataset-under", "false", "", "", "", "", "", emptyMap());
        Database database2 = new Database("dataset-under", ConfigConstants.TDB2, "", "", "","", "", "", "target/test-abac-DB", "", "false", "", "", "", "", "", emptyMap());
        configToCheck.setServer(serverToCheck);
        List<Service> servicesToCheck = new ArrayList<>();
        servicesToCheck.add(service1);
        configToCheck.setServices(servicesToCheck);
        List<Database> databasesTocCheck = new ArrayList<>();
        databasesTocCheck.add(database1);
        databasesTocCheck.add(database2);
        configToCheck.setDatabases(databasesTocCheck);
        configToCheck.setVersion("1.0");
        Map<String, Object> map = ycp.parseYAMLConfigToMap("src/test/files/yaml/correct/abac/config-abac-labelsstore-path.yaml");
        ConfigStruct config = ycp.mapToConfigStruct(map);
        assertEquals(config.toString(), configToCheck.toString());
    }

    @Test
    public void ABACtripleDefaultLabelsTest() {
        ConfigStruct configToCheck = new ConfigStruct();
        Server serverToCheck = new Server("Fuseki server simple", emptyMap());
        List<Endpoint> endpoints1 = new ArrayList<>();
        endpoints1.add(new Endpoint("", "query", emptyMap()));
        endpoints1.add(new Endpoint("upload", "upload", emptyMap()));
        Service service1 = new Service("ds", endpoints1, "abac-db");
        Database database1 = new Database("abac-db", ConfigConstants.ABAC, "abac/attribute-store.ttl", "", "abac/labels.ttl","", "*", "", "", "dataset-under", "false", "", "", "", "", "", emptyMap());
        Database database2 = new Database("dataset-under", ConfigConstants.TIM, "", "", "","", "", "src/main/files/abac/data-and-labels.trig", "", "", "false", "", "", "", "", "", emptyMap());
        configToCheck.setServer(serverToCheck);
        List<Service> servicesToCheck = new ArrayList<>();
        servicesToCheck.add(service1);
        configToCheck.setServices(servicesToCheck);
        List<Database> databasesTocCheck = new ArrayList<>();
        databasesTocCheck.add(database1);
        databasesTocCheck.add(database2);
        configToCheck.setDatabases(databasesTocCheck);
        configToCheck.setVersion("1.0");
        Map<String, Object> map = ycp.parseYAMLConfigToMap("src/test/files/yaml/correct/abac/config-abac-tdl.yaml");
        ConfigStruct config = ycp.mapToConfigStruct(map);
        assertEquals(config.toString(), configToCheck.toString());
    }

    @Test
    public void conflictingLabelsTest() {
        RuntimeException ex = assertThrows(RuntimeException.class,() -> {
            ycp.runYAMLParser("src/test/files/yaml/wrong/abac/config-conflicting-labels.yaml");
        });
        assertEquals("java.lang.IllegalArgumentException: Both a labels file and a labels store were specified for ABAC database \"abac-tdb2-db\". Only one is permitted.", ex.getMessage());
    }

    @Test
    public void ABACcacheTest() {
        ConfigStruct configToCheck = new ConfigStruct();
        Server serverToCheck = new Server("Fuseki server simple", emptyMap());
        List<Endpoint> endpoints1 = new ArrayList<>();
        endpoints1.add(new Endpoint("", "query", emptyMap()));
        endpoints1.add(new Endpoint("upload", "upload", emptyMap()));
        Service service1 = new Service("ds", endpoints1, "abac-db");
        Database database1 = new Database("abac-db", ConfigConstants.ABAC, "", "http://localhost:3134/users/lookup/{user}", "","", "", "", "", "dataset-under", "true", "1", "PT1S", "", "", "", emptyMap());
        Database database2 = new Database("dataset-under", ConfigConstants.TIM, "", "", "","", "", "src/main/files/abac/data-and-labels.trig", "", "", "false", "", "", "", "", "", emptyMap());
        configToCheck.setServer(serverToCheck);
        List<Service> servicesToCheck = new ArrayList<>();
        servicesToCheck.add(service1);
        configToCheck.setServices(servicesToCheck);
        List<Database> databasesTocCheck = new ArrayList<>();
        databasesTocCheck.add(database1);
        databasesTocCheck.add(database2);
        configToCheck.setDatabases(databasesTocCheck);
        configToCheck.setVersion("1.0");
        Map<String, Object> map = ycp.parseYAMLConfigToMap("src/test/files/yaml/correct/abac/config-abac-cache.yaml");
        ConfigStruct config = ycp.mapToConfigStruct(map);
        assertEquals(config.toString(), configToCheck.toString());
    }

    @Test
    public void cacheNotBooleanTest() {
        RuntimeException ex = assertThrows(RuntimeException.class,() -> {
            ycp.runYAMLParser("src/test/files/yaml/wrong/abac/config-abac-cache-not-bool.yaml");
        });
        assertEquals("java.lang.IllegalArgumentException: The value of \"cache\" on the database \"abac-mem-db\" is \"hmm\", which is not a boolean.", ex.getMessage());
    }

    @Test
    public void invalidAttributeCacheExpiryTimeTest() {
        RuntimeException ex = assertThrows(RuntimeException.class,() -> {
            ycp.runYAMLParser("src/test/files/yaml/wrong/abac/config-abac-cacheexpirytime-invalid.yaml");
        });
        assertEquals("java.lang.IllegalArgumentException: The value of \"attributeCacheExpiryTime\" on the database \"abac-mem-db\" is \"123bbb.dd\", which is not a valid time.", ex.getMessage());
    }

    @Test
    public void invalidHierarchyCacheExpiryTimeTest() {
        RuntimeException ex = assertThrows(RuntimeException.class,() -> {
            ycp.runYAMLParser("src/test/files/yaml/wrong/abac/config-abac-hierarchy-cacheexpirytime-invalid.yaml");
        });
        assertEquals("java.lang.IllegalArgumentException: The value of \"hierarchyCacheExpiryTime\" is \"123bbb.dd\" which is not a valid time.", ex.getMessage());
    }

    @Test
    public void noUnderlyingDbTest() {
        RuntimeException ex = assertThrows(RuntimeException.class,() -> {
            ycp.runYAMLParser("src/test/files/yaml/wrong/abac/config-no-underlying-db.yaml");
        });
        assertEquals("java.lang.IllegalArgumentException: Mismatch between the databases referenced in services and the existing databases.", ex.getMessage());
    }

    @Test
    public void underlyingDbDuplicatedTest() {
        RuntimeException ex = assertThrows(RuntimeException.class,() -> {
            ycp.runYAMLParser("src/test/files/yaml/wrong/abac/config-abac-underlying-db-duplicated.yaml");
        });
        assertEquals("java.lang.IllegalArgumentException: Mismatch between the databases referenced in services and the existing databases.", ex.getMessage());
    }

    @Test
    public void sameUnderlyingDbTest() {
        RuntimeException ex = assertThrows(RuntimeException.class,() -> {
            ycp.runYAMLParser("src/test/files/yaml/wrong/abac/config-abac-same-underlying-db.yaml");
        });
        assertEquals("java.lang.IllegalArgumentException: Mismatch between the databases referenced in services and the existing databases.", ex.getMessage());
    }

    @Test
    public void multipleABACDbsTest() {
        ConfigStruct configToCheck = new ConfigStruct();
        Server serverToCheck = new Server("Fuseki server simple", emptyMap());
        List<Endpoint> endpoints1 = new ArrayList<>();
        endpoints1.add(new Endpoint("", "query", emptyMap()));
        endpoints1.add(new Endpoint("upload", "upload", emptyMap()));
        Service service1 = new Service("ds", endpoints1, "abac-db");
        Service service2 = new Service("ds-2", endpoints1, "abac-db-2");
        Database database1 = new Database("abac-db", ConfigConstants.ABAC, "abac/attribute-store.ttl", "", "","", "", "", "", "dataset-under", "false", "", "", "", "", "", emptyMap());
        Database database2 = new Database("dataset-under", ConfigConstants.TIM, "", "", "","", "", "src/main/files/abac/data-and-labels.trig", "", "", "false", "", "", "", "", "", emptyMap());
        Database database3 = new Database("abac-db-2", ConfigConstants.ABAC, "abac/attribute-store.ttl", "", "","", "", "", "", "dataset-under-2", "false", "", "", "", "", "", emptyMap());
        Database database4 = new Database("dataset-under-2", ConfigConstants.TIM, "", "", "","", "", "src/main/files/abac/data-and-labels.trig", "", "", "false", "", "", "", "", "", emptyMap());
        configToCheck.setServer(serverToCheck);
        List<Service> servicesToCheck = new ArrayList<>();
        servicesToCheck.add(service1);
        servicesToCheck.add(service2);
        configToCheck.setServices(servicesToCheck);
        List<Database> databasesTocCheck = new ArrayList<>();
        databasesTocCheck.add(database1);
        databasesTocCheck.add(database2);
        databasesTocCheck.add(database3);
        databasesTocCheck.add(database4);
        configToCheck.setDatabases(databasesTocCheck);
        configToCheck.setVersion("1.0");
        Map<String, Object> map = ycp.parseYAMLConfigToMap("src/test/files/yaml/correct/abac/config-abac-multiple.yaml");
        ConfigStruct config = ycp.mapToConfigStruct(map);
        assertEquals(config.toString(), configToCheck.toString());
    }

    @Test
    public void ABACcachedHierarchiesTest() {
        ConfigStruct configToCheck = new ConfigStruct();
        Server serverToCheck = new Server("Fuseki server simple", emptyMap());
        List<Endpoint> endpoints1 = new ArrayList<>();
        endpoints1.add(new Endpoint("", "query", emptyMap()));
        endpoints1.add(new Endpoint("upload", "upload", emptyMap()));
        Service service1 = new Service("ds", endpoints1, "abac-db");
        Database database1 = new Database("abac-db", ConfigConstants.ABAC, "", "http://localhost:3134/users/lookup/{user}", "","", "", "", "", "dataset-under", "true", "", "", "http://localhost:3234/users/lookup/{user}", "1", "PT1S", emptyMap());
        Database database2 = new Database("dataset-under", ConfigConstants.TIM, "", "", "","", "", "src/main/files/abac/data-and-labels.trig", "", "", "false", "", "", "", "", "", emptyMap());
        configToCheck.setServer(serverToCheck);
        List<Service> servicesToCheck = new ArrayList<>();
        servicesToCheck.add(service1);
        configToCheck.setServices(servicesToCheck);
        List<Database> databasesTocCheck = new ArrayList<>();
        databasesTocCheck.add(database1);
        databasesTocCheck.add(database2);
        configToCheck.setDatabases(databasesTocCheck);
        configToCheck.setVersion("1.0");
        Map<String, Object> map = ycp.parseYAMLConfigToMap("src/test/files/yaml/correct/abac/config-abac-cached-hierarchies.yaml");
        ConfigStruct config = ycp.mapToConfigStruct(map);
        assertEquals(config.toString(), configToCheck.toString());
    }

    @Test
    public void prefixesTest() {
        ConfigStruct configToCheck = new ConfigStruct();
        Server serverToCheck = new Server("Fuseki server simple", emptyMap());
        Map<String, String> prefixes = new HashMap<>();
        prefixes.put("fk", "http://jena.apache.org/fuseki/kafka#");
        prefixes.put("cqrs", "http://telicent.io/cqrs#");
        prefixes.put("graphql", "https://telicent.io/fuseki/modules/graphql#");
        prefixes.put("authz", "http://telicent.io/security#");
        List<Endpoint> endpoints1 = new ArrayList<>();
        endpoints1.add(new Endpoint("upload", "authz:upload", emptyMap()));
        endpoints1.add(new Endpoint("", "authz:query", emptyMap()));
        endpoints1.add(new Endpoint("data-update", "update", emptyMap()));
        Service service1 = new Service("ds", endpoints1, "tdb2-db");
        Database database1 = new Database("tdb2-db", ConfigConstants.TDB2, "", "", "","", "", "", "target/test-DB", "", "false", "", "", "", "", "", emptyMap());
        configToCheck.setServer(serverToCheck);
        List<Service> servicesToCheck = new ArrayList<>();
        servicesToCheck.add(service1);
        configToCheck.setServices(servicesToCheck);
        List<Database> databasesTocCheck = new ArrayList<>();
        databasesTocCheck.add(database1);
        configToCheck.setDatabases(databasesTocCheck);
        configToCheck.setVersion("1.0");
        configToCheck.setPrefixes(prefixes);
        Map<String, Object> map = ycp.parseYAMLConfigToMap("src/test/files/yaml/correct/prefixes/config-prefixes-1.yaml");
        ConfigStruct config = ycp.mapToConfigStruct(map);
        assertEquals(config.toString(), configToCheck.toString());
    }

    @Test
    public void missingPrefixTest() {
        RuntimeException ex = assertThrows(RuntimeException.class,() -> {
            ycp.runYAMLParser("src/test/files/yaml/wrong/prefixes/config-missing-prefix.yaml");
        });
        assertEquals("java.lang.IllegalArgumentException: The namespace http://jena.apache.org/fuseki/kafka# has no prefix assigned.", ex.getMessage());
    }

    @Test
    public void missingUriTest() {
        RuntimeException ex = assertThrows(RuntimeException.class,() -> {
            ycp.runYAMLParser("src/test/files/yaml/wrong/prefixes/config-missing-uri.yaml");
        });
        assertEquals("java.lang.IllegalArgumentException: The prefix cqrs has no namespace assigned.", ex.getMessage());
    }

    @Test
    public void invalidPrefixTest() {
        RuntimeException ex = assertThrows(RuntimeException.class,() -> {
            ycp.runYAMLParser("src/test/files/yaml/wrong/prefixes/config-invalid-prefix.yaml");
        });
        assertEquals("java.lang.IllegalArgumentException: Prefix fk... is not a valid prefix.", ex.getMessage());
    }

    @Test
    public void invalidUriTest() {
        RuntimeException ex = assertThrows(RuntimeException.class,() -> {
            ycp.runYAMLParser("src/test/files/yaml/wrong/prefixes/config-invalid-uri.yaml");
        });
        assertEquals("java.lang.IllegalArgumentException: Namespace ..invalidUri..:( is not a valid URI.", ex.getMessage());
    }

    @Test
    public void undefinedPrefixTest() {
        RuntimeException ex = assertThrows(RuntimeException.class,() -> {
            ycp.runYAMLParser("src/test/files/yaml/wrong/prefixes/config-undefined-prefix.yaml");
        });
        assertEquals("java.lang.IllegalArgumentException: Prefix authz is undefined", ex.getMessage());
    }

    @Test
    public void conflictingPrefixesTest() {
        RuntimeException ex = assertThrows(RuntimeException.class,() -> {
            ycp.runYAMLParser("src/test/files/yaml/wrong/prefixes/config-conflicting-prefixes.yaml");
        });
        assertEquals("java.lang.IllegalArgumentException: The prefix fk is already assigned to a different uri.", ex.getMessage());
    }

    @Test
    public void duplicatePrefixesTest() {
        RuntimeException ex = assertThrows(RuntimeException.class,() -> {
            ycp.runYAMLParser("src/test/files/yaml/wrong/prefixes/config-duplicate-prefixes.yaml");
        });
        assertEquals("java.lang.IllegalArgumentException: The prefix fk is already assigned to a different uri.", ex.getMessage());
    }

    @Test
    public void connectorsTest() {
        ConfigStruct configToCheck = new ConfigStruct();
        Server serverToCheck = new Server("Fuseki server simple", emptyMap());
        Map<String, String> prefixes = new HashMap<>();
        List<Endpoint> endpoints1 = new ArrayList<>();
        Map<String, String> settings1 = new HashMap<String, String>();
        settings1.put("arq:queryTimeout", "1000,10000");
        endpoints1.add(new Endpoint("sparql", "query",settings1));
        endpoints1.add(new Endpoint("data-update", "update", emptyMap()));
        Service service1 = new Service("ds", endpoints1, "tdb2-db");
        Database database1 = new Database("tdb2-db", ConfigConstants.TDB2, "", "", "","", "", "", "target/test-DB", "", "false", "", "", "", "", "", emptyMap());
        List<Endpoint> endpoints2 = new ArrayList<>();
        Map<String, String> settings2 = new HashMap<String, String>();
        settings2.put("arq:queryTimeout", "1000,10000");
        endpoints2.add(new Endpoint("sparql", "query",settings2));
        endpoints2.add(new Endpoint("data-update", "update", emptyMap()));
        Service service2 = new Service("ds2", endpoints2, "tdb2-db");
        configToCheck.setServer(serverToCheck);
        Map<String, String> config1 = new HashMap<String, String>();
        config1.put("key1", "value1");
        config1.put("key2", "value2");
        Connector connector1 = new Connector("/ds", "env:{ENV_KAFKA_TOPIC:RDF}", "localhost:9092", "dDatabases/RDF.state", "JenaFusekiKafka", "true", "true", config1);
        Connector connector2 = new Connector("/ds2", "env:{ENV_KAFKA_TOPIC:RDF}", "localhost:9093", "dDatabases/RDF.state", "JenaFusekiKafka2", "true", "true", emptyMap());
        List<Service> servicesToCheck = new ArrayList<>();
        servicesToCheck.add(service1);
        servicesToCheck.add(service2);
        configToCheck.setServices(servicesToCheck);
        List<Database> databasesToCheck = new ArrayList<>();
        databasesToCheck.add(database1);
        configToCheck.setDatabases(databasesToCheck);
        List<Connector> connectorsToCheck = new ArrayList<>();
        connectorsToCheck.add(connector1);
        connectorsToCheck.add(connector2);
        configToCheck.setConnectors(connectorsToCheck);
        configToCheck.setVersion("1.0");
        configToCheck.setPrefixes(prefixes);
        Map<String, Object> map = ycp.parseYAMLConfigToMap("src/test/files/yaml/correct/connector/config-multiple-connectors.yaml");
        ConfigStruct config = ycp.mapToConfigStruct(map);
        assertEquals(config.toString(), configToCheck.toString());
    }

    @Test
    public void missingFusekiServiceNameTest() {
        RuntimeException ex = assertThrows(RuntimeException.class,() -> {
            ycp.runYAMLParser("src/test/files/yaml/wrong/connector/config-connector-missing-service-name.yaml");
        });
        assertEquals("java.lang.IllegalArgumentException: No destination Fuseki service name.", ex.getMessage());
    }

    @Test
    public void missingTopicTest() {
        RuntimeException ex = assertThrows(RuntimeException.class,() -> {
            ycp.runYAMLParser("src/test/files/yaml/wrong/connector/config-connector-missing-topic.yaml");
        });
        assertEquals("java.lang.IllegalArgumentException: Missing topic on the \"/ds\" service connector.", ex.getMessage());
    }

    @Test
    public void missingBootstrapServersTest() {
        RuntimeException ex = assertThrows(RuntimeException.class,() -> {
            ycp.runYAMLParser("src/test/files/yaml/wrong/connector/config-connector-missing-bootstrap-servers.yaml");
        });
        assertEquals("java.lang.IllegalArgumentException: The \"bootstrapSevers\" field is empty on the \"/ds\" service connector.", ex.getMessage());
    }

    @Test
    public void missingStateFileTest() {
        RuntimeException ex = assertThrows(RuntimeException.class,() -> {
            ycp.runYAMLParser("src/test/files/yaml/wrong/connector/config-connector-missing-state-file.yaml");
        });
        assertEquals("java.lang.IllegalArgumentException: The \"stateFile\" field is empty on the \"/ds\" service connector.", ex.getMessage());
    }

    @Test
    public void ServiceNameMismatchTest1() {
        RuntimeException ex = assertThrows(RuntimeException.class,() -> {
            ycp.runYAMLParser("src/test/files/yaml/wrong/connector/config-connector-servicename-mismatch-1.yaml");
        });
        assertEquals("java.lang.IllegalArgumentException: Mismatch between existing services and the destination services of the connectors.", ex.getMessage());
    }

    @Test
    public void ServiceNameMismatchTest2() {
        RuntimeException ex = assertThrows(RuntimeException.class,() -> {
            ycp.runYAMLParser("src/test/files/yaml/wrong/connector/config-connector-servicename-mismatch-2.yaml");
        });
        assertEquals("java.lang.IllegalArgumentException: Mismatch between existing services and the destination services of the connectors.", ex.getMessage());
    }

    @Test
    public void connectorNonBooleanTest1() {
        RuntimeException ex = assertThrows(RuntimeException.class,() -> {
            ycp.runYAMLParser("src/test/files/yaml/wrong/connector/config-connector-non-bools-1.yaml");
        });
        assertEquals("java.lang.IllegalArgumentException: The value of \"replayTopic\" on the \"/ds\" service connector is \"blabla\", which is not a boolean.", ex.getMessage());
    }

    @Test
    public void connectorNonBooleanTest2() {
        RuntimeException ex = assertThrows(RuntimeException.class,() -> {
            ycp.runYAMLParser("src/test/files/yaml/wrong/connector/config-connector-non-bools-2.yaml");
        });
        assertEquals("java.lang.IllegalArgumentException: The value of \"syncTopic\" on the \"/ds\" service connector is \"blabla\", which is not a boolean.", ex.getMessage());
    }

}
