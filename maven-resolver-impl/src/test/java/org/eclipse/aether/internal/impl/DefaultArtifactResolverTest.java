package org.eclipse.aether.internal.impl;

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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RepositoryEvent.EventType;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactProperties;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.impl.UpdateCheckManager;
import org.eclipse.aether.impl.VersionResolver;
import org.eclipse.aether.internal.impl.DefaultArtifactResolver;
import org.eclipse.aether.internal.impl.DefaultUpdateCheckManager;
import org.eclipse.aether.internal.test.util.TestFileProcessor;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.internal.test.util.TestLocalRepositoryManager;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.LocalArtifactRegistration;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalArtifactResult;
import org.eclipse.aether.repository.LocalMetadataRegistration;
import org.eclipse.aether.repository.LocalMetadataRequest;
import org.eclipse.aether.repository.LocalMetadataResult;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.repository.WorkspaceRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRequest;
import org.eclipse.aether.resolution.VersionResolutionException;
import org.eclipse.aether.resolution.VersionResult;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.MetadataDownload;
import org.eclipse.aether.transfer.ArtifactNotFoundException;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.util.repository.SimpleResolutionErrorPolicy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 */
public class DefaultArtifactResolverTest
{
    private DefaultArtifactResolver resolver;

    private DefaultRepositorySystemSession session;

    private TestLocalRepositoryManager lrm;

    private StubRepositoryConnectorProvider repositoryConnectorProvider;

    private Artifact artifact;

    private RecordingRepositoryConnector connector;

    @Before
    public void setup()
    {
        UpdateCheckManager updateCheckManager = new StaticUpdateCheckManager( true );
        repositoryConnectorProvider = new StubRepositoryConnectorProvider();
        VersionResolver versionResolver = new StubVersionResolver();
        session = TestUtils.newSession();
        lrm = (TestLocalRepositoryManager) session.getLocalRepositoryManager();
        resolver = new DefaultArtifactResolver();
        resolver.setFileProcessor( new TestFileProcessor() );
        resolver.setRepositoryEventDispatcher( new StubRepositoryEventDispatcher() );
        resolver.setVersionResolver( versionResolver );
        resolver.setUpdateCheckManager( updateCheckManager );
        resolver.setRepositoryConnectorProvider( repositoryConnectorProvider );
        resolver.setRemoteRepositoryManager( new StubRemoteRepositoryManager() );
        resolver.setSyncContextFactory( new StubSyncContextFactory() );
        resolver.setOfflineController( new DefaultOfflineController() );

        artifact = new DefaultArtifact( "gid", "aid", "", "ext", "ver" );

        connector = new RecordingRepositoryConnector();
        repositoryConnectorProvider.setConnector( connector );
    }

    @After
    public void teardown()
        throws Exception
    {
        if ( session.getLocalRepository() != null )
        {
            TestFileUtils.deleteFile( session.getLocalRepository().getBasedir() );
        }
    }

    @Test
    public void testResolveLocalArtifactSuccessful()
        throws IOException, ArtifactResolutionException
    {
        File tmpFile = TestFileUtils.createTempFile( "tmp" );
        Map<String, String> properties = new HashMap<>();
        properties.put( ArtifactProperties.LOCAL_PATH, tmpFile.getAbsolutePath() );
        artifact = artifact.setProperties( properties );

        ArtifactRequest request = new ArtifactRequest( artifact, null, "" );
        ArtifactResult result = resolver.resolveArtifact( session, request );

        assertTrue( result.getExceptions().isEmpty() );

        Artifact resolved = result.getArtifact();
        assertNotNull( resolved.getFile() );
        resolved = resolved.setFile( null );

        assertEquals( artifact, resolved );
    }

    @Test
    public void testResolveLocalArtifactUnsuccessful()
        throws IOException
    {
        File tmpFile = TestFileUtils.createTempFile( "tmp" );
        Map<String, String> properties = new HashMap<>();
        properties.put( ArtifactProperties.LOCAL_PATH, tmpFile.getAbsolutePath() );
        artifact = artifact.setProperties( properties );

        tmpFile.delete();

        ArtifactRequest request = new ArtifactRequest( artifact, null, "" );

        try
        {
            resolver.resolveArtifact( session, request );
            fail( "expected exception" );
        }
        catch ( ArtifactResolutionException e )
        {
            assertNotNull( e.getResults() );
            assertEquals( 1, e.getResults().size() );

            ArtifactResult result = e.getResults().get( 0 );

            assertSame( request, result.getRequest() );

            assertFalse( result.getExceptions().isEmpty() );
            assertTrue( result.getExceptions().get( 0 ) instanceof ArtifactNotFoundException );

            Artifact resolved = result.getArtifact();
            assertNull( resolved );
        }

    }

    @Test
    public void testResolveRemoteArtifact()
        throws ArtifactResolutionException
    {
        connector.setExpectGet( artifact );

        ArtifactRequest request = new ArtifactRequest( artifact, null, "" );
        request.addRepository( new RemoteRepository.Builder( "id", "default", "file:///" ).build() );

        ArtifactResult result = resolver.resolveArtifact( session, request );

        assertTrue( result.getExceptions().isEmpty() );

        Artifact resolved = result.getArtifact();
        assertNotNull( resolved.getFile() );

        resolved = resolved.setFile( null );
        assertEquals( artifact, resolved );

        connector.assertSeenExpected();
    }

    @Test
    public void testResolveRemoteArtifactUnsuccessful()
    {
        RecordingRepositoryConnector connector = new RecordingRepositoryConnector()
        {

            @Override
            public void get( Collection<? extends ArtifactDownload> artifactDownloads,
                             Collection<? extends MetadataDownload> metadataDownloads )
            {
                super.get( artifactDownloads, metadataDownloads );
                ArtifactDownload download = artifactDownloads.iterator().next();
                ArtifactTransferException exception =
                    new ArtifactNotFoundException( download.getArtifact(), null, "not found" );
                download.setException( exception );
            }

        };

        connector.fail = true;
        connector.setExpectGet( artifact );
        repositoryConnectorProvider.setConnector( connector );

        ArtifactRequest request = new ArtifactRequest( artifact, null, "" );
        request.addRepository( new RemoteRepository.Builder( "id", "default", "file:///" ).build() );

        try
        {
            resolver.resolveArtifact( session, request );
            fail( "expected exception" );
        }
        catch ( ArtifactResolutionException e )
        {
            connector.assertSeenExpected();
            assertNotNull( e.getResults() );
            assertEquals( 1, e.getResults().size() );

            ArtifactResult result = e.getResults().get( 0 );

            assertSame( request, result.getRequest() );

            assertFalse( result.getExceptions().isEmpty() );
            assertTrue( result.getExceptions().get( 0 ) instanceof ArtifactNotFoundException );

            Artifact resolved = result.getArtifact();
            assertNull( resolved );
        }

    }

    @Test
    public void testArtifactNotFoundCache()
        throws Exception
    {
        RecordingRepositoryConnector connector = new RecordingRepositoryConnector()
        {
            @Override
            public void get( Collection<? extends ArtifactDownload> artifactDownloads,
                             Collection<? extends MetadataDownload> metadataDownloads )
            {
                super.get( artifactDownloads, metadataDownloads );
                for ( ArtifactDownload download : artifactDownloads )
                {
                    download.getFile().delete();
                    ArtifactTransferException exception =
                        new ArtifactNotFoundException( download.getArtifact(), null, "not found" );
                    download.setException( exception );
                }
            }
        };

        repositoryConnectorProvider.setConnector( connector );
        resolver.setUpdateCheckManager( new DefaultUpdateCheckManager().setUpdatePolicyAnalyzer( new DefaultUpdatePolicyAnalyzer() ) );

        session.setResolutionErrorPolicy( new SimpleResolutionErrorPolicy( true, false ) );
        session.setUpdatePolicy( RepositoryPolicy.UPDATE_POLICY_NEVER );

        RemoteRepository remoteRepo = new RemoteRepository.Builder( "id", "default", "file:///" ).build();

        Artifact artifact1 = artifact;
        Artifact artifact2 = artifact.setVersion( "ver2" );

        ArtifactRequest request1 = new ArtifactRequest( artifact1, Arrays.asList( remoteRepo ), "" );
        ArtifactRequest request2 = new ArtifactRequest( artifact2, Arrays.asList( remoteRepo ), "" );

        connector.setExpectGet( artifact1, artifact2 );
        try
        {
            resolver.resolveArtifacts( session, Arrays.asList( request1, request2 ) );
            fail( "expected exception" );
        }
        catch ( ArtifactResolutionException e )
        {
            connector.assertSeenExpected();
        }

        TestFileUtils.writeString( new File( lrm.getRepository().getBasedir(), lrm.getPathForLocalArtifact( artifact2 ) ),
                             "artifact" );
        lrm.setArtifactAvailability( artifact2, false );

        DefaultUpdateCheckManagerTest.resetSessionData( session );
        connector.resetActual();
        connector.setExpectGet( new Artifact[0] );
        try
        {
            resolver.resolveArtifacts( session, Arrays.asList( request1, request2 ) );
            fail( "expected exception" );
        }
        catch ( ArtifactResolutionException e )
        {
            connector.assertSeenExpected();
            for ( ArtifactResult result : e.getResults() )
            {
                Throwable t = result.getExceptions().get( 0 );
                assertEquals( t.toString(), true, t instanceof ArtifactNotFoundException );
                assertEquals( t.toString(), true, t.getMessage().contains( "cached" ) );
            }
        }
    }

    @Test
    public void testResolveFromWorkspace()
        throws IOException, ArtifactResolutionException
    {
        WorkspaceReader workspace = new WorkspaceReader()
        {

            public WorkspaceRepository getRepository()
            {
                return new WorkspaceRepository( "default" );
            }

            public List<String> findVersions( Artifact artifact )
            {
                return Arrays.asList( artifact.getVersion() );
            }

            public File findArtifact( Artifact artifact )
            {
                try
                {
                    return TestFileUtils.createTempFile( artifact.toString() );
                }
                catch ( IOException e )
                {
                    throw new RuntimeException( e.getMessage(), e );
                }
            }
        };
        session.setWorkspaceReader( workspace );

        ArtifactRequest request = new ArtifactRequest( artifact, null, "" );
        request.addRepository( new RemoteRepository.Builder( "id", "default", "file:///" ).build() );

        ArtifactResult result = resolver.resolveArtifact( session, request );

        assertTrue( result.getExceptions().isEmpty() );

        Artifact resolved = result.getArtifact();
        assertNotNull( resolved.getFile() );

        assertEquals( resolved.toString(), TestFileUtils.readString( resolved.getFile() ) );

        resolved = resolved.setFile( null );
        assertEquals( artifact, resolved );

        connector.assertSeenExpected();
    }

    @Test
    public void testResolveFromWorkspaceFallbackToRepository()
        throws ArtifactResolutionException
    {
        WorkspaceReader workspace = new WorkspaceReader()
        {

            public WorkspaceRepository getRepository()
            {
                return new WorkspaceRepository( "default" );
            }

            public List<String> findVersions( Artifact artifact )
            {
                return Arrays.asList( artifact.getVersion() );
            }

            public File findArtifact( Artifact artifact )
            {
                return null;
            }
        };
        session.setWorkspaceReader( workspace );

        connector.setExpectGet( artifact );
        repositoryConnectorProvider.setConnector( connector );

        ArtifactRequest request = new ArtifactRequest( artifact, null, "" );
        request.addRepository( new RemoteRepository.Builder( "id", "default", "file:///" ).build() );

        ArtifactResult result = resolver.resolveArtifact( session, request );

        assertTrue( "exception on resolveArtifact", result.getExceptions().isEmpty() );

        Artifact resolved = result.getArtifact();
        assertNotNull( resolved.getFile() );

        resolved = resolved.setFile( null );
        assertEquals( artifact, resolved );

        connector.assertSeenExpected();
    }

    @Test
    public void testRepositoryEventsSuccessfulLocal()
        throws ArtifactResolutionException, IOException
    {
        RecordingRepositoryListener listener = new RecordingRepositoryListener();
        session.setRepositoryListener( listener );

        File tmpFile = TestFileUtils.createTempFile( "tmp" );
        Map<String, String> properties = new HashMap<>();
        properties.put( ArtifactProperties.LOCAL_PATH, tmpFile.getAbsolutePath() );
        artifact = artifact.setProperties( properties );

        ArtifactRequest request = new ArtifactRequest( artifact, null, "" );
        resolver.resolveArtifact( session, request );

        List<RepositoryEvent> events = listener.getEvents();
        assertEquals( 2, events.size() );
        RepositoryEvent event = events.get( 0 );
        assertEquals( EventType.ARTIFACT_RESOLVING, event.getType() );
        assertNull( event.getException() );
        assertEquals( artifact, event.getArtifact() );

        event = events.get( 1 );
        assertEquals( EventType.ARTIFACT_RESOLVED, event.getType() );
        assertNull( event.getException() );
        assertEquals( artifact, event.getArtifact().setFile( null ) );
    }

    @Test
    public void testRepositoryEventsUnsuccessfulLocal()
    {
        RecordingRepositoryListener listener = new RecordingRepositoryListener();
        session.setRepositoryListener( listener );

        Map<String, String> properties = new HashMap<>();
        properties.put( ArtifactProperties.LOCAL_PATH, "doesnotexist" );
        artifact = artifact.setProperties( properties );

        ArtifactRequest request = new ArtifactRequest( artifact, null, "" );
        try
        {
            resolver.resolveArtifact( session, request );
            fail( "expected exception" );
        }
        catch ( ArtifactResolutionException ignored )
        {
        }

        List<RepositoryEvent> events = listener.getEvents();
        assertEquals( 2, events.size() );

        RepositoryEvent event = events.get( 0 );
        assertEquals( artifact, event.getArtifact() );
        assertEquals( EventType.ARTIFACT_RESOLVING, event.getType() );

        event = events.get( 1 );
        assertEquals( artifact, event.getArtifact() );
        assertEquals( EventType.ARTIFACT_RESOLVED, event.getType() );
        assertNotNull( event.getException() );
        assertEquals( 1, event.getExceptions().size() );

    }

    @Test
    public void testRepositoryEventsSuccessfulRemote()
        throws ArtifactResolutionException
    {
        RecordingRepositoryListener listener = new RecordingRepositoryListener();
        session.setRepositoryListener( listener );

        ArtifactRequest request = new ArtifactRequest( artifact, null, "" );
        request.addRepository( new RemoteRepository.Builder( "id", "default", "file:///" ).build() );

        resolver.resolveArtifact( session, request );

        List<RepositoryEvent> events = listener.getEvents();
        assertEquals( events.toString(), 4, events.size() );
        RepositoryEvent event = events.get( 0 );
        assertEquals( EventType.ARTIFACT_RESOLVING, event.getType() );
        assertNull( event.getException() );
        assertEquals( artifact, event.getArtifact() );

        event = events.get( 1 );
        assertEquals( EventType.ARTIFACT_DOWNLOADING, event.getType() );
        assertNull( event.getException() );
        assertEquals( artifact, event.getArtifact().setFile( null ) );

        event = events.get( 2 );
        assertEquals( EventType.ARTIFACT_DOWNLOADED, event.getType() );
        assertNull( event.getException() );
        assertEquals( artifact, event.getArtifact().setFile( null ) );

        event = events.get( 3 );
        assertEquals( EventType.ARTIFACT_RESOLVED, event.getType() );
        assertNull( event.getException() );
        assertEquals( artifact, event.getArtifact().setFile( null ) );
    }

    @Test
    public void testRepositoryEventsUnsuccessfulRemote()
    {
        RecordingRepositoryConnector connector = new RecordingRepositoryConnector()
        {

            @Override
            public void get( Collection<? extends ArtifactDownload> artifactDownloads,
                             Collection<? extends MetadataDownload> metadataDownloads )
            {
                super.get( artifactDownloads, metadataDownloads );
                ArtifactDownload download = artifactDownloads.iterator().next();
                ArtifactTransferException exception =
                    new ArtifactNotFoundException( download.getArtifact(), null, "not found" );
                download.setException( exception );
            }

        };
        connector.fail = true;
        repositoryConnectorProvider.setConnector( connector );

        RecordingRepositoryListener listener = new RecordingRepositoryListener();
        session.setRepositoryListener( listener );

        ArtifactRequest request = new ArtifactRequest( artifact, null, "" );
        request.addRepository( new RemoteRepository.Builder( "id", "default", "file:///" ).build() );

        try
        {
            resolver.resolveArtifact( session, request );
            fail( "expected exception" );
        }
        catch ( ArtifactResolutionException ignored )
        {
        }

        List<RepositoryEvent> events = listener.getEvents();
        assertEquals( events.toString(), 4, events.size() );

        RepositoryEvent event = events.get( 0 );
        assertEquals( artifact, event.getArtifact() );
        assertEquals( EventType.ARTIFACT_RESOLVING, event.getType() );

        event = events.get( 1 );
        assertEquals( artifact, event.getArtifact() );
        assertEquals( EventType.ARTIFACT_DOWNLOADING, event.getType() );

        event = events.get( 2 );
        assertEquals( artifact, event.getArtifact() );
        assertEquals( EventType.ARTIFACT_DOWNLOADED, event.getType() );
        assertNotNull( event.getException() );
        assertEquals( 1, event.getExceptions().size() );

        event = events.get( 3 );
        assertEquals( artifact, event.getArtifact() );
        assertEquals( EventType.ARTIFACT_RESOLVED, event.getType() );
        assertNotNull( event.getException() );
        assertEquals( 1, event.getExceptions().size() );
    }

    @Test
    public void testVersionResolverFails()
    {
        resolver.setVersionResolver( new VersionResolver()
        {

            public VersionResult resolveVersion( RepositorySystemSession session, VersionRequest request )
                throws VersionResolutionException
            {
                throw new VersionResolutionException( new VersionResult( request ) );
            }
        } );

        ArtifactRequest request = new ArtifactRequest( artifact, null, "" );
        try
        {
            resolver.resolveArtifact( session, request );
            fail( "expected exception" );
        }
        catch ( ArtifactResolutionException e )
        {
            connector.assertSeenExpected();
            assertNotNull( e.getResults() );
            assertEquals( 1, e.getResults().size() );

            ArtifactResult result = e.getResults().get( 0 );

            assertSame( request, result.getRequest() );

            assertFalse( result.getExceptions().isEmpty() );
            assertTrue( result.getExceptions().get( 0 ) instanceof VersionResolutionException );

            Artifact resolved = result.getArtifact();
            assertNull( resolved );
        }
    }

    @Test
    public void testRepositoryEventsOnVersionResolverFail()
    {
        resolver.setVersionResolver( new VersionResolver()
        {

            public VersionResult resolveVersion( RepositorySystemSession session, VersionRequest request )
                throws VersionResolutionException
            {
                throw new VersionResolutionException( new VersionResult( request ) );
            }
        } );

        RecordingRepositoryListener listener = new RecordingRepositoryListener();
        session.setRepositoryListener( listener );

        ArtifactRequest request = new ArtifactRequest( artifact, null, "" );
        try
        {
            resolver.resolveArtifact( session, request );
            fail( "expected exception" );
        }
        catch ( ArtifactResolutionException ignored )
        {
        }

        List<RepositoryEvent> events = listener.getEvents();
        assertEquals( 2, events.size() );

        RepositoryEvent event = events.get( 0 );
        assertEquals( artifact, event.getArtifact() );
        assertEquals( EventType.ARTIFACT_RESOLVING, event.getType() );

        event = events.get( 1 );
        assertEquals( artifact, event.getArtifact() );
        assertEquals( EventType.ARTIFACT_RESOLVED, event.getType() );
        assertNotNull( event.getException() );
        assertEquals( 1, event.getExceptions().size() );
    }

    @Test
    public void testLocalArtifactAvailable()
        throws ArtifactResolutionException
    {
        session.setLocalRepositoryManager( new LocalRepositoryManager()
        {

            public LocalRepository getRepository()
            {
                return null;
            }

            public String getPathForRemoteMetadata( Metadata metadata, RemoteRepository repository, String context )
            {
                return null;
            }

            public String getPathForRemoteArtifact( Artifact artifact, RemoteRepository repository, String context )
            {
                return null;
            }

            public String getPathForLocalMetadata( Metadata metadata )
            {
                return null;
            }

            public String getPathForLocalArtifact( Artifact artifact )
            {
                return null;
            }

            public LocalArtifactResult find( RepositorySystemSession session, LocalArtifactRequest request )
            {

                LocalArtifactResult result = new LocalArtifactResult( request );
                result.setAvailable( true );
                try
                {
                    result.setFile( TestFileUtils.createTempFile( "" ) );
                }
                catch ( IOException e )
                {
                    e.printStackTrace();
                }
                return result;
            }

            public void add( RepositorySystemSession session, LocalArtifactRegistration request )
            {
            }

            public LocalMetadataResult find( RepositorySystemSession session, LocalMetadataRequest request )
            {
                LocalMetadataResult result = new LocalMetadataResult( request );
                try
                {
                    result.setFile( TestFileUtils.createTempFile( "" ) );
                }
                catch ( IOException e )
                {
                    e.printStackTrace();
                }
                return result;
            }

            public void add( RepositorySystemSession session, LocalMetadataRegistration request )
            {
            }
        } );

        ArtifactRequest request = new ArtifactRequest( artifact, null, "" );
        request.addRepository( new RemoteRepository.Builder( "id", "default", "file:///" ).build() );

        ArtifactResult result = resolver.resolveArtifact( session, request );

        assertTrue( result.getExceptions().isEmpty() );

        Artifact resolved = result.getArtifact();
        assertNotNull( resolved.getFile() );

        resolved = resolved.setFile( null );
        assertEquals( artifact, resolved );

    }

    @Test
    public void testFindInLocalRepositoryWhenVersionWasFoundInLocalRepository()
        throws ArtifactResolutionException
    {
        session.setLocalRepositoryManager( new LocalRepositoryManager()
        {

            public LocalRepository getRepository()
            {
                return null;
            }

            public String getPathForRemoteMetadata( Metadata metadata, RemoteRepository repository, String context )
            {
                return null;
            }

            public String getPathForRemoteArtifact( Artifact artifact, RemoteRepository repository, String context )
            {
                return null;
            }

            public String getPathForLocalMetadata( Metadata metadata )
            {
                return null;
            }

            public String getPathForLocalArtifact( Artifact artifact )
            {
                return null;
            }

            public LocalArtifactResult find( RepositorySystemSession session, LocalArtifactRequest request )
            {

                LocalArtifactResult result = new LocalArtifactResult( request );
                result.setAvailable( false );
                try
                {
                    result.setFile( TestFileUtils.createTempFile( "" ) );
                }
                catch ( IOException e )
                {
                    e.printStackTrace();
                }
                return result;
            }

            public void add( RepositorySystemSession session, LocalArtifactRegistration request )
            {
            }

            public LocalMetadataResult find( RepositorySystemSession session, LocalMetadataRequest request )
            {
                return new LocalMetadataResult( request );
            }

            public void add( RepositorySystemSession session, LocalMetadataRegistration request )
            {
            }
        } );
        ArtifactRequest request = new ArtifactRequest( artifact, null, "" );
        request.addRepository( new RemoteRepository.Builder( "id", "default", "file:///" ).build() );

        resolver.setVersionResolver( new VersionResolver()
        {

            public VersionResult resolveVersion( RepositorySystemSession session, VersionRequest request )
            {
                return new VersionResult( request ).setRepository( new LocalRepository( "id" ) ).setVersion( request.getArtifact().getVersion() );
            }
        } );
        ArtifactResult result = resolver.resolveArtifact( session, request );

        assertTrue( result.getExceptions().isEmpty() );

        Artifact resolved = result.getArtifact();
        assertNotNull( resolved.getFile() );

        resolved = resolved.setFile( null );
        assertEquals( artifact, resolved );
    }

    @Test
    public void testFindInLocalRepositoryWhenVersionRangeWasResolvedFromLocalRepository()
        throws ArtifactResolutionException
    {
        session.setLocalRepositoryManager( new LocalRepositoryManager()
        {

            public LocalRepository getRepository()
            {
                return null;
            }

            public String getPathForRemoteMetadata( Metadata metadata, RemoteRepository repository, String context )
            {
                return null;
            }

            public String getPathForRemoteArtifact( Artifact artifact, RemoteRepository repository, String context )
            {
                return null;
            }

            public String getPathForLocalMetadata( Metadata metadata )
            {
                return null;
            }

            public String getPathForLocalArtifact( Artifact artifact )
            {
                return null;
            }

            public LocalArtifactResult find( RepositorySystemSession session, LocalArtifactRequest request )
            {

                LocalArtifactResult result = new LocalArtifactResult( request );
                result.setAvailable( false );
                try
                {
                    result.setFile( TestFileUtils.createTempFile( "" ) );
                }
                catch ( IOException e )
                {
                    e.printStackTrace();
                }
                return result;
            }

            public void add( RepositorySystemSession session, LocalArtifactRegistration request )
            {
            }

            public LocalMetadataResult find( RepositorySystemSession session, LocalMetadataRequest request )
            {
                return new LocalMetadataResult( request );
            }

            public void add( RepositorySystemSession session, LocalMetadataRegistration request )
            {
            }

        } );
        ArtifactRequest request = new ArtifactRequest( artifact, null, "" );

        resolver.setVersionResolver( new VersionResolver()
        {

            public VersionResult resolveVersion( RepositorySystemSession session, VersionRequest request )
            {
                return new VersionResult( request ).setVersion( request.getArtifact().getVersion() );
            }
        } );
        ArtifactResult result = resolver.resolveArtifact( session, request );

        assertTrue( result.getExceptions().isEmpty() );

        Artifact resolved = result.getArtifact();
        assertNotNull( resolved.getFile() );

        resolved = resolved.setFile( null );
        assertEquals( artifact, resolved );
    }

}
