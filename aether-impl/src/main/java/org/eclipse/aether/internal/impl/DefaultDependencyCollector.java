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
package org.eclipse.aether.internal.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactProperties;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.collection.DependencyManagement;
import org.eclipse.aether.collection.DependencyManager;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.collection.DependencyTraverser;
import org.eclipse.aether.collection.VersionFilter;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.impl.ArtifactDescriptorReader;
import org.eclipse.aether.impl.DependencyCollector;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.impl.VersionRangeResolver;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.spi.log.LoggerFactory;
import org.eclipse.aether.spi.log.NullLoggerFactory;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.graph.manager.DependencyManagerUtils;
import org.eclipse.aether.util.graph.transformer.TransformationContextKeys;
import org.eclipse.aether.version.Version;

/**
 */
@Named
@Component( role = DependencyCollector.class )
public class DefaultDependencyCollector
    implements DependencyCollector, Service
{

    @Requirement( role = LoggerFactory.class )
    private Logger logger = NullLoggerFactory.LOGGER;

    @Requirement
    private RemoteRepositoryManager remoteRepositoryManager;

    @Requirement
    private ArtifactDescriptorReader descriptorReader;

    @Requirement
    private VersionRangeResolver versionRangeResolver;

    public DefaultDependencyCollector()
    {
        // enables default constructor
    }

    @Inject
    DefaultDependencyCollector( RemoteRepositoryManager remoteRepositoryManager,
                                ArtifactDescriptorReader artifactDescriptorReader,
                                VersionRangeResolver versionRangeResolver, LoggerFactory loggerFactory )
    {
        setRemoteRepositoryManager( remoteRepositoryManager );
        setArtifactDescriptorReader( artifactDescriptorReader );
        setVersionRangeResolver( versionRangeResolver );
        setLoggerFactory( loggerFactory );
    }

    public void initService( ServiceLocator locator )
    {
        setLoggerFactory( locator.getService( LoggerFactory.class ) );
        setRemoteRepositoryManager( locator.getService( RemoteRepositoryManager.class ) );
        setArtifactDescriptorReader( locator.getService( ArtifactDescriptorReader.class ) );
        setVersionRangeResolver( locator.getService( VersionRangeResolver.class ) );
    }

    public DefaultDependencyCollector setLoggerFactory( LoggerFactory loggerFactory )
    {
        this.logger = NullLoggerFactory.getSafeLogger( loggerFactory, getClass() );
        return this;
    }

    void setLogger( LoggerFactory loggerFactory )
    {
        // plexus support
        setLoggerFactory( loggerFactory );
    }

    public DefaultDependencyCollector setRemoteRepositoryManager( RemoteRepositoryManager remoteRepositoryManager )
    {
        if ( remoteRepositoryManager == null )
        {
            throw new IllegalArgumentException( "remote repository manager has not been specified" );
        }
        this.remoteRepositoryManager = remoteRepositoryManager;
        return this;
    }

    public DefaultDependencyCollector setArtifactDescriptorReader( ArtifactDescriptorReader artifactDescriptorReader )
    {
        if ( artifactDescriptorReader == null )
        {
            throw new IllegalArgumentException( "artifact descriptor reader has not been specified" );
        }
        this.descriptorReader = artifactDescriptorReader;
        return this;
    }

    public DefaultDependencyCollector setVersionRangeResolver( VersionRangeResolver versionRangeResolver )
    {
        if ( versionRangeResolver == null )
        {
            throw new IllegalArgumentException( "version range resolver has not been specified" );
        }
        this.versionRangeResolver = versionRangeResolver;
        return this;
    }

    public CollectResult collectDependencies( RepositorySystemSession session, CollectRequest request )
        throws DependencyCollectionException
    {
        session = optimizeSession( session );

        RequestTrace trace = RequestTrace.newChild( request.getTrace(), request );

        CollectResult result = new CollectResult( request );

        DependencySelector depSelector = session.getDependencySelector();
        DependencyManager depManager = session.getDependencyManager();
        DependencyTraverser depTraverser = session.getDependencyTraverser();
        VersionFilter verFilter = session.getVersionFilter();

        Dependency root = request.getRoot();
        List<RemoteRepository> repositories = request.getRepositories();
        List<Dependency> dependencies = request.getDependencies();
        List<Dependency> managedDependencies = request.getManagedDependencies();

        Map<String, Object> stats = logger.isDebugEnabled() ? new LinkedHashMap<String, Object>() : null;
        long time1 = System.currentTimeMillis();

        DefaultDependencyNode node = null;
        if ( root != null )
        {
            List<? extends Version> versions;
            VersionRangeResult rangeResult;
            try
            {
                VersionRangeRequest rangeRequest =
                    new VersionRangeRequest( root.getArtifact(), request.getRepositories(), request.getRequestContext() );
                rangeRequest.setTrace( trace );
                rangeResult = versionRangeResolver.resolveVersionRange( session, rangeRequest );
                versions = filterVersions( root, rangeResult, verFilter, new DefaultVersionFilterContext( session ) );
            }
            catch ( VersionRangeResolutionException e )
            {
                result.addException( e );
                throw new DependencyCollectionException( result, e.getMessage() );
            }

            Version version = versions.get( versions.size() - 1 );
            root = root.setArtifact( root.getArtifact().setVersion( version.toString() ) );

            ArtifactDescriptorResult descriptorResult;
            try
            {
                ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
                descriptorRequest.setArtifact( root.getArtifact() );
                descriptorRequest.setRepositories( request.getRepositories() );
                descriptorRequest.setRequestContext( request.getRequestContext() );
                descriptorRequest.setTrace( trace );
                if ( isLackingDescriptor( root.getArtifact() ) )
                {
                    descriptorResult = new ArtifactDescriptorResult( descriptorRequest );
                }
                else
                {
                    descriptorResult = descriptorReader.readArtifactDescriptor( session, descriptorRequest );
                }
            }
            catch ( ArtifactDescriptorException e )
            {
                result.addException( e );
                throw new DependencyCollectionException( result, e.getMessage() );
            }

            root = root.setArtifact( descriptorResult.getArtifact() );

            if ( !session.isIgnoreArtifactDescriptorRepositories() )
            {
                repositories =
                    remoteRepositoryManager.aggregateRepositories( session, repositories,
                                                                   descriptorResult.getRepositories(), true );
            }
            dependencies = mergeDeps( dependencies, descriptorResult.getDependencies() );
            managedDependencies = mergeDeps( managedDependencies, descriptorResult.getManagedDependencies() );

            node = new DefaultDependencyNode( root );
            node.setRequestContext( request.getRequestContext() );
            node.setRelocations( descriptorResult.getRelocations() );
            node.setVersionConstraint( rangeResult.getVersionConstraint() );
            node.setVersion( version );
            node.setAliases( descriptorResult.getAliases() );
            node.setRepositories( request.getRepositories() );
        }
        else
        {
            node = new DefaultDependencyNode( request.getRootArtifact() );
        }

        result.setRoot( node );

        boolean traverse = root == null || depTraverser == null || depTraverser.traverseDependency( root );
        String errorPath = null;
        if ( traverse && !dependencies.isEmpty() )
        {
            DataPool pool = new DataPool( session );

            NodeStack nodes = new NodeStack();
            nodes.push( node );

            DefaultDependencyCollectionContext context =
                new DefaultDependencyCollectionContext( session, request.getRootArtifact(), root, managedDependencies );

            DefaultVersionFilterContext versionContext = new DefaultVersionFilterContext( session );

            Args args = new Args( result, session, trace, pool, nodes, context, versionContext );

            process( args, dependencies, repositories,
                     ( depSelector != null ) ? depSelector.deriveChildSelector( context ) : null,
                     ( depManager != null ) ? depManager.deriveChildManager( context ) : null,
                     ( depTraverser != null ) ? depTraverser.deriveChildTraverser( context ) : null,
                     ( verFilter != null ) ? verFilter.deriveChildFilter( context ) : null );

            errorPath = args.errorPath;
        }

        long time2 = System.currentTimeMillis();

        DependencyGraphTransformer transformer = session.getDependencyGraphTransformer();
        if ( transformer != null )
        {
            try
            {
                DefaultDependencyGraphTransformationContext context =
                    new DefaultDependencyGraphTransformationContext( session );
                context.put( TransformationContextKeys.STATS, stats );
                result.setRoot( transformer.transformGraph( node, context ) );
            }
            catch ( RepositoryException e )
            {
                result.addException( e );
            }
        }

        if ( stats != null )
        {
            long time3 = System.currentTimeMillis();
            stats.put( "DefaultDependencyCollector.collectTime", time2 - time1 );
            stats.put( "DefaultDependencyCollector.transformTime", time3 - time2 );
            logger.debug( "Dependency collection stats: " + stats );
        }

        if ( errorPath != null )
        {
            throw new DependencyCollectionException( result, "Failed to collect dependencies at " + errorPath );
        }
        if ( !result.getExceptions().isEmpty() )
        {
            throw new DependencyCollectionException( result );
        }

        return result;
    }

    private RepositorySystemSession optimizeSession( RepositorySystemSession session )
    {
        DefaultRepositorySystemSession optimized = new DefaultRepositorySystemSession( session );
        optimized.setArtifactTypeRegistry( CachingArtifactTypeRegistry.newInstance( session ) );
        return optimized;
    }

    private List<Dependency> mergeDeps( List<Dependency> dominant, List<Dependency> recessive )
    {
        List<Dependency> result;
        if ( dominant == null || dominant.isEmpty() )
        {
            result = recessive;
        }
        else if ( recessive == null || recessive.isEmpty() )
        {
            result = dominant;
        }
        else
        {
            result = new ArrayList<Dependency>( dominant.size() + recessive.size() );
            Collection<String> ids = new HashSet<String>();
            for ( Dependency dependency : dominant )
            {
                ids.add( getId( dependency.getArtifact() ) );
                result.add( dependency );
            }
            for ( Dependency dependency : recessive )
            {
                if ( !ids.contains( getId( dependency.getArtifact() ) ) )
                {
                    result.add( dependency );
                }
            }
        }
        return result;
    }

    private String getId( Artifact a )
    {
        return a.getGroupId() + ':' + a.getArtifactId() + ':' + a.getClassifier() + ':' + a.getExtension();
    }

    private void process( final Args args, List<Dependency> dependencies, List<RemoteRepository> repositories,
                          DependencySelector depSelector, DependencyManager depManager,
                          DependencyTraverser depTraverser, VersionFilter verFilter )
    {
        nextDependency: for ( Dependency dependency : dependencies )
        {
            boolean disableVersionManagement = false;

            List<Artifact> relocations = Collections.emptyList();

            thisDependency: while ( true )
            {
                if ( depSelector != null && !depSelector.selectDependency( dependency ) )
                {
                    continue nextDependency;
                }

                DependencyManagement depMngt =
                    ( depManager != null ) ? depManager.manageDependency( dependency ) : null;
                int managedBits = 0;
                String premanagedVersion = null;
                String premanagedScope = null;
                Boolean premanagedOptional = null;

                if ( depMngt != null )
                {
                    if ( depMngt.getVersion() != null && !disableVersionManagement )
                    {
                        Artifact artifact = dependency.getArtifact();
                        premanagedVersion = artifact.getVersion();
                        dependency = dependency.setArtifact( artifact.setVersion( depMngt.getVersion() ) );
                        managedBits |= DependencyNode.MANAGED_VERSION;
                    }
                    if ( depMngt.getProperties() != null )
                    {
                        Artifact artifact = dependency.getArtifact();
                        dependency = dependency.setArtifact( artifact.setProperties( depMngt.getProperties() ) );
                        managedBits |= DependencyNode.MANAGED_PROPERTIES;
                    }
                    if ( depMngt.getScope() != null )
                    {
                        premanagedScope = dependency.getScope();
                        dependency = dependency.setScope( depMngt.getScope() );
                        managedBits |= DependencyNode.MANAGED_SCOPE;
                    }
                    if ( depMngt.getOptional() != null )
                    {
                        premanagedOptional = dependency.isOptional();
                        dependency = dependency.setOptional( depMngt.getOptional() );
                        managedBits |= DependencyNode.MANAGED_OPTIONAL;
                    }
                    if ( depMngt.getExclusions() != null )
                    {
                        dependency = dependency.setExclusions( depMngt.getExclusions() );
                        managedBits |= DependencyNode.MANAGED_EXCLUSIONS;
                    }
                }
                disableVersionManagement = false;

                boolean noDescriptor = isLackingDescriptor( dependency.getArtifact() );

                boolean traverse =
                    !noDescriptor && ( depTraverser == null || depTraverser.traverseDependency( dependency ) );

                List<? extends Version> versions;
                VersionRangeResult rangeResult;
                try
                {
                    VersionRangeRequest rangeRequest = new VersionRangeRequest();
                    rangeRequest.setArtifact( dependency.getArtifact() );
                    rangeRequest.setRepositories( repositories );
                    rangeRequest.setRequestContext( args.result.getRequest().getRequestContext() );
                    rangeRequest.setTrace( args.trace );

                    Object key = args.pool.toKey( rangeRequest );
                    rangeResult = args.pool.getConstraint( key, rangeRequest );
                    if ( rangeResult == null )
                    {
                        rangeResult = versionRangeResolver.resolveVersionRange( args.session, rangeRequest );
                        args.pool.putConstraint( key, rangeResult );
                    }

                    versions = filterVersions( dependency, rangeResult, verFilter, args.versionContext );
                }
                catch ( VersionRangeResolutionException e )
                {
                    addException( args, dependency, e );
                    continue nextDependency;
                }

                for ( Version version : versions )
                {
                    Artifact originalArtifact = dependency.getArtifact().setVersion( version.toString() );
                    Dependency d = dependency.setArtifact( originalArtifact );

                    ArtifactDescriptorResult descriptorResult;
                    {
                        ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
                        descriptorRequest.setArtifact( d.getArtifact() );
                        descriptorRequest.setRepositories( repositories );
                        descriptorRequest.setRequestContext( args.result.getRequest().getRequestContext() );
                        descriptorRequest.setTrace( args.trace );

                        if ( noDescriptor )
                        {
                            descriptorResult = new ArtifactDescriptorResult( descriptorRequest );
                        }
                        else
                        {
                            Object key = args.pool.toKey( descriptorRequest );
                            descriptorResult = args.pool.getDescriptor( key, descriptorRequest );
                            if ( descriptorResult == null )
                            {
                                try
                                {
                                    descriptorResult =
                                        descriptorReader.readArtifactDescriptor( args.session, descriptorRequest );
                                    args.pool.putDescriptor( key, descriptorResult );
                                }
                                catch ( ArtifactDescriptorException e )
                                {
                                    addException( args, d, e );
                                    args.pool.putDescriptor( key, e );
                                    continue;
                                }
                            }
                            else if ( descriptorResult == DataPool.NO_DESCRIPTOR )
                            {
                                continue;
                            }
                        }
                    }

                    d = d.setArtifact( descriptorResult.getArtifact() );

                    DependencyNode node = args.nodes.top();

                    DependencyNode cycleNode = args.nodes.find( d.getArtifact() );
                    if ( cycleNode != null )
                    {
                        DefaultDependencyNode child = new DefaultDependencyNode( d );
                        child.setChildren( cycleNode.getChildren() );
                        child.setManagedBits( managedBits );
                        if ( args.premanagedState )
                        {
                            child.setData( DependencyManagerUtils.NODE_DATA_PREMANAGED_VERSION, premanagedVersion );
                            child.setData( DependencyManagerUtils.NODE_DATA_PREMANAGED_SCOPE, premanagedScope );
                            child.setData( DependencyManagerUtils.NODE_DATA_PREMANAGED_OPTIONAL, premanagedOptional );
                        }
                        child.setRelocations( relocations );
                        child.setVersionConstraint( rangeResult.getVersionConstraint() );
                        child.setVersion( version );
                        child.setAliases( descriptorResult.getAliases() );
                        child.setRepositories( cycleNode.getRepositories() );
                        child.setRequestContext( cycleNode.getRequestContext() );

                        node.getChildren().add( child );

                        continue;
                    }

                    if ( !descriptorResult.getRelocations().isEmpty() )
                    {
                        relocations = descriptorResult.getRelocations();

                        disableVersionManagement =
                            originalArtifact.getGroupId().equals( d.getArtifact().getGroupId() )
                                && originalArtifact.getArtifactId().equals( d.getArtifact().getArtifactId() );

                        dependency = d;
                        continue thisDependency;
                    }

                    d = args.pool.intern( d.setArtifact( args.pool.intern( d.getArtifact() ) ) );

                    List<RemoteRepository> repos =
                        getRemoteRepositories( rangeResult.getRepository( version ), repositories );

                    DefaultDependencyNode child = new DefaultDependencyNode( d );
                    child.setManagedBits( managedBits );
                    if ( args.premanagedState )
                    {
                        child.setData( DependencyManagerUtils.NODE_DATA_PREMANAGED_VERSION, premanagedVersion );
                        child.setData( DependencyManagerUtils.NODE_DATA_PREMANAGED_SCOPE, premanagedScope );
                        child.setData( DependencyManagerUtils.NODE_DATA_PREMANAGED_OPTIONAL, premanagedOptional );
                    }
                    child.setRelocations( relocations );
                    child.setVersionConstraint( rangeResult.getVersionConstraint() );
                    child.setVersion( version );
                    child.setAliases( descriptorResult.getAliases() );
                    child.setRepositories( repos );
                    child.setRequestContext( args.result.getRequest().getRequestContext() );

                    node.getChildren().add( child );

                    boolean recurse = traverse && !descriptorResult.getDependencies().isEmpty();
                    if ( recurse )
                    {
                        DefaultDependencyCollectionContext context = args.collectionContext;
                        context.set( d, descriptorResult.getManagedDependencies() );

                        DependencySelector childSelector =
                            ( depSelector != null ) ? depSelector.deriveChildSelector( context ) : null;
                        DependencyManager childManager =
                            ( depManager != null ) ? depManager.deriveChildManager( context ) : null;
                        DependencyTraverser childTraverser =
                            ( depTraverser != null ) ? depTraverser.deriveChildTraverser( context ) : null;
                        VersionFilter childFilter =
                            ( verFilter != null ) ? verFilter.deriveChildFilter( context ) : null;

                        List<RemoteRepository> childRepos = null;
                        if ( args.ignoreRepos )
                        {
                            childRepos = repositories;
                        }
                        else
                        {
                            childRepos =
                                remoteRepositoryManager.aggregateRepositories( args.session, repositories,
                                                                               descriptorResult.getRepositories(), true );
                        }

                        Object key =
                            args.pool.toKey( d.getArtifact(), childRepos, childSelector, childManager, childTraverser,
                                             childFilter );

                        List<DependencyNode> children = args.pool.getChildren( key );
                        if ( children == null )
                        {
                            args.pool.putChildren( key, child.getChildren() );

                            args.nodes.push( child );

                            process( args, descriptorResult.getDependencies(), childRepos, childSelector, childManager,
                                     childTraverser, childFilter );

                            args.nodes.pop();
                        }
                        else
                        {
                            child.setChildren( children );
                        }
                    }
                }

                break;
            }
        }
    }

    private boolean isLackingDescriptor( Artifact artifact )
    {
        return artifact.getProperty( ArtifactProperties.LOCAL_PATH, null ) != null;
    }

    private void addException( Args args, Dependency dependency, Exception e )
    {
        if ( args.maxExceptions < 0 || args.result.getExceptions().size() < args.maxExceptions )
        {
            args.result.addException( e );
            if ( args.errorPath == null )
            {
                StringBuilder buffer = new StringBuilder( 256 );
                for ( int i = 0; i < args.nodes.size(); i++ )
                {
                    if ( buffer.length() > 0 )
                    {
                        buffer.append( " -> " );
                    }
                    Dependency dep = args.nodes.get( i ).getDependency();
                    if ( dep == null )
                    {
                        continue;
                    }
                    buffer.append( dep.getArtifact() );
                }
                if ( buffer.length() > 0 )
                {
                    buffer.append( " -> " );
                }
                buffer.append( dependency.getArtifact() );
                args.errorPath = buffer.toString();
            }
        }
    }

    private List<RemoteRepository> getRemoteRepositories( ArtifactRepository repository,
                                                          List<RemoteRepository> repositories )
    {
        if ( repository instanceof RemoteRepository )
        {
            return Collections.singletonList( (RemoteRepository) repository );
        }
        else if ( repository != null )
        {
            return Collections.emptyList();
        }
        return repositories;
    }

    private List<? extends Version> filterVersions( Dependency dependency, VersionRangeResult rangeResult,
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

    static class Args
    {

        final CollectResult result;

        final RepositorySystemSession session;

        final boolean ignoreRepos;

        final int maxExceptions;

        final boolean premanagedState;

        final RequestTrace trace;

        final DataPool pool;

        final NodeStack nodes;

        final DefaultDependencyCollectionContext collectionContext;

        final DefaultVersionFilterContext versionContext;

        String errorPath;

        public Args( CollectResult result, RepositorySystemSession session, RequestTrace trace, DataPool pool,
                     NodeStack nodes, DefaultDependencyCollectionContext collectionContext,
                     DefaultVersionFilterContext versionContext )
        {
            this.result = result;
            this.session = session;
            this.ignoreRepos = session.isIgnoreArtifactDescriptorRepositories();
            this.maxExceptions = ConfigUtils.getInteger( session, 50, "aether.dependencyCollector.maxExceptions" );
            this.premanagedState = ConfigUtils.getBoolean( session, false, DependencyManagerUtils.CONFIG_PROP_VERBOSE );
            this.trace = trace;
            this.pool = pool;
            this.nodes = nodes;
            this.collectionContext = collectionContext;
            this.versionContext = versionContext;
        }

    }

}
