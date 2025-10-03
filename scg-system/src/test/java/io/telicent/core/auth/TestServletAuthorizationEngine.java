package io.telicent.core.auth;

import io.telicent.servlet.auth.jwt.PathExclusion;
import io.telicent.smart.caches.configuration.auth.policy.Policy;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.jena.fuseki.server.DataAccessPoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.mockito.Mockito.when;

public class TestServletAuthorizationEngine {

    private static final Map<PathExclusion, Policy> ROLES = new LinkedHashMap<>();
    private static final Map<PathExclusion, Policy> PERMS = new LinkedHashMap<>();

    @BeforeAll
    public static void setup() {
        // Generate an example set of policies
        DataAccessPoint knowledge = TestSCGAuthPolicyGeneration.mockDataset("/knowledge");
        DataAccessPoint catalog = TestSCGAuthPolicyGeneration.mockDataset("/catalog");
        SCG_AuthPolicy.addTelicentEndpointPolicies(ROLES, PERMS);
        SCG_AuthPolicy.addDatasetRolesPolicy(ROLES, knowledge);
        SCG_AuthPolicy.addDatasetRolesPolicy(ROLES, catalog);
        SCG_AuthPolicy.addDatasetPermissionsPolicy(PERMS, knowledge);
        SCG_AuthPolicy.addDatasetPermissionsPolicy(PERMS, catalog);
    }

    private final class InspectableServletAuthorizationEngine extends ServletAuthorizationEngine {

        /**
         * Creates a new authorization engine
         *
         * @param rolesPolicies       Roles policy mappings
         * @param permissionsPolicies Permissions policy mappings
         * @param rolesFallback       Fallback roles policy, {@link Policy#ALLOW_ALL} if not set
         * @param permissionsFallback Fallback permissions policy, {@link Policy#ALLOW_ALL} if not set
         */
        InspectableServletAuthorizationEngine(Map<PathExclusion, Policy> rolesPolicies,
                                              Map<PathExclusion, Policy> permissionsPolicies, Policy rolesFallback,
                                              Policy permissionsFallback) {
            super(rolesPolicies, permissionsPolicies, rolesFallback, permissionsFallback);
        }

        public Policy findRoles(ServletAuthorizationContext context) {
            return super.getRolesPolicy(context);
        }

        public Policy findPermissions(ServletAuthorizationContext context) {
            return super.getPermissionsPolicy(context);
        }
    }

    private InspectableServletAuthorizationEngine createEngine() {
        return new InspectableServletAuthorizationEngine(ROLES, PERMS, Policy.DENY_ALL, Policy.DENY_ALL);
    }

    private static Stream<Arguments> protectedPaths() {
        //@formatter:off
        return Stream.of(
                // Various dataset endpoints
                Arguments.of("/knowledge/", "query"),
                Arguments.of("/knowledge/", "cqrs"),
                Arguments.of("/knowledge/access/query", null),
                Arguments.of("/$/labels/knowledge", null),
                Arguments.of("/knowledge", null),
                // Administrative endpoints
                Arguments.of("/$/backups/create", null),
                Arguments.of("/$/backups/restore", null),
                Arguments.of("/$/compactall", null)
        );
        //@formatter:on
    }

    @ParameterizedTest
    @MethodSource("protectedPaths")
    public void givenRequestToProtectedPath_whenFindingPolicy_thenNonFallbackPolicyIsFound(String servletPath,
                                                                                           String pathInfo) {
        // Given
        ServletAuthorizationContext context = prepareContext(servletPath, pathInfo);
        InspectableServletAuthorizationEngine engine = createEngine();

        // When
        Policy roles = engine.findRoles(context);
        Policy permissions = engine.findPermissions(context);

        // Then
        Assertions.assertNotNull(roles);
        Assertions.assertNotNull(permissions);
        Assertions.assertNotEquals(Policy.DENY_ALL, roles);
        Assertions.assertNotEquals(Policy.DENY_ALL, permissions);
    }

    private static ServletAuthorizationContext prepareContext(String servletPath, String pathInfo) {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getServletPath()).thenReturn(servletPath);
        when(request.getPathInfo()).thenReturn(pathInfo);
        return new ServletAuthorizationContext(request);
    }

    private static Stream<Arguments> otherPaths() {
        //@formatter:off
        return Stream.of(
                // The /$/ping endpoint has no policy, this is because in normal use it's excluded from Authentication
                // so AuthZ would never apply anyway
                Arguments.of("/$/ping", null),
                // Some random unrecognised paths
                Arguments.of(null, "/no-such-path"),
                Arguments.of(null, "/deeply/nested/path/to/nothing.txt"),
                // Blank endpoint under a dataset
                Arguments.of("/knowledge/", null),
                // Unknown endpoint under a dataset
                Arguments.of("/knowledge/", "unknown"));
        //@formatter:on
    }

    @ParameterizedTest
    @MethodSource("otherPaths")
    public void givenRequestToOtherPaths_whenFindingPolicy_thenFallbackPolicyIsFound(String servletPath,
                                                                                     String pathInfo) {
        // Given
        ServletAuthorizationContext context = prepareContext(servletPath, pathInfo);
        InspectableServletAuthorizationEngine engine = createEngine();

        // When
        Policy roles = engine.findRoles(context);
        Policy permissions = engine.findPermissions(context);

        // Then
        Assertions.assertNotNull(roles);
        Assertions.assertNotNull(permissions);
        Assertions.assertEquals(Policy.DENY_ALL, roles);
        Assertions.assertEquals(Policy.DENY_ALL, permissions);
    }
}
