package org.eclipse.aether.internal.impl.collect.bf;

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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

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
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.impl.ArtifactDescriptorReader;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.impl.VersionRangeResolver;
import org.eclipse.aether.internal.impl.collect.CachingArtifactTypeRegistry;
import org.eclipse.aether.internal.impl.collect.DataPool;
import org.eclipse.aether.internal.impl.collect.DefaultDependencyCollectionContext;
import org.eclipse.aether.internal.impl.collect.DefaultDependencyCycle;
import org.eclipse.aether.internal.impl.collect.DefaultDependencyGraphTransformationContext;
import org.eclipse.aether.internal.impl.collect.DefaultVersionFilterContext;
import org.eclipse.aether.internal.impl.collect.DependencyCollectorDelegate;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.graph.manager.DependencyManagerUtils;
import org.eclipse.aether.util.graph.transformer.TransformationContextKeys;
import org.eclipse.aether.version.Version;

import static java.util.Objects.requireNonNull;
import static org.eclipse.aether.internal.impl.collect.DefaultDependencyCycle.find;

/**
 * Breadth-first {@link org.eclipse.aether.impl.DependencyCollector}
 *
 * @since 1.8.0
 */
@Singleton
@Named( BfDependencyCollector.NAME )
public class BfDependencyCollector
    extends DependencyCollectorDelegate implements Service
{
    public static final String NAME = "bf";

    /**
     * The key in the repository session's {@link RepositorySystemSession#getConfigProperties()
     * configuration properties} used to store a {@link Boolean} flag controlling the resolver's skip mode.
     *
     * @since 1.8.0
     */
    public static final String CONFIG_PROP_SKIPPER = "aether.dependencyCollector.bf.skipper";

    /**
     * The default value for {@link #CONFIG_PROP_SKIPPER}, {@code true}.
     *
     * @since 1.8.0
     */
    public static final boolean CONFIG_PROP_SKIPPER_DEFAULT = true;

    public BfDependencyCollector()
    {
        // enables default constructor
    }

    @Inject
    BfDependencyCollector( RemoteRepositoryManager remoteRepositoryManager,
                           ArtifactDescriptorReader artifactDescriptorReader,
                           VersionRangeResolver versionRangeResolver )
    {
        super( remoteRepositoryManager, artifactDescriptorReader, versionRangeResolver );
    }

    @SuppressWarnings( "checkstyle:methodlength" )
    public CollectResult collectDependencies( RepositorySystemSession session, CollectRequest request )
        throws DependencyCollectionException
    {
        requireNonNull( session, "session cannot be null" );
        requireNonNull( request, "request cannot be null" );
        session = optimizeSession( session );

        boolean useSkip = ConfigUtils.getBoolean(
                session, CONFIG_PROP_SKIPPER_DEFAULT, CONFIG_PROP_SKIPPER
        );
        if ( useSkip )
        {
            logger.debug( "Collector skip mode enabled" );
        }

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

        Map<String, Object> stats = new LinkedHashMap<>();
        long time1 = System.nanoTime();

        DefaultDependencyNode node;
        if ( root != null )
        {
            List<? extends Version> versions;
            VersionRangeResult rangeResult;
            try
            {
                VersionRangeRequest rangeRequest =
                    new VersionRangeRequest( root.getArtifact(), request.getRepositories(),
                                             request.getRequestContext() );
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
                repositories = remoteRepositoryManager.aggregateRepositories( session, repositories,
                                                                              descriptorResult.getRepositories(),
                                                                              true );
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
            node.setRequestContext( request.getRequestContext() );
            node.setRepositories( request.getRepositories() );
        }

        result.setRoot( node );

        boolean traverse = root == null || depTraverser == null || depTraverser.traverseDependency( root );
        String errorPath = null;
        if ( traverse && !dependencies.isEmpty() )
        {
            DataPool pool = new DataPool( session );

            DefaultDependencyCollectionContext context =
                new DefaultDependencyCollectionContext( session, request.getRootArtifact(), root, managedDependencies );

            DefaultVersionFilterContext versionContext = new DefaultVersionFilterContext( session );

            Args args =
                    new Args( session, trace, pool, context, versionContext, request,
                            useSkip ? DependencyResolutionSkipper.defaultSkipper()
                                    : DependencyResolutionSkipper.neverSkipper() );
            Results results = new Results( result, session );

            DependencySelector rootDepSelector =
                    depSelector != null ? depSelector.deriveChildSelector( context ) : null;
            DependencyManager rootDepManager = depManager != null ? depManager.deriveChildManager( context ) : null;
            DependencyTraverser rootDepTraverser =
                    depTraverser != null ? depTraverser.deriveChildTraverser( context ) : null;
            VersionFilter rootVerFilter = verFilter != null ? verFilter.deriveChildFilter( context ) : null;

            List<DependencyNode> parents = Collections.singletonList( node );
            for ( Dependency dependency : dependencies )
            {
                args.dependencyProcessingQueue.add(
                        new DependencyProcessingContext( rootDepSelector, rootDepManager, rootDepTraverser,
                                rootVerFilter, repositories, managedDependencies, parents,
                                dependency ) );
            }

            while ( !args.dependencyProcessingQueue.isEmpty() )
            {
                processDependency( args, results, args.dependencyProcessingQueue.remove(), Collections.emptyList(),
                        false );
            }

            args.skipper.report();
            errorPath = results.errorPath;
        }

        long time2 = System.nanoTime();

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

        long time3 = System.nanoTime();
        if ( logger.isDebugEnabled() )
        {
            stats.put( "BfDependencyCollector.collectTime", time2 - time1 );
            stats.put( "BfDependencyCollector.transformTime", time3 - time2 );
            logger.debug( "Dependency collection stats {}", stats );
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

    private static RepositorySystemSession optimizeSession( RepositorySystemSession session )
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
            int initialCapacity = dominant.size() + recessive.size();
            result = new ArrayList<>( initialCapacity );
            Collection<String> ids = new HashSet<>( initialCapacity, 1.0f );
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

    private static String getId( Artifact a )
    {
        return a.getGroupId() + ':' + a.getArtifactId() + ':' + a.getClassifier() + ':' + a.getExtension();
    }

    @SuppressWarnings( "checkstyle:parameternumber" )
    private void processDependency( Args args, Results results, DependencyProcessingContext context,
                                    List<Artifact> relocations, boolean disableVersionManagement )
    {

        if ( context.depSelector != null && !context.depSelector.selectDependency( context.dependency ) )
        {
            return;
        }

        PremanagedDependency preManaged =
                PremanagedDependency.create( context.depManager, context.dependency, disableVersionManagement,
                        args.premanagedState );
        Dependency dependency = preManaged.managedDependency;

        boolean noDescriptor = isLackingDescriptor( dependency.getArtifact() );

        boolean traverse =
                !noDescriptor && ( context.depTraverser == null || context.depTraverser.traverseDependency(
                        dependency ) );

        List<? extends Version> versions;
        VersionRangeResult rangeResult;
        try
        {
            VersionRangeRequest rangeRequest = createVersionRangeRequest( args, context.repositories, dependency );

            rangeResult = cachedResolveRangeResult( rangeRequest, args.pool, args.session );

            versions = filterVersions( dependency, rangeResult, context.verFilter, args.versionContext );
        }
        catch ( VersionRangeResolutionException e )
        {
            results.addException( dependency, e, context.parents );
            return;
        }

        //Resolve newer version first to maximize benefits of skipper
        Collections.reverse( versions );
        for ( Version version : versions )
        {
            Artifact originalArtifact = dependency.getArtifact().setVersion( version.toString() );
            Dependency d = dependency.setArtifact( originalArtifact );

            ArtifactDescriptorRequest descriptorRequest =
                    createArtifactDescriptorRequest( args, context.repositories, d );

            final ArtifactDescriptorResult descriptorResult =
                    noDescriptor
                            ? new ArtifactDescriptorResult( descriptorRequest )
                            : resolveCachedArtifactDescriptor( args.pool, descriptorRequest, args.session,
                                    context.withDependency( d ), results );

            if ( descriptorResult != null )
            {
                d = d.setArtifact( descriptorResult.getArtifact() );

                int cycleEntry = find( context.parents, d.getArtifact() );
                if ( cycleEntry >= 0 )
                {
                    results.addCycle( context.parents, cycleEntry, d );
                    DependencyNode cycleNode = context.parents.get( cycleEntry );
                    if ( cycleNode.getDependency() != null )
                    {
                        DefaultDependencyNode child =
                                createDependencyNode( relocations, preManaged, rangeResult, version, d,
                                        descriptorResult, cycleNode );
                        context.getParent().getChildren().add( child );
                        continue;
                    }
                }

                if ( !descriptorResult.getRelocations().isEmpty() )
                {
                    boolean disableVersionManagementSubsequently =
                        originalArtifact.getGroupId().equals( d.getArtifact().getGroupId() )
                            && originalArtifact.getArtifactId().equals( d.getArtifact().getArtifactId() );

                    processDependency( args, results, context.withDependency( d ), descriptorResult.getRelocations(),
                            disableVersionManagementSubsequently );
                    return;
                }
                else
                {
                    d = args.pool.intern( d.setArtifact( args.pool.intern( d.getArtifact() ) ) );

                    List<RemoteRepository> repos =
                        getRemoteRepositories( rangeResult.getRepository( version ), context.repositories );

                    DefaultDependencyNode child =
                        createDependencyNode( relocations, preManaged, rangeResult, version, d,
                                              descriptorResult.getAliases(), repos, args.request.getRequestContext() );

                    context.getParent().getChildren().add( child );

                    boolean recurse = traverse && !descriptorResult.getDependencies().isEmpty();
                    DependencyProcessingContext parentContext = context.withDependency( d );
                    if ( recurse )
                    {
                        doRecurse( args, parentContext, descriptorResult, child );
                    }
                    else if(!args.skipper.skipResolution( child, parentContext.parents ))
                    {
                        List<DependencyNode> parents = new ArrayList<>( parentContext.parents.size() + 1 );
                        parents.addAll( parentContext.parents );
                        parents.add( child );
                        args.skipper.cache( child, parents );
                    }
                }
            }
            else
            {
                List<RemoteRepository> repos =
                    getRemoteRepositories( rangeResult.getRepository( version ), context.repositories );
                DefaultDependencyNode child =
                    createDependencyNode( relocations, preManaged, rangeResult, version, d, null, repos,
                                          args.request.getRequestContext() );
                context.getParent().getChildren().add( child );
            }
        }
    }

    @SuppressWarnings( "checkstyle:parameternumber" )
    private void doRecurse( Args args, DependencyProcessingContext parentContext,
                            ArtifactDescriptorResult descriptorResult, DefaultDependencyNode child )
    {
        DefaultDependencyCollectionContext context = args.collectionContext;
        context.set( parentContext.dependency, descriptorResult.getManagedDependencies() );

        DependencySelector childSelector =
                parentContext.depSelector != null ? parentContext.depSelector.deriveChildSelector( context ) : null;
        DependencyManager childManager =
                parentContext.depManager != null ? parentContext.depManager.deriveChildManager( context ) : null;
        DependencyTraverser childTraverser =
                parentContext.depTraverser != null ? parentContext.depTraverser.deriveChildTraverser( context ) : null;
        VersionFilter childFilter =
                parentContext.verFilter != null ? parentContext.verFilter.deriveChildFilter( context ) : null;

        final List<RemoteRepository> childRepos =
                args.ignoreRepos
                        ? parentContext.repositories
                        : remoteRepositoryManager.aggregateRepositories( args.session, parentContext.repositories,
                        descriptorResult.getRepositories(), true );

        Object key =
                args.pool.toKey( parentContext.dependency.getArtifact(), childRepos, childSelector, childManager,
                        childTraverser, childFilter );

        List<DependencyNode> children = args.pool.getChildren( key );
        if ( children == null )
        {
            boolean skipResolution = args.skipper.skipResolution( child, parentContext.parents );
            if ( !skipResolution )
            {
                List<DependencyNode> parents = new ArrayList<>( parentContext.parents.size() + 1 );
                parents.addAll( parentContext.parents );
                parents.add( child );
                for ( Dependency dependency : descriptorResult.getDependencies() )
                {
                    args.dependencyProcessingQueue.add(
                            new DependencyProcessingContext( childSelector, childManager, childTraverser, childFilter,
                                    childRepos, descriptorResult.getManagedDependencies(), parents, dependency ) );
                }
                args.pool.putChildren( key, child.getChildren() );
                args.skipper.cache( child, parents );
            }
        }
        else
        {
            child.setChildren( children );
        }
    }

    private ArtifactDescriptorResult resolveCachedArtifactDescriptor( DataPool pool,
                                                                      ArtifactDescriptorRequest descriptorRequest,
                                                                      RepositorySystemSession session,
                                                                      DependencyProcessingContext context,
                                                                      Results results )
    {
        Object key = pool.toKey( descriptorRequest );
        ArtifactDescriptorResult descriptorResult = pool.getDescriptor( key, descriptorRequest );
        if ( descriptorResult == null )
        {
            try
            {
                descriptorResult = descriptorReader.readArtifactDescriptor( session, descriptorRequest );
                pool.putDescriptor( key, descriptorResult );
            }
            catch ( ArtifactDescriptorException e )
            {
                results.addException( context.dependency, e, context.parents );
                pool.putDescriptor( key, e );
                return null;
            }

        }
        else if ( descriptorResult == DataPool.NO_DESCRIPTOR )
        {
            return null;
        }

        return descriptorResult;
    }

    @SuppressWarnings( "checkstyle:parameternumber" )
    private static DefaultDependencyNode createDependencyNode( List<Artifact> relocations,
                                                               PremanagedDependency preManaged,
                                                               VersionRangeResult rangeResult, Version version,
                                                               Dependency d, Collection<Artifact> aliases,
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
        return child;
    }

    private static DefaultDependencyNode createDependencyNode( List<Artifact> relocations,
                                                               PremanagedDependency preManaged,
                                                               VersionRangeResult rangeResult, Version version,
                                                               Dependency d, ArtifactDescriptorResult descriptorResult,
                                                               DependencyNode cycleNode )
    {
        DefaultDependencyNode child =
            createDependencyNode( relocations, preManaged, rangeResult, version, d, descriptorResult.getAliases(),
                                  cycleNode.getRepositories(), cycleNode.getRequestContext() );
        child.setChildren( cycleNode.getChildren() );
        return child;
    }

    private static ArtifactDescriptorRequest createArtifactDescriptorRequest( Args args,
                                                                              List<RemoteRepository> repositories,
                                                                              Dependency d )
    {
        ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
        descriptorRequest.setArtifact( d.getArtifact() );
        descriptorRequest.setRepositories( repositories );
        descriptorRequest.setRequestContext( args.request.getRequestContext() );
        descriptorRequest.setTrace( args.trace );
        return descriptorRequest;
    }

    private static VersionRangeRequest createVersionRangeRequest( Args args, List<RemoteRepository> repositories,
                                                                  Dependency dependency )
    {
        VersionRangeRequest rangeRequest = new VersionRangeRequest();
        rangeRequest.setArtifact( dependency.getArtifact() );
        rangeRequest.setRepositories( repositories );
        rangeRequest.setRequestContext( args.request.getRequestContext() );
        rangeRequest.setTrace( args.trace );
        return rangeRequest;
    }

    private VersionRangeResult cachedResolveRangeResult( VersionRangeRequest rangeRequest, DataPool pool,
                                                         RepositorySystemSession session )
        throws VersionRangeResolutionException
    {
        Object key = pool.toKey( rangeRequest );
        VersionRangeResult rangeResult = pool.getConstraint( key, rangeRequest );
        if ( rangeResult == null )
        {
            rangeResult = versionRangeResolver.resolveVersionRange( session, rangeRequest );
            pool.putConstraint( key, rangeResult );
        }
        return rangeResult;
    }

    private static boolean isLackingDescriptor( Artifact artifact )
    {
        return artifact.getProperty( ArtifactProperties.LOCAL_PATH, null ) != null;
    }

    private static List<RemoteRepository> getRemoteRepositories( ArtifactRepository repository,
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

    private static List<? extends Version> filterVersions( Dependency dependency, VersionRangeResult rangeResult,
                                                           VersionFilter verFilter,
                                                           DefaultVersionFilterContext verContext )
        throws VersionRangeResolutionException
    {
        if ( rangeResult.getVersions().isEmpty() )
        {
            throw new VersionRangeResolutionException( rangeResult,
                                                       "No versions available for " + dependency.getArtifact()
                                                           + " within specified range" );
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
                throw new VersionRangeResolutionException( rangeResult,
                        "Failed to filter versions for " + dependency.getArtifact(), e );
            }
            versions = verContext.get();
            if ( versions.isEmpty() )
            {
                throw new VersionRangeResolutionException( rangeResult,
                        "No acceptable versions for " + dependency.getArtifact() + ": " + rangeResult.getVersions() );
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

        final RepositorySystemSession session;

        final boolean ignoreRepos;

        final boolean premanagedState;

        final RequestTrace trace;

        final DataPool pool;

        final Queue<DependencyProcessingContext> dependencyProcessingQueue = new ArrayDeque<>( 128 );

        final DefaultDependencyCollectionContext collectionContext;

        final DefaultVersionFilterContext versionContext;

        final CollectRequest request;

        final DependencyResolutionSkipper skipper;

        Args( RepositorySystemSession session, RequestTrace trace, DataPool pool,
                     DefaultDependencyCollectionContext collectionContext, DefaultVersionFilterContext versionContext,
                     CollectRequest request, DependencyResolutionSkipper skipper )
        {
            this.session = session;
            this.request = request;
            this.ignoreRepos = session.isIgnoreArtifactDescriptorRepositories();
            this.premanagedState = ConfigUtils.getBoolean( session, false, DependencyManagerUtils.CONFIG_PROP_VERBOSE );
            this.trace = trace;
            this.pool = pool;
            this.collectionContext = collectionContext;
            this.versionContext = versionContext;
            this.skipper = skipper;
        }

    }

    static class Results
    {

        private final CollectResult result;

        final int maxExceptions;

        final int maxCycles;

        String errorPath;

        Results( CollectResult result, RepositorySystemSession session )
        {
            this.result = result;

            maxExceptions =
                    ConfigUtils.getInteger( session, CONFIG_PROP_MAX_EXCEPTIONS_DEFAULT, CONFIG_PROP_MAX_EXCEPTIONS );

            maxCycles = ConfigUtils.getInteger( session, CONFIG_PROP_MAX_CYCLES_DEFAULT, CONFIG_PROP_MAX_CYCLES );
        }

        public void addException( Dependency dependency, Exception e, List<DependencyNode> nodes )
        {
            if ( maxExceptions < 0 || result.getExceptions().size() < maxExceptions )
            {
                result.addException( e );
                if ( errorPath == null )
                {
                    StringBuilder buffer = new StringBuilder( 256 );
                    for ( DependencyNode node : nodes )
                    {
                        if ( buffer.length() > 0 )
                        {
                            buffer.append( " -> " );
                        }
                        Dependency dep = node.getDependency();
                        if ( dep != null )
                        {
                            buffer.append( dep.getArtifact() );
                        }
                    }
                    if ( buffer.length() > 0 )
                    {
                        buffer.append( " -> " );
                    }
                    buffer.append( dependency.getArtifact() );
                    errorPath = buffer.toString();
                }
            }
        }

        public void addCycle( List<DependencyNode> nodes, int cycleEntry, Dependency dependency )
        {
            if ( maxCycles < 0 || result.getCycles().size() < maxCycles )
            {
                result.addCycle( new DefaultDependencyCycle( nodes, cycleEntry, dependency ) );
            }
        }

    }

    static class PremanagedDependency
    {

        final String premanagedVersion;

        final String premanagedScope;

        final Boolean premanagedOptional;

        /**
         * @since 1.1.0
         */
        final Collection<Exclusion> premanagedExclusions;

        /**
         * @since 1.1.0
         */
        final Map<String, String> premanagedProperties;

        final int managedBits;

        final Dependency managedDependency;

        final boolean premanagedState;

        @SuppressWarnings( "checkstyle:parameternumber" )
        PremanagedDependency( String premanagedVersion, String premanagedScope, Boolean premanagedOptional,
                              Collection<Exclusion> premanagedExclusions, Map<String, String> premanagedProperties,
                              int managedBits, Dependency managedDependency, boolean premanagedState )
        {
            this.premanagedVersion = premanagedVersion;
            this.premanagedScope = premanagedScope;
            this.premanagedOptional = premanagedOptional;
            this.premanagedExclusions =
                premanagedExclusions != null
                    ? Collections.unmodifiableCollection( new ArrayList<>( premanagedExclusions ) )
                    : null;

            this.premanagedProperties =
                premanagedProperties != null
                    ? Collections.unmodifiableMap( new HashMap<>( premanagedProperties ) )
                    : null;

            this.managedBits = managedBits;
            this.managedDependency = managedDependency;
            this.premanagedState = premanagedState;
        }

        static PremanagedDependency create( DependencyManager depManager, Dependency dependency,
                                            boolean disableVersionManagement, boolean premanagedState )
        {
            DependencyManagement depMngt = depManager != null ? depManager.manageDependency( dependency ) : null;

            int managedBits = 0;
            String premanagedVersion = null;
            String premanagedScope = null;
            Boolean premanagedOptional = null;
            Collection<Exclusion> premanagedExclusions = null;
            Map<String, String> premanagedProperties = null;

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
                    premanagedProperties = artifact.getProperties();
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
                    premanagedExclusions = dependency.getExclusions();
                    dependency = dependency.setExclusions( depMngt.getExclusions() );
                    managedBits |= DependencyNode.MANAGED_EXCLUSIONS;
                }
            }
            return new PremanagedDependency( premanagedVersion, premanagedScope, premanagedOptional,
                                             premanagedExclusions, premanagedProperties, managedBits, dependency,
                                             premanagedState );

        }

        public void applyTo( DefaultDependencyNode child )
        {
            child.setManagedBits( managedBits );
            if ( premanagedState )
            {
                child.setData( DependencyManagerUtils.NODE_DATA_PREMANAGED_VERSION, premanagedVersion );
                child.setData( DependencyManagerUtils.NODE_DATA_PREMANAGED_SCOPE, premanagedScope );
                child.setData( DependencyManagerUtils.NODE_DATA_PREMANAGED_OPTIONAL, premanagedOptional );
                child.setData( DependencyManagerUtils.NODE_DATA_PREMANAGED_EXCLUSIONS, premanagedExclusions );
                child.setData( DependencyManagerUtils.NODE_DATA_PREMANAGED_PROPERTIES, premanagedProperties );
            }
        }

    }

}
