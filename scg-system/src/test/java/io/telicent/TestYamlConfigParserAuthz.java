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
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.system.FusekiLogging;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.*;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.exec.QueryExec;
import org.apache.jena.sparql.exec.RowSet;
import org.apache.jena.sparql.exec.RowSetOps;
import org.apache.jena.sparql.exec.RowSetRewindable;
import org.apache.jena.sparql.exec.http.DSP;
import org.apache.jena.sparql.exec.http.QueryExecHTTPBuilder;
import org.apache.jena.sparql.resultset.ResultSetCompare;
import org.junit.jupiter.api.*;

import java.util.List;

import static io.telicent.LibTestsSCG.*;
import static io.telicent.TestSmartCacheGraphIntegration.launchServer;
import static io.telicent.core.SmartCacheGraph.construct;
import static org.junit.jupiter.api.Assertions.*;

/*
* Test SCG usage of varying configuration parameters.
*/
class TestYamlConfigParserAuthz {

    private static final String DIR = "src/test/files";
    private FusekiServer server;

    private static final String serviceName = "/ds";
    public static RowSetRewindable expectedRSR;

    @BeforeAll
    public static void before() {
        Model comparisonModel = ModelFactory.createDefaultModel();
        String baseURI = "http://example/";
        Resource s1 = comparisonModel.createResource(baseURI + "s1");
        Property p1 = comparisonModel.createProperty(baseURI + "p1");
        Literal l1 = comparisonModel.createTypedLiteral(1234, XSDDatatype.XSDinteger);
        Resource s2 = comparisonModel.createResource(baseURI + "s");
        Property p2 = comparisonModel.createProperty(baseURI + "p2");
        Literal l2 = comparisonModel.createTypedLiteral(789, XSDDatatype.XSDinteger);
        comparisonModel.add(s1, p1, l1);
        comparisonModel.add(s2, p2, l2);

        String queryString = "SELECT ?s ?p ?o WHERE { ?s ?p ?o }";
        Query query = QueryFactory.create(queryString);
        QueryExec qExec = QueryExec.dataset(DatasetGraphFactory.create(comparisonModel.getGraph())).query(query).build();
        expectedRSR = qExec.select().rewindable();
    }



    @BeforeEach
    void setUp() throws Exception {
        FusekiLogging.setLogging();
        SysFusekiABAC.init();
        LibTestsSCG.setupAuthentication();
        LibTestsSCG.disableInitialCompaction();
    }

    @AfterEach
    void clearDown() throws Exception {
        if (null != server) {
            server.stop();
        }
        Configurator.reset();
        LibTestsSCG.teardownAuthentication();
    }


    @Test
    void yaml_config_abac() {
        // given
        List<String> arguments = List.of("--conf",DIR + "/yaml/config-abac-multiple.yaml");
        // when
        server = construct(arguments.toArray(new String[0])).start();
        RowSetRewindable actualResponseRSR;
        LibTestsSCG.uploadFile(server.serverURL() + serviceName + "/upload", DIR + "/yaml/data-and-labels.trig");//load(server);
        String validToken = tokenForUser("u1");
        actualResponseRSR = QueryExecHTTPBuilder.service(server.serverURL() + serviceName)
                .query("SELECT * { ?s ?p ?o }")
                .httpHeader(tokenHeader(),
                        tokenHeaderValue("u1"))
                .select().rewindable();
        //actualResponseRSR = LibTestsSCG.queryWithToken(server.serverURL() + serviceName, "SELECT * { ?s ?p ?o }", "u1").rewindable();//query(server, "u1");

        // then
        boolean equals = ResultSetCompare.isomorphic(expectedRSR, actualResponseRSR);
        assertTrue(equals);
    }

    @Test
    void yaml_config_abac_2() {
        // given
        List<String> arguments = List.of("--conf",DIR + "/yaml/config-abac-tim.yaml");
        // when
        server = construct(arguments.toArray(new String[0])).start();
        RowSetRewindable actualResponseRSR;
        LibTestsSCG.uploadFile(server.serverURL()  + serviceName + "/upload", DIR + "/yaml/data-and-labels.trig");//load(server);
        actualResponseRSR = LibTestsSCG.queryWithToken(server.serverURL() + serviceName, "SELECT * { ?s ?p ?o }", "u1").rewindable();//query(server, "u1");

        // then
        boolean equals = ResultSetCompare.isomorphic(expectedRSR, actualResponseRSR);
        assertTrue(equals);
    }

    @Test
    void givenValidToken_whenMakingARequest_thenSuccess() {
        server = launchServer("/yaml/config-abac-tim.ttl");
        // given
        String validToken = tokenForUser("u1");
        // when
        RowSet rowSet = QueryExecHTTPBuilder.service(server.serverURL() + serviceName)
                .query("SELECT * { ?s ?p ?o }")
                .httpHeader(tokenHeader(),
                        tokenHeaderValue(validToken))
                .select();
        // then
        assertNotNull(rowSet);
        assertEquals(0, RowSetOps.count(rowSet));
    }


    private static void load(FusekiServer server) {
        String URL = "http://localhost:" + server.getPort() + serviceName;
        String uploadURL = URL + "/upload";
        load(uploadURL, DIR + "/yaml/data-and-labels.trig");
    }

    private static RowSetRewindable query(FusekiServer server, String user) {
        String URL = "http://localhost:" + server.getPort() + serviceName;
        return query(URL, user);
    }

    private static void load(String uploadURL, String filename) {
        DSP.service(uploadURL).POST(filename);
    }

    private static RowSetRewindable query(String url, String user) {
        String queryString = "SELECT * { ?s ?p ?o }";
        return query(url, user, queryString);
    }

    private static RowSetRewindable query(String url, String user, String queryString) {
        System.out.println("Query: " + user);
        RowSetRewindable rowSet =
                QueryExecHTTPBuilder.service(url)
                        .query(queryString)
                        .httpHeader("Authorization", "Bearer user:" + user)
                        .select()
                        .rewindable();
        long x = RowSetOps.count(rowSet);
        System.out.printf("User = %s ; returned %d results\n", user, x);
        rowSet.reset();
        RowSetOps.out(System.out, rowSet);
        return rowSet;
    }
}
