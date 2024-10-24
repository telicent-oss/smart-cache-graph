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

import org.apache.jena.fuseki.system.FusekiLogging;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFWriter;
import org.apache.jena.sys.JenaSystem;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;


import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

import static java.util.Collections.emptyMap;
import static org.junit.jupiter.api.Assertions.*;
import static yamlconfig.ConfigConstants.*;
import static yamlconfig.ConfigConstants.JA_NS;

public class TestRDFConfigGenerator {
    YAMLConfigParser ycp = new YAMLConfigParser();
    RDFConfigGenerator rcg = new RDFConfigGenerator();
    String baseUri = "http://testing.com/";

    static {
        FusekiLogging.setLogging();}

    @BeforeAll
    public static void before() {
        JenaSystem.init();
    }

    @Test
    public void singleDatabaseTest() {
        ConfigStruct config = ycp.runYAMLParser("src/test/files/yaml/correct/config-tim.yaml");
        Model model = rcg.createRDFModel(config);
        try (FileOutputStream out = new FileOutputStream("target/files/rdf/rdf-generator-test-1.ttl")) {
            model.write(out, "TTL");
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        Model model2 = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model2, "target/files/rdf/rdf-generator-test-1.ttl", baseUri, Lang.TURTLE);

        Property databaseProperty = model2.createProperty(baseUri + "#", "mem-db");
        model2.setNsPrefix("", baseUri);
        Model databaseModel = ModelFactory.createDefaultModel();
        StmtIterator iterator = model2.listStatements(databaseProperty, null, (RDFNode) null);
        while (iterator.hasNext()) {
            Statement stmt = iterator.nextStatement();
            databaseModel.add(stmt);
        }
        Model expectedDatabaseModel = ModelFactory.createDefaultModel();
        expectedDatabaseModel.setNsPrefix("", baseUri);
        RDFDataMgr.read(expectedDatabaseModel, "src/test/files/rdf/database1.ttl", baseUri, Lang.TURTLE);

        boolean isIsomorphic = databaseModel.isIsomorphicWith(expectedDatabaseModel);
        if ( ! isIsomorphic ) {
            printDifference(expectedDatabaseModel, databaseModel);
        }
        assertTrue(isIsomorphic);
    }

    @Test
    public void multipleDatabasesTest() {
        ConfigStruct config = ycp.runYAMLParser("src/test/files/yaml/correct/config-nocomment.yaml");
        Model model = rcg.createRDFModel(config);
        try (FileOutputStream out = new FileOutputStream("target/files/rdf/rdf-generator-test-2.ttl")) {
            model.write(out, "TTL");
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        Model model2 = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model2, "target/files/rdf/rdf-generator-test-2.ttl", baseUri, Lang.TURTLE);

        model2.setNsPrefix("", baseUri);
        Model databaseModel = ModelFactory.createDefaultModel();

        Pattern pattern = Pattern.compile(".*db.*", Pattern.CASE_INSENSITIVE);
        ResIterator resIterator = model2.listSubjects();

        while (resIterator.hasNext()) {
            Resource resource = resIterator.nextResource();
            String localName = resource.getLocalName();

            if (localName != null && pattern.matcher(localName).matches()) {
                StmtIterator iterator = model2.listStatements(resource, null, (RDFNode) null);
                while (iterator.hasNext()) {
                    Statement stmt = iterator.nextStatement();
                    databaseModel.add(stmt);
                    RDFNode object = stmt.getObject();
                    if (object.isAnon()) {
                        collectBlankNodeStatements(databaseModel, model2, object.asResource());
                    }
                }
            }
        }
        Model expectedDatabaseModel = ModelFactory.createDefaultModel();
        expectedDatabaseModel.setNsPrefix("", baseUri);
        RDFDataMgr.read(expectedDatabaseModel, "src/test/files/rdf/database2.ttl", baseUri, Lang.TURTLE);

        boolean isIsomorphic = databaseModel.isIsomorphicWith(expectedDatabaseModel);
        if ( ! isIsomorphic ) {
            printDifference(expectedDatabaseModel, databaseModel);
        }
        assertTrue(isIsomorphic);
    }

    @Test
    public void endpointsTest() {
        ConfigStruct config = ycp.runYAMLParser("src/test/files/yaml/correct/config-nocomment.yaml");
        Model modelNoPrefix = rcg.createRDFModel(config);
        try (FileOutputStream out = new FileOutputStream("target/files/rdf/rdf-generator-test-2.ttl")) {
            modelNoPrefix.write(out, "TTL");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
        Model model = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model, "target/files/rdf/rdf-generator-test-2.ttl", baseUri, Lang.TURTLE);
        model.setNsPrefix("", baseUri);

        Model endpointsModel = ModelFactory.createDefaultModel();

        Resource serviceForEndpoints = endpointsModel.createResource(baseUri + "#" + "service")
                .addProperty(RDF.type, endpointsModel.createResource(FUSEKI_NS + "Service"));

        Pattern pattern = Pattern.compile(".*service.*", Pattern.CASE_INSENSITIVE);
        ResIterator resIterator = model.listSubjects();

        while (resIterator.hasNext()) {
            Resource resource = resIterator.nextResource();
            String localName = resource.getLocalName();

            if (localName != null && pattern.matcher(localName).matches()) {
                StmtIterator iterator = model.listStatements(resource, null, (RDFNode) null);

                while (iterator.hasNext()) {
                    Statement stmt = iterator.nextStatement();
                    RDFNode object = stmt.getObject();
                    Property property = stmt.getPredicate();
                    if (object.isAnon()) {
                        Resource endpointRes = endpointsModel.createResource();
                        collectBlankNodeStatementsResource(endpointRes, model, object.asResource());
                        serviceForEndpoints.addProperty(property, endpointRes);
                    }
                }
            }
        }
        Model expectedEndpointsModel = ModelFactory.createDefaultModel();
        expectedEndpointsModel.setNsPrefix("", baseUri);
        RDFDataMgr.read(expectedEndpointsModel, "src/test/files/rdf/endpoints-test.ttl", baseUri, Lang.TURTLE);

        boolean isIsomorphic = endpointsModel.isIsomorphicWith(expectedEndpointsModel);
        if ( ! isIsomorphic ) {
            printDifference(expectedEndpointsModel, endpointsModel);
        }
        assertTrue(isIsomorphic);
    }

    @Test
    public void databaseContextTest() {
        ConfigStruct config = ycp.runYAMLParser("src/test/files/yaml/correct/config-db-query-timeout.yaml");
        Model model = rcg.createRDFModel(config);
        try (FileOutputStream out = new FileOutputStream("target/files/rdf/rdf-generator-test-5.ttl")) {
            model.write(out, "TTL");
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        Model model2 = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model2, "target/files/rdf/rdf-generator-test-5.ttl", baseUri, Lang.TURTLE);

        Property databaseProperty = model2.createProperty(baseUri + "#", "tdb2-db");
        model2.setNsPrefix("", baseUri);
        Model databaseModel = ModelFactory.createDefaultModel();
        StmtIterator iterator = model2.listStatements(databaseProperty, null, (RDFNode) null);
        while (iterator.hasNext()) {
            Statement stmt = iterator.nextStatement();
            databaseModel.add(stmt);
            RDFNode object = stmt.getObject();
            if (object.isAnon()) {
                collectBlankNodeStatements(databaseModel, model2, object.asResource());
            }
        }
        Model contextModel = ModelFactory.createDefaultModel();
        Property jaContext = databaseModel.createProperty(JA_NS, "context");
        Resource databaseResource = databaseModel.getResource(baseUri + "#" + "tdb2-db");

        if (databaseResource.hasProperty(jaContext)) {
            Statement contextStmt = databaseResource.getProperty(jaContext);
            RDFNode contextNode = contextStmt.getObject();

            contextModel.add(databaseResource, RDF.type, databaseResource.getProperty(RDF.type).getObject());
            contextModel.add(databaseResource, jaContext, contextNode);

            if (contextNode.isResource()) {
                Resource contextResource = contextNode.asResource();
                StmtIterator contextProperties = contextResource.listProperties();
                while (contextProperties.hasNext()) {
                    Statement stmt = contextProperties.nextStatement();
                    contextModel.add(stmt);
                }
            }
        }
        Model expectedContextModel = ModelFactory.createDefaultModel();
        expectedContextModel.setNsPrefix("", baseUri);
        RDFDataMgr.read(expectedContextModel, "src/test/files/rdf/db-context.ttl", baseUri, Lang.TURTLE);

        boolean isIsomorphic = contextModel.isIsomorphicWith(expectedContextModel);
        if ( ! isIsomorphic ) {
            printDifference(expectedContextModel, contextModel);
        }
        assertTrue(isIsomorphic);
    }

    @Test
    public void multipleDatabasesContextTest() {
        ConfigStruct config = ycp.runYAMLParser("src/test/files/yaml/correct/config-many-contexts.yaml");
        Model model = rcg.createRDFModel(config);
        try (FileOutputStream out = new FileOutputStream("target/files/rdf/rdf-generator-test-7.ttl")) {
            model.write(out, "TTL");
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        Model model2 = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model2, "target/files/rdf/rdf-generator-test-7.ttl", baseUri, Lang.TURTLE);

        model2.setNsPrefix("", baseUri);
        Model databaseModel = ModelFactory.createDefaultModel();

        Pattern pattern = Pattern.compile(".*db.*", Pattern.CASE_INSENSITIVE);
        ResIterator resIterator = model2.listSubjects();

        while (resIterator.hasNext()) {
            Resource resource = resIterator.nextResource();
            String localName = resource.getLocalName();

            if (localName != null && pattern.matcher(localName).matches()) {
                StmtIterator iterator = model2.listStatements(resource, null, (RDFNode) null);
                while (iterator.hasNext()) {
                    Statement stmt = iterator.nextStatement();
                    databaseModel.add(stmt);
                    RDFNode object = stmt.getObject();
                    if (object.isAnon()) {
                        collectBlankNodeStatements(databaseModel, model2, object.asResource());
                    }
                }
            }
        }
        Model contextModel = ModelFactory.createDefaultModel();
        Property jaContext = databaseModel.createProperty(JA_NS, "context");
        ResIterator resIteratorContext = databaseModel.listSubjects();

        while (resIteratorContext.hasNext()) {
            Resource databaseResource = resIteratorContext.nextResource();
            if (databaseResource.hasProperty(jaContext)) {
                Statement contextStmt = databaseResource.getProperty(jaContext);
                RDFNode contextNode = contextStmt.getObject();

                contextModel.add(databaseResource, RDF.type, databaseResource.getProperty(RDF.type).getObject());
                contextModel.add(databaseResource, jaContext, contextNode);

                if (contextNode.isResource()) {
                    Resource contextResource = contextNode.asResource();
                    StmtIterator contextProperties = contextResource.listProperties();
                    while (contextProperties.hasNext()) {
                        Statement stmt = contextProperties.nextStatement();
                        contextModel.add(stmt);
                    }
                }
            }
        }
        Model expectedDatabaseModel = ModelFactory.createDefaultModel();
        expectedDatabaseModel.setNsPrefix("", baseUri);
        RDFDataMgr.read(expectedDatabaseModel, "src/test/files/rdf/db-context-multiple.ttl", baseUri, Lang.TURTLE);

        boolean isIsomorphic = contextModel.isIsomorphicWith(expectedDatabaseModel);
        if ( ! isIsomorphic ) {
            printDifference(expectedDatabaseModel, contextModel);
        }
        assertTrue(isIsomorphic);
    }

    @Test
    public void endpointContextTest() {
        ConfigStruct config = ycp.runYAMLParser("src/test/files/yaml/correct/config-endpoint-query-timeout.yaml");
        Model modelNoPrefix = rcg.createRDFModel(config);
        try (FileOutputStream out = new FileOutputStream("target/files/rdf/rdf-generator-test-6.ttl")) {
            modelNoPrefix.write(out, "TTL");
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        Model model = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model, "target/files/rdf/rdf-generator-test-6.ttl", baseUri, Lang.TURTLE);
        model.setNsPrefix("", baseUri);

        Model endpointsModel = ModelFactory.createDefaultModel();

        Resource serviceForEndpoints = endpointsModel.createResource(baseUri + "#" + "service")
                .addProperty(RDF.type, endpointsModel.createResource(FUSEKI_NS + "Service"));
        Pattern pattern = Pattern.compile(".*service.*", Pattern.CASE_INSENSITIVE);
        ResIterator resIterator = model.listSubjects();
        Property jaContext = endpointsModel.createProperty(JA_NS + "#", "context");

        while (resIterator.hasNext()) {
            Resource resource = resIterator.nextResource();
            String localName = resource.getLocalName();
            if (localName != null && pattern.matcher(localName).matches()) {
                StmtIterator iterator = model.listStatements(resource, null, (RDFNode) null);
                while (iterator.hasNext()) {
                    Statement stmt = iterator.nextStatement();
                    RDFNode object = stmt.getObject();
                    Property property = stmt.getPredicate();
                    if (object.isAnon()) {
                        Resource endpointRes = endpointsModel.createResource();
                        collectBlankNodeStatementsResourceContext(endpointRes, model, object.asResource());
                        serviceForEndpoints.addProperty(property, endpointRes);
                    }
                }
            }
        }
        Model expectedEndpointsModel = ModelFactory.createDefaultModel();
        expectedEndpointsModel.setNsPrefix("", baseUri);
        RDFDataMgr.read(expectedEndpointsModel, "src/test/files/rdf/endpoints-context.ttl", baseUri, Lang.TURTLE);

        boolean isIsomorphic = endpointsModel.isIsomorphicWith(expectedEndpointsModel);
        if ( ! isIsomorphic ) {
            printDifference(expectedEndpointsModel, endpointsModel);
        }
        assertTrue(isIsomorphic);
    }

    @Test
    public void multipleEndpointContextTest() {
        ConfigStruct config = ycp.runYAMLParser("src/test/files/yaml/correct/config-many-contexts.yaml");
        Model modelNoPrefix = rcg.createRDFModel(config);
        try (FileOutputStream out = new FileOutputStream("target/files/rdf/rdf-generator-test-8.ttl")) {
            modelNoPrefix.write(out, "TTL");
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        Model model = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model, "target/files/rdf/rdf-generator-test-8.ttl", baseUri, Lang.TURTLE);
        model.setNsPrefix("", baseUri);
        Model endpointsModel = ModelFactory.createDefaultModel();

        Resource serviceForEndpoints = endpointsModel.createResource(baseUri + "#" + "service")
                .addProperty(RDF.type, endpointsModel.createResource(FUSEKI_NS + "Service"));
        Pattern pattern = Pattern.compile(".*service.*", Pattern.CASE_INSENSITIVE);
        ResIterator resIterator = model.listSubjects();
        Property jaContext = endpointsModel.createProperty(JA_NS + "#", "context");

        while (resIterator.hasNext()) {
            Resource resource = resIterator.nextResource();
            String localName = resource.getLocalName();
            if (localName != null && pattern.matcher(localName).matches()) {
                StmtIterator iterator = model.listStatements(resource, null, (RDFNode) null);
                while (iterator.hasNext()) {
                    Statement stmt = iterator.nextStatement();
                    RDFNode object = stmt.getObject();
                    Property property = stmt.getPredicate();
                    if (object.isAnon()) {
                        Resource endpointRes = endpointsModel.createResource();
                        collectBlankNodeStatementsResourceContext(endpointRes, model, object.asResource());
                        serviceForEndpoints.addProperty(property, endpointRes);
                    }
                }
            }
        }
        Model expectedEndpointsModel = ModelFactory.createDefaultModel();
        expectedEndpointsModel.setNsPrefix("", baseUri);
        RDFDataMgr.read(expectedEndpointsModel, "src/test/files/rdf/endpoints-context-multiple.ttl", baseUri, Lang.TURTLE);

        boolean isIsomorphic = endpointsModel.isIsomorphicWith(expectedEndpointsModel);
        if ( ! isIsomorphic ) {
            printDifference(expectedEndpointsModel, endpointsModel);
        }
        assertTrue(isIsomorphic);
    }

    @Test
    public void fullConfigTest1() {
        ConfigStruct config = new ConfigStruct();
        Server server = new Server("Fuseki server 1", null);
        Map<String, String> settings1 = new HashMap<String, String>();
        settings1.put("arq:queryTimeout", "1000,10000");
        List<Endpoint> endpoints1 = new ArrayList<>();
        endpoints1.add(new Endpoint("", "query", null));
        endpoints1.add(new Endpoint("sparql", "query", settings1));
        endpoints1.add(new Endpoint("data-update", "update", null));
        Service service1 = new Service("/ds", endpoints1, "mem-db");
        Database database1 = new Database("mem-db", ConfigConstants.TIM, "", "","", "", "", "", "", "", "", "", "","", "", "", null);
        List<Endpoint> endpoints2 = new ArrayList<>();
        endpoints2.add(new Endpoint("sparql", "query", null));
        Service service2 = new Service("/db2", endpoints2, "tdb2-db");
        Map<String, String> settings2 = new HashMap<String, String>();
        settings2.put( "tdb2:unionDefaultGraph", "true");
        Database database2 = new Database("tdb2-db", ConfigConstants.TDB2, "", "", "", "",  "", "", "DB2", "", "", "", "", "", "", "", settings2);
        config.setServer(server);
        List<Service> services = new ArrayList<>();
        services.add(service1);
        services.add(service2);
        config.setServices(services);
        List<Database> databases = new ArrayList<>();
        databases.add(database1);
        databases.add(database2);
        config.setDatabases(databases);
        config.setVersion("1.0");
        Model model = rcg.createRDFModel(config);
        try (FileOutputStream out = new FileOutputStream("target/files/rdf/rdf-generator-test-1.ttl")) {
            model.write(out, "TTL");
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        Model model2 = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model2, "target/files/rdf/rdf-generator-test-1.ttl", baseUri, Lang.TURTLE);

        Model expectedDatabaseModel = ModelFactory.createDefaultModel();
        expectedDatabaseModel.setNsPrefix("", baseUri);
        RDFDataMgr.read(expectedDatabaseModel, "src/test/files/rdf/full-config-test-1.ttl", baseUri, Lang.TURTLE);

        boolean isIsomorphic = model2.isIsomorphicWith(expectedDatabaseModel);
        if ( ! isIsomorphic ) {
            printDifference(expectedDatabaseModel, model2);
        }
        assertTrue(isIsomorphic);
    }

    @Test
    public void fullConfigTest2() {
        ConfigStruct config = new ConfigStruct();
        Server server = new Server("Fuseki server simple", null);
        Map<String, String> settings1 = new HashMap<String, String>();
        settings1.put("arq:queryTimeout", "1,1");
        List<Endpoint> endpoints1 = new ArrayList<>();
        endpoints1.add(new Endpoint("sparql", "query", null));
        endpoints1.add(new Endpoint("data-update", "update", null));
        Service service1 = new Service("ds", endpoints1, "tdb2-db");
        Database database1 = new Database("tdb2-db", ConfigConstants.TDB2, "", "", "", "", "", "", "target/test-DB", "", "", "", "", "", "", "", settings1);
        config.setServer(server);
        List<Service> services = new ArrayList<>();
        services.add(service1);
        config.setServices(services);
        List<Database> databases = new ArrayList<>();
        databases.add(database1);
        config.setDatabases(databases);
        config.setVersion("1.0");
        Model model = rcg.createRDFModel(config);
        try (FileOutputStream out = new FileOutputStream("target/files/rdf/rdf-generator-test-2.ttl")) {
            model.write(out, "TTL");
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        Model model2 = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model2, "target/files/rdf/rdf-generator-test-2.ttl", baseUri, Lang.TURTLE);

        Model expectedDatabaseModel = ModelFactory.createDefaultModel();
        expectedDatabaseModel.setNsPrefix("", baseUri);
        RDFDataMgr.read(expectedDatabaseModel, "src/test/files/rdf/full-config-test-2.ttl", baseUri, Lang.TURTLE);

        boolean isIsomorphic = model2.isIsomorphicWith(expectedDatabaseModel);
        if ( ! isIsomorphic ) {
            printDifference(expectedDatabaseModel, model2);
        }
        assertTrue(isIsomorphic);
    }

    @Test
    public void fullConfigTest3() {
        ConfigStruct config = new ConfigStruct();
        Server server = new Server("Fuseki server 1", null);
        Map<String, String> settings1 = new HashMap<String, String>();
        settings1.put("arq:queryTimeout", "1000,10000");
        List<Endpoint> endpoints1 = new ArrayList<>();
        endpoints1.add(new Endpoint("", "query", null));
        endpoints1.add(new Endpoint("sparql", "query", settings1));
        endpoints1.add(new Endpoint("data-update", "update", null));
        Service service1 = new Service("/ds", endpoints1, "mem-db");
        Database database1 = new Database("mem-db", ConfigConstants.TIM, "", "", "", "", "", "target/mem-db.ttl", "", "", "", "", "", "", "", "", null);
        List<Endpoint> endpoints2 = new ArrayList<>();
        Service service2 = new Service("/db2", endpoints2, "tdb2-db");
        Map<String, String> settings2 = new HashMap<String, String>();
        settings2.put( "tdb2:unionDefaultGraph", "true");
        settings2.put( "arq:queryTimeout",  "1000,10000");
        Database database2 = new Database("tdb2-db", ConfigConstants.TDB2, "", "", "", "", "", "", "location/test-DB", "", "", "", "", "", "", "", settings2);
        config.setServer(server);
        List<Service> services = new ArrayList<>();
        services.add(service1);
        services.add(service2);
        config.setServices(services);
        List<Database> databases = new ArrayList<>();
        databases.add(database1);
        databases.add(database2);
        config.setDatabases(databases);
        config.setVersion("1.0");
        Model model = rcg.createRDFModel(config);
        try (FileOutputStream out = new FileOutputStream("target/files/rdf/rdf-generator-test-3.ttl")) {
            model.write(out, "TTL");
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        Model model2 = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model2, "target/files/rdf/rdf-generator-test-3.ttl", baseUri, Lang.TURTLE);

        Model expectedDatabaseModel = ModelFactory.createDefaultModel();
        expectedDatabaseModel.setNsPrefix("", baseUri);
        RDFDataMgr.read(expectedDatabaseModel, "src/test/files/rdf/full-config-test-3.ttl", baseUri, Lang.TURTLE);

        boolean isIsomorphic = model2.isIsomorphicWith(expectedDatabaseModel);
        if ( ! isIsomorphic ) {
            printDifference(expectedDatabaseModel, model2);
        }
        assertTrue(isIsomorphic);
    }

    @Test
    public void fullConfigTest4() {
        ConfigStruct config = new ConfigStruct();
        Server serverToCheck = new Server("Fuseki server simple", emptyMap());
        List<Endpoint> endpoints1 = new ArrayList<>();
        endpoints1.add(new Endpoint("", "query", emptyMap()));
        endpoints1.add(new Endpoint("upload", "upload", emptyMap()));
        Service service1 = new Service("ds", endpoints1, "abac-mem-db");
        Database database1 = new Database("abac-mem-db", ConfigConstants.ABAC, "abac/attribute-store.ttl", "", "labels.ttl", "", "!", "", "", "dataset-under", "false", "", "", "", "", "", emptyMap());
        Database database2 = new Database("dataset-under", ConfigConstants.TIM, "", "", "", "", "", "src/main/files/abac/data-and-labels.trig", "", "", "false", "", "", "", "", "", emptyMap());
        config.setServer(serverToCheck);
        List<Service> servicesToCheck = new ArrayList<>();
        servicesToCheck.add(service1);
        config.setServices(servicesToCheck);
        List<Database> databasesTocCheck = new ArrayList<>();
        databasesTocCheck.add(database1);
        databasesTocCheck.add(database2);
        config.setDatabases(databasesTocCheck);
        config.setVersion("1.0");
        Model model = rcg.createRDFModel(config);
        try (FileOutputStream out = new FileOutputStream("target/files/rdf/rdf-generator-test-4.ttl")) {
            model.write(out, "TTL");
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        Model model2 = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model2, "target/files/rdf/rdf-generator-test-4.ttl", baseUri, Lang.TURTLE);

        Model expectedDatabaseModel = ModelFactory.createDefaultModel();
        expectedDatabaseModel.setNsPrefix("", baseUri);
        RDFDataMgr.read(expectedDatabaseModel, "src/test/files/rdf/full-config-test-4.ttl", baseUri, Lang.TURTLE);

        boolean isIsomorphic = model2.isIsomorphicWith(expectedDatabaseModel);
        if ( ! isIsomorphic ) {
            printDifference(expectedDatabaseModel, model2);
        }
        assertTrue(isIsomorphic);
    }

    @Test
    public void ABACattributesDatabaseTest() {
        ConfigStruct config = ycp.runYAMLParser("src/test/files/yaml/correct/abac/config-abac-tim.yaml");
        Model model = rcg.createRDFModel(config);
        try (FileOutputStream out = new FileOutputStream("target/files/rdf/rdf-generator-test-9.ttl")) {
            model.write(out, "TTL");
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        Model model2 = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model2, "target/files/rdf/rdf-generator-test-9.ttl", baseUri, Lang.TURTLE);

        String datasetName = "";

        Property databaseProperty = model2.createProperty(baseUri + "#", "abac-mem-db");
        model2.setNsPrefix("", baseUri);
        Model databaseModel = ModelFactory.createDefaultModel();
        StmtIterator iterator = model2.listStatements(databaseProperty, null, (RDFNode) null);
        while (iterator.hasNext()) {
            Statement stmt = iterator.nextStatement();
            databaseModel.add(stmt);
            if (stmt.getPredicate().toString().equals("http://telicent.io/security#dataset"))
                datasetName = stmt.getObject().toString();
        }

        Property underlyingDatabaseProperty = model2.createProperty(datasetName);
        StmtIterator iterator2 = model2.listStatements(underlyingDatabaseProperty, null, (RDFNode) null);
        while (iterator2.hasNext()) {
            Statement stmt = iterator2.nextStatement();
            databaseModel.add(stmt);
        }

        Model expectedDatabaseModel = ModelFactory.createDefaultModel();
        expectedDatabaseModel.setNsPrefix("", baseUri);
        RDFDataMgr.read(expectedDatabaseModel, "src/test/files/rdf/abac/database-abac-tim-attributes.ttl", baseUri, Lang.TURTLE);

        boolean isIsomorphic = databaseModel.isIsomorphicWith(expectedDatabaseModel);
        if ( ! isIsomorphic ) {
            printDifference(expectedDatabaseModel, databaseModel);
        }
        assertTrue(isIsomorphic);
    }

    @Test
    public void ABACattributesURLDatabaseTest() {
        ConfigStruct config = ycp.runYAMLParser("src/test/files/yaml/correct/abac/config-abac-remote-attributes.yaml");
        Model model = rcg.createRDFModel(config);
        try (FileOutputStream out = new FileOutputStream("target/files/rdf/rdf-generator-test-10.ttl")) {
            model.write(out, "TTL");
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        Model model2 = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model2, "target/files/rdf/rdf-generator-test-10.ttl", baseUri, Lang.TURTLE);

        String datasetName = "";
        Property databaseProperty = model2.createProperty(baseUri + "#", "abac-db");
        model2.setNsPrefix("", baseUri);
        Model databaseModel = ModelFactory.createDefaultModel();
        StmtIterator iterator = model2.listStatements(databaseProperty, null, (RDFNode) null);
        while (iterator.hasNext()) {
            Statement stmt = iterator.nextStatement();
            databaseModel.add(stmt);
            if (stmt.getPredicate().toString().equals("http://telicent.io/security#dataset"))
                datasetName = stmt.getObject().toString();
        }

        Property underlyingDatabaseProperty = model2.createProperty(datasetName);
        StmtIterator iterator2 = model2.listStatements(underlyingDatabaseProperty, null, (RDFNode) null);
        while (iterator2.hasNext()) {
            Statement stmt = iterator2.nextStatement();
            databaseModel.add(stmt);
        }

        Model expectedDatabaseModel = ModelFactory.createDefaultModel();
        expectedDatabaseModel.setNsPrefix("", baseUri);
        RDFDataMgr.read(expectedDatabaseModel, "src/test/files/rdf/abac/database-abac-tim-remote-attributes.ttl", baseUri, Lang.TURTLE);

        boolean isIsomorphic = databaseModel.isIsomorphicWith(expectedDatabaseModel);
        if ( ! isIsomorphic ) {
            printDifference(expectedDatabaseModel, databaseModel);
        }
        assertTrue(isIsomorphic);
    }

    @Test
    public void ABAClabelsDatabaseTest() {
        ConfigStruct config = ycp.runYAMLParser("src/test/files/yaml/correct/abac/config-abac-labels-file.yaml");
        Model model = rcg.createRDFModel(config);
        try (FileOutputStream out = new FileOutputStream("target/files/rdf/rdf-generator-test-11.ttl")) {
            model.write(out, "TTL");
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        Model model2 = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model2, "target/files/rdf/rdf-generator-test-11.ttl", baseUri, Lang.TURTLE);

        String datasetName = "";
        Property databaseProperty = model2.createProperty(baseUri + "#", "abac-db");
        model2.setNsPrefix("", baseUri);
        Model databaseModel = ModelFactory.createDefaultModel();
        StmtIterator iterator = model2.listStatements(databaseProperty, null, (RDFNode) null);
        while (iterator.hasNext()) {
            Statement stmt = iterator.nextStatement();
            databaseModel.add(stmt);
            if (stmt.getPredicate().toString().equals("http://telicent.io/security#dataset"))
                datasetName = stmt.getObject().toString();
        }

        Property underlyingDatabaseProperty = model2.createProperty(datasetName);
        StmtIterator iterator2 = model2.listStatements(underlyingDatabaseProperty, null, (RDFNode) null);
        while (iterator2.hasNext()) {
            Statement stmt = iterator2.nextStatement();
            databaseModel.add(stmt);
        }

        Model expectedDatabaseModel = ModelFactory.createDefaultModel();
        expectedDatabaseModel.setNsPrefix("", baseUri);
        RDFDataMgr.read(expectedDatabaseModel, "src/test/files/rdf/abac/database-abac-labels-file.ttl", baseUri, Lang.TURTLE);

        boolean isIsomorphic = databaseModel.isIsomorphicWith(expectedDatabaseModel);
        if ( ! isIsomorphic ) {
            printDifference(expectedDatabaseModel, databaseModel);
        }
        assertTrue(isIsomorphic);
    }

    @Test
    public void ABAClabelsStoreDatabaseTest() {
        ConfigStruct config = ycp.runYAMLParser("src/test/files/yaml/correct/abac/config-abac-labelsstore-path.yaml");
        Model model = rcg.createRDFModel(config);
        try (FileOutputStream out = new FileOutputStream("target/files/rdf/rdf-generator-test-12.ttl")) {
            model.write(out, "TTL");
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        Model model2 = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model2, "target/files/rdf/rdf-generator-test-12.ttl", baseUri, Lang.TURTLE);

        String datasetName = "";
        Property databaseProperty = model2.createProperty(baseUri + "#", "abac-db");
        model2.setNsPrefix("", baseUri);
        Model databaseModel = ModelFactory.createDefaultModel();
        StmtIterator iterator = model2.listStatements(databaseProperty, null, (RDFNode) null);
        while (iterator.hasNext()) {
            Statement stmt = iterator.nextStatement();
            databaseModel.add(stmt);
            RDFNode object = stmt.getObject();
            if (object.isAnon()) {
                collectBlankNodeStatements(databaseModel, model2, object.asResource());
            }
            if (stmt.getPredicate().toString().equals("http://telicent.io/security#dataset"))
                datasetName = stmt.getObject().toString();
        }

        Property underlyingDatabaseProperty = model2.createProperty(datasetName);
        StmtIterator iterator2 = model2.listStatements(underlyingDatabaseProperty, null, (RDFNode) null);
        while (iterator2.hasNext()) {
            Statement stmt = iterator2.nextStatement();
            databaseModel.add(stmt);
        }

        Model expectedDatabaseModel = ModelFactory.createDefaultModel();
        expectedDatabaseModel.setNsPrefix("", baseUri);
        RDFDataMgr.read(expectedDatabaseModel, "src/test/files/rdf/abac/database-abac-labelsstore-path.ttl", baseUri, Lang.TURTLE);

        boolean isIsomorphic = databaseModel.isIsomorphicWith(expectedDatabaseModel);
        if ( ! isIsomorphic ) {
            printDifference(expectedDatabaseModel, databaseModel);
        }
        assertTrue(isIsomorphic);
    }

    @Test
    public void ABACtripleDefaultLabelsDatabaseTest() {
        ConfigStruct config = ycp.runYAMLParser("src/test/files/yaml/correct/abac/config-abac-tdl.yaml");
        Model model = rcg.createRDFModel(config);
        try (FileOutputStream out = new FileOutputStream("target/files/rdf/rdf-generator-test-13.ttl")) {
            model.write(out, "TTL");
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        Model model2 = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model2, "target/files/rdf/rdf-generator-test-13.ttl", baseUri, Lang.TURTLE);

        String datasetName = "";
        Property databaseProperty = model2.createProperty(baseUri + "#", "abac-db");
        model2.setNsPrefix("", baseUri);
        Model databaseModel = ModelFactory.createDefaultModel();
        StmtIterator iterator = model2.listStatements(databaseProperty, null, (RDFNode) null);
        while (iterator.hasNext()) {
            Statement stmt = iterator.nextStatement();
            databaseModel.add(stmt);
            if (stmt.getPredicate().toString().equals("http://telicent.io/security#dataset"))
                datasetName = stmt.getObject().toString();
        }

        Property underlyingDatabaseProperty = model2.createProperty(datasetName);
        StmtIterator iterator2 = model2.listStatements(underlyingDatabaseProperty, null, (RDFNode) null);
        while (iterator2.hasNext()) {
            Statement stmt = iterator2.nextStatement();
            databaseModel.add(stmt);
        }

        Model expectedDatabaseModel = ModelFactory.createDefaultModel();
        expectedDatabaseModel.setNsPrefix("", baseUri);
        RDFDataMgr.read(expectedDatabaseModel, "src/test/files/rdf/abac/database-abac-tdl.ttl", baseUri, Lang.TURTLE);

        boolean isIsomorphic = databaseModel.isIsomorphicWith(expectedDatabaseModel);
        if ( ! isIsomorphic ) {
            printDifference(expectedDatabaseModel, databaseModel);
        }
        assertTrue(isIsomorphic);
    }

    @Test
    public void ABACcacheTest() {
        ConfigStruct config = ycp.runYAMLParser("src/test/files/yaml/correct/abac/config-abac-cache.yaml");
        Model model = rcg.createRDFModel(config);
        try (FileOutputStream out = new FileOutputStream("target/files/rdf/rdf-generator-test-14.ttl")) {
            model.write(out, "TTL");
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        Model model2 = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model2, "target/files/rdf/rdf-generator-test-14.ttl", baseUri, Lang.TURTLE);

        String datasetName = "";
        Property databaseProperty = model2.createProperty(baseUri + "#", "abac-db");
        model2.setNsPrefix("", baseUri);
        Model databaseModel = ModelFactory.createDefaultModel();
        StmtIterator iterator = model2.listStatements(databaseProperty, null, (RDFNode) null);
        while (iterator.hasNext()) {
            Statement stmt = iterator.nextStatement();
            databaseModel.add(stmt);
            if (stmt.getPredicate().toString().equals("http://telicent.io/security#dataset"))
                datasetName = stmt.getObject().toString();
        }

        Property underlyingDatabaseProperty = model2.createProperty(datasetName);
        StmtIterator iterator2 = model2.listStatements(underlyingDatabaseProperty, null, (RDFNode) null);
        while (iterator2.hasNext()) {
            Statement stmt = iterator2.nextStatement();
            databaseModel.add(stmt);
        }

        Model expectedDatabaseModel = ModelFactory.createDefaultModel();
        expectedDatabaseModel.setNsPrefix("", baseUri);
        RDFDataMgr.read(expectedDatabaseModel, "src/test/files/rdf/abac/database-abac-cache.ttl", baseUri, Lang.TURTLE);

        boolean isIsomorphic = databaseModel.isIsomorphicWith(expectedDatabaseModel);
        if ( ! isIsomorphic ) {
            printDifference(expectedDatabaseModel, databaseModel);
        }
        assertTrue(isIsomorphic);
    }

    @Test
    public void ABACcachedHierarchiesTest() {
        ConfigStruct config = ycp.runYAMLParser("src/test/files/yaml/correct/abac/config-abac-cached-hierarchies.yaml");
        Model model = rcg.createRDFModel(config);
        try (FileOutputStream out = new FileOutputStream("target/files/rdf/rdf-generator-test-15.ttl")) {
            model.write(out, "TTL");
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        Model model2 = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model2, "target/files/rdf/rdf-generator-test-15.ttl", baseUri, Lang.TURTLE);

        String datasetName = "";
        Property databaseProperty = model2.createProperty(baseUri + "#", "abac-db");
        model2.setNsPrefix("", baseUri);
        Model databaseModel = ModelFactory.createDefaultModel();
        StmtIterator iterator = model2.listStatements(databaseProperty, null, (RDFNode) null);
        while (iterator.hasNext()) {
            Statement stmt = iterator.nextStatement();
            databaseModel.add(stmt);
            if (stmt.getPredicate().toString().equals("http://telicent.io/security#dataset"))
                datasetName = stmt.getObject().toString();
        }

        Property underlyingDatabaseProperty = model2.createProperty(datasetName);
        StmtIterator iterator2 = model2.listStatements(underlyingDatabaseProperty, null, (RDFNode) null);
        while (iterator2.hasNext()) {
            Statement stmt = iterator2.nextStatement();
            databaseModel.add(stmt);
        }

        Model expectedDatabaseModel = ModelFactory.createDefaultModel();
        expectedDatabaseModel.setNsPrefix("", baseUri);
        RDFDataMgr.read(expectedDatabaseModel, "src/test/files/rdf/abac/database-abac-cached-hierarchies.ttl", baseUri, Lang.TURTLE);

        boolean isIsomorphic = databaseModel.isIsomorphicWith(expectedDatabaseModel);
        if ( ! isIsomorphic ) {
            printDifference(expectedDatabaseModel, databaseModel);
        }
        assertTrue(isIsomorphic);
    }

    @Test
    public void connectorsTest() {
        ConfigStruct config = ycp.runYAMLParser("src/test/files/yaml/correct/connector/config-multiple-connectors.yaml");
        Model model = rcg.createRDFModel(config);
        try (FileOutputStream out = new FileOutputStream("target/files/rdf/rdf-generator-test-16.ttl")) {
            model.write(out, "TTL");
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        Model model2 = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model2, "target/files/rdf/rdf-generator-test-16.ttl", baseUri, Lang.TURTLE);

        model2.setNsPrefix("", baseUri);
        Model connectorModel = ModelFactory.createDefaultModel();

        Pattern pattern = Pattern.compile(".*connector.*", Pattern.CASE_INSENSITIVE);
        ResIterator resIterator = model2.listSubjects();

        while (resIterator.hasNext()) {
            Resource resource = resIterator.nextResource();
            String localName = resource.getLocalName();

            if (localName != null && pattern.matcher(localName).matches()) {
                StmtIterator iterator = model2.listStatements(resource, null, (RDFNode) null);
                while (iterator.hasNext()) {
                    Statement stmt = iterator.nextStatement();
                    RDFNode object = stmt.getObject();
                    if (object.isResource() && object.canAs(RDFList.class)) {
                        RDFList list = object.as(RDFList.class);
                        RDFList copiedList = collectConfigLists(connectorModel, model2, list);
                        connectorModel.add(resource, stmt.getPredicate(), copiedList);
                    }
                    else
                        connectorModel.add(stmt);
                }
            }
        }
        Model expectedConnectorModel = ModelFactory.createDefaultModel();
        expectedConnectorModel.setNsPrefix("", baseUri);
        expectedConnectorModel.setNsPrefix("fuseki", ConfigConstants.FUSEKI_NS);
        expectedConnectorModel.setNsPrefix("rdf", ConfigConstants.RDF_NS);
        expectedConnectorModel.setNsPrefix("ja", ConfigConstants.JA_NS);
        expectedConnectorModel.setNsPrefix("tdb2", ConfigConstants.TDB2_NS);
        expectedConnectorModel.setNsPrefix("authz", ConfigConstants.AUTHZ_NS);
        expectedConnectorModel.setNsPrefix("fk", ConfigConstants.FK_NS);
        RDFDataMgr.read(expectedConnectorModel, "src/test/files/rdf/connector1.ttl", baseUri, Lang.TURTLE);

        boolean isIsomorphic = connectorModel.isIsomorphicWith(expectedConnectorModel);
        if ( ! isIsomorphic ) {
            printDifference(expectedConnectorModel, connectorModel);
        }
        assertTrue(isIsomorphic);
    }


    @Test
    public void hierarchyCacheSizeNotIntTest() {
        RuntimeException ex = assertThrows(RuntimeException.class,() -> {
            ConfigStruct config = ycp.runYAMLParser("src/test/files/yaml/wrong/abac/config-abac-hierarchy-cachesize-not-int.yaml");
            Model model = rcg.createRDFModel(config);
        });
        assertEquals("The value of \"hierarchyCacheSize\" is \"bb\" which is not a valid number.", ex.getMessage());
    }

    @Test
    public void attributeCacheSizeNotIntTest() {
        RuntimeException ex = assertThrows(RuntimeException.class,() -> {
            ConfigStruct config = ycp.runYAMLParser("src/test/files/yaml/wrong/abac/config-abac-cachesize-not-int.yaml");
            Model model = rcg.createRDFModel(config);
        });
        assertEquals("The value of \"attributeCacheSize\" is \"bb\" which is not a valid number.", ex.getMessage());
    }

    @Test
    public void prefixesTest() {
        ConfigStruct config = ycp.runYAMLParser("src/test/files/yaml/correct/prefixes/config-prefixes-1.yaml");
        Model model = rcg.createRDFModel(config);
        try (FileOutputStream out = new FileOutputStream("target/files/rdf/rdf-generator-test-16.ttl")) {
            model.write(out, "TTL");
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        Model model2 = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model2, "target/files/rdf/rdf-generator-test-16.ttl", baseUri, Lang.TURTLE);

        Map<String, String> prefixesMap = model2.getNsPrefixMap();
        Model actualModel = ModelFactory.createDefaultModel();
        actualModel.setNsPrefixes(prefixesMap);

        Model expectedModel = ModelFactory.createDefaultModel();
        expectedModel.setNsPrefix("", baseUri);
        RDFDataMgr.read(expectedModel, "src/test/files/rdf/prefixes1.ttl", baseUri, Lang.TURTLE);

        boolean isIsomorphic = actualModel.isIsomorphicWith(expectedModel);
        if ( ! isIsomorphic ) {
            printDifference(expectedModel, model2);
        }
        assertTrue(isIsomorphic);
    }

    private RDFList collectConfigLists(Model targetModel, Model sourceModel, Resource sourceList) {
        RDFList targetList = targetModel.createList();
        RDFList sourceListAsList = sourceList.as(RDFList.class);
        for (int i = 0; i < sourceListAsList.size(); i ++) {
            targetList = targetList.with(sourceListAsList.get(i));
        }
        return targetList;
    }

    private void collectBlankNodeStatements(Model targetModel, Model sourceModel, Resource blankNode) {
        StmtIterator iterator = sourceModel.listStatements(blankNode, null, (RDFNode) null);
        while (iterator.hasNext()) {
            Statement stmt = iterator.nextStatement();
            targetModel.add(stmt);
        }
    }

    private void collectBlankNodeStatementsResource(final Resource targetResource, Model sourceModel, final Resource blankNode) {
        Model destModel = targetResource.getModel();
        // list statements with blank node as the subject
        StmtIterator iterator = sourceModel.listStatements(blankNode, null, (RDFNode) null);
        while (iterator.hasNext()) {
            Statement stmt = iterator.nextStatement();
            Property property = stmt.getPredicate();
            RDFNode object = stmt.getObject();
            if (object.isLiteral()) {
                targetResource.addLiteral(property, object.asLiteral());
            }
            else {
                Resource objectResource = object.asResource();
                targetResource.addProperty(property, objectResource);
                targetResource.getModel();
                // if object is a blank node
                if (object.isAnon()) {
                    collectBlankNodeStatementsResource(objectResource, sourceModel, objectResource);
                    StmtIterator iter = sourceModel.listStatements(objectResource, null, (RDFNode)null);
                    while(iter.hasNext()) {
                        Statement stmt2 = iter.next();
                        destModel.add(stmt2);
                    }
                }
            }
        }
    }

    private void collectBlankNodeStatementsResourceContext(final Resource targetResource, Model sourceModel, final Resource blankNode) {
        Model destModel = targetResource.getModel();
        Property jaContext = destModel.createProperty(JA_NS, "context");
        StmtIterator iterator = sourceModel.listStatements(blankNode, null, (RDFNode) null);
        while (iterator.hasNext()) {
            Statement stmt = iterator.nextStatement();
            Property prop = stmt.getPredicate();
            if (prop.equals(jaContext)) {
                Property property = stmt.getPredicate();
                RDFNode object = stmt.getObject();
                if (object.isLiteral()) {
                    targetResource.addLiteral(property, object.asLiteral());
                }
                else {
                    Resource objectResource = object.asResource();
                    targetResource.addProperty(property, objectResource);
                    targetResource.getModel();
                    if (object.isAnon()) {
                        collectBlankNodeStatementsResource(objectResource, sourceModel, objectResource);
                        StmtIterator iter = sourceModel.listStatements(objectResource, null, (RDFNode) null);
                        while (iter.hasNext()) {
                            Statement stmt2 = iter.next();
                            destModel.add(stmt2);
                        }
                    }
                }
            }
        }
    }

    private void printDifference(Model expected, Model actual) {
        actual.setNsPrefixes(expected);
        System.out.println("===========");
        System.out.println("Expected:");
        RDFWriter.source(expected).lang(Lang.TTL).output(System.out);
        System.out.println("----");
        System.out.println("Actual:");
        RDFWriter.source(actual).lang(Lang.TTL).output(System.out);
    }

}
