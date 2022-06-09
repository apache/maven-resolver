package org.eclipse.aether.internal.impl.collect.df;

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

import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

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
import org.eclipse.aether.internal.impl.collect.DefaultDependencyCycle;
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

/**
 * Depth-first {@link org.eclipse.aether.impl.DependencyCollector} (the "original" default). Originally
 * this class was located a package higher (as "default" implementation).
 *
 * @since 1.8.0
 */
@Singleton
@Named( DfDependencyCollector.NAME )
public class DfDependencyCollector
        extends DependencyCollectorDelegate implements Service
{
    public static final String NAME = "df";

    /**
     * Default ctor for SL.
     *
     * @deprecated Will be dropped once SL gone.
     */
    @Deprecated
    public DfDependencyCollector()
    {
        // enables default constructor
    }

    @Inject
    DfDependencyCollector( RemoteRepositoryManager remoteRepositoryManager,
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
        NodeStack nodes = new NodeStack();
        nodes.push( node );

        Args args = new Args( session, pool, nodes, context, versionContext, request );

        process( args, trace, results, dependencies, repositories,
                session.getDependencySelector() != null
                        ? session.getDependencySelector().deriveChildSelector( context ) : null,
                session.getDependencyManager() != null
                        ? session.getDependencyManager().deriveChildManager( context ) : null,
                session.getDependencyTraverser() != null
                        ? session.getDependencyTraverser().deriveChildTraverser( context ) : null,
                session.getVersionFilter() != null
                        ? session.getVersionFilter().deriveChildFilter( context ) : null );
    }

        @SuppressWarnings( "checkstyle:parameternumber" )
    private void process( final Args args, RequestTrace trace, Results results, List<Dependency> dependencies,
                          List<RemoteRepository> repositories, DependencySelector depSelector,
                          DependencyManager depManager, DependencyTraverser depTraverser, VersionFilter verFilter )
    {
        for ( Dependency dependency : dependencies )
        {
            processDependency( args, trace, results, repositories, depSelector, depManager, depTraverser, verFilter,
                               dependency );
        }
    }

    @SuppressWarnings( "checkstyle:parameternumber" )
    private void processDependency( Args args, RequestTrace trace, Results results, List<RemoteRepository> repositories,
                                    DependencySelector depSelector, DependencyManager depManager,
                                    DependencyTraverser depTraverser, VersionFilter verFilter, Dependency dependency )
    {

        List<Artifact> relocations = Collections.emptyList();
        processDependency( args, trace, results, repositories, depSelector, depManager, depTraverser, verFilter,
                dependency, relocations, false );
    }

    @SuppressWarnings( "checkstyle:parameternumber" )
    private void processDependency( Args args, RequestTrace parent, Results results,
                                    List<RemoteRepository> repositories, DependencySelector depSelector,
                                    DependencyManager depManager, DependencyTraverser depTraverser,
                                    VersionFilter verFilter, Dependency dependency, List<Artifact> relocations,
                                    boolean disableVersionManagement )
    {
        if ( depSelector != null && !depSelector.selectDependency( dependency ) )
        {
            return;
        }

        RequestTrace trace = collectStepTrace( parent, args.request.getRequestContext(), args.nodes.nodes, dependency );
        PremanagedDependency preManaged =
            PremanagedDependency.create( depManager, dependency, disableVersionManagement, args.premanagedState );
        dependency = preManaged.getManagedDependency();

        boolean noDescriptor = isLackingDescriptor( dependency.getArtifact() );

        boolean traverse = !noDescriptor && ( depTraverser == null || depTraverser.traverseDependency( dependency ) );

        List<? extends Version> versions;
        VersionRangeResult rangeResult;
        try
        {
            VersionRangeRequest rangeRequest = createVersionRangeRequest( args.request.getRequestContext(), trace,
                    repositories, dependency );

            rangeResult = cachedResolveRangeResult( rangeRequest, args.pool, args.session );

            versions = filterVersions( dependency, rangeResult, verFilter, args.versionContext );
        }
        catch ( VersionRangeResolutionException e )
        {
            results.addException( dependency, e, args.nodes.nodes );
            return;
        }

        for ( Version version : versions )
        {
            Artifact originalArtifact = dependency.getArtifact().setVersion( version.toString() );
            Dependency d = dependency.setArtifact( originalArtifact );

            ArtifactDescriptorRequest descriptorRequest = createArtifactDescriptorRequest(
                    args.request.getRequestContext(), trace, repositories, d );

            final ArtifactDescriptorResult descriptorResult =
                getArtifactDescriptorResult( args, results, noDescriptor, d, descriptorRequest );
            if ( descriptorResult != null )
            {
                d = d.setArtifact( descriptorResult.getArtifact() );

                DependencyNode node = args.nodes.top();

                int cycleEntry = DefaultDependencyCycle.find( args.nodes.nodes, d.getArtifact() );
                if ( cycleEntry >= 0 )
                {
                    results.addCycle( args.nodes.nodes, cycleEntry, d );
                    DependencyNode cycleNode = args.nodes.get( cycleEntry );
                    if ( cycleNode.getDependency() != null )
                    {
                        DefaultDependencyNode child =
                            createDependencyNode( relocations, preManaged, rangeResult, version, d, descriptorResult,
                                                  cycleNode );
                        node.getChildren().add( child );
                        continue;
                    }
                }

                if ( !descriptorResult.getRelocations().isEmpty() )
                {
                    boolean disableVersionManagementSubsequently =
                        originalArtifact.getGroupId().equals( d.getArtifact().getGroupId() )
                            && originalArtifact.getArtifactId().equals( d.getArtifact().getArtifactId() );

                    processDependency( args, parent, results, repositories, depSelector, depManager, depTraverser,
                            verFilter, d, descriptorResult.getRelocations(), disableVersionManagementSubsequently );
                    return;
                }
                else
                {
                    d = args.pool.intern( d.setArtifact( args.pool.intern( d.getArtifact() ) ) );

                    List<RemoteRepository> repos =
                        getRemoteRepositories( rangeResult.getRepository( version ), repositories );

                    DefaultDependencyNode child =
                        createDependencyNode( relocations, preManaged, rangeResult, version, d,
                                              descriptorResult.getAliases(), repos, args.request.getRequestContext() );

                    node.getChildren().add( child );

                    boolean recurse = traverse && !descriptorResult.getDependencies().isEmpty();
                    if ( recurse )
                    {
                        doRecurse( args, parent, results, repositories, depSelector, depManager, depTraverser,
                                verFilter, d, descriptorResult, child );
                    }
                }
            }
            else
            {
                DependencyNode node = args.nodes.top();
                List<RemoteRepository> repos =
                    getRemoteRepositories( rangeResult.getRepository( version ), repositories );
                DefaultDependencyNode child =
                    createDependencyNode( relocations, preManaged, rangeResult, version, d, null, repos,
                                          args.request.getRequestContext() );
                node.getChildren().add( child );
            }
        }
    }

    @SuppressWarnings( "checkstyle:parameternumber" )
    private void doRecurse( Args args, RequestTrace trace, Results results, List<RemoteRepository> repositories,
                            DependencySelector depSelector, DependencyManager depManager,
                            DependencyTraverser depTraverser, VersionFilter verFilter, Dependency d,
                            ArtifactDescriptorResult descriptorResult, DefaultDependencyNode child )
    {
        DefaultDependencyCollectionContext context = args.collectionContext;
        context.set( d, descriptorResult.getManagedDependencies() );

        DependencySelector childSelector = depSelector != null ? depSelector.deriveChildSelector( context ) : null;
        DependencyManager childManager = depManager != null ? depManager.deriveChildManager( context ) : null;
        DependencyTraverser childTraverser = depTraverser != null ? depTraverser.deriveChildTraverser( context ) : null;
        VersionFilter childFilter = verFilter != null ? verFilter.deriveChildFilter( context ) : null;

        final List<RemoteRepository> childRepos =
            args.ignoreRepos
                ? repositories
                : remoteRepositoryManager.aggregateRepositories( args.session, repositories,
                                                                 descriptorResult.getRepositories(), true );

        Object key =
            args.pool.toKey( d.getArtifact(), childRepos, childSelector, childManager, childTraverser, childFilter );

        List<DependencyNode> children = args.pool.getChildren( key );
        if ( children == null )
        {
            args.pool.putChildren( key, child.getChildren() );

            args.nodes.push( child );

            process( args, trace, results, descriptorResult.getDependencies(), childRepos, childSelector, childManager,
                     childTraverser, childFilter );

            args.nodes.pop();
        }
        else
        {
            child.setChildren( children );
        }
    }

    private ArtifactDescriptorResult getArtifactDescriptorResult( Args args, Results results, boolean noDescriptor,
                                                                  Dependency d,
                                                                  ArtifactDescriptorRequest descriptorRequest )
    {
        return noDescriptor
                   ? new ArtifactDescriptorResult( descriptorRequest )
                   : resolveCachedArtifactDescriptor( args.pool, descriptorRequest, args.session, d, results, args );

    }

    private ArtifactDescriptorResult resolveCachedArtifactDescriptor( DataPool pool,
                                                                      ArtifactDescriptorRequest descriptorRequest,
                                                                      RepositorySystemSession session, Dependency d,
                                                                      Results results, Args args )
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
                results.addException( d, e, args.nodes.nodes );
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

        final NodeStack nodes;

        final DefaultDependencyCollectionContext collectionContext;

        final DefaultVersionFilterContext versionContext;

        final CollectRequest request;

        Args( RepositorySystemSession session, DataPool pool, NodeStack nodes,
                     DefaultDependencyCollectionContext collectionContext, DefaultVersionFilterContext versionContext,
                     CollectRequest request )
        {
            this.session = session;
            this.request = request;
            this.ignoreRepos = session.isIgnoreArtifactDescriptorRepositories();
            this.premanagedState = ConfigUtils.getBoolean( session, false, DependencyManagerUtils.CONFIG_PROP_VERBOSE );
            this.pool = pool;
            this.nodes = nodes;
            this.collectionContext = collectionContext;
            this.versionContext = versionContext;
        }

    }
}