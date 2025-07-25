package io.telicent.model;

import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;
import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestKeyPair {

    @Test
    public void test_absolute_uris() throws MalformedURLException, URISyntaxException {
        KeyPair result = KeyPair.fromValues("file:///tmp/private.asc","file:///tmp/public.asc","password");
        assertNotNull(result);
    }

    @Test
    public void test_file_path_only() throws URISyntaxException, MalformedURLException {
        KeyPair result = KeyPair.fromValues("/tmp/private.asc","/tmp/public.asc","password");
        assertNotNull(result);
    }

    @Test
    public void test_not_absolute_uris() {
        assertThrows( IllegalArgumentException.class, () -> KeyPair.fromValues("tmp/private.asc","tmp/public.asc","password"));
    }

    @Test
    public void test_invalid_uri_syntax() {
        assertThrows( URISyntaxException.class, () -> KeyPair.fromValues("tmp /private.asc","tmp /public.asc","password"));
    }

}
