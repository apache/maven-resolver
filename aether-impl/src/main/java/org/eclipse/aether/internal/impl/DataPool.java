/*******************************************************************************
 * Copyright (c) 2010, 2012 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.internal.impl;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.eclipse.aether.RepositoryCache;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.DependencyManager;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.collection.DependencyTraverser;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionConstraint;

/**
 */
final class DataPool
{

    private static final String ARTIFACT_POOL = DataPool.class.getName() + "$Artifact";

    private static final String DEPENDENCY_POOL = DataPool.class.getName() + "$Dependency";

    private static final String DESCRIPTORS = DataPool.class.getName() + "$Descriptors";

    public static final ArtifactDescriptorResult NO_DESCRIPTOR =
        new ArtifactDescriptorResult( new ArtifactDescriptorRequest() );

    private ObjectPool<Artifact> artifacts;

    private ObjectPool<Dependency> dependencies;

    private Map<Object, Descriptor> descriptors;

    private Map<Object, Constraint> constraints = new WeakHashMap<Object, Constraint>();

    private Map<Object, List<DependencyNode>> nodes = new HashMap<Object, List<DependencyNode>>( 256 );

    @SuppressWarnings( "unchecked" )
    public DataPool( RepositorySystemSession session )
    {
        RepositoryCache cache = session.getCache();

        if ( cache != null )
        {
            artifacts = (ObjectPool<Artifact>) cache.get( session, ARTIFACT_POOL );
            dependencies = (ObjectPool<Dependency>) cache.get( session, DEPENDENCY_POOL );
            descriptors = (Map<Object, Descriptor>) cache.get( session, DESCRIPTORS );
        }

        if ( artifacts == null )
        {
            artifacts = new ObjectPool<Artifact>();
            if ( cache != null )
            {
                cache.put( session, ARTIFACT_POOL, artifacts );
            }
        }

        if ( dependencies == null )
        {
            dependencies = new ObjectPool<Dependency>();
            if ( cache != null )
            {
                cache.put( session, DEPENDENCY_POOL, dependencies );
            }
        }

        if ( descriptors == null )
        {
            descriptors = Collections.synchronizedMap( new WeakHashMap<Object, Descriptor>( 256 ) );
            if ( cache != null )
            {
                cache.put( session, DESCRIPTORS, descriptors );
            }
        }
    }

    public Artifact intern( Artifact artifact )
    {
        return artifacts.intern( artifact );
    }

    public Dependency intern( Dependency dependency )
    {
        return dependencies.intern( dependency );
    }

    public Object toKey( ArtifactDescriptorRequest request )
    {
        return request.getArtifact();
    }

    public ArtifactDescriptorResult getDescriptor( Object key, ArtifactDescriptorRequest request )
    {
        Descriptor descriptor = descriptors.get( key );
        if ( descriptor != null )
        {
            return descriptor.toResult( request );
        }
        return null;
    }

    public void putDescriptor( Object key, ArtifactDescriptorResult result )
    {
        descriptors.put( key, new GoodDescriptor( result ) );
    }

    public void putDescriptor( Object key, ArtifactDescriptorException e )
    {
        descriptors.put( key, BadDescriptor.INSTANCE );
    }

    public Object toKey( VersionRangeRequest request )
    {
        return new ConstraintKey( request );
    }

    public VersionRangeResult getConstraint( Object key, VersionRangeRequest request )
    {
        Constraint constraint = constraints.get( key );
        if ( constraint != null )
        {
            return constraint.toResult( request );
        }
        return null;
    }

    public void putConstraint( Object key, VersionRangeResult result )
    {
        constraints.put( key, new Constraint( result ) );
    }

    public Object toKey( Artifact artifact, List<RemoteRepository> repositories, DependencySelector selector,
                         DependencyManager manager, DependencyTraverser traverser )
    {
        return new GraphKey( artifact, repositories, selector, manager, traverser );
    }

    public List<DependencyNode> getChildren( Object key )
    {
        return nodes.get( key );
    }

    public void putChildren( Object key, List<DependencyNode> children )
    {
        nodes.put( key, children );
    }

    static abstract class Descriptor
    {

        public abstract ArtifactDescriptorResult toResult( ArtifactDescriptorRequest request );

    }

    static class GoodDescriptor
        extends Descriptor
    {

        final Artifact artifact;

        final List<Artifact> relocations;

        final Collection<Artifact> aliases;

        final List<RemoteRepository> repositories;

        final List<Dependency> dependencies;

        final List<Dependency> managedDependencies;

        public GoodDescriptor( ArtifactDescriptorResult result )
        {
            artifact = result.getArtifact();
            relocations = result.getRelocations();
            aliases = result.getAliases();
            dependencies = result.getDependencies();
            managedDependencies = result.getManagedDependencies();
            repositories = result.getRepositories();
        }

        public ArtifactDescriptorResult toResult( ArtifactDescriptorRequest request )
        {
            ArtifactDescriptorResult result = new ArtifactDescriptorResult( request );
            result.setArtifact( artifact );
            result.setRelocations( relocations );
            result.setAliases( aliases );
            result.setDependencies( dependencies );
            result.setManagedDependencies( managedDependencies );
            result.setRepositories( repositories );
            return result;
        }

    }

    static class BadDescriptor
        extends Descriptor
    {

        static final BadDescriptor INSTANCE = new BadDescriptor();

        public ArtifactDescriptorResult toResult( ArtifactDescriptorRequest request )
        {
            return NO_DESCRIPTOR;
        }

    }

    static class Constraint
    {

        final Map<Version, ArtifactRepository> repositories;

        final VersionConstraint versionConstraint;

        public Constraint( VersionRangeResult result )
        {
            versionConstraint = result.getVersionConstraint();
            repositories = new LinkedHashMap<Version, ArtifactRepository>();
            for ( Version version : result.getVersions() )
            {
                repositories.put( version, result.getRepository( version ) );
            }
        }

        public VersionRangeResult toResult( VersionRangeRequest request )
        {
            VersionRangeResult result = new VersionRangeResult( request );
            for ( Map.Entry<Version, ArtifactRepository> entry : repositories.entrySet() )
            {
                result.addVersion( entry.getKey() );
                result.setRepository( entry.getKey(), entry.getValue() );
            }
            result.setVersionConstraint( versionConstraint );
            return result;
        }

    }

    static class ConstraintKey
    {

        private final Artifact artifact;

        private final List<RemoteRepository> repositories;

        private final int hashCode;

        public ConstraintKey( VersionRangeRequest request )
        {
            artifact = request.getArtifact();
            repositories = request.getRepositories();
            hashCode = artifact.hashCode();
        }

        @Override
        public boolean equals( Object obj )
        {
            if ( obj == this )
            {
                return true;
            }
            else if ( !( obj instanceof ConstraintKey ) )
            {
                return false;
            }
            ConstraintKey that = (ConstraintKey) obj;
            return artifact.equals( that.artifact ) && equals( repositories, that.repositories );
        }

        private static boolean equals( Collection<RemoteRepository> repos1, Collection<RemoteRepository> repos2 )
        {
            if ( repos1.size() != repos2.size() )
            {
                return false;
            }
            for ( Iterator<RemoteRepository> it1 = repos1.iterator(), it2 = repos2.iterator(); it1.hasNext(); )
            {
                RemoteRepository repo1 = it1.next();
                RemoteRepository repo2 = it2.next();
                if ( repo1.isRepositoryManager() != repo2.isRepositoryManager() )
                {
                    return false;
                }
                if ( repo1.isRepositoryManager() )
                {
                    if ( !equals( repo1.getMirroredRepositories(), repo2.getMirroredRepositories() ) )
                    {
                        return false;
                    }
                }
                else if ( !repo1.getUrl().equals( repo2.getUrl() ) )
                {
                    return false;
                }
                else if ( repo1.getPolicy( true ).isEnabled() != repo2.getPolicy( true ).isEnabled() )
                {
                    return false;
                }
                else if ( repo1.getPolicy( false ).isEnabled() != repo2.getPolicy( false ).isEnabled() )
                {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode()
        {
            return hashCode;
        }

    }

    static class GraphKey
    {

        private final Artifact artifact;

        private final List<RemoteRepository> repositories;

        private final DependencySelector selector;

        private final DependencyManager manager;

        private final DependencyTraverser traverser;

        private final int hashCode;

        public GraphKey( Artifact artifact, List<RemoteRepository> repositories, DependencySelector selector,
                         DependencyManager manager, DependencyTraverser traverser )
        {
            this.artifact = artifact;
            this.repositories = repositories;
            this.selector = selector;
            this.manager = manager;
            this.traverser = traverser;

            int hash = 17;
            hash = hash * 31 + artifact.hashCode();
            hash = hash * 31 + repositories.hashCode();
            hash = hash * 31 + selector.hashCode();
            hash = hash * 31 + manager.hashCode();
            hash = hash * 31 + traverser.hashCode();
            hashCode = hash;
        }

        @Override
        public boolean equals( Object obj )
        {
            if ( obj == this )
            {
                return true;
            }
            else if ( !( obj instanceof GraphKey ) )
            {
                return false;
            }
            GraphKey that = (GraphKey) obj;
            return artifact.equals( that.artifact ) && repositories.equals( that.repositories )
                && selector.equals( that.selector ) && manager.equals( that.manager )
                && traverser.equals( that.traverser );
        }

        @Override
        public int hashCode()
        {
            return hashCode;
        }

    }

}
