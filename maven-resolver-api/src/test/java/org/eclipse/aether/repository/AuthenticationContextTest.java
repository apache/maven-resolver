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

import static org.junit.Assert.*;

import java.io.File;
import java.util.Map;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.junit.Test;

public class AuthenticationContextTest {

    private RepositorySystemSession newSession() {
        return new DefaultRepositorySystemSession();
    }

    private RemoteRepository newRepo(Authentication auth, Proxy proxy) {
        return new RemoteRepository.Builder("test", "default", "http://localhost") //
                .setAuthentication(auth)
                .setProxy(proxy)
                .build();
    }

    private Proxy newProxy(Authentication auth) {
        return new Proxy(Proxy.TYPE_HTTP, "localhost", 8080, auth);
    }

    private Authentication newAuth() {
        return new Authentication() {
            public void fill(AuthenticationContext context, String key, Map<String, String> data) {
                assertNotNull(context);
                assertNotNull(context.getSession());
                assertNotNull(context.getRepository());
                assertNull("fill() should only be called once", context.get("key"));
                context.put("key", "value");
            }

            public void digest(AuthenticationDigest digest) {
                fail("AuthenticationContext should not call digest()");
            }
        };
    }

    @Test
    public void testForRepository() {
        RepositorySystemSession session = newSession();
        RemoteRepository repo = newRepo(newAuth(), newProxy(newAuth()));
        AuthenticationContext context = AuthenticationContext.forRepository(session, repo);
        assertNotNull(context);
        assertSame(session, context.getSession());
        assertSame(repo, context.getRepository());
        assertNull(context.getProxy());
        assertEquals("value", context.get("key"));
        assertEquals("value", context.get("key"));
    }

    @Test
    public void testForRepository_NoAuth() {
        RepositorySystemSession session = newSession();
        RemoteRepository repo = newRepo(null, newProxy(newAuth()));
        AuthenticationContext context = AuthenticationContext.forRepository(session, repo);
        assertNull(context);
    }

    @Test
    public void testForProxy() {
        RepositorySystemSession session = newSession();
        Proxy proxy = newProxy(newAuth());
        RemoteRepository repo = newRepo(newAuth(), proxy);
        AuthenticationContext context = AuthenticationContext.forProxy(session, repo);
        assertNotNull(context);
        assertSame(session, context.getSession());
        assertSame(repo, context.getRepository());
        assertSame(proxy, context.getProxy());
        assertEquals("value", context.get("key"));
        assertEquals("value", context.get("key"));
    }

    @Test
    public void testForProxy_NoProxy() {
        RepositorySystemSession session = newSession();
        Proxy proxy = null;
        RemoteRepository repo = newRepo(newAuth(), proxy);
        AuthenticationContext context = AuthenticationContext.forProxy(session, repo);
        assertNull(context);
    }

    @Test
    public void testForProxy_NoProxyAuth() {
        RepositorySystemSession session = newSession();
        Proxy proxy = newProxy(null);
        RemoteRepository repo = newRepo(newAuth(), proxy);
        AuthenticationContext context = AuthenticationContext.forProxy(session, repo);
        assertNull(context);
    }

    @Test
    public void testGet_StringVsChars() {
        AuthenticationContext context = AuthenticationContext.forRepository(newSession(), newRepo(newAuth(), null));
        context.put("key", new char[] {'v', 'a', 'l', '1'});
        assertEquals("val1", context.get("key"));
        context.put("key", "val2");
        assertArrayEquals(new char[] {'v', 'a', 'l', '2'}, context.get("key", char[].class));
    }

    @Test
    public void testGet_StringVsFile() {
        AuthenticationContext context = AuthenticationContext.forRepository(newSession(), newRepo(newAuth(), null));
        context.put("key", "val1");
        assertEquals(new File("val1"), context.get("key", File.class));
        context.put("key", new File("val2"));
        assertEquals("val2", context.get("key"));
    }

    @Test
    public void testPut_EraseCharArrays() {
        AuthenticationContext context = AuthenticationContext.forRepository(newSession(), newRepo(newAuth(), null));
        char[] secret = {'v', 'a', 'l', 'u', 'e'};
        context.put("key", secret);
        context.put("key", secret.clone());
        assertArrayEquals(new char[] {0, 0, 0, 0, 0}, secret);
    }

    @Test
    public void testClose_EraseCharArrays() {
        AuthenticationContext.close(null);

        AuthenticationContext context = AuthenticationContext.forRepository(newSession(), newRepo(newAuth(), null));
        char[] secret = {'v', 'a', 'l', 'u', 'e'};
        context.put("key", secret);
        AuthenticationContext.close(context);
        assertArrayEquals(new char[] {0, 0, 0, 0, 0}, secret);
    }
}
