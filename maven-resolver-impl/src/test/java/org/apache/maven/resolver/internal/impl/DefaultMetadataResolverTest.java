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

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.maven.resolver.DefaultRepositorySystemSession;
import org.apache.maven.resolver.internal.test.util.TestFileUtils;
import org.apache.maven.resolver.internal.test.util.TestLocalRepositoryManager;
import org.apache.maven.resolver.internal.test.util.TestUtils;
import org.apache.maven.resolver.metadata.DefaultMetadata;
import org.apache.maven.resolver.metadata.Metadata;
import org.apache.maven.resolver.repository.LocalMetadataRegistration;
import org.apache.maven.resolver.repository.RemoteRepository;
import org.apache.maven.resolver.resolution.MetadataRequest;
import org.apache.maven.resolver.resolution.MetadataResult;
import org.apache.maven.resolver.spi.connector.ArtifactDownload;
import org.apache.maven.resolver.spi.connector.MetadataDownload;
import org.apache.maven.resolver.transfer.MetadataNotFoundException;
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

    private DefaultRepositorySystemSession session;

    private Metadata metadata;

    private RecordingRepositoryConnector connector;

    private TestLocalRepositoryManager lrm;

    @Before
    public void setup()
        throws Exception
    {
        session = TestUtils.newSession();
        lrm = (TestLocalRepositoryManager) session.getLocalRepositoryManager();
        connectorProvider = new StubRepositoryConnectorProvider();
        resolver = new DefaultMetadataResolver();
        resolver.setUpdateCheckManager( new StaticUpdateCheckManager( true ) );
        resolver.setRepositoryEventDispatcher( new StubRepositoryEventDispatcher() );
        resolver.setRepositoryConnectorProvider( connectorProvider );
        resolver.setRemoteRepositoryManager( new StubRemoteRepositoryManager() );
        resolver.setSyncContextFactory( new StubSyncContextFactory() );
        resolver.setOfflineController( new DefaultOfflineController() );
        repository =
            new RemoteRepository.Builder( "test-DMRT", "default",
                                          TestFileUtils.createTempDir().toURI().toURL().toString() ).build();
        metadata = new DefaultMetadata( "gid", "aid", "ver", "maven-metadata.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT );
        connector = new RecordingRepositoryConnector();
        connectorProvider.setConnector( connector );
    }

    @After
    public void teardown()
        throws Exception
    {
        TestFileUtils.deleteFile( new File( new URI( repository.getUrl() ) ) );
        TestFileUtils.deleteFile( session.getLocalRepository().getBasedir() );
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

        TestFileUtils.writeString( file, file.getAbsolutePath() );

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
        TestFileUtils.writeString( file, file.getAbsolutePath() );
        metadata.setFile( file );

        MetadataRequest request = new MetadataRequest( metadata, repository, "" );
        request.setDeleteLocalCopyIfMissing( true );

        List<MetadataResult> results = resolver.resolveMetadata( session, Arrays.asList( request ) );
        assertEquals( 1, results.size() );
        MetadataResult result = results.get( 0 );

        assertNotNull( result.getException() );
        assertFalse( file.exists() );
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
        TestFileUtils.writeString( file, file.getAbsolutePath() );

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
        TestFileUtils.writeString( file, file.getAbsolutePath() );

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
