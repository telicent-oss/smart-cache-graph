package io.telicent.core.auth;

import io.telicent.core.CQRS;
import io.telicent.jena.graphql.fuseki.SysGraphQL;
import io.telicent.servlet.auth.jwt.PathExclusion;
import io.telicent.smart.caches.configuration.auth.policy.Policy;
import io.telicent.smart.caches.configuration.auth.policy.PolicyKind;
import io.telicent.smart.caches.configuration.auth.policy.TelicentPermissions;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.jena.fuseki.server.DataAccessPoint;
import org.apache.jena.fuseki.server.DataService;
import org.apache.jena.fuseki.server.Endpoint;
import org.apache.jena.fuseki.server.Operation;
import org.apache.jena.sys.JenaSystem;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;

public class TestSCGAuthPolicyGeneration {

    static {
        JenaSystem.init();
    }

    protected static final Operation CUSTOM_OP = Operation.alloc("https.//example.org/custom-op", "custom", "custom");

    public static DataAccessPoint mockDataset(String datasetName) {
        DataAccessPoint dap = Mockito.mock(DataAccessPoint.class);
        when(dap.getName()).thenReturn(datasetName);
        List<Endpoint> endpoints = new ArrayList<>();
        endpoints.add(Endpoint.create(Operation.Query, "query"));
        endpoints.add(Endpoint.create(Operation.Upload, "upload"));
        endpoints.add(Endpoint.create(SysGraphQL.OP_GRAPHQL, "graphql"));
        endpoints.add(Endpoint.create(CQRS.Vocab.operationUpdateCQRS, "cqrs"));
        endpoints.add(Endpoint.create(CUSTOM_OP, "custom"));
        DataService ds = Mockito.mock(DataService.class);
        when(ds.getEndpoints()).thenReturn(endpoints);
        when(dap.getDataService()).thenReturn(ds);
        return dap;
    }

    private void verifyPermissions(Map<PathExclusion, Policy> policies, String path, String... expected) {
        PathExclusion pathExclusion = new PathExclusion(path);
        Policy policy = policies.get(pathExclusion);
        Assertions.assertNotNull(policy, "Missing expected policy for path " + path);
        Assertions.assertEquals(PolicyKind.REQUIRE_ALL, policy.kind());
        for (String permission : expected) {
            Assertions.assertTrue(ArrayUtils.contains(policy.values(), permission),
                                  "Missing expected permission " + permission + " for path " + path);
        }
    }

    private void verifyNoPolicy(Map<PathExclusion, Policy> policies, String path) {
        PathExclusion pathExclusion = new PathExclusion(path);
        Policy policy = policies.get(pathExclusion);
        Assertions.assertNull(policy, "No policy expected for path " + path);
    }

    private void verifyDatasetPermissions(Map<PathExclusion, Policy> policies, String datasetName) {
        // Permissions available on a dataset
        String readPermission = TelicentPermissions.readPermission(datasetName);
        String writePermission = TelicentPermissions.writePermission(datasetName);
        String compactPermission = TelicentPermissions.compactPermission(datasetName);

        // Verify permissions generated for defined endpoints
        verifyPermissions(policies, "/" + datasetName + "/query", readPermission);
        verifyPermissions(policies, "/" + datasetName + "/upload", readPermission, writePermission);
        verifyPermissions(policies, "/" + datasetName + "/graphql", readPermission);
        verifyPermissions(policies, "/" + datasetName + "/cqrs", readPermission, writePermission);
        verifyNoPolicy(policies, "/" + datasetName + "/custom");
        verifyPermissions(policies, "/" + datasetName, readPermission, writePermission);

        // Verify permissions generated for Telicent custom endpoints
        verifyPermissions(policies, "/" + datasetName + "/access/*", readPermission);
        verifyPermissions(policies, "/$/labels/" + datasetName, readPermission);
        verifyPermissions(policies, "/$/compact/" + datasetName, compactPermission);
    }

    private void verifyRoles(Map<PathExclusion, Policy> policies, String path, String... expected) {
        PathExclusion pathExclusion = new PathExclusion(path);
        Policy policy = policies.get(pathExclusion);
        Assertions.assertNotNull(policy, "Missing expected policy for path " + path);
        Assertions.assertEquals(PolicyKind.REQUIRE_ANY, policy.kind());
        for (String role : expected) {
            Assertions.assertTrue(ArrayUtils.contains(policy.values(), role),
                                  "Missing expected role " + role + " for path " + path);
        }
    }

    private void verifyDatasetRoles(Map<PathExclusion, Policy> policies, String datasetName) {
        // Verify roles generated from defined endpoints
        verifyRoles(policies, "/" + datasetName + "/query", SCG_AuthPolicy.DEFAULT_ROLES.values());
        verifyRoles(policies, "/" + datasetName + "/upload", SCG_AuthPolicy.DEFAULT_ROLES.values());
        verifyRoles(policies, "/" + datasetName + "/graphql", SCG_AuthPolicy.DEFAULT_ROLES.values());
        verifyRoles(policies, "/" + datasetName + "/cqrs", SCG_AuthPolicy.DEFAULT_ROLES.values());
        verifyNoPolicy(policies, "/" + datasetName + "/custom");
        verifyRoles(policies, "/" + datasetName, SCG_AuthPolicy.DEFAULT_ROLES.values());

        // Verify roles generated for Telicent custom endpoints
        verifyRoles(policies, "/" + datasetName + "/access/*", SCG_AuthPolicy.DEFAULT_ROLES.values());
        verifyRoles(policies, "/$/labels/" + datasetName, SCG_AuthPolicy.DEFAULT_ROLES.values());
    }

    @Test
    public void givenBasicDataset_whenGeneratingPolicy_thenExpectedPoliciesGenerated() {
        // Given
        DataAccessPoint dap = mockDataset("/knowledge");

        // When
        Map<PathExclusion, Policy> roles = new HashMap<>();
        Map<PathExclusion, Policy> perms = new HashMap<>();
        SCG_AuthPolicy.addDatasetRolesPolicy(roles, dap);
        SCG_AuthPolicy.addDatasetPermissionsPolicy(perms, dap);

        // Then
        verifyDatasetRoles(roles, "knowledge");
        verifyDatasetPermissions(perms, "knowledge");
        // NB - The /$/compactall permissions are dynamically built based upon all the registered datasets so in this
        //      case will only have one permission required
        verifyPermissions(perms, "/$/compactall", TelicentPermissions.compactPermission("knowledge"));
    }

    @Test
    public void givenMultipleDatasets_whenGeneratingPolicy_thenSeparatePoliciesGenerated() {
        // Given
        DataAccessPoint knowledge = mockDataset("/knowledge");
        DataAccessPoint catalog = mockDataset("/catalog");

        // When
        Map<PathExclusion, Policy> roles = new HashMap<>();
        Map<PathExclusion, Policy> perms = new HashMap<>();
        SCG_AuthPolicy.addDatasetRolesPolicy(roles, knowledge);
        SCG_AuthPolicy.addDatasetPermissionsPolicy(perms, knowledge);
        SCG_AuthPolicy.addDatasetRolesPolicy(roles, catalog);
        SCG_AuthPolicy.addDatasetPermissionsPolicy(perms, catalog);

        // Then
        verifyDatasetRoles(roles, "knowledge");
        verifyDatasetPermissions(perms, "knowledge");
        verifyDatasetRoles(roles, "catalog");
        verifyDatasetPermissions(perms, "catalog");
        // NB - The /$/compactall permissions are dynamically built based upon all the registered datasets
        verifyPermissions(perms, "/$/compactall", TelicentPermissions.compactPermission("knowledge"),
                          TelicentPermissions.compactPermission("catalog"));
    }

    @Test
    public void givenDatasetNameWithoutLeadingSlash_whenGeneratingPolicy_thenExpectedPoliciesGenerated() {
        // Given
        DataAccessPoint dap = mockDataset("knowledge");

        // When
        Map<PathExclusion, Policy> roles = new HashMap<>();
        Map<PathExclusion, Policy> perms = new HashMap<>();
        SCG_AuthPolicy.addDatasetRolesPolicy(roles, dap);
        SCG_AuthPolicy.addDatasetPermissionsPolicy(perms, dap);

        // Then
        verifyDatasetRoles(roles, "knowledge");
        verifyDatasetPermissions(perms, "knowledge");
    }

    @Test
    public void givenEmptyPolicies_whenGeneratingStaticTelicentPolicy_thenExpectedPoliciesGenerated() {
        // Given
        Map<PathExclusion, Policy> roles = new HashMap<>();
        Map<PathExclusion, Policy> perms = new HashMap<>();

        // When
        SCG_AuthPolicy.addTelicentEndpointPolicies(roles, perms);

        // Then
        Assertions.assertFalse(roles.isEmpty());
        Assertions.assertFalse(perms.isEmpty());
        verifyRoles(roles, "/$/compactall", SCG_AuthPolicy.ADMIN_ROLES.values());
        // NB - Permissions for /$/compactall are dynamically built based on the datasets so will be undefined in this
        //      case
        verifyNoPolicy(perms, "/$/compactall");
        verifyRoles(roles, "/$/backups/*", SCG_AuthPolicy.ADMIN_ROLES.values());
        verifyPermissions(perms, "/$/backups/create", SCG_AuthPolicy.BACKUP_CREATE.values());
        verifyPermissions(perms, "/$/backups/delete", SCG_AuthPolicy.BACKUP_DELETE.values());
        verifyPermissions(perms, "/$/backups/restore", SCG_AuthPolicy.BACKUP_RESTORE.values());
        verifyPermissions(perms, "/$/backups/*", SCG_AuthPolicy.BACKUP_READ_ONLY.values());
    }
}
