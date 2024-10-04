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
import org.apache.jena.cmd.CmdException;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.system.FusekiLogging;
import org.apache.jena.http.HttpOp;
import org.apache.jena.riot.SysRIOT;
import org.apache.jena.riot.WebContent;
import org.apache.jena.sparql.ARQException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.telicent.core.SmartCacheGraph.construct;
import static org.junit.jupiter.api.Assertions.*;

/*
* Test SCG usage of varying configuration parameters.
*/
class TestYamlConfigParser {

    private static final String DIR = "src/test/files";
    private FusekiServer server;

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
    void yaml_config() {
        // given
        List<String> arguments = List.of("--yaml-config", "--config",DIR + "/yaml/config-tim.yaml");
        String[] args = arguments.toArray(new String[0]);
        // when
        server = construct(arguments.toArray(new String[0])).start();
        int port = server.getPort();
        String url = "http://localhost:" + port + "/ds/";
        HttpOp.httpPost(url + update, WebContent.contentTypeSPARQLUpdate, sparqlUpdate);
        String encodedQuery = java.net.URLEncoder.encode(sparqlQuery, java.nio.charset.StandardCharsets.UTF_8);
        String actualResponse = HttpOp.httpGetString(url + query + encodedQuery);

        // then
        assertNotNull(server);
        server.stop();
        assertEquals(expectedResponse, actualResponse);
    }

}
