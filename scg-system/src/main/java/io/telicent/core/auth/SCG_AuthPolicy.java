package io.telicent.core.auth;

import io.telicent.core.CQRS;
import io.telicent.jena.graphql.fuseki.SysGraphQL;
import io.telicent.servlet.auth.jwt.PathExclusion;
import io.telicent.smart.caches.configuration.auth.policy.Policy;
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

    /**
     * Known Fuseki operations that are read-only operations
     */
    public static final Set<Operation> READ_OPERATIONS =
            Set.of(Operation.Query, Operation.GSP_R, Operation.PREFIXES_R, Operation.Shacl, SysGraphQL.OP_GRAPHQL);
    /**
     * Known Fuseki operations that are read/write operations
     */
    public static final Set<Operation> READ_WRITE_OPERATIONS =
            Set.of(Operation.Update, Operation.GSP_RW, Operation.Upload, Operation.Patch,
                   CQRS.Vocab.operationUpdateCQRS);

    private SCG_AuthPolicy() {
    }

    public static void addDatasetRolesPolicy(Map<PathExclusion, Policy> policies, DataAccessPoint dap) {
        addPolicies(policies, dap, SCG_AuthPolicy::rolesPolicyForOperation);
    }

    public static void addDatasetPermissionsPolicy(Map<PathExclusion, Policy> policies, DataAccessPoint dap) {
        addPolicies(policies, dap, SCG_AuthPolicy::permissionsPolicyForOperation);
    }

    private static void addPolicies(Map<PathExclusion, Policy> policies, DataAccessPoint dap,
                                    PolicyGenerator generator) {
        String pathPrefix = "/" + dap.getName() + "/";
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
        return String.format(template, datasetName);
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
