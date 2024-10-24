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

import org.apache.jena.fuseki.server.Operation;
import org.apache.jena.fuseki.server.OperationRegistry;
import org.apache.jena.irix.IRIx;

import java.util.*;
import java.util.regex.Matcher;

import static yamlconfig.ConfigConstants.*;


/** ConfigStruct is the Java data structure the Yaml file gets parsed to by the YAMLConfigParser.
 It is then used to generate the RDF config file by RDFConfigGenerator. */
public class ConfigStruct {
    private String version;
    private Map<String, String> prefixes;
    private Server server;
    private List<Service> services;
    private List<Database> databases;
    private List<Connector> connectors;

    public String getVersion() {
        return version;
    }

    public Map<String, String> getPrefixes() { return prefixes; }

    public void setPrefixes(Map<String, String> prefixes) { this.prefixes = prefixes; }

    public void setVersion(String version) {
        this.version = version;
    }

    public Server getServer() {
        return server;
    }

    public void setServer(Server server) {
        this.server = server;
    }

    public List<Service> getServices() {
        return services;
    }

    public void setServices(List<Service> services) {
        this.services = services;
    }

    public List<Database> getDatabases() {
        return databases;
    }

    public void setDatabases(List<Database> databases) {
        this.databases = databases;
    }

    @Override
    public String toString() {
       String config = "";
       config = config + "version: " + getVersion() + "\n";
       config = config + "prefixes: " + "\n";
        if(prefixes != null) {
            for (Map.Entry<String, String> entry : getPrefixes().entrySet()) {
                String prefix = entry.getKey();
                String uri = entry.getValue();
                config = config + "prefix: " + prefix + ", namespace: " + uri + "\n";
            }
        }
       config = config + "server: " + this.server.toString() + "\n";

        config += "services: " + "\n";
        if(services != null) {
            for (Service service : getServices()) {
                config = config + service.toString() + "\n";
            }
        }
        config += "\n" + "databases: ";
        for(Database database : getDatabases()) {
            config = config + database.toString() + "\n";
        }
        if(connectors != null) {
            config += "\n" + "connectors: ";
            for (Connector connector : getConnectors()) {
                config = config + connector.toString() + "\n";
            }
        }
       return config;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfigStruct that = (ConfigStruct) o;
        return Objects.equals(version, that.version) &&
                Objects.equals(server, that.server) && Objects.equals(services, that.services) && Objects.equals(databases, that.databases) && Objects.equals(connectors, that.connectors);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, server, services, databases, connectors);
    }

    public boolean checkDatabaseMismatch() {
        Set<String> databasesFromServices = new HashSet<>();
        for (Service service : this.services) {
            databasesFromServices.add(service.database());
        }
        //look for duplicate databases
        Set<String> databasesFromDatabases = new HashSet<>();
        for (Database db : this.databases) {
            if (!databasesFromDatabases.add(db.name())) {
                return false;
            }
        }
        List<Database> abacDatabases = databases.stream()
                .filter(db -> ConfigConstants.ABAC.equals(db.dbType()))
                .toList();
        for(Database db : abacDatabases) {
            if(!databasesFromDatabases.contains(db.dataset()))
                return false;
            databasesFromDatabases.remove(db.dataset());
        }
        return  databasesFromServices.equals(databasesFromDatabases);
    }

    public boolean checkConnectorMismatch() {
        if(this.connectors != null) {
            Set<String> servicesFromConnectors = new HashSet<>();
            for (Connector connector : this.connectors) {
                if (!servicesFromConnectors.add(connector.fusekiServiceName()))
                    return false;
            }
            Set<String> servicesFromServices = new HashSet<>();
            for (Service service : this.services) {
                if (!servicesFromServices.add("/" + service.name())) {
                    return false;
                }
            }
            for (String service : servicesFromConnectors) {
                if (!servicesFromServices.contains(service))
                    return false;
            }
        }
        return true;
    }

    public void checkEndpointOperations() {
        OperationRegistry operationRegistry = OperationRegistry.createStd();
        for (Service service : this.services) {
            for (Endpoint endpoint : service.endpoints()) {
                Operation operation;
                Matcher matcher = prefixedField.matcher(endpoint.operation());

                if (matcher.matches()) {
                    String prefix = matcher.group(1);
                    String op = matcher.group(2);
                    String uri = getPrefixes().get(prefix);
                    if (uri == null)
                        throw new RuntimeException("Prefix " + prefix + " is undefined");
                    operation = Operation.alloc(uri + op, op, "");
                }
                else
                    operation = Operation.alloc(ConfigConstants.FUSEKI_NS + endpoint.operation(), endpoint.operation(), "");
                if(!operationRegistry.isRegistered(operation))
                    throw new RuntimeException("Operation " + endpoint.operation() + " does not exist in the OperationRegistry");
            }
        }
    }

    public void checkDatabaseTypes() {
        for (Database database : this.databases) {
            if (!(database.dbType().equals(ConfigConstants.TIM)) && !(database.dbType().equals(ConfigConstants.TDB2)) && !(database.dbType().equals(ConfigConstants.ABAC)))
                throw new IllegalArgumentException("Unsupported database type " + database.dbType() + " for database " + database.name());
            if(database.settings() != null)
                if(database.settings().get("tdb2:unionDefaultGraph") != null)
                    if (!(String.valueOf(database.settings().get("tdb2:unionDefaultGraph")).equals("true")) && !(String.valueOf(database.settings().get("tdb2:unionDefaultGraph")).equals("false")))
                        throw new IllegalArgumentException("The value of 'tdb2:unionDefaultGraph' in " + database.name() + " settings is not a boolean");
        }
    }

    public void validatePrefixes() {
        if (prefixes != null) {
            log.info("getPrefixes not null");
            for (Map.Entry<String, String> entry : getPrefixes().entrySet()) {
                if (!prefixRegex.matcher(entry.getKey()).matches())
                    throw new IllegalArgumentException("Prefix " + entry.getKey() + " is not a valid prefix.");
                try {
                    IRIx iri = IRIx.create(entry.getValue());
                }
                catch (Exception ex) {
                    throw new IllegalArgumentException("Namespace " + entry.getValue() + " is not a valid URI.");
                }
            }
        }
    }

    public void checkForLabelsInABACtdb2() {
        for (Database database : databases) {
            if (database.dbType().equals(ABAC)) {
                Optional<Database> result = databases.stream()
                        .filter(databaseUnder -> databaseUnder.name().equals(database.dataset()))
                        .findFirst();
                if (result.isPresent()) {
                    if (result.get().dbType().equals(TDB2) && database.labels().isEmpty() && database.labelsStore().isEmpty())
                        log.warn("ABAC TDB2 database \"" + database.name() + "\" is missing a labelsStore or a labels file.");
                }
            }
        }
    }

    public List<Connector> getConnectors() {
        return connectors;
    }

    public void setConnectors(List<Connector> connectors) {
        this.connectors = connectors;
    }
}

