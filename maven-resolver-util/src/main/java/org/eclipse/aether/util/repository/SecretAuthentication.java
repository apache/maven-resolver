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
package org.eclipse.aether.util.repository;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.AuthenticationDigest;

import static java.util.Objects.requireNonNull;

/**
 * Authentication block that manages a single authentication key and its secret string value (password, passphrase).
 * Unlike {@link StringAuthentication}, the string value is kept in an encrypted buffer and only decrypted when needed
 * to reduce the potential of leaking the secret in a heap dump.
 */
final class SecretAuthentication implements Authentication {

    private static final Object[] KEYS;

    static {
        KEYS = new Object[16];
        for (int i = 0; i < KEYS.length; i++) {
            KEYS[i] = new Object();
        }
    }

    private final String key;

    private final char[] value;

    private final int secretHash;

    SecretAuthentication(String key, String value) {
        this((value != null) ? value.toCharArray() : null, key);
    }

    SecretAuthentication(String key, char[] value) {
        this(copy(value), key);
    }

    private SecretAuthentication(char[] value, String key) {
        this.key = requireNonNull(key, "authentication key cannot be null");
        if (key.isEmpty()) {
            throw new IllegalArgumentException("authentication key cannot be empty");
        }
        this.secretHash = Arrays.hashCode(value) ^ KEYS[0].hashCode();
        this.value = xor(value);
    }

    private static char[] copy(char[] chars) {
        return (chars != null) ? chars.clone() : null;
    }

    @SuppressWarnings("checkstyle:magicnumber")
    private char[] xor(char[] chars) {
        if (chars != null) {
            int mask = System.identityHashCode(this);
            for (int i = 0; i < chars.length; i++) {
                int key = KEYS[(i >> 1) % KEYS.length].hashCode();
                key ^= mask;
                chars[i] ^= ((i & 1) == 0) ? (key & 0xFFFF) : (key >>> 16);
            }
        }
        return chars;
    }

    private static void clear(char[] chars) {
        if (chars != null) {
            for (int i = 0; i < chars.length; i++) {
                chars[i] = '\0';
            }
        }
    }

    public void fill(AuthenticationContext context, String key, Map<String, String> data) {
        requireNonNull(context, "context cannot be null");
        char[] secret = copy(value);
        xor(secret);
        context.put(this.key, secret);
        // secret will be cleared upon AuthenticationContext.close()
    }

    public void digest(AuthenticationDigest digest) {
        char[] secret = copy(value);
        try {
            xor(secret);
            digest.update(key);
            digest.update(secret);
        } finally {
            clear(secret);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || !getClass().equals(obj.getClass())) {
            return false;
        }
        SecretAuthentication that = (SecretAuthentication) obj;
        if (!Objects.equals(key, that.key) || secretHash != that.secretHash) {
            return false;
        }
        char[] secret = copy(value);
        char[] thatSecret = copy(that.value);
        try {
            xor(secret);
            that.xor(thatSecret);
            return Arrays.equals(secret, thatSecret);
        } finally {
            clear(secret);
            clear(thatSecret);
        }
    }

    @Override
    public int hashCode() {
        int hash = 17;
        hash = hash * 31 + key.hashCode();
        hash = hash * 31 + secretHash;
        return hash;
    }

    @Override
    public String toString() {
        return key + "=" + ((value != null) ? "***" : "null");
    }
}
