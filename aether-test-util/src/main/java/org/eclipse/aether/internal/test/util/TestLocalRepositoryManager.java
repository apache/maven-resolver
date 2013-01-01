/*******************************************************************************
 * Copyright (c) 2010, 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.internal.test.util;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

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
 * A simplistic local repository manager that uses a temporary base directory.
 */
public class TestLocalRepositoryManager
    implements LocalRepositoryManager
{

    private LocalRepository localRepository;

    private Set<Artifact> unavailableArtifacts = new HashSet<Artifact>();

    private Set<Artifact> artifactRegistrations = new HashSet<Artifact>();

    private Set<Metadata> metadataRegistrations = new HashSet<Metadata>();

    public TestLocalRepositoryManager()
    {
        try
        {
            localRepository = new LocalRepository( TestFileUtils.createTempDir( "test-local-repo" ) );
        }
        catch ( IOException e )
        {
            throw new IllegalStateException( e );
        }
    }

    public LocalRepository getRepository()
    {
        return localRepository;
    }

    public String getPathForLocalArtifact( Artifact artifact )
    {
        String artifactId = artifact.getArtifactId();
        String groupId = artifact.getGroupId();
        String extension = artifact.getExtension();
        String version = artifact.getVersion();
        String classifier = artifact.getClassifier();

        String path =
            String.format( "%s/%s/%s/%s-%s-%s%s.%s", groupId, artifactId, version, groupId, artifactId, version,
                           classifier, extension );
        return path;
    }

    public String getPathForRemoteArtifact( Artifact artifact, RemoteRepository repository, String context )
    {
        return getPathForLocalArtifact( artifact );
    }

    public String getPathForLocalMetadata( Metadata metadata )
    {
        String artifactId = metadata.getArtifactId();
        String groupId = metadata.getGroupId();
        String version = metadata.getVersion();
        return String.format( "%s/%s/%s/%s-%s-%s.xml", groupId, artifactId, version, groupId, artifactId, version );
    }

    public String getPathForRemoteMetadata( Metadata metadata, RemoteRepository repository, String context )
    {
        return getPathForLocalMetadata( metadata );
    }

    public LocalArtifactResult find( RepositorySystemSession session, LocalArtifactRequest request )
    {
        Artifact artifact = request.getArtifact();

        LocalArtifactResult result = new LocalArtifactResult( request );
        File file = new File( localRepository.getBasedir(), getPathForLocalArtifact( artifact ) );
        result.setFile( file.isFile() ? file : null );
        result.setAvailable( file.isFile() && !unavailableArtifacts.contains( artifact ) );

        return result;
    }

    public void add( RepositorySystemSession session, LocalArtifactRegistration request )
    {
        artifactRegistrations.add( request.getArtifact() );
    }

    public LocalMetadataResult find( RepositorySystemSession session, LocalMetadataRequest request )
    {
        Metadata metadata = request.getMetadata();

        LocalMetadataResult result = new LocalMetadataResult( request );
        File file = new File( localRepository.getBasedir(), getPathForLocalMetadata( metadata ) );
        result.setFile( file.isFile() ? file : null );

        return result;
    }

    public void add( RepositorySystemSession session, LocalMetadataRegistration request )
    {
        metadataRegistrations.add( request.getMetadata() );
    }

    public Set<Artifact> getArtifactRegistration()
    {
        return artifactRegistrations;
    }

    public Set<Metadata> getMetadataRegistration()
    {
        return metadataRegistrations;
    }

    public void setArtifactAvailability( Artifact artifact, boolean available )
    {
        if ( available )
        {
            unavailableArtifacts.remove( artifact );
        }
        else
        {
            unavailableArtifacts.add( artifact );
        }
    }

}
