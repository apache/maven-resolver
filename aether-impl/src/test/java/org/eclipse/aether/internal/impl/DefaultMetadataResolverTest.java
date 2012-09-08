/*******************************************************************************
 * Copyright (c) 2010, 2012 Sonatype, Inc.
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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.eclipse.aether.internal.impl.DefaultMetadataResolver;
import org.eclipse.aether.internal.test.impl.TestLocalRepositoryManager;
import org.eclipse.aether.internal.test.impl.TestRepositorySystemSession;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.LocalMetadataRegistration;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.MetadataRequest;
import org.eclipse.aether.resolution.MetadataResult;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.MetadataDownload;
import org.eclipse.aether.transfer.MetadataNotFoundException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 */
public class DefaultMetadataResolverTest
{

    private DefaultMetadataResolver resolver;

    private StubRepositoryConnectorProvider connectorProvider;

    private RemoteRepository repository;

    private TestRepositorySystemSession session;

    private Metadata metadata;

    private RecordingRepositoryConnector connector;

    private TestLocalRepositoryManager lrm;

    @Before
    public void setup()
        throws Exception
    {
        session = new TestRepositorySystemSession();
        lrm = (TestLocalRepositoryManager) session.getLocalRepositoryManager();
        connectorProvider = new StubRepositoryConnectorProvider();
        resolver = new DefaultMetadataResolver();
        resolver.setUpdateCheckManager( new StaticUpdateCheckManager( true ) );
        resolver.setRepositoryEventDispatcher( new StubRepositoryEventDispatcher() );
        resolver.setRepositoryConnectorProvider( connectorProvider );
        resolver.setRemoteRepositoryManager( new StubRemoteRepositoryManager() );
        resolver.setSyncContextFactory( new StubSyncContextFactory() );
        repository =
            new RemoteRepository( "test-DMRT", "default", TestFileUtils.createTempDir().toURI().toURL().toString() );
        metadata = new DefaultMetadata( "gid", "aid", "ver", "maven-metadata.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT );
        connector = new RecordingRepositoryConnector();
        connectorProvider.setConnector( connector );
    }

    @After
    public void teardown()
        throws Exception
    {
        TestFileUtils.delete( new File( new URI( repository.getUrl() ) ) );
        TestFileUtils.delete( session.getLocalRepository().getBasedir() );
    }

    @Test
    public void testNoRepositoryFailing()
    {
        MetadataRequest request = new MetadataRequest( metadata, null, "" );
        List<MetadataResult> results = resolver.resolveMetadata( session, Arrays.asList( request ) );

        assertEquals( 1, results.size() );

        MetadataResult result = results.get( 0 );
        assertSame( request, result.getRequest() );
        assertNotNull( "" + ( result.getMetadata() != null ? result.getMetadata().getFile() : result.getMetadata() ),
                       result.getException() );
        assertEquals( MetadataNotFoundException.class, result.getException().getClass() );

        assertNull( result.getMetadata() );
    }

    @Test
    public void testResolve()
        throws IOException
    {
        connector.setExpectGet( metadata );

        // prepare "download"
        File file =
            new File( session.getLocalRepository().getBasedir(),
                      session.getLocalRepositoryManager().getPathForRemoteMetadata( metadata, repository, "" ) );

        TestFileUtils.write( file.getAbsolutePath(), file );

        MetadataRequest request = new MetadataRequest( metadata, repository, "" );
        List<MetadataResult> results = resolver.resolveMetadata( session, Arrays.asList( request ) );

        assertEquals( 1, results.size() );

        MetadataResult result = results.get( 0 );
        assertSame( request, result.getRequest() );
        assertNull( result.getException() );
        assertNotNull( result.getMetadata() );
        assertNotNull( result.getMetadata().getFile() );

        assertEquals( file, result.getMetadata().getFile() );
        assertEquals( metadata, result.getMetadata().setFile( null ) );

        connector.assertSeenExpected();
        Set<Metadata> metadataRegistration =
            ( (TestLocalRepositoryManager) session.getLocalRepositoryManager() ).getMetadataRegistration();
        assertTrue( metadataRegistration.contains( metadata ) );
        assertEquals( 1, metadataRegistration.size() );
    }

    @Test
    public void testRemoveMetadataIfMissing()
        throws IOException
    {
        connector = new RecordingRepositoryConnector()
        {

            @Override
            public void get( Collection<? extends ArtifactDownload> artifactDownloads,
                             Collection<? extends MetadataDownload> metadataDownloads )
            {
                super.get( artifactDownloads, metadataDownloads );
                for ( MetadataDownload d : metadataDownloads )
                {
                    d.setException( new MetadataNotFoundException( metadata, repository ) );
                }
            }

        };
        connectorProvider.setConnector( connector );

        File file =
            new File( session.getLocalRepository().getBasedir(),
                      session.getLocalRepositoryManager().getPathForRemoteMetadata( metadata, repository, "" ) );
        TestFileUtils.write( file.getAbsolutePath(), file );
        metadata.setFile( file );

        MetadataRequest request = new MetadataRequest( metadata, repository, "" );
        request.setDeleteLocalCopyIfMissing( true );

        List<MetadataResult> results = resolver.resolveMetadata( session, Arrays.asList( request ) );
        assertEquals( 1, results.size() );
        MetadataResult result = results.get( 0 );

        assertNotNull( result.getException() );
        assertEquals( false, file.exists() );
    }

    @Test
    public void testOfflineSessionResolveMetadataMissing()
    {
        session.setOffline( true );
        MetadataRequest request = new MetadataRequest( metadata, repository, "" );
        List<MetadataResult> results = resolver.resolveMetadata( session, Arrays.asList( request ) );

        assertEquals( 1, results.size() );

        MetadataResult result = results.get( 0 );
        assertSame( request, result.getRequest() );
        assertNotNull( result.getException() );
        assertNull( result.getMetadata() );

        connector.assertSeenExpected();
    }

    @Test
    public void testOfflineSessionResolveMetadata()
        throws IOException
    {
        session.setOffline( true );

        String path = session.getLocalRepositoryManager().getPathForRemoteMetadata( metadata, repository, "" );
        File file = new File( session.getLocalRepository().getBasedir(), path );
        TestFileUtils.write( file.getAbsolutePath(), file );

        // set file to use in TestLRM find()
        metadata = metadata.setFile( file );

        MetadataRequest request = new MetadataRequest( metadata, repository, "" );
        List<MetadataResult> results = resolver.resolveMetadata( session, Arrays.asList( request ) );

        assertEquals( 1, results.size() );
        MetadataResult result = results.get( 0 );
        assertSame( request, result.getRequest() );
        assertNull( String.valueOf( result.getException() ), result.getException() );
        assertNotNull( result.getMetadata() );
        assertNotNull( result.getMetadata().getFile() );

        assertEquals( file, result.getMetadata().getFile() );
        assertEquals( metadata.setFile( null ), result.getMetadata().setFile( null ) );

        connector.assertSeenExpected();
    }

    @Test
    public void testFavorLocal()
        throws IOException
    {
        lrm.add( session, new LocalMetadataRegistration( metadata ) );
        String path = session.getLocalRepositoryManager().getPathForLocalMetadata( metadata );
        File file = new File( session.getLocalRepository().getBasedir(), path );
        TestFileUtils.write( file.getAbsolutePath(), file );

        MetadataRequest request = new MetadataRequest( metadata, repository, "" );
        request.setFavorLocalRepository( true );
        resolver.setUpdateCheckManager( new StaticUpdateCheckManager( true, true ) );

        List<MetadataResult> results = resolver.resolveMetadata( session, Arrays.asList( request ) );

        assertEquals( 1, results.size() );
        MetadataResult result = results.get( 0 );
        assertSame( request, result.getRequest() );
        assertNull( String.valueOf( result.getException() ), result.getException() );

        connector.assertSeenExpected();
    }
}
