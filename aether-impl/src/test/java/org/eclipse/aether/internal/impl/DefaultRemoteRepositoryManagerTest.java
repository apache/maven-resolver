/*******************************************************************************
 * Copyright (c) 2010, 2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.internal.impl;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.impl.UpdateCheck;
import org.eclipse.aether.impl.UpdateCheckManager;
import org.eclipse.aether.internal.impl.DefaultRemoteRepositoryManager;
import org.eclipse.aether.internal.test.impl.SysoutLoggerFactory;
import org.eclipse.aether.internal.test.impl.TestRepositorySystemSession;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.MirrorSelector;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.ProxySelector;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.transfer.MetadataTransferException;
import org.junit.Before;
import org.junit.Test;

/**
 * 
 */
public class DefaultRemoteRepositoryManagerTest
{

    private TestRepositorySystemSession session;

    private DefaultRemoteRepositoryManager manager;

    @Before
    public void setup()
        throws Exception
    {
        session = new TestRepositorySystemSession();
        session.setChecksumPolicy( null );
        session.setUpdatePolicy( null );
        manager = new DefaultRemoteRepositoryManager( new StubUpdateCheckManager(), null );
        manager.setLoggerFactory( new SysoutLoggerFactory() );
    }

    public void teardown()
        throws Exception
    {
        manager = null;
        session = null;
    }

    private RemoteRepository newRepo( String id, String url, boolean enabled, String updates, String checksums )
    {
        RepositoryPolicy policy = new RepositoryPolicy( enabled, updates, checksums );
        return new RemoteRepository( id, "test", url ).setPolicy( true, policy ).setPolicy( false, policy );
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

        RemoteRepository repo = new RemoteRepository( "id", "type", "http://localhost" );
        repo.setPolicy( true, snapshotPolicy );
        repo.setPolicy( false, releasePolicy );

        RepositoryPolicy effectivePolicy = manager.getPolicy( session, repo, true, true );
        assertEquals( true, effectivePolicy.isEnabled() );
        assertEquals( RepositoryPolicy.CHECKSUM_POLICY_IGNORE, effectivePolicy.getChecksumPolicy() );
        assertEquals( RepositoryPolicy.UPDATE_POLICY_ALWAYS, effectivePolicy.getUpdatePolicy() );
    }

    @Test
    public void testAggregateSimpleRepos()
    {
        RemoteRepository dominant1 = newRepo( "a", "file://", false, "", "" );

        RemoteRepository recessive1 = newRepo( "a", "http://", true, "", "" );
        RemoteRepository recessive2 = newRepo( "b", "file://", true, "", "" );

        List<RemoteRepository> result =
            manager.aggregateRepositories( session, Arrays.asList( dominant1 ),
                                           Arrays.asList( recessive1, recessive2 ), false );

        assertEquals( 2, result.size() );
        assertEqual( dominant1, result.get( 0 ) );
        assertEqual( recessive2, result.get( 1 ) );
    }

    @Test
    public void testAggregateMirrorRepos_DominantMirrorComplete()
    {
        RemoteRepository dominant1 = newRepo( "a", "http://", false, "", "" );
        RemoteRepository dominantMirror1 = newRepo( "x", "file://", false, "", "" );
        dominantMirror1.setMirroredRepositories( Arrays.asList( dominant1 ) );

        RemoteRepository recessive1 = newRepo( "a", "https://", true, "", "" );
        RemoteRepository recessiveMirror1 = newRepo( "x", "http://", true, "", "" );
        recessiveMirror1.setMirroredRepositories( Arrays.asList( recessive1 ) );

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
        RemoteRepository dominant1 = newRepo( "a", "http://", false, "", "" );
        RemoteRepository dominantMirror1 = newRepo( "x", "file://", false, "", "" );
        dominantMirror1.setMirroredRepositories( Arrays.asList( dominant1 ) );

        RemoteRepository recessive1 = newRepo( "a", "https://", true, "", "" );
        RemoteRepository recessive2 = newRepo( "b", "https://", true, "", "" );
        RemoteRepository recessiveMirror1 = newRepo( "x", "http://", true, "", "" );
        recessiveMirror1.setMirroredRepositories( Arrays.asList( recessive1, recessive2 ) );

        List<RemoteRepository> result =
            manager.aggregateRepositories( session, Arrays.asList( dominantMirror1 ),
                                           Arrays.asList( recessiveMirror1 ), false );

        assertEquals( 1, result.size() );
        assertEqual( newRepo( "x", "file://", true, "", "" ), result.get( 0 ) );
        assertEquals( 2, result.get( 0 ).getMirroredRepositories().size() );
        assertEquals( dominant1, result.get( 0 ).getMirroredRepositories().get( 0 ) );
        assertEquals( recessive2, result.get( 0 ).getMirroredRepositories().get( 1 ) );
    }

    @Test
    public void testMirrorAuthentication()
    {
        final RemoteRepository repo = newRepo( "a", "http://", false, "", "" );
        final RemoteRepository mirror = newRepo( "a", "http://", false, "", "" );
        mirror.setAuthentication( new Authentication( "username", "password" ) );
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
        assertEquals( "username", result.get( 0 ).getAuthentication().getUsername() );
        assertEquals( "password", result.get( 0 ).getAuthentication().getPassword() );
    }

    @Test
    public void testMirrorProxy()
    {
        final RemoteRepository repo = newRepo( "a", "http://", false, "", "" );
        final RemoteRepository mirror = newRepo( "a", "http://", false, "", "" );
        mirror.setProxy( new Proxy( "http", "host", 2011, null ) );
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
        final RemoteRepository repo = newRepo( "a", "http://", false, "", "" );
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

    private static class StubUpdateCheckManager
        implements UpdateCheckManager
    {

        public String getEffectiveUpdatePolicy( RepositorySystemSession session, String policy1, String policy2 )
        {
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
                return Integer.valueOf( s );
            }
            else
            {
                // assume "never"
                return Integer.MAX_VALUE;
            }
        }

        public boolean isUpdatedRequired( RepositorySystemSession session, long lastModified, String policy )
        {
            return false;
        }

        public void checkArtifact( RepositorySystemSession session,
                                   UpdateCheck<Artifact, ArtifactTransferException> check )
        {
        }

        public void touchArtifact( RepositorySystemSession session,
                                   UpdateCheck<Artifact, ArtifactTransferException> check )
        {
        }

        public void checkMetadata( RepositorySystemSession session,
                                   UpdateCheck<Metadata, MetadataTransferException> check )
        {
        }

        public void touchMetadata( RepositorySystemSession session,
                                   UpdateCheck<Metadata, MetadataTransferException> check )
        {
        }

    }

}
