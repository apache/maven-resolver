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

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.AuthenticationDigest;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SecretAuthenticationTest {

    private RepositorySystemSession newSession() {
        return TestUtils.newSession();
    }

    private RemoteRepository newRepo(Authentication auth) {
        return new RemoteRepository.Builder("test", "default", "http://localhost")
                .setAuthentication(auth)
                .build();
    }

    private AuthenticationContext newContext(Authentication auth) {
        return AuthenticationContext.forRepository(newSession(), newRepo(auth));
    }

    private String newDigest(Authentication auth) {
        return AuthenticationDigest.forRepository(newSession(), newRepo(auth));
    }

    @Test
    void testConstructor_CopyChars() {
        char[] value = {'v', 'a', 'l'};
        new SecretAuthentication("key", value);
        assertArrayEquals(new char[] {'v', 'a', 'l'}, value);
    }

    @Test
    void testFill() {
        Authentication auth = new SecretAuthentication("key", "value");
        try (AuthenticationContext context = newContext(auth)) {
            assertNull(context.get("another-key"));
            assertEquals("value", context.get("key"));
        }
    }

    @Test
    void testDigest() {
        Authentication auth1 = new SecretAuthentication("key", "value");
        Authentication auth2 = new SecretAuthentication("key", "value");
        String digest1 = newDigest(auth1);
        String digest2 = newDigest(auth2);
        assertEquals(digest1, digest2);

        Authentication auth3 = new SecretAuthentication("key", "Value");
        String digest3 = newDigest(auth3);
        assertNotEquals(digest3, digest1);

        Authentication auth4 = new SecretAuthentication("Key", "value");
        String digest4 = newDigest(auth4);
        assertNotEquals(digest4, digest1);
    }

    @Test
    void testEquals() {
        Authentication auth1 = new SecretAuthentication("key", "value");
        Authentication auth2 = new SecretAuthentication("key", "value");
        Authentication auth3 = new SecretAuthentication("key", "Value");
        assertEquals(auth1, auth2);
        assertNotEquals(auth1, auth3);
        assertNotEquals(null, auth1);
    }

    @Test
    void testHashCode() {
        Authentication auth1 = new SecretAuthentication("key", "value");
        Authentication auth2 = new SecretAuthentication("key", "value");
        assertEquals(auth1.hashCode(), auth2.hashCode());
    }
}
