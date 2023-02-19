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
package org.eclipse.aether.internal.impl;

import java.util.Arrays;
import java.util.List;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.eclipse.aether.util.repository.DefaultAuthenticationSelector;
import org.eclipse.aether.util.repository.DefaultMirrorSelector;
import org.eclipse.aether.util.repository.DefaultProxySelector;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class DefaultRepositorySystemTest {

    private DefaultRepositorySystem system;

    private DefaultRepositorySystemSession session;

    @Before
    public void init() {
        DefaultRemoteRepositoryManager remoteRepoManager = new DefaultRemoteRepositoryManager();
        system = new DefaultRepositorySystem();
        system.setRemoteRepositoryManager(remoteRepoManager);
        session = TestUtils.newSession();
    }

    @Test
    public void testNewResolutionRepositories() {
        Proxy proxy = new Proxy("http", "localhost", 8080);
        DefaultProxySelector proxySelector = new DefaultProxySelector();
        proxySelector.add(proxy, null);
        session.setProxySelector(proxySelector);

        Authentication auth = new AuthenticationBuilder().addUsername("user").build();
        DefaultAuthenticationSelector authSelector = new DefaultAuthenticationSelector();
        authSelector.add("mirror", auth);
        authSelector.add("test-2", auth);
        session.setAuthenticationSelector(authSelector);

        DefaultMirrorSelector mirrorSelector = new DefaultMirrorSelector();
        mirrorSelector.add("mirror", "http:void", "default", false, "test-1", null);
        session.setMirrorSelector(mirrorSelector);

        RemoteRepository rawRepo1 = new RemoteRepository.Builder("test-1", "default", "http://void").build();
        RemoteRepository rawRepo2 = new RemoteRepository.Builder("test-2", "default", "http://null").build();
        List<RemoteRepository> resolveRepos =
                system.newResolutionRepositories(session, Arrays.asList(rawRepo1, rawRepo2));
        assertNotNull(resolveRepos);
        assertEquals(2, resolveRepos.size());
        RemoteRepository resolveRepo = resolveRepos.get(0);
        assertNotNull(resolveRepo);
        assertEquals("mirror", resolveRepo.getId());
        assertSame(proxy, resolveRepo.getProxy());
        assertSame(auth, resolveRepo.getAuthentication());
        resolveRepo = resolveRepos.get(1);
        assertNotNull(resolveRepo);
        assertEquals("test-2", resolveRepo.getId());
        assertSame(proxy, resolveRepo.getProxy());
        assertSame(auth, resolveRepo.getAuthentication());
    }

    @Test
    public void testNewDeploymentRepository() {
        Proxy proxy = new Proxy("http", "localhost", 8080);
        DefaultProxySelector proxySelector = new DefaultProxySelector();
        proxySelector.add(proxy, null);
        session.setProxySelector(proxySelector);

        Authentication auth = new AuthenticationBuilder().addUsername("user").build();
        DefaultAuthenticationSelector authSelector = new DefaultAuthenticationSelector();
        authSelector.add("test", auth);
        session.setAuthenticationSelector(authSelector);

        DefaultMirrorSelector mirrorSelector = new DefaultMirrorSelector();
        mirrorSelector.add("mirror", "file:void", "default", false, "*", null);
        session.setMirrorSelector(mirrorSelector);

        RemoteRepository rawRepo = new RemoteRepository.Builder("test", "default", "http://void").build();
        RemoteRepository deployRepo = system.newDeploymentRepository(session, rawRepo);
        assertNotNull(deployRepo);
        assertSame(proxy, deployRepo.getProxy());
        assertSame(auth, deployRepo.getAuthentication());
    }
}
