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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.maven.resolver.DefaultRepositorySystemSession;
import org.apache.maven.resolver.RepositoryEvent;
import org.apache.maven.resolver.RepositoryEvent.EventType;
import org.apache.maven.resolver.RepositoryException;
import org.apache.maven.resolver.artifact.Artifact;
import org.apache.maven.resolver.artifact.DefaultArtifact;
import org.apache.maven.resolver.deployment.DeployRequest;
import org.apache.maven.resolver.deployment.DeploymentException;
import org.apache.maven.resolver.internal.test.util.TestFileProcessor;
import org.apache.maven.resolver.internal.test.util.TestFileUtils;
import org.apache.maven.resolver.internal.test.util.TestUtils;
import org.apache.maven.resolver.metadata.DefaultMetadata;
import org.apache.maven.resolver.metadata.MergeableMetadata;
import org.apache.maven.resolver.metadata.Metadata;
import org.apache.maven.resolver.metadata.Metadata.Nature;
import org.apache.maven.resolver.repository.RemoteRepository;
import org.apache.maven.resolver.spi.connector.ArtifactDownload;
import org.apache.maven.resolver.spi.connector.ArtifactUpload;
import org.apache.maven.resolver.spi.connector.MetadataDownload;
import org.apache.maven.resolver.spi.connector.MetadataUpload;
import org.apache.maven.resolver.spi.connector.RepositoryConnector;
import org.apache.maven.resolver.transfer.MetadataNotFoundException;
import org.apache.maven.resolver.transform.FileTransformer;
import org.apache.maven.resolver.util.artifact.SubArtifact;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DefaultDeployerTest
{

    private Artifact artifact;

    private DefaultMetadata metadata;

    private DefaultRepositorySystemSession session;

    private StubRepositoryConnectorProvider connectorProvider;

    private DefaultDeployer deployer;

    private DeployRequest request;

    private RecordingRepositoryConnector connector;

    private RecordingRepositoryListener listener;

    @Before
    public void setup()
        throws IOException
    {
        artifact = new DefaultArtifact( "gid", "aid", "jar", "ver" );
        artifact = artifact.setFile( TestFileUtils.createTempFile( "artifact" ) );
        metadata =
            new DefaultMetadata( "gid", "aid", "ver", "type", Nature.RELEASE_OR_SNAPSHOT,
                                 TestFileUtils.createTempFile( "metadata" ) );

        session = TestUtils.newSession();
        connectorProvider = new StubRepositoryConnectorProvider();

        deployer = new DefaultDeployer();
        deployer.setRepositoryConnectorProvider( connectorProvider );
        deployer.setRemoteRepositoryManager( new StubRemoteRepositoryManager() );
        deployer.setRepositoryEventDispatcher( new StubRepositoryEventDispatcher() );
        deployer.setUpdateCheckManager( new StaticUpdateCheckManager( true ) );
        deployer.setFileProcessor( new TestFileProcessor() );
        deployer.setSyncContextFactory( new StubSyncContextFactory() );
        deployer.setOfflineController( new DefaultOfflineController() );

        request = new DeployRequest();
        request.setRepository( new RemoteRepository.Builder( "id", "default", "file:///" ).build() );
        connector = new RecordingRepositoryConnector( session );
        connectorProvider.setConnector( connector );

        listener = new RecordingRepositoryListener();
        session.setRepositoryListener( listener );
    }

    @After
    public void teardown()
        throws Exception
    {
        if ( session.getLocalRepository() != null )
        {
            TestFileUtils.deleteFile( session.getLocalRepository().getBasedir() );
        }
        session = null;
        listener = null;
        connector = null;
        connectorProvider = null;
        deployer = null;
    }

    @Test
    public void testSuccessfulDeploy()
        throws DeploymentException
    {

        connector.setExpectPut( artifact );
        connector.setExpectPut( metadata );

        request.addArtifact( artifact );
        request.addMetadata( metadata );

        deployer.deploy( session, request );

        connector.assertSeenExpected();
    }

    @Test( expected = DeploymentException.class )
    public void testNullArtifactFile()
        throws DeploymentException
    {
        request.addArtifact( artifact.setFile( null ) );
        deployer.deploy( session, request );
    }

    @Test( expected = DeploymentException.class )
    public void testNullMetadataFile()
        throws DeploymentException
    {
        request.addArtifact( artifact.setFile( null ) );
        deployer.deploy( session, request );
    }

    @Test
    public void testSuccessfulArtifactEvents()
        throws DeploymentException
    {
        request.addArtifact( artifact );

        deployer.deploy( session, request );

        List<RepositoryEvent> events = listener.getEvents();
        assertEquals( 2, events.size() );

        RepositoryEvent event = events.get( 0 );
        assertEquals( EventType.ARTIFACT_DEPLOYING, event.getType() );
        assertEquals( artifact, event.getArtifact() );
        assertNull( event.getException() );

        event = events.get( 1 );
        assertEquals( EventType.ARTIFACT_DEPLOYED, event.getType() );
        assertEquals( artifact, event.getArtifact() );
        assertNull( event.getException() );
    }

    @Test
    public void testFailingArtifactEvents()
    {
        connector.fail = true;

        request.addArtifact( artifact );

        try
        {
            deployer.deploy( session, request );
            fail( "expected exception" );
        }
        catch ( DeploymentException e )
        {
            List<RepositoryEvent> events = listener.getEvents();
            assertEquals( 2, events.size() );

            RepositoryEvent event = events.get( 0 );
            assertEquals( EventType.ARTIFACT_DEPLOYING, event.getType() );
            assertEquals( artifact, event.getArtifact() );
            assertNull( event.getException() );

            event = events.get( 1 );
            assertEquals( EventType.ARTIFACT_DEPLOYED, event.getType() );
            assertEquals( artifact, event.getArtifact() );
            assertNotNull( event.getException() );
        }
    }

    @Test
    public void testSuccessfulMetadataEvents()
        throws DeploymentException
    {
        request.addMetadata( metadata );

        deployer.deploy( session, request );

        List<RepositoryEvent> events = listener.getEvents();
        assertEquals( 2, events.size() );

        RepositoryEvent event = events.get( 0 );
        assertEquals( EventType.METADATA_DEPLOYING, event.getType() );
        assertEquals( metadata, event.getMetadata() );
        assertNull( event.getException() );

        event = events.get( 1 );
        assertEquals( EventType.METADATA_DEPLOYED, event.getType() );
        assertEquals( metadata, event.getMetadata() );
        assertNull( event.getException() );
    }

    @Test
    public void testFailingMetdataEvents()
    {
        connector.fail = true;

        request.addMetadata( metadata );

        try
        {
            deployer.deploy( session, request );
            fail( "expected exception" );
        }
        catch ( DeploymentException e )
        {
            List<RepositoryEvent> events = listener.getEvents();
            assertEquals( 2, events.size() );

            RepositoryEvent event = events.get( 0 );
            assertEquals( EventType.METADATA_DEPLOYING, event.getType() );
            assertEquals( metadata, event.getMetadata() );
            assertNull( event.getException() );

            event = events.get( 1 );
            assertEquals( EventType.METADATA_DEPLOYED, event.getType() );
            assertEquals( metadata, event.getMetadata() );
            assertNotNull( event.getException() );
        }
    }

    @Test
    public void testStaleLocalMetadataCopyGetsDeletedBeforeMergeWhenMetadataIsNotCurrentlyPresentInRemoteRepo()
        throws Exception
    {
        MergeableMetadata metadata = new MergeableMetadata()
        {

            public Metadata setFile( File file )
            {
                return this;
            }

            public String getVersion()
            {
                return "";
            }

            public String getType()
            {
                return "test.properties";
            }

            public Nature getNature()
            {
                return Nature.RELEASE;
            }

            public String getGroupId()
            {
                return "org";
            }

            public File getFile()
            {
                return null;
            }

            public String getArtifactId()
            {
                return "aether";
            }

            public Metadata setProperties( Map<String, String> properties )
            {
                return this;
            }

            public Map<String, String> getProperties()
            {
                return Collections.emptyMap();
            }

            public String getProperty( String key, String defaultValue )
            {
                return defaultValue;
            }

            public void merge( File current, File result )
                throws RepositoryException
            {
                requireNonNull( current, "current cannot be null" );
                requireNonNull( result, "result cannot be null" );
                Properties props = new Properties();

                try
                {
                    if ( current.isFile() )
                    {
                        TestFileUtils.readProps( current, props );
                    }

                    props.setProperty( "new", "value" );

                    TestFileUtils.writeProps( result, props );
                }
                catch ( IOException e )
                {
                    throw new RepositoryException( e.getMessage(), e );
                }
            }

            public boolean isMerged()
            {
                return false;
            }
        };

        connectorProvider.setConnector( new RepositoryConnector()
        {

            public void put( Collection<? extends ArtifactUpload> artifactUploads,
                             Collection<? extends MetadataUpload> metadataUploads )
            {
            }

            public void get( Collection<? extends ArtifactDownload> artifactDownloads,
                             Collection<? extends MetadataDownload> metadataDownloads )
            {
                if ( metadataDownloads != null )
                {
                    for ( MetadataDownload download : metadataDownloads )
                    {
                        download.setException( new MetadataNotFoundException( download.getMetadata(), null, null ) );
                    }
                }
            }

            public void close()
            {
            }
        } );

        request.addMetadata( metadata );

        File metadataFile =
            new File( session.getLocalRepository().getBasedir(),
                      session.getLocalRepositoryManager().getPathForRemoteMetadata( metadata, request.getRepository(),
                                                                                    "" ) );
        Properties props = new Properties();
        props.setProperty( "old", "value" );
        TestFileUtils.writeProps( metadataFile, props );

        deployer.deploy( session, request );

        props = new Properties();
        TestFileUtils.readProps( metadataFile, props );
        assertNull( props.toString(), props.get( "old" ) );
    }

    @Test
    public void testFileTransformer() throws Exception
    {
        final Artifact transformedArtifact = new SubArtifact( artifact, null, "raj" );
        FileTransformer transformer = new FileTransformer()
        {
            @Override
            public InputStream transformData( File file )
            {
                return new ByteArrayInputStream( "transformed data".getBytes( StandardCharsets.UTF_8 ) );
            }
            
            @Override
            public Artifact transformArtifact( Artifact artifact )
            {
                return transformedArtifact;
            }
        };
        
        StubFileTransformerManager fileTransformerManager = new StubFileTransformerManager();
        fileTransformerManager.addFileTransformer( "jar", transformer );
        session.setFileTransformerManager( fileTransformerManager );
        
        request = new DeployRequest();
        request.addArtifact( artifact );
        deployer.deploy( session, request );
        
        Artifact putArtifact = connector.getActualArtifactPutRequests().get( 0 );
        assertEquals( transformedArtifact, putArtifact );
    }

}
