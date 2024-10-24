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

package dev;

import io.telicent.jena.abac.core.Attributes;
import io.telicent.jena.abac.core.AttributesStore;
import io.telicent.jena.abac.fuseki.FMod_ABAC;
import io.telicent.jena.abac.services.AttributeService;
import io.telicent.jena.abac.services.SimpleAttributesStore;
import org.apache.jena.atlas.lib.FileOps;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiModule;
import org.apache.jena.fuseki.main.sys.FusekiModules;
import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.sparql.exec.RowSetOps;
import org.apache.jena.sparql.exec.RowSetRewindable;
import org.apache.jena.sparql.exec.http.DSP;
import org.apache.jena.sparql.exec.http.QueryExecHTTPBuilder;
import org.apache.jena.sys.JenaSystem;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import yamlconfig.ConfigStruct;
import yamlconfig.RDFConfigGenerator;
import yamlconfig.YAMLConfigParser;

import java.io.FileOutputStream;
import java.io.IOException;

import static io.telicent.jena.abac.services.LibAuthService.serviceURL;
import static yamlconfig.ConfigConstants.log;

public class Dev {
    private static final String DIR = "src/main/files/";
    private static final String serviceName = "/ds";

    public static void main(String ...args) {
        JenaSystem.init();
        Configurator.setAllLevels(log.getName(), Level.getLevel("info"));

        Graph g = RDFParser.source(DIR+"abac/attribute-store.ttl").toGraph();
        AttributesStore attrStore = Attributes.buildStore(g);
        String mockServerURL = SimpleAttributesStore.run(0, attrStore);

        String lookupUserAttributesURL = serviceURL(mockServerURL, AttributeService.lookupUserAttributeTemplate);
        System.setProperty("USER_ATTRIBUTES_URL", lookupUserAttributesURL);

        YAMLConfigParser ycp = new YAMLConfigParser();
        RDFConfigGenerator rcg = new RDFConfigGenerator();
        try {
            ConfigStruct config2 = ycp.runYAMLParser("src/main/files/config-abac-tdb2.yaml");
            Model model2 = rcg.createRDFModel(config2);
            model2.write(System.out, "TTL");
            try (FileOutputStream out = new FileOutputStream("src/main/files/config.ttl")) {
                model2.write(out, "TTL");
            } catch (IOException e) {
                log.error(e.getMessage());
            }
            log.info(config2.toString());
            FusekiServer server2 = server("config.ttl");
            server2.start();
            log.info("Server started");
            load(server2);
            query(server2, "u1"); // 3
            query(server2, "u2"); // 2
            server2.stop();
        }
        catch (RuntimeException ex) {
            log.error(ex.getMessage());
        }
        System.exit(0);

    }

    private static void load(FusekiServer server) {
        String URL = "http://localhost:" + server.getPort() + serviceName;
        String uploadURL = URL + "/upload";
        load(uploadURL, DIR + "abac/data-and-labels.trig");
    }

    private static void query(FusekiServer server, String user) {
        String URL = "http://localhost:" + server.getPort() + serviceName;
        query(URL, user);
    }

    private static void load(String uploadURL, String filename) {
        DSP.service(uploadURL).POST(filename);
    }

    private static void query(String url, String user) {
        String queryString = "SELECT * { ?s ?p ?o }";
        query(url, user, queryString);
    }

    private static void query(String url, String user, String queryString) {
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
    }

    private static FusekiServer server(String config) {
        FusekiModule fmod = new FMod_ABAC();
        FusekiModules mods = FusekiModules.create(fmod);
        return FusekiServer.create().port(3131)
                .fusekiModules(mods)
                .parseConfigFile(FileOps.concatPaths(DIR, config))
                .build();
    }
}
