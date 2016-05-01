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
package org.eclipse.aether.internal.impl;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.internal.impl.EnhancedLocalRepositoryManager;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.metadata.Metadata.Nature;
import org.eclipse.aether.repository.LocalArtifactRegistration;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalArtifactResult;
import org.eclipse.aether.repository.LocalMetadataRequest;
import org.eclipse.aether.repository.LocalMetadataResult;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class EnhancedLocalRepositoryManagerTest
{

    private Artifact artifact;

    private Artifact snapshot;

    private File basedir;

    private EnhancedLocalRepositoryManager manager;

    private File artifactFile;

    private RemoteRepository repository;

    private String testContext = "project/compile";

    private RepositorySystemSession session;

    private Metadata metadata;

    private Metadata noVerMetadata;

    @Before
    public void setup()
        throws Exception
    {
        String url = TestFileUtils.createTempDir( "enhanced-remote-repo" ).toURI().toURL().toString();
        repository =
            new RemoteRepository.Builder( "enhanced-remote-repo", "default", url ).setRepositoryManager( true ).build();

        artifact =
            new DefaultArtifact( "gid", "aid", "", "jar", "1-test", Collections.<String, String> emptyMap(),
                                 TestFileUtils.createTempFile( "artifact" ) );

        snapshot =
            new DefaultArtifact( "gid", "aid", "", "jar", "1.0-20120710.231549-9",
                                 Collections.<String, String> emptyMap(), TestFileUtils.createTempFile( "artifact" ) );

        metadata =
            new DefaultMetadata( "gid", "aid", "1-test", "maven-metadata.xml", Nature.RELEASE,
                                 TestFileUtils.createTempFile( "metadata" ) );

        noVerMetadata =
            new DefaultMetadata( "gid", "aid", null, "maven-metadata.xml", Nature.RELEASE,
                                 TestFileUtils.createTempFile( "metadata" ) );

        basedir = TestFileUtils.createTempDir( "enhanced-repo" );
        session = TestUtils.newSession();
        manager = new EnhancedLocalRepositoryManager( basedir, session );

        artifactFile = new File( basedir, manager.getPathForLocalArtifact( artifact ) );
    }

    @After
    public void tearDown()
        throws Exception
    {
        TestFileUtils.deleteFile( basedir );
        TestFileUtils.deleteFile( new File( new URI( repository.getUrl() ) ) );

        session = null;
        manager = null;
        repository = null;
        artifact = null;
    }

    private long addLocalArtifact( Artifact artifact )
        throws IOException
    {
        manager.add( session, new LocalArtifactRegistration( artifact ) );
        String path = manager.getPathForLocalArtifact( artifact );

        return copy( artifact, path );
    }

    private long addRemoteArtifact( Artifact artifact )
        throws IOException
    {
        Collection<String> contexts = Arrays.asList( testContext );
        manager.add( session, new LocalArtifactRegistration( artifact, repository, contexts ) );
        String path = manager.getPathForRemoteArtifact( artifact, repository, testContext );
        return copy( artifact, path );
    }
    
    private long copy( Metadata metadata, String path )
        throws IOException
    {
        if ( metadata.getFile() == null )
        {
            return -1;
        }
        return TestFileUtils.copyFile( metadata.getFile(), new File( basedir, path ) );
    }

    private long copy( Artifact artifact, String path )
        throws IOException
    {
        if ( artifact.getFile() == null )
        {
            return -1;
        }
        File artifactFile = new File( basedir, path );
        return TestFileUtils.copyFile( artifact.getFile(), artifactFile );
    }

    @Test
    public void testGetPathForLocalArtifact()
    {
        Artifact artifact = new DefaultArtifact( "g.i.d:a.i.d:1.0-SNAPSHOT" );
        assertEquals( "1.0-SNAPSHOT", artifact.getBaseVersion() );
        assertEquals( "g/i/d/a.i.d/1.0-SNAPSHOT/a.i.d-1.0-SNAPSHOT.jar", manager.getPathForLocalArtifact( artifact ) );

        artifact = new DefaultArtifact( "g.i.d:a.i.d:1.0-20110329.221805-4" );
        assertEquals( "1.0-SNAPSHOT", artifact.getBaseVersion() );
        assertEquals( "g/i/d/a.i.d/1.0-SNAPSHOT/a.i.d-1.0-SNAPSHOT.jar", manager.getPathForLocalArtifact( artifact ) );
    }

    @Test
    public void testGetPathForRemoteArtifact()
    {
        RemoteRepository remoteRepo = new RemoteRepository.Builder( "repo", "default", "ram:/void" ).build();

        Artifact artifact = new DefaultArtifact( "g.i.d:a.i.d:1.0-SNAPSHOT" );
        assertEquals( "1.0-SNAPSHOT", artifact.getBaseVersion() );
        assertEquals( "g/i/d/a.i.d/1.0-SNAPSHOT/a.i.d-1.0-SNAPSHOT.jar",
                      manager.getPathForRemoteArtifact( artifact, remoteRepo, "" ) );

        artifact = new DefaultArtifact( "g.i.d:a.i.d:1.0-20110329.221805-4" );
        assertEquals( "1.0-SNAPSHOT", artifact.getBaseVersion() );
        assertEquals( "g/i/d/a.i.d/1.0-SNAPSHOT/a.i.d-1.0-20110329.221805-4.jar",
                      manager.getPathForRemoteArtifact( artifact, remoteRepo, "" ) );
    }

    @Test
    public void testFindLocalArtifact()
        throws Exception
    {
        addLocalArtifact( artifact );

        LocalArtifactRequest request = new LocalArtifactRequest( artifact, null, null );
        LocalArtifactResult result = manager.find( session, request );
        assertTrue( result.isAvailable() );
        assertEquals( null, result.getRepository() );

        snapshot = snapshot.setVersion( snapshot.getBaseVersion() );
        addLocalArtifact( snapshot );

        request = new LocalArtifactRequest( snapshot, null, null );
        result = manager.find( session, request );
        assertTrue( result.isAvailable() );
        assertEquals( null, result.getRepository() );
    }

    @Test
    public void testFindRemoteArtifact()
        throws Exception
    {
        addRemoteArtifact( artifact );

        LocalArtifactRequest request = new LocalArtifactRequest( artifact, Arrays.asList( repository ), testContext );
        LocalArtifactResult result = manager.find( session, request );
        assertTrue( result.isAvailable() );
        assertEquals( repository, result.getRepository() );

        addRemoteArtifact( snapshot );

        request = new LocalArtifactRequest( snapshot, Arrays.asList( repository ), testContext );
        result = manager.find( session, request );
        assertTrue( result.isAvailable() );
        assertEquals( repository, result.getRepository() );
    }

    @Test
    public void testDoNotFindDifferentContext()
        throws Exception
    {
        addRemoteArtifact( artifact );

        LocalArtifactRequest request = new LocalArtifactRequest( artifact, Arrays.asList( repository ), "different" );
        LocalArtifactResult result = manager.find( session, request );
        assertFalse( result.isAvailable() );
    }

    @Test
    public void testDoNotFindNullFile()
        throws Exception
    {
        artifact = artifact.setFile( null );
        addLocalArtifact( artifact );

        LocalArtifactRequest request = new LocalArtifactRequest( artifact, Arrays.asList( repository ), testContext );
        LocalArtifactResult result = manager.find( session, request );
        assertFalse( result.isAvailable() );
    }

    @Test
    public void testDoNotFindDeletedFile()
        throws Exception
    {
        addLocalArtifact( artifact );
        assertTrue( "could not delete artifact file", artifactFile.delete() );

        LocalArtifactRequest request = new LocalArtifactRequest( artifact, Arrays.asList( repository ), testContext );
        LocalArtifactResult result = manager.find( session, request );
        assertFalse( result.isAvailable() );
    }

    @Test
    public void testFindUntrackedFile()
        throws Exception
    {
        copy( artifact, manager.getPathForLocalArtifact( artifact ) );

        LocalArtifactRequest request = new LocalArtifactRequest( artifact, Arrays.asList( repository ), testContext );
        LocalArtifactResult result = manager.find( session, request );
        assertTrue( result.isAvailable() );
    }

    private long addMetadata( Metadata metadata, RemoteRepository repo )
        throws IOException
    {
        String path;
        if ( repo == null )
        {
            path = manager.getPathForLocalMetadata( metadata );
        }
        else
        {
            path = manager.getPathForRemoteMetadata( metadata, repo, testContext );
        }
        System.err.println( path );

        return copy( metadata, path );
    }

    @Test
    public void testFindLocalMetadata()
        throws Exception
    {
        addMetadata( metadata, null );

        LocalMetadataRequest request = new LocalMetadataRequest( metadata, null, testContext );
        LocalMetadataResult result = manager.find( session, request );

        assertNotNull( result.getFile() );
    }

    @Test
    public void testFindLocalMetadataNoVersion()
        throws Exception
    {
        addMetadata( noVerMetadata, null );

        LocalMetadataRequest request = new LocalMetadataRequest( noVerMetadata, null, testContext );
        LocalMetadataResult result = manager.find( session, request );

        assertNotNull( result.getFile() );
    }

    @Test
    public void testDoNotFindRemoteMetadataDifferentContext()
        throws Exception
    {
        addMetadata( noVerMetadata, repository );
        addMetadata( metadata, repository );

        LocalMetadataRequest request = new LocalMetadataRequest( noVerMetadata, repository, "different" );
        LocalMetadataResult result = manager.find( session, request );
        assertNull( result.getFile() );

        request = new LocalMetadataRequest( metadata, repository, "different" );
        result = manager.find( session, request );
        assertNull( result.getFile() );
    }

    @Test
    public void testFindArtifactUsesTimestampedVersion()
        throws Exception
    {
        Artifact artifact = new DefaultArtifact( "g.i.d:a.i.d:1.0-SNAPSHOT" );
        File file = new File( basedir, manager.getPathForLocalArtifact( artifact ) );
        TestFileUtils.writeString( file, "test" );
        addLocalArtifact( artifact );

        artifact = artifact.setVersion( "1.0-20110329.221805-4" );
        LocalArtifactRequest request = new LocalArtifactRequest();
        request.setArtifact( artifact );
        LocalArtifactResult result = manager.find( session, request );
        assertNull( result.toString(), result.getFile() );
        assertFalse( result.toString(), result.isAvailable() );
    }

}
