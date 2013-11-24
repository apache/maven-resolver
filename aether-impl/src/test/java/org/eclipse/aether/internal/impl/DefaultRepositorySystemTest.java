/*******************************************************************************
 * Copyright (c) 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.internal.impl;

import static org.junit.Assert.*;

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

public class DefaultRepositorySystemTest
{

    private DefaultRepositorySystem system;

    private DefaultRepositorySystemSession session;

    @Before
    public void init()
    {
        DefaultRemoteRepositoryManager remoteRepoManager = new DefaultRemoteRepositoryManager();
        system = new DefaultRepositorySystem();
        system.setRemoteRepositoryManager( remoteRepoManager );
        session = TestUtils.newSession();
    }

    @Test
    public void testNewResolutionRepositories()
    {
        Proxy proxy = new Proxy( "http", "localhost", 8080 );
        DefaultProxySelector proxySelector = new DefaultProxySelector();
        proxySelector.add( proxy, null );
        session.setProxySelector( proxySelector );

        Authentication auth = new AuthenticationBuilder().addUsername( "user" ).build();
        DefaultAuthenticationSelector authSelector = new DefaultAuthenticationSelector();
        authSelector.add( "mirror", auth );
        authSelector.add( "test-2", auth );
        session.setAuthenticationSelector( authSelector );

        DefaultMirrorSelector mirrorSelector = new DefaultMirrorSelector();
        mirrorSelector.add( "mirror", "http:void", "default", false, "test-1", null );
        session.setMirrorSelector( mirrorSelector );

        RemoteRepository rawRepo1 = new RemoteRepository.Builder( "test-1", "default", "http://void" ).build();
        RemoteRepository rawRepo2 = new RemoteRepository.Builder( "test-2", "default", "http://null" ).build();
        List<RemoteRepository> resolveRepos =
            system.newResolutionRepositories( session, Arrays.asList( rawRepo1, rawRepo2 ) );
        assertNotNull( resolveRepos );
        assertEquals( 2, resolveRepos.size() );
        RemoteRepository resolveRepo = resolveRepos.get( 0 );
        assertNotNull( resolveRepo );
        assertEquals( "mirror", resolveRepo.getId() );
        assertSame( proxy, resolveRepo.getProxy() );
        assertSame( auth, resolveRepo.getAuthentication() );
        resolveRepo = resolveRepos.get( 1 );
        assertNotNull( resolveRepo );
        assertEquals( "test-2", resolveRepo.getId() );
        assertSame( proxy, resolveRepo.getProxy() );
        assertSame( auth, resolveRepo.getAuthentication() );
    }

    @Test
    public void testNewDeploymentRepository()
    {
        Proxy proxy = new Proxy( "http", "localhost", 8080 );
        DefaultProxySelector proxySelector = new DefaultProxySelector();
        proxySelector.add( proxy, null );
        session.setProxySelector( proxySelector );

        Authentication auth = new AuthenticationBuilder().addUsername( "user" ).build();
        DefaultAuthenticationSelector authSelector = new DefaultAuthenticationSelector();
        authSelector.add( "test", auth );
        session.setAuthenticationSelector( authSelector );

        DefaultMirrorSelector mirrorSelector = new DefaultMirrorSelector();
        mirrorSelector.add( "mirror", "file:void", "default", false, "*", null );
        session.setMirrorSelector( mirrorSelector );

        RemoteRepository rawRepo = new RemoteRepository.Builder( "test", "default", "http://void" ).build();
        RemoteRepository deployRepo = system.newDeploymentRepository( session, rawRepo );
        assertNotNull( deployRepo );
        assertSame( proxy, deployRepo.getProxy() );
        assertSame( auth, deployRepo.getAuthentication() );
    }

}
