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
import org.eclipse.aether.spi.log.Logger;

/**
 * A local repository manager that realizes the classical Maven 2.0 local repository.
 */
class SimpleLocalRepositoryManager
    implements LocalRepositoryManager
{

    private final LocalRepository repository;

    public SimpleLocalRepositoryManager( File basedir )
    {
        this( basedir, "simple" );
    }

    public SimpleLocalRepositoryManager( String basedir )
    {
        this( ( basedir != null ) ? new File( basedir ) : null, "simple" );
    }

    SimpleLocalRepositoryManager( File basedir, String type )
    {
        if ( basedir == null )
        {
            throw new IllegalArgumentException( "base directory has not been specified" );
        }
        repository = new LocalRepository( basedir.getAbsoluteFile(), type );
    }

    public SimpleLocalRepositoryManager setLogger( Logger logger )
    {
        return this;
    }

    public LocalRepository getRepository()
    {
        return repository;
    }

    String getPathForArtifact( Artifact artifact, boolean local )
    {
        StringBuilder path = new StringBuilder( 128 );

        path.append( artifact.getGroupId().replace( '.', '/' ) ).append( '/' );

        path.append( artifact.getArtifactId() ).append( '/' );

        path.append( artifact.getBaseVersion() ).append( '/' );

        path.append( artifact.getArtifactId() ).append( '-' );
        if ( local )
        {
            path.append( artifact.getBaseVersion() );
        }
        else
        {
            path.append( artifact.getVersion() );
        }

        if ( artifact.getClassifier().length() > 0 )
        {
            path.append( '-' ).append( artifact.getClassifier() );
        }

        if ( artifact.getExtension().length() > 0 )
        {
            path.append( '.' ).append( artifact.getExtension() );
        }

        return path.toString();
    }

    public String getPathForLocalArtifact( Artifact artifact )
    {
        return getPathForArtifact( artifact, true );
    }

    public String getPathForRemoteArtifact( Artifact artifact, RemoteRepository repository, String context )
    {
        return getPathForArtifact( artifact, false );
    }

    public String getPathForLocalMetadata( Metadata metadata )
    {
        return getPath( metadata, "local" );
    }

    public String getPathForRemoteMetadata( Metadata metadata, RemoteRepository repository, String context )
    {
        return getPath( metadata, getRepositoryKey( repository, context ) );
    }

    String getRepositoryKey( RemoteRepository repository, String context )
    {
        String key;

        if ( repository.isRepositoryManager() )
        {
            // repository serves dynamic contents, take request parameters into account for key

            StringBuilder buffer = new StringBuilder( 128 );

            buffer.append( repository.getId() );

            buffer.append( '-' );

            SortedSet<String> subKeys = new TreeSet<String>();
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

    private String getPath( Metadata metadata, String repositoryKey )
    {
        StringBuilder path = new StringBuilder( 128 );

        if ( metadata.getGroupId().length() > 0 )
        {
            path.append( metadata.getGroupId().replace( '.', '/' ) ).append( '/' );

            if ( metadata.getArtifactId().length() > 0 )
            {
                path.append( metadata.getArtifactId() ).append( '/' );

                if ( metadata.getVersion().length() > 0 )
                {
                    path.append( metadata.getVersion() ).append( '/' );
                }
            }
        }

        path.append( insertRepositoryKey( metadata.getType(), repositoryKey ) );

        return path.toString();
    }

    private String insertRepositoryKey( String filename, String repositoryKey )
    {
        String result;
        int idx = filename.indexOf( '.' );
        if ( idx < 0 )
        {
            result = filename + '-' + repositoryKey;
        }
        else
        {
            result = filename.substring( 0, idx ) + '-' + repositoryKey + filename.substring( idx );
        }
        return result;
    }

    public LocalArtifactResult find( RepositorySystemSession session, LocalArtifactRequest request )
    {
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

    public void add( RepositorySystemSession session, LocalArtifactRegistration request )
    {
        // noop
    }

    @Override
    public String toString()
    {
        return String.valueOf( getRepository() );
    }

    public LocalMetadataResult find( RepositorySystemSession session, LocalMetadataRequest request )
    {
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

    public void add( RepositorySystemSession session, LocalMetadataRegistration request )
    {
        // noop
    }

}
