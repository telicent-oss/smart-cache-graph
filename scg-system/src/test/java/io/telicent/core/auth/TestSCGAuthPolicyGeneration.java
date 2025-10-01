package io.telicent.core.auth;

import io.telicent.core.CQRS;
import io.telicent.jena.graphql.fuseki.SysGraphQL;
import io.telicent.servlet.auth.jwt.PathExclusion;
import io.telicent.smart.caches.configuration.auth.policy.Policy;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.jena.fuseki.server.DataAccessPoint;
import org.apache.jena.fuseki.server.DataService;
import org.apache.jena.fuseki.server.Endpoint;
import org.apache.jena.fuseki.server.Operation;
import org.apache.jena.sys.JenaSystem;
import org.bouncycastle.util.Arrays;
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

    // TODO PathExclusion needs to implement equals() and hashCode() for use as a Map key

    private void verifyPermissions(Map<PathExclusion, Policy> policies, String path, String... expected) {
        PathExclusion pathExclusion = new PathExclusion(path);
        Policy policy = policies.get(pathExclusion);
        Assertions.assertNotNull(policy, "Missing expected policy for path " + path);
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

    @Test
    public void givenBasicDataset_whenGeneratingPolicy_thenExpectedPoliciesGenerated() {
        // Given
        DataAccessPoint dap = Mockito.mock(DataAccessPoint.class);
        when(dap.getName()).thenReturn("/knowledge");
        List<Endpoint> endpoints = new ArrayList<>();
        endpoints.add(Endpoint.create(Operation.Query, "query"));
        endpoints.add(Endpoint.create(Operation.Upload, "upload"));
        endpoints.add(Endpoint.create(SysGraphQL.OP_GRAPHQL, "graphql"));
        endpoints.add(Endpoint.create(CQRS.Vocab.operationUpdateCQRS, "cqrs"));
        endpoints.add(Endpoint.create(Operation.alloc("https://example.org/custom-op", "custom", "custom"), "custom"));
        DataService ds = Mockito.mock(DataService.class);
        when(ds.getEndpoints()).thenReturn(endpoints);
        when(dap.getDataService()).thenReturn(ds);

        // When
        Map<PathExclusion, Policy> policies = new HashMap<>();
        SCG_AuthPolicy.addDatasetPermissionsPolicy(policies, dap);

        // Then
        verifyPermissions(policies, "/knowledge/query", "api:knowledge:read");
        verifyPermissions(policies, "/knowledge/upload", "api:knowledge:read", "api:knowledge:write");
        verifyPermissions(policies, "/knowledge/graphql", "api:knowledge:read");
        verifyPermissions(policies, "/knowledge/cqrs", "api:knowledge:read", "api:knowledge:write");
        verifyNoPolicy(policies, "/knowledge/custom");
    }
}
