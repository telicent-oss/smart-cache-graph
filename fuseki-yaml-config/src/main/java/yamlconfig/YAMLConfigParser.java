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

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.*;

import static java.util.Collections.emptyMap;
import static yamlconfig.ConfigConstants.*;


/** Contains all the logic necessary to parse Yaml config files to ConfigStruct objects, called by {@link #runYAMLParser(String)} . */
public class YAMLConfigParser {

    /** Takes the path to the YAML config file and returns a ConfigStruct object. Throws a RuntimeException.*/
    public ConfigStruct runYAMLParser(String path) throws RuntimeException{
        try {
            Map<String, Object> map = parseYAMLConfigToMap(path);
            ConfigStruct config = mapToConfigStruct(map);
            validateConfigStruct(config);
            return config;
        }
        catch (UncheckedIOException | IllegalArgumentException | ClassCastException | NullPointerException ex) {
            throw new RuntimeException(ex);
        }
    }

    /** Parse a YAML config file to a map using SnakeYaml. */
    public Map<String, Object> parseYAMLConfigToMap(String path) throws UncheckedIOException {
        Yaml yaml = new Yaml();
        try ( InputStream inputStream = new FileInputStream(path) ) {
            return yaml.load(inputStream);
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    /** Parse the map created from the YAML config file to a ConfigStruct. */
    public ConfigStruct mapToConfigStruct(Map<String, Object> map) {
        ConfigStruct configStruct = new ConfigStruct();
        if (map.containsKey(version)) {
            configStruct.setVersion(map.get(version).toString());
        } else {
            throw new IllegalArgumentException("'version' field is missing");
        }
        parsePrefixes(map, configStruct);
        parseServer(map, configStruct);
        parseServices(map,configStruct);
        parseDatabases(map, configStruct);
        parseConnectors(map, configStruct);

        return configStruct;
    }

    public void parsePrefixes(Map<String, Object> map, ConfigStruct configStruct) {
        if (map.containsKey(prefixes)) {
            Object prefixesObj = map.get(prefixes);
            List<Object> prefixesList = castToList("Prefixes", prefixesObj);
            Map<String, String> prefixesMap = new HashMap<>();
            for (Object entry : prefixesList) {
                Map<String, Object> prefixMap = castToMapObject("PrefixPair", entry);
                if(findString(prefixMap, "prefix", null) == null || findString(prefixMap, "prefix", null).isEmpty())
                    throw new IllegalArgumentException("The namespace " + findString(prefixMap, "namespace", "") + " has no prefix assigned.");
                if(findString(prefixMap, "namespace", null) == null || findString(prefixMap, "namespace", null).isEmpty())
                    throw new IllegalArgumentException("The prefix " + findString(prefixMap, "prefix", "") + " has no namespace assigned.");
                if(!prefixesMap.containsKey(findString(prefixMap, "prefix", "")))
                    prefixesMap.put(findString(prefixMap, "prefix", ""), findString(prefixMap, "namespace", ""));
                else
                    throw new IllegalArgumentException("The prefix " + findString(prefixMap, "prefix", "") + " is already assigned to a different uri.");
            }
            configStruct.setPrefixes(prefixesMap);
            configStruct.validatePrefixes();
        }
    }

    public void parseServer(Map<String, Object> map, ConfigStruct configStruct) {
        if (map.containsKey(server)) {
            Object serverObj = map.get(server);
            Map<String, Object> serverMap = castToMapObject("Server", serverObj);
            String name = findString(serverMap, ConfigConstants.name, "");
            Map<String, String> settings = findMapString(serverMap, ConfigConstants.settings, emptyMap());

            // name - required, settings - optional
            if(name == null || name.isEmpty())
                throw new IllegalArgumentException("No name for server");
            Server server = new Server(name, settings);
            configStruct.setServer(server);
        } else {
            throw new IllegalArgumentException("'server' field is missing");
        }
    }

    public void parseEndpoints(Map.Entry<String, Object> field, List<Endpoint> endpoints, String name) {
        List<Object> endpointsList = castToList("Endpoints", field.getValue());
        if (endpointsList == null) {
            log.warn("No endpoints for service {}", name);
        }
        else {
            for (Object endpoint : endpointsList) {
                Map<String, Object> endpointMap = castToMapObject("Endpoint", endpoint);
                String endpointName = findString(endpointMap, ConfigConstants.name, "");
                String operation = findString(endpointMap, ConfigConstants.operation, "");
                Map<String, String> settings = findMapString(endpointMap, ConfigConstants.settings, emptyMap());

                // name - optional, operation - required, settings - optional
                if (operation == null || operation.isEmpty())
                    throw new IllegalArgumentException("No operation defined for endpoint " + endpointName);
                Endpoint tempEndpoint = new Endpoint(endpointName, operation, settings);
                endpoints.add(tempEndpoint);
            }
        }
    }

    public void parseServices(Map<String, Object> map, ConfigStruct configStruct) {
        if (map.containsKey(services)) {
            Object servicesObj = map.get(services);
            if (servicesObj == null) {
                log.warn("No services defined");
            }
            else {
                List<Object> servicesList = castToList("Services", servicesObj);
                List<Service> services = new ArrayList<>();
                for (Object entry : servicesList) {
                    Map<String, Object> serviceMap = castToMapObject("Service", entry);
                    String name = findString(serviceMap, ConfigConstants.name, "");
                    List<Endpoint> endpoints = new ArrayList<>();
                    for (Map.Entry<String, Object> field : serviceMap.entrySet()) {
                        if (field.getKey().equals(ConfigConstants.endpoints))
                            parseEndpoints(field, endpoints, name);
                    }
                    String database = findString(serviceMap, ConfigConstants.database, "");

                    // name required, endpoints required, but can be empty, database required
                    if (name == null || name.isEmpty())
                        throw new IllegalArgumentException("No name defined for service");
                    if (database == null || database.isEmpty())
                        throw new IllegalArgumentException("No database for service " + name);

                    Service serviceTemp = new Service(name, endpoints, database);
                    services.add(serviceTemp);
                }
                configStruct.setServices(services);
            }
        } else {
            throw new IllegalArgumentException("'services' field is missing");
        }
    }

    public void parseDatabases(Map<String, Object> map, ConfigStruct configStruct) {
        if (map.containsKey(databases)) {
            List<Database> databases = new ArrayList<>();
            Object databasesObj = map.get(ConfigConstants.databases);
            List<Object> databasesList = castToList("Databases", databasesObj);
            if (databasesList == null || databasesList.isEmpty())
                throw new NullPointerException("No databases defined");
            for (Object database : databasesList) {
                Map<String, Object> databaseMap = castToMapObject("Database", database);
                String databaseName = findString(databaseMap, name, "");
                String dbtype = findString(databaseMap, ConfigConstants.dbtype, "");
                String attributes = findString(databaseMap, ConfigConstants.attributes, "");
                String attributesURL = findString(databaseMap, ConfigConstants.attributesURL, "");
                String labels = findString(databaseMap, ConfigConstants.labels, "");
                String labelsStore = findString(databaseMap, ConfigConstants.labelsStore, "");
                String tripleDefaultLabels = findString(databaseMap, ConfigConstants.tdl, "");
                String data = findString(databaseMap, ConfigConstants.data, "");
                String location = findString(databaseMap, ConfigConstants.location, "");
                String dataset = findString(databaseMap, ConfigConstants.dataset, "");
                String cache = findString(databaseMap, ConfigConstants.cache, "false");
                String attributeCacheSize = findString(databaseMap, ConfigConstants.attributeCacheSize, "");
                String attributeCacheExpiryTime = findString(databaseMap, ConfigConstants.attributeCacheExpiryTime, "");
                String hierarchiesURL = findString(databaseMap, ConfigConstants.hierarchiesURL, "");
                String hierarchyCacheSize = findString(databaseMap, ConfigConstants.hierarchyCacheSize, "");
                String hierarchyCacheExpiryTime = findString(databaseMap, ConfigConstants.hierarchyCacheExpiryTime, "");
                Map<String, String> settings = findMapString(databaseMap, ConfigConstants.settings, emptyMap());

                // name - required, dbtype - required, data - optional, location - required if TDB2, settings - optional
                if(databaseName == null || databaseName.isEmpty())
                    throw new IllegalArgumentException("No database name");
                if(dbtype == null || dbtype.isEmpty())
                    throw new IllegalArgumentException("No database type for " + databaseName);
                if((location == null || location.isEmpty()) && dbtype.equals(TDB2))
                    throw new IllegalArgumentException("TDB2 database " + databaseName + " is missing location");

                if(dbtype.equals(ABAC)) {
                    if(dataset == null || dataset.isEmpty())
                        throw new IllegalArgumentException("ABAC database " + databaseName + " is missing dataset");
                    if(attributes == null || attributes.isEmpty()) {
                        if (attributesURL == null || attributesURL.isEmpty())
                            throw new IllegalArgumentException("ABAC database " + databaseName + " is missing attribute store");
                    }
                    else if(attributesURL != null && !attributesURL.isEmpty())
                            throw new IllegalArgumentException("Both an in-memory and remote attribute store were specified for ABAC database \"" + databaseName + "\". Only one is permitted.");
                    if((labels != null && !labels.isEmpty()) && (labelsStore != null && !labelsStore.isEmpty()))
                        throw new IllegalArgumentException("Both a labels file and a labels store were specified for ABAC database \"" + databaseName + "\". Only one is permitted.");
                    if(!cache.equals("true") && !cache.equals("false"))
                        throw new IllegalArgumentException("The value of \"cache\" on the database \"" + databaseName + "\" is \"" + cache + "\", which is not a boolean.");
                    if(!attributeCacheSize.isEmpty() && !XSDDatatype.XSDduration.isValid(attributeCacheExpiryTime))
                        throw new IllegalArgumentException("The value of \"attributeCacheExpiryTime\" on the database \"" + databaseName + "\" is \"" + attributeCacheExpiryTime + "\", which is not a valid time.");
                    if(!hierarchyCacheSize.isEmpty() && !XSDDatatype.XSDduration.isValid(hierarchyCacheExpiryTime))
                        throw new IllegalArgumentException("The value of \"hierarchyCacheExpiryTime\" is \"" + hierarchyCacheExpiryTime + "\" which is not a valid time.");
                }
                Database tempDatabase = new Database(databaseName, dbtype, attributes, attributesURL, labels, labelsStore, tripleDefaultLabels, data, location, dataset, cache, attributeCacheSize, attributeCacheExpiryTime, hierarchiesURL, hierarchyCacheSize, hierarchyCacheExpiryTime, settings);
                databases.add(tempDatabase);
            }
            configStruct.setDatabases(databases);
        } else {
            throw new IllegalArgumentException("'databases' field is missing");
        }
    }

    public void parseConnectors(Map<String, Object> map, ConfigStruct configStruct) {
        if (map.containsKey(connectors)) {
            List<Connector> connectors = new ArrayList<>();
            Object connectorsObj = map.get(ConfigConstants.connectors);
            List<Object> connectorsList = castToList("Connectors", connectorsObj);
            if (connectorsList != null && !connectorsList.isEmpty()) {
                for (Object connector : connectorsList) {
                    Map<String, Object> connectorMap = castToMapObject("Connector", connector);
                    String fusekiServiceName = findString(connectorMap, fusekiService, "");
                    String topic = findString(connectorMap, ConfigConstants.topic, "");
                    String bootstrapServers = findString(connectorMap, ConfigConstants.bootstrapServers, "");
                    String stateFile = findString(connectorMap, ConfigConstants.stateFile, "");
                    String groupId = findString(connectorMap, ConfigConstants.groupId, "");
                    String replayTopic = findString(connectorMap, ConfigConstants.replayTopic, "false");
                    String syncTopic = findString(connectorMap, ConfigConstants.syncTopic, "false");
                    Map<String, String> config = findMapString(connectorMap, ConfigConstants.config, emptyMap());

                    // fusekiServiceName - required, topic - required, bootstrapServers - required, stateFile - required
                    if (fusekiServiceName == null || fusekiServiceName.isEmpty())
                        throw new IllegalArgumentException("No destination Fuseki service name.");
                    if (topic == null || topic.isEmpty())
                        throw new IllegalArgumentException("Missing topic on the \"" + fusekiServiceName + "\" service connector.");
                    if (bootstrapServers == null || bootstrapServers.isEmpty())
                        throw new IllegalArgumentException("The \"bootstrapSevers\" field is empty on the \"" + fusekiServiceName + "\" service connector.");
                    if (stateFile == null || stateFile.isEmpty())
                        throw new IllegalArgumentException("The \"stateFile\" field is empty on the \"" + fusekiServiceName + "\" service connector.");
                    if(!replayTopic.equals("true") && !replayTopic.equals("false"))
                        throw new IllegalArgumentException("The value of \"replayTopic\" on the \"" + fusekiServiceName + "\" service connector is \"" + replayTopic + "\", which is not a boolean.");
                    if(!syncTopic.equals("true") && !syncTopic.equals("false"))
                        throw new IllegalArgumentException("The value of \"syncTopic\" on the \"" + fusekiServiceName + "\" service connector is \"" + syncTopic + "\", which is not a boolean.");
                    Connector tempConnector = new Connector(fusekiServiceName, topic, bootstrapServers, stateFile, groupId, replayTopic, syncTopic, config);
                    connectors.add(tempConnector);
                }
                configStruct.setConnectors(connectors);
            }
        }
    }

    public void validateConfigStruct(ConfigStruct configStruct) {
        if (!configStruct.checkDatabaseMismatch())
            throw new IllegalArgumentException("Mismatch between the databases referenced in services and the existing databases.");
        if (!configStruct.checkConnectorMismatch())
            throw new IllegalArgumentException("Mismatch between existing services and the destination services of the connectors.");
        try {
            configStruct.checkEndpointOperations();
            configStruct.checkDatabaseTypes();
            configStruct.checkForLabelsInABACtdb2();
        }
        catch (RuntimeException ex) {
            throw new IllegalArgumentException(ex.getMessage());
        }
    }

    private ArrayList<Object> castToList(String name, Object object) {
        ArrayList<Object> list;
        try {
            list = (ArrayList<Object>) object;
        }
        catch (ClassCastException ex) {
            throw new ClassCastException(name + " cannot be parsed to a List, found " + object.getClass().getCanonicalName());
        }
        return list;
    }


    private LinkedHashMap<String, String> castToMapString(String name, Object object) {
        LinkedHashMap<String, String> map;
        try {
            map = (LinkedHashMap<String, String>) object;
        }
        catch (ClassCastException ex) {
            throw new ClassCastException(name + " cannot be parsed to a Map, found " + object.getClass().getCanonicalName());
        }
        return map;
    }

    private LinkedHashMap<String, Object> castToMapObject(String name, Object object) {
        LinkedHashMap<String, Object> map;
        try {
            map = (LinkedHashMap<String, Object>) object;
        }
        catch (ClassCastException ex) {
            throw new ClassCastException(name + " cannot be parsed to a Map, found " + object.getClass().getCanonicalName());
        }
        return map;
    }

    public String findString(Map<String, Object> map, String property, String defaultValue) {
        if (map.containsKey(property)) {
            Object rawValue = map.get(property);
            return rawValue != null ? rawValue.toString() : defaultValue;
        }
        return defaultValue;
    }

    public Map<String, String> findMapString(Map<String, Object> map, String property, Map<String, String> defaultValue) {
        if (map.containsKey(property)) {
            Map<String, String> rawValue = castToMapString(property, map.get(property));
            return rawValue != null ? rawValue : defaultValue;
        }
        return defaultValue;
    }
}
