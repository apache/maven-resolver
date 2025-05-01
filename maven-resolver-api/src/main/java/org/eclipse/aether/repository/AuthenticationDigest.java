/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.eclipse.aether.repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.eclipse.aether.RepositorySystemSession;

/**
 * A helper to calculate a fingerprint/digest for the authentication data of a repository/proxy. Such a fingerprint can
 * be used to detect changes in the authentication data across JVM restarts without exposing sensitive information.
 */
public final class AuthenticationDigest {

    private final MessageDigest digest;

    private final RepositorySystemSession session;

    private final RemoteRepository repository;

    private final Proxy proxy;

    /**
     * Gets the fingerprint for the authentication of the specified repository.
     *
     * @param session The repository system session during which the fingerprint is requested, must not be {@code null}.
     * @param repository The repository whose authentication is to be fingerprinted, must not be {@code null}.
     * @return The fingerprint of the repository authentication or an empty string if no authentication is configured,
     *         never {@code null}.
     */
    public static String forRepository(RepositorySystemSession session, RemoteRepository repository) {
        String digest = "";
        Authentication auth = repository.getAuthentication();
        if (auth != null) {
            AuthenticationDigest authDigest = new AuthenticationDigest(session, repository, null);
            auth.digest(authDigest);
            digest = authDigest.digest();
        }
        return digest;
    }

    /**
     * Gets the fingerprint for the authentication of the specified repository's proxy.
     *
     * @param session The repository system session during which the fingerprint is requested, must not be {@code null}.
     * @param repository The repository whose proxy authentication is to be fingerprinted, must not be {@code null}.
     * @return The fingerprint of the proxy authentication or an empty string if no proxy is present or if no proxy
     *         authentication is configured, never {@code null}.
     */
    public static String forProxy(RepositorySystemSession session, RemoteRepository repository) {
        String digest = "";
        Proxy proxy = repository.getProxy();
        if (proxy != null) {
            Authentication auth = proxy.getAuthentication();
            if (auth != null) {
                AuthenticationDigest authDigest = new AuthenticationDigest(session, repository, proxy);
                auth.digest(authDigest);
                digest = authDigest.digest();
            }
        }
        return digest;
    }

    private AuthenticationDigest(RepositorySystemSession session, RemoteRepository repository, Proxy proxy) {
        this.session = session;
        this.repository = repository;
        this.proxy = proxy;
        digest = newDigest();
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            try {
                return MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException ne) {
                throw new IllegalStateException(ne);
            }
        }
    }

    /**
     * Gets the repository system session during which the authentication fingerprint is calculated.
     *
     * @return The repository system session, never {@code null}.
     */
    public RepositorySystemSession getSession() {
        return session;
    }

    /**
     * Gets the repository requiring authentication. If {@link #getProxy()} is not {@code null}, the data gathered by
     * this authentication digest does not apply to the repository's host but rather the proxy.
     *
     * @return The repository to be contacted, never {@code null}.
     */
    public RemoteRepository getRepository() {
        return repository;
    }

    /**
     * Gets the proxy (if any) to be authenticated with.
     *
     * @return The proxy or {@code null} if authenticating directly with the repository's host.
     */
    public Proxy getProxy() {
        return proxy;
    }

    /**
     * Updates the digest with the specified strings.
     *
     * @param strings The strings to update the digest with, may be {@code null} or contain {@code null} elements.
     */
    public void update(String... strings) {
        if (strings != null) {
            for (String string : strings) {
                if (string != null) {
                    digest.update(string.getBytes(StandardCharsets.UTF_8));
                }
            }
        }
    }

    /**
     * Updates the digest with the specified characters.
     *
     * @param chars The characters to update the digest with, may be {@code null}.
     */
    public void update(char... chars) {
        if (chars != null) {
            for (char c : chars) {
                digest.update((byte) (c >> 8));
                digest.update((byte) (c & 0xFF));
            }
        }
    }

    /**
     * Updates the digest with the specified bytes.
     *
     * @param bytes The bytes to update the digest with, may be {@code null}.
     */
    public void update(byte... bytes) {
        if (bytes != null) {
            digest.update(bytes);
        }
    }

    private String digest() {
        byte[] bytes = digest.digest();
        StringBuilder buffer = new StringBuilder(bytes.length * 2);
        for (byte aByte : bytes) {
            int b = aByte & 0xFF;
            if (b < 0x10) {
                buffer.append('0');
            }
            buffer.append(Integer.toHexString(b));
        }
        return buffer.toString();
    }
}
