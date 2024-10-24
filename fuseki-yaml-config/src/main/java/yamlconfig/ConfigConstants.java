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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ConfigConstants {
    public final static Logger log = LoggerFactory.getLogger("fuseki-yaml-config");
    public static final Pattern prefixRegex = Pattern.compile("\\p{Alpha}([\\w.-]*\\w)?");
    public static final Pattern prefixedField = Pattern.compile("^([^:]+):([^:]+)$");

    // database types
    public final static String TIM = "TIM";
    public final  static String TDB2 = "TDB2";
    public final  static String ABAC = "ABAC";

    // URIs
    public static final String NS =  "#";
    public static final String FUSEKI_NS = "http://jena.apache.org/fuseki#";
    public static final String RDF_NS = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
    public static final String JA_NS = "http://jena.hpl.hp.com/2005/11/Assembler#";
    public static final String TDB2_NS = "http://jena.apache.org/2016/tdb#";
    public static final String AUTHZ_NS = "http://telicent.io/security#";
    public static final String FK_NS = "http://jena.apache.org/fuseki/kafka#";

    // keys for parsing Yaml configs
    public static final String version = "version";
    public static final String name = "name";
    public static final String settings = "settings";
    public static final String operation = "operation";
    public static final String prefixes = "prefixes";
    public static final String server = "server";
    public static final String services = "services";
    public static final String endpoints = "endpoints";
    public static final String database = "database";
    public static final String databases = "databases";
    public static final String connectors = "connectors";
    public static final String connector = "connector";

    // keys for parsing databases
    public static final String dbtype = "dbtype";
    public static final String data = "data";
    public static final String location = "location";
    public static final String dataset = "dataset";
    public static final String attributes = "attributes";
    public static final String attributesURL = "attributes-url";
    public static final String labels = "labels";
    public static final String labelsStore = "labels-store";
    public static final String tdl = "triple-default-labels";
    public static final String cache = "cache";
    public static final String attributeCacheSize = "attribute-cache-size";
    public static final String attributeCacheExpiryTime = "attribute-cache-expiry-time";
    public static final String hierarchyCacheSize = "hierarchy-cache-size";
    public static final String hierarchyCacheExpiryTime = "hierarchy-cache-expiry-time";
    public static final String hierarchiesURL = "hierarchies-url";

    // keys for parsing connectors
    public static final String fusekiService = "fuseki-service";
    public static final String bootstrapServers = "bootstrap-servers";
    public static final String topic = "topic";
    public static final String stateFile = "state-file";
    public static final String groupId = "group-id";
    public static final String replayTopic = "replay-topic";
    public static final String syncTopic = "sync-topic";
    public static final String config = "config";

    public static Boolean isPrefixed(String field) {
        Matcher matcher = prefixedField.matcher(field);
        return matcher.matches();
    }
}
