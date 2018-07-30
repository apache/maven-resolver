package org.eclipse.aether.internal.impl.collect;

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

import static org.eclipse.aether.internal.impl.collect.DependencyCollectionUtils.addDependencyNode;
import static org.eclipse.aether.internal.impl.collect.DependencyCollectionUtils.createArtifactDescriptorRequest;
import static org.eclipse.aether.internal.impl.collect.DependencyCollectionUtils.createDependencyNode;
import static org.eclipse.aether.internal.impl.collect.DependencyCollectionUtils.createVersionRangeRequest;
import static org.eclipse.aether.internal.impl.collect.DependencyCollectionUtils.filterVersions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static java.util.Objects.requireNonNull;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Named;

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactProperties;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.collection.DependencyTraverser;
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
import org.eclipse.aether.util.concurrency.FutureResult;
import org.eclipse.aether.util.concurrency.WorkerThreadFactory;
import org.eclipse.aether.util.graph.transformer.TransformationContextKeys;
import org.eclipse.aether.version.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 */
@Named
public class DefaultDependencyCollector
    implements DependencyCollector, Service
{

    static final String CONFIG_PROP_MAX_EXCEPTIONS = "aether.dependencyCollector.maxExceptions";

    static final String CONFIG_PROP_MAX_CYCLES = "aether.dependencyCollector.maxCycles";

    private static final Logger LOGGER = LoggerFactory.getLogger( DefaultDependencyCollector.class );

    private RemoteRepositoryManager remoteRepositoryManager;

    private ArtifactDescriptorReader descriptorReader;

    private VersionRangeResolver versionRangeResolver;

    private final ExecutorService executor = getExecutor();

    public DefaultDependencyCollector()
    {
        // enables default constructor
    }

    @Inject
    DefaultDependencyCollector( RemoteRepositoryManager remoteRepositoryManager,
                                ArtifactDescriptorReader artifactDescriptorReader,
                                VersionRangeResolver versionRangeResolver )
    {
        setRemoteRepositoryManager( remoteRepositoryManager );
        setArtifactDescriptorReader( artifactDescriptorReader );
        setVersionRangeResolver( versionRangeResolver );
    }

    public void initService( ServiceLocator locator )
    {
        setRemoteRepositoryManager( locator.getService( RemoteRepositoryManager.class ) );
        setArtifactDescriptorReader( locator.getService( ArtifactDescriptorReader.class ) );
        setVersionRangeResolver( locator.getService( VersionRangeResolver.class ) );
    }

    public DefaultDependencyCollector setRemoteRepositoryManager( RemoteRepositoryManager remoteRepositoryManager )
    {
        this.remoteRepositoryManager = requireNonNull( remoteRepositoryManager, "remote repository provider cannot be null" );
        return this;
    }

    public DefaultDependencyCollector setArtifactDescriptorReader( ArtifactDescriptorReader artifactDescriptorReader )
    {
        descriptorReader = requireNonNull( artifactDescriptorReader, "artifact descriptor reader cannot be null" );
        return this;
    }

    public DefaultDependencyCollector setVersionRangeResolver( VersionRangeResolver versionRangeResolver )
    {
        this.versionRangeResolver = requireNonNull( versionRangeResolver, "version range resolver cannot be null" );
        return this;
    }

    /**
     * Setup executor with 5 daemon threads in pool.
     */
    private ExecutorService getExecutor(  )
    {
        return new ThreadPoolExecutor( 5, 5, 3L, TimeUnit.SECONDS,
                                new LinkedBlockingQueue<Runnable>(),
                                new WorkerThreadFactory( null ) );
    }

    public CollectResult collectDependencies( RepositorySystemSession session, CollectRequest request )
        throws DependencyCollectionException
    {
        session = DependencyCollectionUtils.optimizeSession( session );

        RequestTrace trace = RequestTrace.newChild( request.getTrace(), request );

        CollectResult result = new CollectResult( request );

        Dependency root = request.getRoot();
        List<RemoteRepository> repositories = request.getRepositories();
        List<Dependency> managedDependencies = request.getManagedDependencies();

        DefaultDependencyCollectionContext context =
                new DefaultDependencyCollectionContext( session, request.getRootArtifact(), root, managedDependencies );
        context.setDependencies( request.getDependencies() );
        context.setCollectResult( result );
        context.setTrace( trace );
        Args args = new Args( session, trace, null, null, context, null, request );
        context.setArgs( args );

        Map<String, Object> stats = LOGGER.isDebugEnabled() ? new LinkedHashMap<String, Object>() : null;
        long time1 = System.nanoTime();

        DefaultDependencyNode node;
        if ( root != null )
        {

            VersionRangeResult rangeResult = resolveRootVersionRange( context );
            ArtifactDescriptorResult descriptorResult = readRootArtifactDescriptor( context );
            root = root.setArtifact( descriptorResult.getArtifact() );

            if ( !session.isIgnoreArtifactDescriptorRepositories() )
            {
                repositories = remoteRepositoryManager.aggregateRepositories( session, repositories,
                        descriptorResult.getRepositories(), true );
            }
            context.setDependencies( mergeDeps( context.getDependencies(), descriptorResult.getDependencies() ) );
            context.setManagedDependencies( mergeDeps( managedDependencies, descriptorResult.getManagedDependencies() ) );

            node = new DefaultDependencyNode( root );
            node.setRequestContext( request.getRequestContext() );
            node.setRelocations( descriptorResult.getRelocations() );
            node.setVersionConstraint( rangeResult.getVersionConstraint() );
            node.setVersion( context.getVersion() );
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

        DependencyTraverser depTraverser = session.getDependencyTraverser();
        boolean traverse = root == null || depTraverser == null || depTraverser.traverseDependency( root );
        String errorPath = null;
        if ( traverse && !context.getDependencies().isEmpty() )
        {
            DataPool pool = new DataPool( session );

            NodeStack nodes = new NodeStack();
            nodes.push( node );

            DefaultVersionFilterContext versionContext = new DefaultVersionFilterContext( session );

            args = new Args( session, trace, pool, nodes, context, versionContext, request );
            Results results = new Results( result, session );
            context.setArgs( args );
            context.setResults( results );
            context.setRepositories( repositories );
            context.setDepTraverser( depTraverser );
            context.prepareDescent();

            process( context );

            errorPath = results.errorPath;
        }

        long time2 = System.nanoTime();

        transformDependencyGraph( context, stats );

        if ( stats != null )
        {
            long time3 = System.nanoTime();
            stats.put( "DefaultDependencyCollector.collectTime", time2 - time1 );
            stats.put( "DefaultDependencyCollector.transformTime", time3 - time2 );
            LOGGER.debug( "Dependency collection stats: {}", stats );
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

    private VersionRangeResult resolveRootVersionRange( DefaultDependencyCollectionContext context )
            throws DependencyCollectionException
    {
        CollectRequest request = context.getArgs().request;
        RepositorySystemSession session = context.getSession();
        List<? extends Version> versions;
        VersionRangeResult rangeResult;
        Artifact artifact = request.getRoot().getArtifact();
        try
        {
            VersionRangeRequest rangeRequest =
                    new VersionRangeRequest( artifact, request.getRepositories(), request.getRequestContext() );
            rangeRequest.setTrace( context.getTrace() );
            rangeResult = versionRangeResolver.resolveVersionRange( session, rangeRequest );
            versions = filterVersions( context.getDependency(), rangeResult, context.getVerFilter(),
                    new DefaultVersionFilterContext( session ) );
        }
        catch ( VersionRangeResolutionException e )
        {
            context.getCollectResult().addException( e );
            throw new DependencyCollectionException( context.getCollectResult(), e.getMessage() );
        }

        Version version = versions.get( versions.size() - 1 );
        context.setVersion( version );
        context.setDependency( request.getRoot().setArtifact( artifact.setVersion( version.toString() ) ) );
        return rangeResult;
    }

    private ArtifactDescriptorResult readRootArtifactDescriptor( DefaultDependencyCollectionContext context )
            throws DependencyCollectionException
    {
        CollectRequest request = context.getArgs().request;
        Artifact artifact = request.getRoot().getArtifact();
        try
        {
            ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
            descriptorRequest.setArtifact( artifact );
            descriptorRequest.setRepositories( request.getRepositories() );
            descriptorRequest.setRequestContext( request.getRequestContext() );
            descriptorRequest.setTrace( context.getTrace() );

            ArtifactDescriptorResult descriptorResult =
                    isLackingDescriptor( artifact ) ? new ArtifactDescriptorResult( descriptorRequest )
                            : descriptorReader.readArtifactDescriptor( context.getSession(), descriptorRequest );
            context.setDependency( request.getRoot().setArtifact( descriptorResult.getArtifact() ) );
            return descriptorResult;
        }
        catch ( ArtifactDescriptorException e )
        {
            context.getCollectResult().addException( e );
            throw new DependencyCollectionException( context.getCollectResult(), e.getMessage() );
        }
    }

    private void transformDependencyGraph( DefaultDependencyCollectionContext context, Map<String, Object> stats )
    {
        RepositorySystemSession session = context.getSession();
        DependencyGraphTransformer transformer = session.getDependencyGraphTransformer();
        if ( transformer != null )
        {
            try
            {
                DefaultDependencyGraphTransformationContext tfContext =
                        new DefaultDependencyGraphTransformationContext( session );
                tfContext.put( TransformationContextKeys.STATS, stats );
                context.getCollectResult().setRoot( transformer.transformGraph( context.getCollectResult().getRoot(),
                        tfContext ) );
            }
            catch ( RepositoryException e )
            {
                context.getCollectResult().addException( e );
            }
        }
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

    private void process( DefaultDependencyCollectionContext context )
    {
        List<DependencyContext> depContexts = new ArrayList<>();
        for ( Dependency d : context.getDependencies() )
        {
            depContexts.add( new DependencyContext( context, d ) );
        }

        List<Future<DependencyContext>> futures = new ArrayList<>();
        for ( DependencyContext dc : depContexts )
        {
            futures.add( asyncProcessDependency( dc ) );
        }
        int pos = 0;
        for ( Future<DependencyContext> future : futures )
        {
            try
            {
                processDependencyNode( future.get() );
            }
            catch ( ExecutionException e )
            {
                context.getResults().addException( context.getDependencies().get( pos ), (Exception) e.getCause(),
                        context.getArgs().nodes );
            }
            catch ( InterruptedException e )
            {
                context.getResults().addException( context.getDependencies().get( pos ), e, context.getArgs().nodes );
            }
            pos++;
        }
    }

    private Future<DependencyContext> asyncProcessDependency( final DependencyContext dc )
    {
        return executor.submit( new Callable<DependencyContext>()
        {

            public DependencyContext call()
                    throws Exception
            {
                return processDependency( dc );
            }
        } );
    }

    private DependencyContext processDependency( DependencyContext dc )
    {
        DefaultDependencyCollectionContext context = dc.context;
        Args args = context.getArgs();
        Results results = context.getResults();

        PremanagedDependency preManaged =
                PremanagedDependency.create( context.getDepManager(), dc.origDependency, dc.disableVersionManagement,
                        args.premanagedState );
        Dependency dependency = preManaged.managedDependency;

        if ( context.getDepSelector() != null && !context.getDepSelector().selectDependency( dependency ) )
        {
            return null;
        }

        boolean noDescriptor = isLackingDescriptor( dependency.getArtifact() );

        boolean traverse = !noDescriptor
                && ( context.getDepTraverser() == null || context.getDepTraverser().traverseDependency( dependency ) );

        try
        {
            VersionRangeRequest rangeRequest = createVersionRangeRequest( args, context.getRepositories(), dependency );
            VersionRangeResult rangeResult = cachedResolveRangeResult( rangeRequest, args.pool, args.session );
            for ( Version version : filterVersions( dependency, rangeResult, context.getVerFilter(),
                    args.versionContext ) )
            {

                Artifact originalArtifact = dependency.getArtifact().setVersion( version.toString() );
                Dependency d = dependency.setArtifact( originalArtifact );

                ArtifactDescriptorRequest descriptorRequest =
                        createArtifactDescriptorRequest( args, context.getRepositories(), d );

                dc.args = args;
                dc.preManaged = preManaged;
                dc.traverse = traverse;
                dc.rangeResult = rangeResult;
                dc.version = version;
                dc.originalArtifact = originalArtifact;
                dc.managedDependency = d;
                dc.futureDescriptorResult =
                        getArtifactDescriptorResult( args, results, noDescriptor, d, descriptorRequest );
            }
        }
        catch ( VersionRangeResolutionException e )
        {
            results.addException( dependency, e, args.nodes );
        }
        return dc;
    }

    private void processDependencyNode( DependencyContext dc )
    {
        if ( dc == null )
        {
            return;
        }
        try
        {
            boolean noResult = dc.futureDescriptorResult == null;
            if ( !noResult )
            {
                dc.descriptorResult = dc.futureDescriptorResult.get();
                noResult = dc.descriptorResult == null;
            }
            if ( noResult )
            {
                List<RemoteRepository> repos =
                        getRemoteRepositories( dc.rangeResult.getRepository( dc.version ), dc.context.getRepositories() );
                addDependencyNode( dc.args.nodes.top(), dc.relocations, dc.preManaged, dc.rangeResult, dc.version,
                        dc.managedDependency, null, repos, dc.args.request.getRequestContext() );
            }
            else
            {
                processDependencyVersion( dc );
            }
        }
        catch ( InterruptedException e )
        {
            dc.context.getResults().addException( dc.preManaged.managedDependency, e, dc.args.nodes );
        }
        catch ( ExecutionException e )
        {
            dc.context.getResults().addException( dc.preManaged.managedDependency, (Exception) e.getCause(),
                    dc.args.nodes );
        }
    }

    private boolean processDependencyVersion( DependencyContext dc )
    {
        Args args = dc.context.getArgs();
        Results results = dc.context.getResults();
        Dependency d = dc.managedDependency.setArtifact( dc.descriptorResult.getArtifact() );
        dc.managedDependency = d;

        DependencyNode node = args.nodes.top();

        int cycleEntry = args.nodes.find( d.getArtifact() );
        if ( cycleEntry >= 0 )
        {
            results.addCycle( args.nodes, cycleEntry, d );
            DependencyNode cycleNode = args.nodes.get( cycleEntry );
            if ( cycleNode.getDependency() != null )
            {
                createDependencyNode( node, dc.relocations, dc.preManaged, dc.rangeResult, dc.version, d,
                        dc.descriptorResult, cycleNode );
                return true;
            }
        }

        if ( !dc.descriptorResult.getRelocations().isEmpty() )
        {
            boolean disableVersionManagementSubsequently =
                    dc.originalArtifact.getGroupId().equals( d.getArtifact().getGroupId() )
                            && dc.originalArtifact.getArtifactId().equals( d.getArtifact().getArtifactId() );

            DependencyContext dc2 = new DependencyContext();
            dc2.context = dc.context;
            dc2.origDependency = d;
            dc2.relocations = dc.descriptorResult.getRelocations();
            dc2.disableVersionManagement = disableVersionManagementSubsequently;
            dc2 = processDependency( dc2 );
            processDependencyNode( dc2 );
            return true;
        }
        else
        {
            d = args.pool.intern( d );

            List<RemoteRepository> repos =
                    getRemoteRepositories( dc.rangeResult.getRepository( dc.version ), dc.context.getRepositories() );

            DefaultDependencyNode child =
                    addDependencyNode( node, dc.relocations, dc.preManaged, dc.rangeResult, dc.version, d,
                            dc.descriptorResult.getAliases(), repos, args.request.getRequestContext() );

            if ( dc.traverse && !dc.descriptorResult.getDependencies().isEmpty() )
            {
                doRecurse( dc.context, d, dc.descriptorResult, child );
            }
            return false;
        }
    }

    private void doRecurse( DefaultDependencyCollectionContext context, Dependency d,
                            ArtifactDescriptorResult descriptorResult, DefaultDependencyNode child )
    {
        context.setDependency( d );
        context.setManagedDependencies( descriptorResult.getManagedDependencies() );

        DefaultDependencyCollectionContext childContext = context.createChildContext();
        new DefaultDependencyCollectionContext( context.getSession(), context.getArtifact(), context.getDependency(),
                context.getManagedDependencies() );
        Args args = context.getArgs();

        final List<RemoteRepository> childRepos = args.ignoreRepos ? context.getRepositories()
                : remoteRepositoryManager.aggregateRepositories( args.session, context.getRepositories(),
                descriptorResult.getRepositories(), true );
        childContext.setRepositories( childRepos );

        Object key = args.pool.toKey( d.getArtifact(), childContext );

        List<DependencyNode> children = args.pool.getChildren( key );
        if ( children == null )
        {
            args.pool.putChildren( key, child.getChildren() );

            args.nodes.push( child );

            childContext.setArgs( args );
            childContext.setResults( context.getResults() );
            childContext.setDependencies( descriptorResult.getDependencies() );

            process( childContext );

            args.nodes.pop();
        }
        else
        {
            child.setChildren( children );
        }
    }

    private Future<ArtifactDescriptorResult> getArtifactDescriptorResult( Args args, Results results,
                                                                          boolean noDescriptor, Dependency d,
                                                                          ArtifactDescriptorRequest descriptorRequest )
    {
        return noDescriptor
                ? new FutureResult<ArtifactDescriptorResult>( new ArtifactDescriptorResult( descriptorRequest ) )
                : resolveCachedArtifactDescriptor( args.pool, descriptorRequest, args.session, d, results,
                args );
    }

    private Future<ArtifactDescriptorResult> resolveCachedArtifactDescriptor( final DataPool pool,
                                                                              final ArtifactDescriptorRequest descriptorRequest,
                                                                              final RepositorySystemSession session,
                                                                              final Dependency d, final Results results,
                                                                              final Args args )
    {
        final Object key = pool.toKey( descriptorRequest );
        Future<ArtifactDescriptorResult> descriptorResult = pool.getDescriptor( key, descriptorRequest );
        if ( descriptorResult == null )
        {
            descriptorResult = executor.submit( new Callable<ArtifactDescriptorResult>()
            {

                public ArtifactDescriptorResult call()
                        throws Exception
                {
                    try
                    {
                        return descriptorReader.readArtifactDescriptor( session, descriptorRequest );
                    }
                    catch ( ArtifactDescriptorException e )
                    {
                        results.addException( d, e, args.nodes );
                        pool.putDescriptor( key, e );
                        return null;
                    }
                }
            } );

            pool.putDescriptor( key, descriptorResult );
        }
        else if ( descriptorResult == DataPool.NO_DESCRIPTOR )
        {
            return new FutureResult<>( null );
        }

        return descriptorResult;
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
}