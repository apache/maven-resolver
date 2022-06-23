package org.apache.maven.resolver.internal.impl;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *  http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import static java.util.Objects.requireNonNull;
import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.maven.resolver.DefaultRepositorySystemSession;
import org.apache.maven.resolver.RepositorySystemSession;
import org.apache.maven.resolver.impl.UpdatePolicyAnalyzer;
import org.apache.maven.resolver.internal.test.util.TestUtils;
import org.apache.maven.resolver.repository.MirrorSelector;
import org.apache.maven.resolver.repository.Proxy;
import org.apache.maven.resolver.repository.ProxySelector;
import org.apache.maven.resolver.repository.RemoteRepository;
import org.apache.maven.resolver.repository.RepositoryPolicy;
import org.apache.maven.resolver.util.repository.AuthenticationBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * 
 */
public class DefaultRemoteRepositoryManagerTest
{

    private DefaultRepositorySystemSession session;

    private DefaultRemoteRepositoryManager manager;

    @Before
    public void setup()
    {
        session = TestUtils.newSession();
        session.setChecksumPolicy( null );
        session.setUpdatePolicy( null );
        manager = new DefaultRemoteRepositoryManager();
        manager.setUpdatePolicyAnalyzer( new StubUpdatePolicyAnalyzer() );
        manager.setChecksumPolicyProvider( new DefaultChecksumPolicyProvider() );
    }

    @After
    public void teardown()
    {
        manager = null;
        session = null;
    }

    private RemoteRepository.Builder newRepo( String id, String url, boolean enabled, String updates, String checksums )
    {
        RepositoryPolicy policy = new RepositoryPolicy( enabled, updates, checksums );
        return new RemoteRepository.Builder( id, "test", url ).setPolicy( policy );
    }

    private void assertEqual( RemoteRepository expected, RemoteRepository actual )
    {
        assertEquals( "id", expected.getId(), actual.getId() );
        assertEquals( "url", expected.getUrl(), actual.getUrl() );
        assertEquals( "type", expected.getContentType(), actual.getContentType() );
        assertEqual( expected.getPolicy( false ), actual.getPolicy( false ) );
        assertEqual( expected.getPolicy( true ), actual.getPolicy( true ) );
    }

    private void assertEqual( RepositoryPolicy expected, RepositoryPolicy actual )
    {
        assertEquals( "enabled", expected.isEnabled(), actual.isEnabled() );
        assertEquals( "checksums", expected.getChecksumPolicy(), actual.getChecksumPolicy() );
        assertEquals( "updates", expected.getUpdatePolicy(), actual.getUpdatePolicy() );
    }

    @Test
    public void testGetPolicy()
    {
        RepositoryPolicy snapshotPolicy =
            new RepositoryPolicy( true, RepositoryPolicy.UPDATE_POLICY_ALWAYS, RepositoryPolicy.CHECKSUM_POLICY_IGNORE );
        RepositoryPolicy releasePolicy =
            new RepositoryPolicy( true, RepositoryPolicy.UPDATE_POLICY_NEVER, RepositoryPolicy.CHECKSUM_POLICY_FAIL );

        RemoteRepository repo = new RemoteRepository.Builder( "id", "type", "http://localhost" ) //
        .setSnapshotPolicy( snapshotPolicy ).setReleasePolicy( releasePolicy ).build();

        RepositoryPolicy effectivePolicy = manager.getPolicy( session, repo, true, true );
        assertTrue( effectivePolicy.isEnabled() );
        assertEquals( RepositoryPolicy.CHECKSUM_POLICY_IGNORE, effectivePolicy.getChecksumPolicy() );
        assertEquals( RepositoryPolicy.UPDATE_POLICY_ALWAYS, effectivePolicy.getUpdatePolicy() );
    }

    @Test
    public void testAggregateSimpleRepos()
    {
        RemoteRepository dominant1 = newRepo( "a", "file://", false, "", "" ).build();

        RemoteRepository recessive1 = newRepo( "a", "http://", true, "", "" ).build();
        RemoteRepository recessive2 = newRepo( "b", "file://", true, "", "" ).build();

        List<RemoteRepository> result =
            manager.aggregateRepositories( session, Arrays.asList( dominant1 ),
                                           Arrays.asList( recessive1, recessive2 ), false );

        assertEquals( 2, result.size() );
        assertEqual( dominant1, result.get( 0 ) );
        assertEqual( recessive2, result.get( 1 ) );
    }

    @Test
    public void testAggregateSimpleRepos_MustKeepDisabledRecessiveRepo()
    {
        RemoteRepository dominant = newRepo( "a", "file://", true, "", "" ).build();

        RemoteRepository recessive1 = newRepo( "b", "http://", false, "", "" ).build();

        List<RemoteRepository> result =
            manager.aggregateRepositories( session, Arrays.asList( dominant ), Arrays.asList( recessive1 ), false );

        RemoteRepository recessive2 = newRepo( recessive1.getId(), "http://", true, "", "" ).build();

        result = manager.aggregateRepositories( session, result, Arrays.asList( recessive2 ), false );

        assertEquals( 2, result.size() );
        assertEqual( dominant, result.get( 0 ) );
        assertEqual( recessive1, result.get( 1 ) );
    }

    @Test
    public void testAggregateMirrorRepos_DominantMirrorComplete()
    {
        RemoteRepository dominant1 = newRepo( "a", "http://", false, "", "" ).build();
        RemoteRepository dominantMirror1 =
            newRepo( "x", "file://", false, "", "" ).addMirroredRepository( dominant1 ).build();

        RemoteRepository recessive1 = newRepo( "a", "https://", true, "", "" ).build();
        RemoteRepository recessiveMirror1 =
            newRepo( "x", "http://", true, "", "" ).addMirroredRepository( recessive1 ).build();

        List<RemoteRepository> result =
            manager.aggregateRepositories( session, Arrays.asList( dominantMirror1 ),
                                           Arrays.asList( recessiveMirror1 ), false );

        assertEquals( 1, result.size() );
        assertEqual( dominantMirror1, result.get( 0 ) );
        assertEquals( 1, result.get( 0 ).getMirroredRepositories().size() );
        assertEquals( dominant1, result.get( 0 ).getMirroredRepositories().get( 0 ) );
    }

    @Test
    public void testAggregateMirrorRepos_DominantMirrorIncomplete()
    {
        RemoteRepository dominant1 = newRepo( "a", "http://", false, "", "" ).build();
        RemoteRepository dominantMirror1 =
            newRepo( "x", "file://", false, "", "" ).addMirroredRepository( dominant1 ).build();

        RemoteRepository recessive1 = newRepo( "a", "https://", true, "", "" ).build();
        RemoteRepository recessive2 = newRepo( "b", "https://", true, "", "" ).build();
        RemoteRepository recessiveMirror1 =
            newRepo( "x", "http://", true, "", "" ).setMirroredRepositories( Arrays.asList( recessive1, recessive2 ) ).build();

        List<RemoteRepository> result =
            manager.aggregateRepositories( session, Arrays.asList( dominantMirror1 ),
                                           Arrays.asList( recessiveMirror1 ), false );

        assertEquals( 1, result.size() );
        assertEqual( newRepo( "x", "file://", true, "", "" ).build(), result.get( 0 ) );
        assertEquals( 2, result.get( 0 ).getMirroredRepositories().size() );
        assertEquals( dominant1, result.get( 0 ).getMirroredRepositories().get( 0 ) );
        assertEquals( recessive2, result.get( 0 ).getMirroredRepositories().get( 1 ) );
    }

    @Test
    public void testMirrorAuthentication()
    {
        final RemoteRepository repo = newRepo( "a", "http://", true, "", "" ).build();
        final RemoteRepository mirror =
            newRepo( "a", "http://", true, "", "" ).setAuthentication( new AuthenticationBuilder().addUsername( "test" ).build() ).build();
        session.setMirrorSelector( new MirrorSelector()
        {
            public RemoteRepository getMirror( RemoteRepository repository )
            {
                return mirror;
            }
        } );

        List<RemoteRepository> result =
            manager.aggregateRepositories( session, Collections.<RemoteRepository> emptyList(), Arrays.asList( repo ),
                                           true );

        assertEquals( 1, result.size() );
        assertSame( mirror.getAuthentication(), result.get( 0 ).getAuthentication() );
    }

    @Test
    public void testMirrorProxy()
    {
        final RemoteRepository repo = newRepo( "a", "http://", true, "", "" ).build();
        final RemoteRepository mirror =
            newRepo( "a", "http://", true, "", "" ).setProxy( new Proxy( "http", "host", 2011, null ) ).build();
        session.setMirrorSelector( new MirrorSelector()
        {
            public RemoteRepository getMirror( RemoteRepository repository )
            {
                return mirror;
            }
        } );

        List<RemoteRepository> result =
            manager.aggregateRepositories( session, Collections.<RemoteRepository> emptyList(), Arrays.asList( repo ),
                                           true );

        assertEquals( 1, result.size() );
        assertEquals( "http", result.get( 0 ).getProxy().getType() );
        assertEquals( "host", result.get( 0 ).getProxy().getHost() );
        assertEquals( 2011, result.get( 0 ).getProxy().getPort() );
    }

    @Test
    public void testProxySelector()
    {
        final RemoteRepository repo = newRepo( "a", "http://", true, "", "" ).build();
        final Proxy proxy = new Proxy( "http", "host", 2011, null );
        session.setProxySelector( new ProxySelector()
        {
            public Proxy getProxy( RemoteRepository repository )
            {
                return proxy;
            }
        } );
        session.setMirrorSelector( new MirrorSelector()
        {
            public RemoteRepository getMirror( RemoteRepository repository )
            {
                return null;
            }
        } );

        List<RemoteRepository> result =
            manager.aggregateRepositories( session, Collections.<RemoteRepository> emptyList(), Arrays.asList( repo ),
                                           true );

        assertEquals( 1, result.size() );
        assertEquals( "http", result.get( 0 ).getProxy().getType() );
        assertEquals( "host", result.get( 0 ).getProxy().getHost() );
        assertEquals( 2011, result.get( 0 ).getProxy().getPort() );
    }

    private static class StubUpdatePolicyAnalyzer
        implements UpdatePolicyAnalyzer
    {

        public String getEffectiveUpdatePolicy( RepositorySystemSession session, String policy1, String policy2 )
        {
            requireNonNull( session, "session cannot be null" );
            return ordinalOfUpdatePolicy( policy1 ) < ordinalOfUpdatePolicy( policy2 ) ? policy1 : policy2;
        }

        private int ordinalOfUpdatePolicy( String policy )
        {
            if ( RepositoryPolicy.UPDATE_POLICY_DAILY.equals( policy ) )
            {
                return 1440;
            }
            else if ( RepositoryPolicy.UPDATE_POLICY_ALWAYS.equals( policy ) )
            {
                return 0;
            }
            else if ( policy != null && policy.startsWith( RepositoryPolicy.UPDATE_POLICY_INTERVAL ) )
            {
                String s = policy.substring( RepositoryPolicy.UPDATE_POLICY_INTERVAL.length() + 1 );
                return Integer.parseInt( s );
            }
            else
            {
                // assume "never"
                return Integer.MAX_VALUE;
            }
        }

        public boolean isUpdatedRequired( RepositorySystemSession session, long lastModified, String policy )
        {
            requireNonNull( session, "session cannot be null" );
            return false;
        }

    }

}
