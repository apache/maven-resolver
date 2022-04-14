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
import java.util.Objects;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.LocalArtifactRegistration;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalArtifactResult;
import org.eclipse.aether.repository.RemoteRepository;

import static java.util.Objects.requireNonNull;

/**
 * Implementation of dynamic local repository manager, subject to change without prior notice.
 *
 * @see DynamicLocalRepositoryManagerFactory
 * @since TBD
 */
class DynamicLocalRepositoryManager
        extends EnhancedLocalRepositoryManager
{
    private final DynamicPrefixComposer dynamicPrefixComposer;

    DynamicLocalRepositoryManager( File basedir,
                                   ArtifactPathComposer artifactPathComposer,
                                   RepositorySystemSession session,
                                   TrackingFileManager trackingFileManager,
                                   DynamicPrefixComposer dynamicPrefixComposer )
    {
        super( basedir, artifactPathComposer, session, trackingFileManager );
        this.dynamicPrefixComposer = requireNonNull( dynamicPrefixComposer );
    }

    private String concatPaths( String prefix, String artifactPath )
    {
        if ( prefix == null || prefix.isEmpty() )
        {
            return artifactPath;
        }
        return prefix + '/' + artifactPath;
    }

    @Override
    public String getPathForLocalArtifact( Artifact artifact )
    {
        return concatPaths(
                dynamicPrefixComposer.getPrefixForLocalArtifact( artifact ),
                super.getPathForLocalArtifact( artifact )
        );
    }

    @Override
    public String getPathForRemoteArtifact( Artifact artifact, RemoteRepository repository, String context )
    {
        return concatPaths(
                dynamicPrefixComposer.getPrefixForRemoteArtifact( artifact, repository, context ),
                super.getPathForRemoteArtifact( artifact, repository, context )
        );
    }

    @Override
    public String getPathForLocalMetadata( Metadata metadata )
    {
        return concatPaths(
                dynamicPrefixComposer.getPrefixForLocalMetadata( metadata ),
                super.getPathForLocalMetadata( metadata )
        );
    }

    @Override
    public String getPathForRemoteMetadata( Metadata metadata, RemoteRepository repository, String context )
    {
        return concatPaths(
                dynamicPrefixComposer.getPrefixForRemoteMetadata( metadata, repository, context ),
                super.getPathForRemoteMetadata( metadata, repository, context )
        );
    }

    @Override
    public LocalArtifactResult find( RepositorySystemSession session, LocalArtifactRequest request )
    {
        if ( !dynamicPrefixComposer.isRemoteSplitByOrigin() )
        {
            return super.find( session, request );
        }
        else
        {
            // we have split storage, so not "tracking" needed (to emulate split)
            // try local first, and then try remotes as in request
            Artifact artifact = request.getArtifact();
            LocalArtifactResult result = new LocalArtifactResult( request );
            String path = getPathForLocalArtifact( artifact );
            File file = new File( getRepository().getBasedir(), path );

            if ( Objects.equals( artifact.getVersion(), artifact.getBaseVersion() ) && file.isFile() )
            {
                result.setFile( file );
                result.setAvailable( true );
            }

            if ( !result.isAvailable() )
            {
                for ( RemoteRepository repository : request.getRepositories() )
                {
                    path = getPathForRemoteArtifact( artifact, repository, request.getContext() );
                    file = new File( getRepository().getBasedir(), path );
                    if ( file.isFile() )
                    {
                        result.setFile( file );
                        result.setAvailable( true );
                        result.setRepository( repository );
                        break;
                    }
                }
            }
            return result;
        }
    }

    @Override
    public void add( RepositorySystemSession session, LocalArtifactRegistration request )
    {
        requireNonNull( session, "session cannot be null" );
        requireNonNull( request, "request cannot be null" );
        if ( !dynamicPrefixComposer.isRemoteSplitByOrigin() )
        {
            super.add( session, request );
        }
    }
}
