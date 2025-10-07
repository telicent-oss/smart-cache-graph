package io.telicent.core.auth;

import io.telicent.jena.abac.AttributeValueSet;
import io.telicent.jena.abac.attributes.Attribute;
import io.telicent.jena.abac.attributes.ValueTerm;
import io.telicent.jena.abac.core.AttributesStoreAuthServer;
import io.telicent.servlet.auth.jwt.JwtServletConstants;
import io.telicent.smart.caches.configuration.auth.UserInfo;
import io.telicent.smart.caches.configuration.auth.UserInfoLookup;
import io.telicent.smart.caches.configuration.auth.UserInfoLookupException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class TestUserInfoFilter {

    private UserInfoLookup mockLookup(UserInfo info) throws UserInfoLookupException {
        UserInfoLookup lookup = Mockito.mock(UserInfoLookup.class);
        when(lookup.lookup(any())).thenReturn(info);
        return lookup;
    }

    private static HttpServletRequest mockRequest(String username) {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        if (username != null) {
            when(request.getAttribute(eq(JwtServletConstants.REQUEST_ATTRIBUTE_RAW_JWT))).thenReturn("token");
            when(request.getRemoteUser()).thenReturn(username);
        }
        return request;
    }

    private static AttributeValueSet findAttributes(String username) {
        AttributesStoreAuthServer attrStore = new AttributesStoreAuthServer(null);
        return attrStore.attributes(username);
    }

    private static HttpServletRequest applyFilter(UserInfoLookup lookup, String username) throws IOException,
            ServletException {
        // Given
        UserInfoFilter filter = new UserInfoFilter(lookup);
        HttpServletRequest request = mockRequest(username);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        FilterChain filterChain = Mockito.mock(FilterChain.class);

        // When
        filter.doFilter(request, response, filterChain);

        // Then
        verify(filterChain, times(1)).doFilter(any(), any());
        return request;
    }

    @Test
    public void givenMinimalUserInfo_whenConvertingToUserAttributes_thenNoAttributes() throws UserInfoLookupException,
            ServletException, IOException {
        // Given
        UserInfo info = UserInfo.builder().preferredName("Mr T. Test").build();
        UserInfoLookup lookup = mockLookup(info);

        // When
        applyFilter(lookup, "test");

        // Then
        AttributeValueSet attrs = findAttributes("test");
        Assertions.assertNotNull(attrs);
        Assertions.assertTrue(attrs.isEmpty());
    }

    private void verifyMissingAttributes(AttributeValueSet attrs, String... missingAttributes) {
        for (String missing : missingAttributes) {
            Attribute a = Attribute.create(missing);
            Assertions.assertFalse(attrs.hasAttribute(a));
        }
    }

    private void verifyAttributes(AttributeValueSet attrs, Map<String, Object> attributes) {
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            Attribute a = Attribute.create(entry.getKey());
            Assertions.assertTrue(attrs.hasAttribute(a));
            Collection<ValueTerm> values = attrs.get(a);

            if (entry.getValue() instanceof Collection<?> expected) {
                Assertions.assertEquals(expected.size(), values.size());
                List<String> expectedValues = expected.stream().map(Object::toString).sorted().toList();
                List<String> actualValues = values.stream().map(ValueTerm::getString).sorted().toList();
                Assertions.assertEquals(expectedValues, actualValues);
            } else {
                Assertions.assertNotEquals(0, values.size());
                verifyValue(entry.getValue(), values.iterator().next());
            }
        }
    }

    private static void verifyValue(Object expected, ValueTerm actual) {
        if (expected instanceof Boolean b) {
            Assertions.assertEquals(b, actual.getBoolean());
        } else {
            Assertions.assertEquals(expected.toString(), actual.getString());
        }
    }

    @Test
    public void givenBasicUserInfo_whenConvertingToUserAttributes_thenAttributes() throws UserInfoLookupException,
            ServletException, IOException {
        // Given
        Map<String, Object> attributes =
                Map.of("email", "test@example.org", "age", 42, "nationality", "GBR", "active", true);
        UserInfo info = UserInfo.builder().preferredName("Mr T. Test").attributes(attributes).build();
        UserInfoLookup lookup = mockLookup(info);

        // When
        applyFilter(lookup, "tester");

        // Then
        AttributeValueSet attrs = findAttributes("tester");
        Assertions.assertNotNull(attrs);
        Assertions.assertFalse(attrs.isEmpty());
        verifyAttributes(attrs, attributes);
    }

    @Test
    public void givenUserInfoWithListAttribute_whenConvertingToUserAttributes_thenAllAttributesMaterialised() throws
            UserInfoLookupException, ServletException, IOException {
        // Given
        Map<String, Object> attributes =
                Map.of("email", List.of("test@example.org", "test@personal.org"), "age", 42, "nationality",
                       List.of("GBR", "US"), "active", true);
        UserInfo info = UserInfo.builder().preferredName("Mr T. Test").attributes(attributes).build();
        UserInfoLookup lookup = mockLookup(info);

        // When
        applyFilter(lookup, "tester");

        // Then
        AttributeValueSet attrs = findAttributes("tester");
        Assertions.assertNotNull(attrs);
        Assertions.assertFalse(attrs.isEmpty());
        verifyAttributes(attrs, attributes);
    }

    @Test
    public void givenUserInfoWithNonConvertibleAttributes_whenConvertingToUserAttributes_thenAttributesIgnored() throws
            UserInfoLookupException, ServletException, IOException {
        // Given
        Map<String, Object> attributes = Map.of("ignored", new Object());
        UserInfo info = UserInfo.builder().preferredName("Mr T. Test").attributes(attributes).build();
        UserInfoLookup lookup = mockLookup(info);

        // When
        applyFilter(lookup, "tester");

        // Then
        AttributeValueSet attrs = findAttributes("tester");
        Assertions.assertNotNull(attrs);
        Assertions.assertTrue(attrs.isEmpty());
        verifyMissingAttributes(attrs, "ignored");
    }

    @Test
    public void givenUserInfoWithMapAttribute_whenConvertingToUserAttributes_thenAttributesFlattened() throws
            UserInfoLookupException, ServletException, IOException {
        // Given
        Map<String, Object> attributes = Map.of("email", "test@example.org", "employment",
                                                Map.of("company", "Telicent Ltd", "title", "QA Engineer", "department",
                                                       "R&D", "manages", List.of("Adam", "Bob", "Eve")));
        UserInfo info = UserInfo.builder().preferredName("Mr T. Test").attributes(attributes).build();
        UserInfoLookup lookup = mockLookup(info);

        // When
        applyFilter(lookup, "tester");

        // Then
        AttributeValueSet attrs = findAttributes("tester");
        Assertions.assertNotNull(attrs);
        Assertions.assertFalse(attrs.isEmpty());
        Map<String, Object> expected =
                Map.of("email", "test@example.org", "employment.company", "Telicent Ltd", "employment.title",
                       "QA Engineer", "employment.department", "R&D", "employment.manages",
                       List.of("Adam", "Bob", "Eve"));
        verifyAttributes(attrs, expected);
    }

    @Test
    public void givenNonRetrievableUserInfo_whenFiltering_thenNoAttributes() throws UserInfoLookupException,
            ServletException, IOException {
        // Given
        UserInfoLookup lookup = Mockito.mock(UserInfoLookup.class);
        when(lookup.lookup(any())).thenThrow(new UserInfoLookupException("failed"));

        // When
        applyFilter(lookup, "test");

        // Then
        AttributeValueSet attrs = findAttributes("test");
        Assertions.assertTrue(attrs.isEmpty());
    }

    @Test
    public void givenNoAuthentication_whenFiltering_thenNoAttributes() throws ServletException, IOException {
        // Given
        UserInfoLookup lookup = Mockito.mock(UserInfoLookup.class);

        // When
        HttpServletRequest request = applyFilter(lookup, null);

        // Then
        verify(request, never()).setAttribute(eq(UserInfo.class.getCanonicalName()), any());
    }

    @Test
    public void givenFailsOnCloseLookup_whenDestroyingFilter_thenOk() throws IOException {
        // Given
        UserInfoLookup lookup = Mockito.mock(UserInfoLookup.class);
        doThrow(new RuntimeException("failed")).when(lookup).close();

        // When and Then
        UserInfoFilter filter = new UserInfoFilter(lookup);
        filter.destroy();
    }
}
