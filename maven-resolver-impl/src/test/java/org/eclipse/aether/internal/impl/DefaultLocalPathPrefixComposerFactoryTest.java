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

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * UT for {@link DefaultLocalPathPrefixComposerFactory}.
 */
public class DefaultLocalPathPrefixComposerFactoryTest
{
    private final Artifact releaseArtifact = new DefaultArtifact("org.group:artifact:1.0");

    private final Artifact snapshotArtifact = new DefaultArtifact("org.group:artifact:1.0-20220228.180000-1");

    private final Metadata releaseMetadata = new DefaultMetadata( "org.group", "artifact", "1.0", "maven-metadata.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT );

    private final Metadata snapshotMetadata = new DefaultMetadata( "org.group", "artifact", "1.0-SNAPSHOT", "maven-metadata.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT );

    private final Metadata gaMetadata = new DefaultMetadata( "org.group", "artifact", null, "maven-metadata.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT );

    private final Metadata gMetadata = new DefaultMetadata( "org.group", null, null, "maven-metadata.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT );

    private final RemoteRepository repository = new RemoteRepository.Builder( "my-repo", "default", "https://repo.maven.apache.org/maven2/" ).build();

    @Test
    public void defaultConfigNoSplitAllNulls()
    {
        DefaultRepositorySystemSession session = TestUtils.newSession();

        LocalPathPrefixComposerFactory factory = new DefaultLocalPathPrefixComposerFactory();
        LocalPathPrefixComposer composer = factory.createComposer( session );
        assertNotNull( composer );

        String prefix;
        prefix = composer.getPathPrefixForLocalArtifact( releaseArtifact );
        assertNull( prefix );

        prefix = composer.getPathPrefixForLocalMetadata( releaseMetadata );
        assertNull( prefix );

        prefix = composer.getPathPrefixForRemoteArtifact( releaseArtifact, repository );
        assertNull( prefix );

        prefix = composer.getPathPrefixForRemoteMetadata( releaseMetadata, repository );
        assertNull( prefix );
    }

    @Test
    public void splitEnabled()
    {
        DefaultRepositorySystemSession session = TestUtils.newSession();
        session.setConfigProperty( "aether.enhancedLocalRepository.split", Boolean.TRUE.toString() );

        LocalPathPrefixComposerFactory factory = new DefaultLocalPathPrefixComposerFactory();
        LocalPathPrefixComposer composer = factory.createComposer( session );
        assertNotNull( composer );

        String prefix;
        prefix = composer.getPathPrefixForLocalArtifact( releaseArtifact );
        assertNotNull( prefix );
        assertEquals( "installed", prefix );

        prefix = composer.getPathPrefixForLocalMetadata( releaseMetadata );
        assertNotNull( prefix );
        assertEquals( "installed", prefix );

        prefix = composer.getPathPrefixForRemoteArtifact( releaseArtifact, repository );
        assertNotNull( prefix );
        assertEquals( "cached", prefix );

        prefix = composer.getPathPrefixForRemoteMetadata( releaseMetadata, repository );
        assertNotNull( prefix );
        assertEquals( "cached", prefix );
    }

    @Test
    public void saneConfig()
    {
        DefaultRepositorySystemSession session = TestUtils.newSession();
        session.setConfigProperty( "aether.enhancedLocalRepository.split", Boolean.TRUE.toString() );
        session.setConfigProperty( "aether.enhancedLocalRepository.splitLocal", Boolean.TRUE.toString() );
        session.setConfigProperty( "aether.enhancedLocalRepository.splitRemoteRepository", Boolean.TRUE.toString() );

        LocalPathPrefixComposerFactory factory = new DefaultLocalPathPrefixComposerFactory();
        LocalPathPrefixComposer composer = factory.createComposer( session );
        assertNotNull( composer );

        String prefix;
        prefix = composer.getPathPrefixForLocalArtifact( releaseArtifact );
        assertNotNull( prefix );
        assertEquals( "installed/releases", prefix );

        prefix = composer.getPathPrefixForLocalArtifact( snapshotArtifact );
        assertNotNull( prefix );
        assertEquals( "installed/snapshots", prefix );

        prefix = composer.getPathPrefixForLocalMetadata( releaseMetadata );
        assertNotNull( prefix );
        assertEquals( "installed/releases", prefix );

        prefix = composer.getPathPrefixForLocalMetadata( snapshotMetadata );
        assertNotNull( prefix );
        assertEquals( "installed/snapshots", prefix );

        prefix = composer.getPathPrefixForLocalMetadata( gaMetadata );
        assertNotNull( prefix );
        assertEquals( "installed/releases", prefix );

        prefix = composer.getPathPrefixForLocalMetadata( gMetadata );
        assertNotNull( prefix );
        assertEquals( "installed/releases", prefix );

        prefix = composer.getPathPrefixForRemoteArtifact( releaseArtifact, repository );
        assertNotNull( prefix );
        assertEquals( "cached/my-repo", prefix );

        prefix = composer.getPathPrefixForRemoteArtifact( snapshotArtifact, repository );
        assertNotNull( prefix );
        assertEquals( "cached/my-repo", prefix );

        prefix = composer.getPathPrefixForRemoteMetadata( releaseMetadata, repository );
        assertNotNull( prefix );
        assertEquals( "cached/my-repo", prefix );

        prefix = composer.getPathPrefixForRemoteMetadata( snapshotMetadata, repository );
        assertNotNull( prefix );
        assertEquals( "cached/my-repo", prefix );

        prefix = composer.getPathPrefixForRemoteMetadata( gaMetadata, repository );
        assertNotNull( prefix );
        assertEquals( "cached/my-repo", prefix );

        prefix = composer.getPathPrefixForRemoteMetadata( gMetadata, repository );
        assertNotNull( prefix );
        assertEquals( "cached/my-repo", prefix );
    }

    @Test
    public void fullConfig()
    {
        DefaultRepositorySystemSession session = TestUtils.newSession();
        session.setConfigProperty( "aether.enhancedLocalRepository.split", Boolean.TRUE.toString() );
        session.setConfigProperty( "aether.enhancedLocalRepository.splitLocal", Boolean.TRUE.toString() );
        session.setConfigProperty( "aether.enhancedLocalRepository.splitRemote", Boolean.TRUE.toString() );
        session.setConfigProperty( "aether.enhancedLocalRepository.splitRemoteRepository", Boolean.TRUE.toString() );

        LocalPathPrefixComposerFactory factory = new DefaultLocalPathPrefixComposerFactory();
        LocalPathPrefixComposer composer = factory.createComposer( session );
        assertNotNull( composer );

        String prefix;
        prefix = composer.getPathPrefixForLocalArtifact( releaseArtifact );
        assertNotNull( prefix );
        assertEquals( "installed/releases", prefix );

        prefix = composer.getPathPrefixForLocalArtifact( snapshotArtifact );
        assertNotNull( prefix );
        assertEquals( "installed/snapshots", prefix );

        prefix = composer.getPathPrefixForLocalMetadata( releaseMetadata );
        assertNotNull( prefix );
        assertEquals( "installed/releases", prefix );

        prefix = composer.getPathPrefixForLocalMetadata( snapshotMetadata );
        assertNotNull( prefix );
        assertEquals( "installed/snapshots", prefix );

        prefix = composer.getPathPrefixForLocalMetadata( gaMetadata );
        assertNotNull( prefix );
        assertEquals( "installed/releases", prefix );

        prefix = composer.getPathPrefixForLocalMetadata( gMetadata );
        assertNotNull( prefix );
        assertEquals( "installed/releases", prefix );

        prefix = composer.getPathPrefixForRemoteArtifact( releaseArtifact, repository );
        assertNotNull( prefix );
        assertEquals( "cached/my-repo/releases", prefix );

        prefix = composer.getPathPrefixForRemoteArtifact( snapshotArtifact, repository );
        assertNotNull( prefix );
        assertEquals( "cached/my-repo/snapshots", prefix );

        prefix = composer.getPathPrefixForRemoteMetadata( releaseMetadata, repository );
        assertNotNull( prefix );
        assertEquals( "cached/my-repo/releases", prefix );

        prefix = composer.getPathPrefixForRemoteMetadata( snapshotMetadata, repository );
        assertNotNull( prefix );
        assertEquals( "cached/my-repo/snapshots", prefix );

        prefix = composer.getPathPrefixForRemoteMetadata( gaMetadata, repository );
        assertNotNull( prefix );
        assertEquals( "cached/my-repo/releases", prefix );

        prefix = composer.getPathPrefixForRemoteMetadata( gMetadata, repository );
        assertNotNull( prefix );
        assertEquals( "cached/my-repo/releases", prefix );
    }
}
