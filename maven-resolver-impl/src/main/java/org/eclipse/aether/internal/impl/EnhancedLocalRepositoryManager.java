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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static java.util.Objects.requireNonNull;

import java.util.Objects;
import java.util.Properties;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.LocalArtifactRegistration;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalArtifactResult;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * These are implementation details for enhanced local repository manager, subject to change without prior notice.
 * Repositories from which a cached artifact was resolved are tracked in a properties file named
 * <code>_remote.repositories</code>, with content key as filename&gt;repo_id and value as empty string. If a file has
 * been installed in the repository, but not downloaded from a remote repository, it is tracked as empty repository id
 * and always resolved. For example:
 *
 * <pre>
 * artifact-1.0.pom>=
 * artifact-1.0.jar>=
 * artifact-1.0.pom>central=
 * artifact-1.0.jar>central=
 * artifact-1.0.zip>central=
 * artifact-1.0-classifier.zip>central=
 * artifact-1.0.pom>my_repo_id=
 * </pre>
 *
 * @see EnhancedLocalRepositoryManagerFactory
 */
class EnhancedLocalRepositoryManager
        extends SimpleLocalRepositoryManager
{

    private static final String LOCAL_REPO_ID = "";

    private final String trackingFilename;

    private final TrackingFileManager trackingFileManager;

    private final LocalPathPrefixComposer localPathPrefixComposer;

    EnhancedLocalRepositoryManager( File basedir,
                                    LocalPathComposer localPathComposer,
                                    String trackingFilename,
                                    TrackingFileManager trackingFileManager,
                                    LocalPathPrefixComposer localPathPrefixComposer )
    {
        super( basedir, "enhanced", localPathComposer );
        this.trackingFilename = requireNonNull( trackingFilename );
        this.trackingFileManager = requireNonNull( trackingFileManager );
        this.localPathPrefixComposer = requireNonNull( localPathPrefixComposer );
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
                localPathPrefixComposer.getPathPrefixForLocalArtifact( artifact ),
                super.getPathForLocalArtifact( artifact )
        );
    }

    @Override
    public String getPathForRemoteArtifact( Artifact artifact, RemoteRepository repository, String context )
    {
        return concatPaths(
                localPathPrefixComposer.getPathPrefixForRemoteArtifact( artifact, repository, context ),
                super.getPathForRemoteArtifact( artifact, repository, context )
        );
    }

    @Override
    public String getPathForLocalMetadata( Metadata metadata )
    {
        return concatPaths(
                localPathPrefixComposer.getPathPrefixForLocalMetadata( metadata ),
                super.getPathForLocalMetadata( metadata )
        );
    }

    @Override
    public String getPathForRemoteMetadata( Metadata metadata, RemoteRepository repository, String context )
    {
        return concatPaths(
                localPathPrefixComposer.getPathPrefixForRemoteMetadata( metadata, repository, context ),
                super.getPathForRemoteMetadata( metadata, repository, context )
        );
    }

    @Override
    public LocalArtifactResult find( RepositorySystemSession session, LocalArtifactRequest request )
    {
        Artifact artifact = request.getArtifact();
        LocalArtifactResult result = new LocalArtifactResult( request );

        String path;
        File file;

        // Local repository CANNOT have timestamped installed, they are created only during deploy
        if ( Objects.equals( artifact.getVersion(), artifact.getBaseVersion() ) )
        {
            path = getPathForLocalArtifact( artifact );
            file = new File( getRepository().getBasedir(), path );
            checkFind( file, result );
        }

        if ( !result.isAvailable() )
        {
            for ( RemoteRepository repository : request.getRepositories() )
            {
                path = getPathForRemoteArtifact( artifact, repository, request.getContext() );
                file = new File( getRepository().getBasedir(), path );

                checkFind( file, result );

                if ( result.isAvailable() )
                {
                    break;
                }
            }
        }

        return result;
    }

    private void checkFind( File file, LocalArtifactResult result )
    {
        if ( file.isFile() )
        {
            result.setFile( file );

            Properties props = readRepos( file );

            if ( props.get( getKey( file, LOCAL_REPO_ID ) ) != null )
            {
                // artifact installed into the local repo is always accepted
                result.setAvailable( true );
            }
            else
            {
                String context = result.getRequest().getContext();
                for ( RemoteRepository repository : result.getRequest().getRepositories() )
                {
                    if ( props.get( getKey( file, getRepositoryKey( repository, context ) ) ) != null )
                    {
                        // artifact downloaded from remote repository is accepted only downloaded from request
                        // repositories
                        result.setAvailable( true );
                        result.setRepository( repository );
                        break;
                    }
                }
                if ( !result.isAvailable() && !isTracked( props, file ) )
                {
                    /*
                     * NOTE: The artifact is present but not tracked at all, for inter-op with simple local repo, assume
                     * the artifact was locally installed.
                     */
                    result.setAvailable( true );
                }
            }
        }
    }

    @Override
    public void add( RepositorySystemSession session, LocalArtifactRegistration request )
    {
        Collection<String> repositories;
        if ( request.getRepository() == null )
        {
            repositories = Collections.singleton( LOCAL_REPO_ID );
        }
        else
        {
            repositories = getRepositoryKeys( request.getRepository(), request.getContexts() );
        }
        if ( request.getRepository() == null )
        {
            addArtifact( request.getArtifact(), repositories, null, null );
        }
        else
        {
            for ( String context : request.getContexts() )
            {
                addArtifact( request.getArtifact(), repositories, request.getRepository(), context );
            }
        }
    }

    private Collection<String> getRepositoryKeys( RemoteRepository repository, Collection<String> contexts )
    {
        Collection<String> keys = new HashSet<>();

        if ( contexts != null )
        {
            for ( String context : contexts )
            {
                keys.add( getRepositoryKey( repository, context ) );
            }
        }

        return keys;
    }

    private void addArtifact( Artifact artifact, Collection<String> repositories, RemoteRepository repository,
                              String context )
    {
        requireNonNull( artifact, "artifact cannot be null" );
        String path = repository == null ? getPathForLocalArtifact( artifact )
                : getPathForRemoteArtifact( artifact, repository, context );
        File file = new File( getRepository().getBasedir(), path );
        addRepo( file, repositories );
    }

    private Properties readRepos( File artifactFile )
    {
        File trackingFile = getTrackingFile( artifactFile );

        Properties props = trackingFileManager.read( trackingFile );

        return ( props != null ) ? props : new Properties();
    }

    private void addRepo( File artifactFile, Collection<String> repositories )
    {
        Map<String, String> updates = new HashMap<>();
        for ( String repository : repositories )
        {
            updates.put( getKey( artifactFile, repository ), "" );
        }

        File trackingFile = getTrackingFile( artifactFile );

        trackingFileManager.update( trackingFile, updates );
    }

    private File getTrackingFile( File artifactFile )
    {
        return new File( artifactFile.getParentFile(), trackingFilename );
    }

    private String getKey( File file, String repository )
    {
        return file.getName() + '>' + repository;
    }

    private boolean isTracked( Properties props, File file )
    {
        if ( props != null )
        {
            String keyPrefix = file.getName() + '>';
            for ( Object key : props.keySet() )
            {
                if ( key.toString().startsWith( keyPrefix ) )
                {
                    return true;
                }
            }
        }
        return false;
    }

}
