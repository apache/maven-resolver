package org.eclipse.aether.repository;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Allows to restrict authentication information to certain URIs (optionally considering additional attributes).
 * This class encapsulates the restrictions in terms of
 * <ol>
 * <li>mandatory host pattern</li>
 * <li>optional port</li>
 * <li>optional protocol</li>
 * <li>optional scheme</li>
 * <li>optional realm</li>
 * <ol>
 */
public class AuthenticationScope {

    private final Pattern hostPattern; // mandatory
    private final int port;   // optional, -1 if not relevant
    private final String protocol; // protocol, null of not relevant
    private final String scheme; // scheme, null of not relevant
    private final String realm; // realm, null of not relevant

    public AuthenticationScope(Pattern hostPattern, int port, String protocol, String scheme, String realm) {
        this.hostPattern = hostPattern;
        this.port = port;
        this.protocol = protocol;
        this.scheme = scheme;
        this.realm = realm;
    }

    public boolean isMatching(URI uri, String scheme, String realm) {
        Matcher hostMatcher = hostPattern.matcher(uri.getHost());
        if (!hostMatcher.matches()) {
            return false;
        }
        if (port != -1 && uri.getPort() != port) {
            return false;
        }
        if (protocol != null && !uri.getScheme().equals(protocol)) {
            return false;
        }
        if (this.scheme != null && !this.scheme.equals(scheme)) {
            return false;
        }
        if (this.realm != null && !this.realm.equals(realm)) {
            return false;
        }
        return true;
    }
}
