package io.telicent.model;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * An immutable class to wrap a key pair and passphrase for use in public key encryption
 *
 * @param privateKeyUrl the URL where the private key can be found
 * @param publicKeyUrl  the URL where the public key can be found
 * @param passphrase    the passphrase use when creating the key pair
 */
public record KeyPair(URL privateKeyUrl, URL publicKeyUrl, String passphrase) {

    /**
     * Creates a KeyPair from the public and private key URL strings and the passphrase
     *
     * @param privateKeyLocation private key URL as string
     * @param publicKeyLocation  public key URL as string
     * @param passphrase         the passphrase used when creating the keys
     * @return the new KeyPair
     * @throws URISyntaxException    if the location cannot be used to create a URI
     * @throws MalformedURLException if the URI cannot be used to create a URL
     */
    public static KeyPair fromValues(final String privateKeyLocation, final String publicKeyLocation, final String passphrase) throws URISyntaxException, MalformedURLException {
        final URL privateKeyUrl = new URI(privateKeyLocation).toURL();
        final URL publicKeyUrl = new URI(publicKeyLocation).toURL();
        return new KeyPair(privateKeyUrl, publicKeyUrl, passphrase);
    }
}
