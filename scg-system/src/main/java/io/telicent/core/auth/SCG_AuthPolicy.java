package io.telicent.core.auth;

import io.telicent.core.CQRS;
import io.telicent.jena.abac.fuseki.ServerABAC;
import io.telicent.jena.graphql.fuseki.SysGraphQL;
import io.telicent.servlet.auth.jwt.PathExclusion;
import io.telicent.smart.caches.configuration.auth.policy.Policy;
import io.telicent.smart.caches.configuration.auth.policy.PolicyKind;
import org.apache.commons.lang3.Strings;
import org.apache.jena.fuseki.Fuseki;
import org.apache.jena.fuseki.server.DataAccessPoint;
import org.apache.jena.fuseki.server.Endpoint;
import org.apache.jena.fuseki.server.Operation;

import java.util.Map;
import java.util.Set;

/**
 * Helper class for generating authorization policies from a dynamically configured SCG instance
 */
public class SCG_AuthPolicy {

    /**
     * Default roles policy applied to all recognised dataset endpoints
     */
    public static final Policy DEFAULT_ROLES = Policy.requireAny("roles", new String[] { "USER", "SUPER_USER" });
    public static final Policy ADMIN_ROLES = Policy.requireAny("roles", new String[] { "ADMIN_SYSTEM", "SUPER_USER" });

    public static final Policy BACKUP_READ_ONLY = Policy.requireAll("permissions", new String[] { "backup.read" });
    public static final Policy BACKUP_READ_WRITE =
            Policy.requireAll("permissions", new String[] { "backup.read", "backup.write" });
    public static final Policy COMPACT = Policy.requireAll("permissions", new String[] { "compact" });

    /**
     * Known Fuseki operations (either Core, or from Telicent modules) that are read-only operations
     */
    //@formatter:off
    public static final Set<Operation> READ_OPERATIONS =
            Set.of(Operation.Query,
                   Operation.GSP_R,
                   Operation.PREFIXES_R,
                   Operation.Shacl,
                   SysGraphQL.OP_GRAPHQL,
                   ServerABAC.Vocab.operationGetLabels,
                   ServerABAC.Vocab.operationGSPRLabels,
                   ServerABAC.Vocab.operationQueryLabels);
    //@formatter:on
    /**
     * Known Fuseki operations (either Core, or from Telicent modules) that are read/write operations
     */
    //@formatter:off
    public static final Set<Operation> READ_WRITE_OPERATIONS =
            Set.of(Operation.Update,
                   Operation.GSP_RW,
                   Operation.Upload,
                   Operation.Patch,
                   CQRS.Vocab.operationUpdateCQRS,
                   ServerABAC.Vocab.operationUploadABAC);
    //@formatter:on

    private SCG_AuthPolicy() {
    }

    /**
     * Generates the set of roles based authorization policies that should apply to the dataset represented by the given
     * data access point
     *
     * @param policies Policies to populate
     * @param dap      Data Access Point
     */
    public static void addDatasetRolesPolicy(Map<PathExclusion, Policy> policies, DataAccessPoint dap) {
        // Auto-generate policies based on configured endpoint operations
        addPolicies(policies, dap, SCG_AuthPolicy::rolesPolicyForOperation);

        // Also add policy for the custom /<dataset>/access/* endpoints FMod_AccessQuery adds
        policies.put(new PathExclusion(getPathPrefix(dap) + "access/*"), DEFAULT_ROLES);

        // Labels Query - /$/labels/<dataset>
        // Default roles
        String datasetName = Strings.CI.removeStart(dap.getName(), "/");
        addPolicy(policies, "/$/labels/" + datasetName, DEFAULT_ROLES);
    }

    /**
     * Generates the set of permissions based authorization policies that should apply to the dataset represented by the
     * given data access point
     *
     * @param policies Policies to populate
     * @param dap      Data Access Point
     */
    public static void addDatasetPermissionsPolicy(Map<PathExclusion, Policy> policies, DataAccessPoint dap) {
        // Auto-generate policies based on configured endpoint operations
        addPolicies(policies, dap, SCG_AuthPolicy::permissionsPolicyForOperation);

        String datasetName = Strings.CI.removeStart(dap.getName(), "/");

        // Also add policy for the custom /<dataset>/access/* endpoints FMod_AccessQuery adds
        policies.put(new PathExclusion(getPathPrefix(dap) + "access/*"), readPermissions(datasetName));

        // Labels Query - /$/labels/<dataset>
        // Dataset read permissions
        addPolicy(policies, "/$/labels/" + datasetName, readPermissions(datasetName));
    }

    private static void addPolicies(Map<PathExclusion, Policy> policies, DataAccessPoint dap,
                                    PolicyGenerator generator) {
        String pathPrefix = getPathPrefix(dap);
        for (Endpoint endpoint : dap.getDataService().getEndpoints()) {
            Operation op = endpoint.getOperation();
            String epName = endpoint.getName();
            Policy policy = null;
            if (op != null) {
                policy = generator.generate(dap.getName(), op, epName);
            }

            if (policy != null) {
                policies.put(new PathExclusion(pathPrefix + epName), policy);
                Fuseki.configLog.info("Added policy for {}{}: {}", pathPrefix, epName, policy);
            }
        }
    }

    private static String getPathPrefix(DataAccessPoint dap) {
        String datasetName = dap.getName();
        StringBuilder prefix = new StringBuilder();
        if (!Strings.CI.startsWith(datasetName, "/")) {
            prefix.append("/");
        }
        prefix.append(datasetName);
        if (!Strings.CI.endsWith(datasetName, "/")) {
            prefix.append("/");
        }
        return prefix.toString();
    }

    /**
     * Adds all the policies for static Telicent endpoints that are not dataset specific
     *
     * @param roles Roles policies to populate
     * @param perms Permissions policies to populate
     */
    public static void addTelicentEndpointPolicies(Map<PathExclusion, Policy> roles, Map<PathExclusion, Policy> perms) {
        // NB - Where we are using wildcard policies the * is transformed into a .* in a regular expression, any other
        //      characters that have significance, e.g. $, need to be escaped which we do by injecting a \ prior to it
        //      which will be interpreted as an escape sequence when the given path is translated into a regular
        //      expression

        // Compact All - /$/compactall
        addPolicy(roles, "/$/compactall", ADMIN_ROLES);
        addPolicy(perms, "/$/compactall", COMPACT);

        // Backup/Restore - /$/backup/*
        // All endpoints require an adminstrator role
        // Endpoints require either backup.read and backup.write permissions as appropriate
        addPolicy(roles, "/\\$/backup/*", ADMIN_ROLES);
        addPolicy(perms, "/$/backup/create", BACKUP_READ_WRITE);
        addPolicy(perms, "/$/backup/restore", BACKUP_READ_WRITE);
        addPolicy(perms, "/\\$/backup/*", BACKUP_READ_ONLY);
    }

    private static void addPolicy(Map<PathExclusion, Policy> policies, String path, Policy policy) {
        PathExclusion key = new PathExclusion(path);
        policies.put(key, policy);
    }

    /**
     * Helper interface for making policy generation logic reusable for both roles and permissions based policies
     */
    @FunctionalInterface
    private interface PolicyGenerator {
        /**
         * Generates a policy
         *
         * @param datasetName  Dataset name
         * @param operation    Operation
         * @param endpointName Endpoint name
         * @return Policy
         */
        Policy generate(String datasetName, Operation operation, String endpointName);
    }

    /**
     * Gets the roles policy for a given operation
     *
     * @param datasetName  Dataset name
     * @param op           Operation
     * @param endpointName Endpoint name
     * @return Roles policy
     */
    static Policy rolesPolicyForOperation(String datasetName, Operation op, String endpointName) {
        // If a known read-only or read/write operation then return default roles policy
        if (READ_OPERATIONS.contains(op) || READ_WRITE_OPERATIONS.contains(op)) {
            return DEFAULT_ROLES;
        }

        // Not a recognised operation so default to null policy which will ultimately drop through to whatever the
        // configured fallback policy is
        return null;
    }

    /**
     * Generates a policy that requires user to hold the {@code api.<dataset>.read} permission
     *
     * @param datasetName Dataset name
     * @return Policy
     */
    private static Policy readPermissions(String datasetName) {
        //@formatter:off
        return Policy.requireAll("permissions", new String[] {
                datasetPermission("api.%s.read", datasetName)
        });
        //@formatter:on
    }

    /**
     * Generates a permission for a dataset
     *
     * @param template    Template, should contain a single {@code %s} placeholder
     * @param datasetName Dataset name to inject into template
     * @return Dataset permission
     */
    private static String datasetPermission(String template, String datasetName) {
        return String.format(template, Strings.CI.removeStart(datasetName, "/"));
    }

    /**
     * Generates a policy that requires users to hold both the {@code api.<dataset>.read} and
     * {@code api.<dataset>.write} permissions
     *
     * @param datasetName Dataset name
     * @return Policy
     */
    private static Policy readWritePermissions(String datasetName) {
        //@formatter:off
        return Policy.requireAll("permissions", new String[] {
                datasetPermission("api.%s.read", datasetName),
                datasetPermission("api.%s.write", datasetName)
        });
        //@formatter:on
    }

    /**
     * Gets the permissions policy for a given operation
     *
     * @param datasetName  Dataset name
     * @param op           Operation
     * @param endpointName Endpoint name
     * @return Permissions policy
     */
    static Policy permissionsPolicyForOperation(String datasetName, Operation op, String endpointName) {
        if (READ_OPERATIONS.contains(op)) {
            return readPermissions(datasetName);
        } else if (READ_WRITE_OPERATIONS.contains(op)) {
            return readWritePermissions(datasetName);
        }

        // Not a recognised operation so default to null policy which will ultimately drop through to whatever the
        // configured fallback policy is
        return null;
    }
}
