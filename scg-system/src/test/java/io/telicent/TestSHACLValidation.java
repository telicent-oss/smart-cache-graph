package io.telicent;

import io.telicent.jena.abac.fuseki.SysFusekiABAC;
import io.telicent.smart.cache.configuration.Configurator;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.system.FusekiLogging;
import org.apache.jena.sparql.exec.http.DSP;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static io.telicent.core.SmartCacheGraph.construct;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestSHACLValidation {

    private static final String DIR = "src/test/files";
    private FusekiServer server;
    private static final String serviceName = "/ds";

    private static final String expectedPass = """
            PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX sh:   <http://www.w3.org/ns/shacl#>
            PREFIX xsd:  <http://www.w3.org/2001/XMLSchema#>
            
            [ rdf:type     sh:ValidationReport;
              sh:conforms  true
            ] .
            """;

    private static final String expectedFail = """
            PREFIX ex:   <http://example.org/>
            PREFIX ies:  <http://ies.data.gov.uk/ontology/ies4#>
            PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX sh:   <http://www.w3.org/ns/shacl#>
            PREFIX xsd:  <http://www.w3.org/2001/XMLSchema#>
            
            [ rdf:type     sh:ValidationReport;
              sh:conforms  false;
              sh:result    [ rdf:type                      sh:ValidationResult;
                             sh:focusNode                  ex:123;
                             sh:resultMessage              "NodeKind[IRI] : Expected IRI for \\"Bob\\"";
                             sh:resultPath                 ies:hasName;
                             sh:resultSeverity             sh:Violation;
                             sh:sourceConstraintComponent  sh:NodeKindConstraintComponent;
                             sh:sourceShape                [] ;
                             sh:value                      "Bob"
                           ]
            ] .
            """;

    @BeforeEach
    void setUp() throws Exception {
        FusekiLogging.setLogging();
        SysFusekiABAC.init();
        LibTestsSCG.teardownAuthentication();
        LibTestsSCG.disableInitialCompaction();
    }

    @AfterEach
    void clearDown() throws Exception {
        if (null != server) {
            server.stop();
        }
        Configurator.reset();
    }

    @Test
    void shacl_validation_pass() throws Exception {
        List<String> arguments = List.of("--conf",DIR + "/config-shacl.ttl");
        server = construct(arguments.toArray(new String[0])).start();
        String URL = "http://localhost:" + server.getPort() + serviceName;
        String uploadURL = URL + "/upload";
        String validationURL = URL + "/shacl?graph=default";
        load(uploadURL, DIR + "/Data/PersonDataValid.ttl");
        String response = validate(validationURL, Paths.get(DIR + "/Data/PersonShape.ttl"));
        assertEquals(expectedPass, response, "Unexpected SHACL validation result");
    }

    @Test
    void shacl_validation_fail() throws Exception {
        List<String> arguments = List.of("--conf",DIR + "/config-shacl.ttl");
        server = construct(arguments.toArray(new String[0])).start();
        String URL = "http://localhost:" + server.getPort() + serviceName;
        String uploadURL = URL + "/upload";
        String validationURL = URL + "/shacl?graph=default";
        load(uploadURL, DIR + "/Data/PersonDataInvalid.ttl");
        String response = validate(validationURL, Paths.get(DIR + "/Data/PersonShape.ttl"));
        assertEquals(expectedFail, response, "Unexpected SHACL validation result");
    }

    private static void load(String uploadURL, String filename) {
        DSP.service(uploadURL).POST(filename);
    }

    private static String validate(String validationURL, Path shaclPath) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(validationURL))
                    .header("Content-type","text/turtle")
                    .POST(HttpRequest.BodyPublishers.ofFile(shaclPath))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        }
    }

}
