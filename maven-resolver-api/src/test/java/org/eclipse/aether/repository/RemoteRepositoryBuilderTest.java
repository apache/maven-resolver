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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.repository.RemoteRepository.Builder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RemoteRepositoryBuilderTest {

    private RemoteRepository prototype;

    @BeforeEach
    void init() {
        prototype = new Builder("id", "type", "file:void").build();
    }

    @Test
    void testReusePrototype() {
        Builder builder = new Builder(prototype);
        assertSame(prototype, builder.build());
    }

    @Test
    void testPrototypeMandatory() {
        assertThrows(NullPointerException.class, () -> new Builder(null));
    }

    @Test
    void testSetId() {
        Builder builder = new Builder(prototype);
        RemoteRepository repo = builder.setId(prototype.getId()).build();
        assertSame(prototype, repo);
        repo = builder.setId("new-id").build();
        assertEquals("new-id", repo.getId());
    }

    @Test
    void testSetContentType() {
        Builder builder = new Builder(prototype);
        RemoteRepository repo =
                builder.setContentType(prototype.getContentType()).build();
        assertSame(prototype, repo);
        repo = builder.setContentType("new-type").build();
        assertEquals("new-type", repo.getContentType());
    }

    @Test
    void testSetUrl() {
        Builder builder = new Builder(prototype);
        RemoteRepository repo = builder.setUrl(prototype.getUrl()).build();
        assertSame(prototype, repo);
        repo = builder.setUrl("file:new").build();
        assertEquals("file:new", repo.getUrl());
    }

    @Test
    void testSetPolicy() {
        Builder builder = new Builder(prototype);
        RemoteRepository repo = builder.setPolicy(prototype.getPolicy(false)).build();
        assertSame(prototype, repo);
        RepositoryPolicy policy = new RepositoryPolicy(true, "never", "fail");
        repo = builder.setPolicy(policy).build();
        assertEquals(policy, repo.getPolicy(true));
        assertEquals(policy, repo.getPolicy(false));
    }

    @Test
    void testSetReleasePolicy() {
        Builder builder = new Builder(prototype);
        RemoteRepository repo =
                builder.setReleasePolicy(prototype.getPolicy(false)).build();
        assertSame(prototype, repo);
        RepositoryPolicy policy = new RepositoryPolicy(true, "never", "fail");
        repo = builder.setReleasePolicy(policy).build();
        assertEquals(policy, repo.getPolicy(false));
        assertEquals(prototype.getPolicy(true), repo.getPolicy(true));
    }

    @Test
    void testSetSnapshotPolicy() {
        Builder builder = new Builder(prototype);
        RemoteRepository repo =
                builder.setSnapshotPolicy(prototype.getPolicy(true)).build();
        assertSame(prototype, repo);
        RepositoryPolicy policy = new RepositoryPolicy(true, "never", "fail");
        repo = builder.setSnapshotPolicy(policy).build();
        assertEquals(policy, repo.getPolicy(true));
        assertEquals(prototype.getPolicy(false), repo.getPolicy(false));
    }

    @Test
    void testSetProxy() {
        Builder builder = new Builder(prototype);
        RemoteRepository repo = builder.setProxy(prototype.getProxy()).build();
        assertSame(prototype, repo);
        Proxy proxy = new Proxy("http", "localhost", 8080);
        repo = builder.setProxy(proxy).build();
        assertEquals(proxy, repo.getProxy());
    }

    @Test
    void testSetAuthentication() {
        Builder builder = new Builder(prototype);
        RemoteRepository repo =
                builder.setAuthentication(prototype.getAuthentication()).build();
        assertSame(prototype, repo);
        Authentication auth = new Authentication() {
            public void fill(AuthenticationContext context, String key, Map<String, String> data) {}

            public void digest(AuthenticationDigest digest) {}
        };
        repo = builder.setAuthentication(auth).build();
        assertEquals(auth, repo.getAuthentication());
    }

    @Test
    void testSetMirroredRepositories() {
        Builder builder = new Builder(prototype);
        RemoteRepository repo = builder.setMirroredRepositories(prototype.getMirroredRepositories())
                .build();
        assertSame(prototype, repo);
        List<RemoteRepository> mirrored = new ArrayList<>(Arrays.asList(repo));
        repo = builder.setMirroredRepositories(mirrored).build();
        assertEquals(mirrored, repo.getMirroredRepositories());
    }

    @Test
    void testAddMirroredRepository() {
        Builder builder = new Builder(prototype);
        RemoteRepository repo = builder.addMirroredRepository(null).build();
        assertSame(prototype, repo);
        repo = builder.addMirroredRepository(prototype).build();
        assertEquals(Arrays.asList(prototype), repo.getMirroredRepositories());
    }

    @Test
    void testSetRepositoryManager() {
        Builder builder = new Builder(prototype);
        RemoteRepository repo =
                builder.setRepositoryManager(prototype.isRepositoryManager()).build();
        assertSame(prototype, repo);
        repo = builder.setRepositoryManager(!prototype.isRepositoryManager()).build();
        assertEquals(!prototype.isRepositoryManager(), repo.isRepositoryManager());
    }
}
