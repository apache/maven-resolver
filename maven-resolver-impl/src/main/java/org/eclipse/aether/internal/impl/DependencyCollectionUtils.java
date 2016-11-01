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

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactProperties;
import org.eclipse.aether.collection.VersionFilter;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;

/**
 * Utility methods for dependency collection.
 *
 */
public class DependencyCollectionUtils
{

    static DefaultDependencyNode addDependencyNode( DependencyNode parent, List<Artifact> relocations,
                                                    PremanagedDependency preManaged, VersionRangeResult rangeResult,
                                                    Version version, Dependency d, Collection<Artifact> aliases,
                                                    List<RemoteRepository> repos, String requestContext )
    {
        DefaultDependencyNode child = new DefaultDependencyNode( d );
        preManaged.applyTo( child );
        child.setRelocations( relocations );
        child.setVersionConstraint( rangeResult.getVersionConstraint() );
        child.setVersion( version );
        child.setAliases( aliases );
        child.setRepositories( repos );
        child.setRequestContext( requestContext );
        parent.getChildren().add( child );
        return child;
    }

    static DefaultDependencyNode createDependencyNode( DependencyNode parent, List<Artifact> relocations,
                                                       PremanagedDependency preManaged, VersionRangeResult rangeResult,
                                                       Version version, Dependency d,
                                                       ArtifactDescriptorResult descriptorResult,
                                                       DependencyNode cycleNode )
    {
        DefaultDependencyNode child =
            addDependencyNode( parent, relocations, preManaged, rangeResult, version, d, descriptorResult.getAliases(),
                               cycleNode.getRepositories(), cycleNode.getRequestContext() );
        child.setChildren( cycleNode.getChildren() );
        return child;
    }

    static ArtifactDescriptorRequest createArtifactDescriptorRequest( Args args, List<RemoteRepository> repositories,
                                                                      Dependency d )
    {
        ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
        descriptorRequest.setArtifact( d.getArtifact() );
        descriptorRequest.setRepositories( repositories );
        descriptorRequest.setRequestContext( args.request.getRequestContext() );
        descriptorRequest.setTrace( args.trace );
        return descriptorRequest;
    }

    static VersionRangeRequest createVersionRangeRequest( Args args, List<RemoteRepository> repositories,
                                                          Dependency dependency )
    {
        VersionRangeRequest rangeRequest = new VersionRangeRequest();
        rangeRequest.setArtifact( dependency.getArtifact() );
        rangeRequest.setRepositories( repositories );
        rangeRequest.setRequestContext( args.request.getRequestContext() );
        rangeRequest.setTrace( args.trace );
        return rangeRequest;
    }

    static boolean isLackingDescriptor( Artifact artifact )
    {
        return artifact.getProperty( ArtifactProperties.LOCAL_PATH, null ) != null;
    }

    static List<RemoteRepository> getRemoteRepositories( ArtifactRepository repository,
                                                         List<RemoteRepository> repositories )
    {
        if ( repository instanceof RemoteRepository )
        {
            return Collections.singletonList( (RemoteRepository) repository );
        }
        if ( repository != null )
        {
            return Collections.emptyList();
        }
        return repositories;
    }

    static List<? extends Version> filterVersions( Dependency dependency, VersionRangeResult rangeResult,
                                                   VersionFilter verFilter, DefaultVersionFilterContext verContext )
                                                       throws VersionRangeResolutionException
    {
        if ( rangeResult.getVersions().isEmpty() )
        {
            throw new VersionRangeResolutionException( rangeResult, "No versions available for "
                + dependency.getArtifact() + " within specified range" );
        }

        List<? extends Version> versions;
        if ( verFilter != null && rangeResult.getVersionConstraint().getRange() != null )
        {
            verContext.set( dependency, rangeResult );
            try
            {
                verFilter.filterVersions( verContext );
            }
            catch ( RepositoryException e )
            {
                throw new VersionRangeResolutionException( rangeResult, "Failed to filter versions for "
                    + dependency.getArtifact() + ": " + e.getMessage(), e );
            }
            versions = verContext.get();
            if ( versions.isEmpty() )
            {
                throw new VersionRangeResolutionException( rangeResult, "No acceptable versions for "
                    + dependency.getArtifact() + ": " + rangeResult.getVersions() );
            }
        }
        else
        {
            versions = rangeResult.getVersions();
        }
        return versions;
    }
    
    static RepositorySystemSession optimizeSession( RepositorySystemSession session )
    {
        DefaultRepositorySystemSession optimized = new DefaultRepositorySystemSession( session );
        optimized.setArtifactTypeRegistry( CachingArtifactTypeRegistry.newInstance( session ) );
        return optimized;
    }

    static String getId( Dependency d )
    {
        Artifact a = d.getArtifact();
        return a.getGroupId() + ':' + a.getArtifactId() + ':' + a.getClassifier() + ':' + a.getExtension();
    }
}