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
import java.util.Collections;
import java.util.List;
import java.util.Queue;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyManager;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.collection.DependencyTraverser;
import org.eclipse.aether.collection.VersionFilter;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.impl.ArtifactDescriptorReader;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.impl.VersionRangeResolver;
import org.eclipse.aether.internal.impl.collect.DataPool;
import org.eclipse.aether.internal.impl.collect.DefaultDependencyCollectionContext;
import org.eclipse.aether.internal.impl.collect.DefaultVersionFilterContext;
import org.eclipse.aether.internal.impl.collect.DependencyCollectorDelegate;
import org.eclipse.aether.internal.impl.collect.PremanagedDependency;
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
import org.eclipse.aether.version.Version;

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

    /**
     * Default ctor for SL.
     *
     * @deprecated Will be dropped once SL gone.
     */
    @Deprecated
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

    @SuppressWarnings( "checkstyle:parameternumber" )
    @Override
    protected void doCollectDependencies( RepositorySystemSession session, RequestTrace trace, DataPool pool,
                                          DefaultDependencyCollectionContext context,
                                          DefaultVersionFilterContext versionContext,
                                          CollectRequest request, DependencyNode node,
                                          List<RemoteRepository> repositories, List<Dependency> dependencies,
                                          List<Dependency> managedDependencies, Results results )
    {
        boolean useSkip = ConfigUtils.getBoolean(
                session, CONFIG_PROP_SKIPPER_DEFAULT, CONFIG_PROP_SKIPPER
        );
        if ( useSkip )
        {
            logger.debug( "Collector skip mode enabled" );
        }

        Args args =
                new Args( session, pool, context, versionContext, request,
                        useSkip ? DependencyResolutionSkipper.defaultSkipper()
                                : DependencyResolutionSkipper.neverSkipper() );

        DependencySelector rootDepSelector = session.getDependencySelector() != null
                ? session.getDependencySelector().deriveChildSelector( context ) : null;
        DependencyManager rootDepManager = session.getDependencyManager() != null
                ? session.getDependencyManager().deriveChildManager( context ) : null;
        DependencyTraverser rootDepTraverser = session.getDependencyTraverser() != null
                ? session.getDependencyTraverser().deriveChildTraverser( context ) : null;
        VersionFilter rootVerFilter = session.getVersionFilter() != null
                ? session.getVersionFilter().deriveChildFilter( context ) : null;

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
            processDependency( args, trace, results, args.dependencyProcessingQueue.remove(), Collections.emptyList(),
                    false );
        }

        args.skipper.report();
    }

    @SuppressWarnings( "checkstyle:parameternumber" )
    private void processDependency( Args args, RequestTrace parent, Results results,
                                    DependencyProcessingContext context, List<Artifact> relocations,
                                    boolean disableVersionManagement )
    {
        if ( context.depSelector != null && !context.depSelector.selectDependency( context.dependency ) )
        {
            return;
        }

        RequestTrace trace = collectStepTrace( parent, args.request.getRequestContext(), context.parents,
                context.dependency );
        PremanagedDependency preManaged =
                PremanagedDependency.create( context.depManager, context.dependency, disableVersionManagement,
                        args.premanagedState );
        Dependency dependency = preManaged.getManagedDependency();

        boolean noDescriptor = isLackingDescriptor( dependency.getArtifact() );

        boolean traverse =
                !noDescriptor && ( context.depTraverser == null || context.depTraverser.traverseDependency(
                        dependency ) );

        List<? extends Version> versions;
        VersionRangeResult rangeResult;
        try
        {
            VersionRangeRequest rangeRequest = createVersionRangeRequest( args.request.getRequestContext(), trace,
                    context.repositories, dependency );

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

            ArtifactDescriptorRequest descriptorRequest = createArtifactDescriptorRequest(
                    args.request.getRequestContext(), trace, context.repositories, d );

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

                    processDependency( args, parent, results, context.withDependency( d ),
                            descriptorResult.getRelocations(), disableVersionManagementSubsequently );
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
                    else if ( !args.skipper.skipResolution( child, parentContext.parents ) )
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

    static class Args
    {

        final RepositorySystemSession session;

        final boolean ignoreRepos;

        final boolean premanagedState;

        final DataPool pool;

        final Queue<DependencyProcessingContext> dependencyProcessingQueue = new ArrayDeque<>( 128 );

        final DefaultDependencyCollectionContext collectionContext;

        final DefaultVersionFilterContext versionContext;

        final CollectRequest request;

        final DependencyResolutionSkipper skipper;

        Args( RepositorySystemSession session, DataPool pool,
                     DefaultDependencyCollectionContext collectionContext, DefaultVersionFilterContext versionContext,
                     CollectRequest request, DependencyResolutionSkipper skipper )
        {
            this.session = session;
            this.request = request;
            this.ignoreRepos = session.isIgnoreArtifactDescriptorRepositories();
            this.premanagedState = ConfigUtils.getBoolean( session, false, DependencyManagerUtils.CONFIG_PROP_VERBOSE );
            this.pool = pool;
            this.collectionContext = collectionContext;
            this.versionContext = versionContext;
            this.skipper = skipper;
        }

    }

}
