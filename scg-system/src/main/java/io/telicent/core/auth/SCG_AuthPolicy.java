package io.telicent.core.auth;

import io.telicent.core.CQRS;
import io.telicent.jena.abac.fuseki.ServerABAC;
import io.telicent.jena.graphql.fuseki.SysGraphQL;
import io.telicent.servlet.auth.jwt.PathExclusion;
import io.telicent.smart.caches.configuration.auth.policy.Policy;
import io.telicent.smart.caches.configuration.auth.policy.TelicentPermissions;
import io.telicent.smart.caches.configuration.auth.policy.TelicentRoles;
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
    public static final Policy DEFAULT_ROLES = Policy.requireAny("roles", TelicentRoles.USER, TelicentRoles.ADMIN_SYSTEM);
    /**
     * Admin roles policy applied to admin only endpoints
     */
    public static final Policy ADMIN_ROLES = Policy.requireAny("roles", TelicentRoles.ADMIN_SYSTEM);
    /**
     * Permissions policy for backup related read-only endpoints
     */
    public static final Policy BACKUP_READ_ONLY = Policy.requireAll("permissions", TelicentPermissions.Backup.READ);
    /**
     * Permissions policy for backup creation endpoint
     */
    public static final Policy BACKUP_CREATE = Policy.requireAll("permissions", TelicentPermissions.Backup.WRITE);
    /**
     * Permissions policy for backup restore endpoint
     */
    public static final Policy BACKUP_RESTORE = Policy.requireAll("permissions", TelicentPermissions.Backup.RESTORE);
    /**
     * Permissions policy for backup delete endpoint
     */
    public static final Policy BACKUP_DELETE = Policy.requireAll("permissions", TelicentPermissions.Backup.DELETE);

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

    /**
     * Private constructor as class only provides static helper methods
     */
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
        addPolicy(policies, getPathPrefix(dap) + "access/*", DEFAULT_ROLES);

        // Labels Query - /$/labels/<dataset>
        // Default roles
        String datasetName = Strings.CI.removeStart(dap.getName(), "/");
        addPolicy(policies, "/$/labels/" + datasetName, DEFAULT_ROLES);

        // Fuseki will also capture requests to the root dataset path and try to dynamically route them based on the
        // request method and body, allow this provided users have the default roles
        addPolicy(policies, "/" + datasetName, DEFAULT_ROLES);
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
        addPolicy(policies, getPathPrefix(dap) + "access/*", readPermissions(datasetName));

        // Labels Query - /$/labels/<dataset>
        // Dataset read permissions
        addPolicy(policies, "/$/labels/" + datasetName, readPermissions(datasetName));

        // Compact endpoints
        Policy compactPolicy =
                Policy.requireAll("permissions", new String[] { TelicentPermissions.compactPermission(datasetName) });
        addPolicy(policies, "/$/compact/" + datasetName, compactPolicy);
        addOrUpdatePolicy(policies, "/$/compactall", compactPolicy);

        // Fuseki will also capture requests to the root dataset path and try to dynamically route them based on the
        // request method and body, since we won't know what kind of request is arriving in advance have to enforce both
        // read and write permissions for this
        addPolicy(policies, "/" + datasetName, readWritePermissions(datasetName));
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
                addPolicy(policies, pathPrefix + epName, policy);
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

        // Backup/Restore - /$/backups/*
        // All endpoints require an adminstrator role
        // Endpoints require either backup.read and backup.write permissions as appropriate
        addPolicy(roles, "/\\$/backups/*", ADMIN_ROLES);
        addPolicy(perms, "/$/backups/create", BACKUP_CREATE);
        addPolicy(perms, "/$/backups/delete", BACKUP_DELETE);
        addPolicy(perms, "/$/backups/restore", BACKUP_RESTORE);
        addPolicy(perms, "/\\$/backups/*", BACKUP_READ_ONLY);
    }

    /**
     * Adds a policy to the policies map, also logging what was added for debugging purposes
     *
     * @param policies    Policies
     * @param pathPattern Path pattern to which the policy applies
     * @param policy      Policy
     */
    private static void addPolicy(Map<PathExclusion, Policy> policies, String pathPattern, Policy policy) {
        PathExclusion key = new PathExclusion(pathPattern);
        policies.put(key, policy);
        Fuseki.configLog.info("Added {} policy for {}: {}", policy.source(), key.getPattern(), policy);
    }

    /**
     * Adds/updates a policy in the policies map, also logging what was added for debugging purposes
     *
     * @param policies    Policies
     * @param pathPattern Path pattern to which the policy applies
     * @param policy      Policy
     */
    private static void addOrUpdatePolicy(Map<PathExclusion, Policy> policies, String pathPattern, Policy policy) {
        PathExclusion key = new PathExclusion(pathPattern);
        Policy existing = policies.get(key);
        if (existing != null) {
            Policy updated = Policy.combine(existing, policy);
            policies.put(key, updated);
            Fuseki.configLog.info("Updated {} policy for {}: {}", policy.source(), key.getPattern(), policy);
        } else {
            addPolicy(policies, pathPattern, policy);
        }
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
                TelicentPermissions.readPermission(Strings.CI.removeStart(datasetName, "/"))
        });
        //@formatter:on
    }

    /**
     * Generates a policy that requires users to hold both the {@code api.<dataset>.read} and
     * {@code api.<dataset>.write} permissions
     *
     * @param datasetName Dataset name
     * @return Policy
     */
    private static Policy readWritePermissions(String datasetName) {
        return Policy.requireAll("permissions",
                                 TelicentPermissions.readWritePermissions(Strings.CI.removeStart(datasetName, "/")));
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
