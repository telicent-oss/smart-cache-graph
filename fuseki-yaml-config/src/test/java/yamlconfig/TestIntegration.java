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

import org.apache.jena.atlas.lib.FileOps;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.system.FusekiLogging;
import org.apache.jena.http.HttpOp;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.WebContent;
import org.apache.jena.sys.JenaSystem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.FileOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static yamlconfig.ConfigConstants.*;

/** Tests for servers without ABAC databases. */
public class TestIntegration {
    YAMLConfigParser ycp = new YAMLConfigParser();
    RDFConfigGenerator rcg = new RDFConfigGenerator();

    public final String url = "http://localhost:3131/ds/";
    public final String update = "data-update";
    public final String query = "sparql?query=";
    public final String expectedResponse = """
                                <?xml version="1.0"?>
                                <sparql xmlns="http://www.w3.org/2005/sparql-results#">
                                  <head>
                                    <variable name="character"/>
                                  </head>
                                  <results>
                                    <result>
                                      <binding name="character">
                                        <uri>https://looneytunes-graph.com/New_Character</uri>
                                      </binding>
                                    </result>
                                  </results>
                                </sparql>
                                """;
    public final String sparqlQuery = """
                PREFIX : <https://looneytunes-graph.com/>
                PREFIX mfg: <http://myfavs-graph.com/>
                SELECT ?character
                WHERE {
                     ?character a :Looney_Tunes_Character ;
                                :name "New Character" .
                }""";

    public final String sparqlUpdate = """
                    PREFIX rdf:      <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                    PREFIX xsd:      <http://www.w3.org/2001/XMLSchema#>
                    PREFIX : <https://looneytunes-graph.com/>
                    INSERT DATA {
                    :New_Character a :Looney_Tunes_Character ;
                        :name "New Character" ;
                        :species "Hare" ;
                        :gender "Male" ;
                        :made_debut_appearance_in :A_Wild_Hare ;
                        :created_by :Tex_Avery ;
                        :personality_trait "Cunning" ;
                        :personality_trait "Smart" ;
                        :personality_trait "Charismatic" ;
                        :known_for_catchphrase "What's up, doc?" .
                   }""";

    @BeforeAll
    public static void before() {
        FileOps.ensureDir("target/files");
        FileOps.ensureDir("target/files/rdf");
        JenaSystem.init();
    }
    static {FusekiLogging.setLogging();}


    @Test
    public void inMemoryDatabaseTest() {
        ConfigStruct config = ycp.runYAMLParser("src/test/files/yaml/correct/config-tim.yaml");
        Model model = rcg.createRDFModel(config);
        try (FileOutputStream out = new FileOutputStream("target/files/rdf/config-test-1.ttl")) {
            model.write(out, "TTL");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
        FusekiServer server =
                FusekiServer.create().port(3131)
                        .parseConfigFile("target/files/rdf/config-test-1.ttl")
                        .build()
                        .start();

        HttpOp.httpPost(url + update, WebContent.contentTypeSPARQLUpdate, sparqlUpdate);
        String encodedQuery = java.net.URLEncoder.encode(sparqlQuery, java.nio.charset.StandardCharsets.UTF_8);
        String actualResponse = HttpOp.httpGetString(url + query + encodedQuery);

        server.stop();
        assertEquals(expectedResponse, actualResponse);
    }

    @Test
    public void tdb2DatabaseTest() {
        ConfigStruct config = ycp.runYAMLParser("src/test/files/yaml/correct/config-tdb2.yaml");
        Model model = rcg.createRDFModel(config);
        try (FileOutputStream out = new FileOutputStream("target/files/rdf/config-test-2.ttl")) {
            model.write(out, "TTL");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
        FusekiServer server =
                FusekiServer.create().port(3131)
                        .parseConfigFile("target/files/rdf/config-test-2.ttl")
                        .build()
                        .start();

        HttpOp.httpPost(url + update, WebContent.contentTypeSPARQLUpdate, sparqlUpdate);
        String encodedQuery = java.net.URLEncoder.encode(sparqlQuery, java.nio.charset.StandardCharsets.UTF_8);
        String actualResponse = HttpOp.httpGetString(url + query + encodedQuery);

        server.stop();
        assertEquals(expectedResponse, actualResponse);
    }

    @Test
    public void multipleServicesAndDatabasesTest() {
        ConfigStruct config = ycp.runYAMLParser("src/test/files/yaml/correct/config-nocomment.yaml");
        Model model = rcg.createRDFModel(config);
        try (FileOutputStream out = new FileOutputStream("target/files/rdf/config-test-3.ttl")) {
            model.write(out, "TTL");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
        FusekiServer server =
                FusekiServer.create().port(3131)
                        .parseConfigFile("target/files/rdf/config-test-3.ttl")
                        .build()
                        .start();

        HttpOp.httpPost(url + update, WebContent.contentTypeSPARQLUpdate, sparqlUpdate);
        String encodedQuery = java.net.URLEncoder.encode(sparqlQuery, java.nio.charset.StandardCharsets.UTF_8);
        String actualResponse = HttpOp.httpGetString(url + query + encodedQuery);

        server.stop();
        assertEquals(expectedResponse, actualResponse);
    }

}
