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

    private static final Path DIR = Path.of("src/test/files");
    private FusekiServer server;
    private static final String SERVICE_NAME = "/ds";
    private HttpClient httpClient;

    private static final String EXPECTED_PASS = """
            PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX sh:   <http://www.w3.org/ns/shacl#>
            PREFIX xsd:  <http://www.w3.org/2001/XMLSchema#>
            
            [ rdf:type     sh:ValidationReport;
              sh:conforms  true
            ] .
            """;

    private static final String EXPECTED_FAIL = """
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
        httpClient = HttpClient.newHttpClient();
    }

    @AfterEach
    void clearDown() {
        if (null != server) {
            server.stop();
        }
        Configurator.reset();
    }

    /**
     * In this test the data conforms with the SHACL shape and therefore the validation should pass
     */
    @Test
    void shacl_validation_pass() throws Exception {
        startServer();
        Path validDataPath = DIR.resolve("Data/PersonDataValid.ttl");
        loadData(validDataPath);
        String response = validateData(Paths.get(DIR + "/Data/PersonShape.ttl"));
        assertEquals(EXPECTED_PASS, response, "Unexpected SHACL validation result");
    }

    /**
     * In this test the data does not conform to the SHACL shape due to the ies:hasName property pointing to a literal
     * string instead of another resource
     */
    @Test
    void shacl_validation_fail() throws Exception {
        startServer();
        Path invalidDataPath = DIR.resolve("Data/PersonDataInvalid.ttl");
        loadData(invalidDataPath);
        String response = validateData(Paths.get(DIR + "/Data/PersonShape.ttl"));
        assertEquals(EXPECTED_FAIL, response, "Unexpected SHACL validation result");
    }

    /**
     * Returns the base URL of the running SCG
     */
    private String getBaseURL() {
        return "http://localhost:" + server.getPort() + SERVICE_NAME;
    }

    /**
     * Calls the upload endpoint passing in the data file for loading
     */
    private void loadData(Path dataFilePath) {
        String uploadURL = getBaseURL() + "/upload";
        DSP.service(uploadURL).POST(dataFilePath.toString());
    }

    /**
     * Calls the SHACL endpoint passing the provided shape file and returning the validation result
     */
    private String validateData(Path shaclShapePath) throws Exception {
        String validationURL = getBaseURL() + "/shacl?graph=default";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(validationURL))
                .header("Content-type", "text/turtle")
                .POST(HttpRequest.BodyPublishers.ofFile(shaclShapePath))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    /**
     * Starts SCG with SHACL endpoint configuration
     */
    private void startServer() {
        List<String> arguments = List.of("--conf", DIR + "/config-shacl.ttl");
        server = construct(arguments.toArray(new String[0])).start();
    }

}
