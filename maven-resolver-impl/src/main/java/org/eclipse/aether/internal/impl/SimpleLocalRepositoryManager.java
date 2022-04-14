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

import java.io.File;
import static java.util.Objects.requireNonNull;
import java.util.SortedSet;
import java.util.TreeSet;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
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

/**
 * A local repository manager that realizes the classical Maven 2.0 local repository.
 */
class SimpleLocalRepositoryManager
    implements LocalRepositoryManager
{

    private final LocalRepository repository;

    private final ArtifactPathComposer artifactPathComposer;

    SimpleLocalRepositoryManager( File basedir, String type, ArtifactPathComposer artifactPathComposer )
    {
        requireNonNull( basedir, "base directory cannot be null" );
        repository = new LocalRepository( basedir.getAbsoluteFile(), type );
        this.artifactPathComposer = requireNonNull( artifactPathComposer );
    }

    @Override
    public LocalRepository getRepository()
    {
        return repository;
    }

    protected String getPathForArtifact( Artifact artifact, boolean local )
    {
        return artifactPathComposer.getPathForArtifact( artifact, local );
    }

    @Override
    public String getPathForLocalArtifact( Artifact artifact )
    {
        requireNonNull( artifact, "artifact cannot be null" );
        return getPathForArtifact( artifact, true );
    }

    @Override
    public String getPathForRemoteArtifact( Artifact artifact, RemoteRepository repository, String context )
    {
        requireNonNull( artifact, "artifact cannot be null" );
        requireNonNull( repository, "repository cannot be null" );
        return getPathForArtifact( artifact, false );
    }

    @Override
    public String getPathForLocalMetadata( Metadata metadata )
    {
        requireNonNull( metadata, "metadata cannot be null" );
        return artifactPathComposer.getPathForMetadata( metadata, "local" );
    }

    @Override
    public String getPathForRemoteMetadata( Metadata metadata, RemoteRepository repository, String context )
    {
        requireNonNull( metadata, "metadata cannot be null" );
        requireNonNull( repository, "repository cannot be null" );
        return artifactPathComposer.getPathForMetadata( metadata, getRepositoryKey( repository, context ) );
    }

    protected String getRepositoryKey( RemoteRepository repository, String context )
    {
        String key;

        if ( repository.isRepositoryManager() )
        {
            // repository serves dynamic contents, take request parameters into account for key

            StringBuilder buffer = new StringBuilder( 128 );

            buffer.append( repository.getId() );

            buffer.append( '-' );

            SortedSet<String> subKeys = new TreeSet<>();
            for ( RemoteRepository mirroredRepo : repository.getMirroredRepositories() )
            {
                subKeys.add( mirroredRepo.getId() );
            }

            SimpleDigest digest = new SimpleDigest();
            digest.update( context );
            for ( String subKey : subKeys )
            {
                digest.update( subKey );
            }
            buffer.append( digest.digest() );

            key = buffer.toString();
        }
        else
        {
            // repository serves static contents, its id is sufficient as key

            key = repository.getId();
        }

        return key;
    }

    @Override
    public LocalArtifactResult find( RepositorySystemSession session, LocalArtifactRequest request )
    {
        requireNonNull( session, "session cannot be null" );
        requireNonNull( request, "request cannot be null" );
        String path = getPathForArtifact( request.getArtifact(), false );
        File file = new File( getRepository().getBasedir(), path );

        LocalArtifactResult result = new LocalArtifactResult( request );
        if ( file.isFile() )
        {
            result.setFile( file );
            result.setAvailable( true );
        }

        return result;
    }

    @Override
    public void add( RepositorySystemSession session, LocalArtifactRegistration request )
    {
        requireNonNull( session, "session cannot be null" );
        requireNonNull( request, "request cannot be null" );
        // noop
    }

    @Override
    public LocalMetadataResult find( RepositorySystemSession session, LocalMetadataRequest request )
    {
        requireNonNull( session, "session cannot be null" );
        requireNonNull( request, "request cannot be null" );
        LocalMetadataResult result = new LocalMetadataResult( request );

        String path;

        Metadata metadata = request.getMetadata();
        String context = request.getContext();
        RemoteRepository remote = request.getRepository();

        if ( remote != null )
        {
            path = getPathForRemoteMetadata( metadata, remote, context );
        }
        else
        {
            path = getPathForLocalMetadata( metadata );
        }

        File file = new File( getRepository().getBasedir(), path );
        if ( file.isFile() )
        {
            result.setFile( file );
        }

        return result;
    }

    @Override
    public void add( RepositorySystemSession session, LocalMetadataRegistration request )
    {
        requireNonNull( session, "session cannot be null" );
        requireNonNull( request, "request cannot be null" );
        // noop
    }

    @Override
    public String toString()
    {
        return String.valueOf( getRepository() );
    }
}
