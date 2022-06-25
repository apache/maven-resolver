package org.apache.maven.resolver.internal.impl;

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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import static java.util.Objects.requireNonNull;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.resolver.spi.log.LoggerFactory;
import org.apache.maven.resolver.RepositorySystem;
import org.apache.maven.resolver.RepositorySystemSession;
import org.apache.maven.resolver.RequestTrace;
import org.apache.maven.resolver.SyncContext;
import org.apache.maven.resolver.artifact.Artifact;
import org.apache.maven.resolver.collection.CollectRequest;
import org.apache.maven.resolver.collection.CollectResult;
import org.apache.maven.resolver.collection.DependencyCollectionException;
import org.apache.maven.resolver.deployment.DeployRequest;
import org.apache.maven.resolver.deployment.DeployResult;
import org.apache.maven.resolver.deployment.DeploymentException;
import org.apache.maven.resolver.graph.DependencyFilter;
import org.apache.maven.resolver.graph.DependencyVisitor;
import org.apache.maven.resolver.impl.ArtifactDescriptorReader;
import org.apache.maven.resolver.impl.ArtifactResolver;
import org.apache.maven.resolver.impl.DependencyCollector;
import org.apache.maven.resolver.impl.Deployer;
import org.apache.maven.resolver.impl.Installer;
import org.apache.maven.resolver.impl.LocalRepositoryProvider;
import org.apache.maven.resolver.impl.MetadataResolver;
import org.apache.maven.resolver.impl.RemoteRepositoryManager;
import org.apache.maven.resolver.spi.synccontext.SyncContextFactory;
import org.apache.maven.resolver.impl.VersionRangeResolver;
import org.apache.maven.resolver.impl.VersionResolver;
import org.apache.maven.resolver.installation.InstallRequest;
import org.apache.maven.resolver.installation.InstallResult;
import org.apache.maven.resolver.installation.InstallationException;
import org.apache.maven.resolver.repository.Authentication;
import org.apache.maven.resolver.repository.LocalRepository;
import org.apache.maven.resolver.repository.LocalRepositoryManager;
import org.apache.maven.resolver.repository.NoLocalRepositoryManagerException;
import org.apache.maven.resolver.repository.Proxy;
import org.apache.maven.resolver.repository.RemoteRepository;
import org.apache.maven.resolver.resolution.ArtifactDescriptorException;
import org.apache.maven.resolver.resolution.ArtifactDescriptorRequest;
import org.apache.maven.resolver.resolution.ArtifactDescriptorResult;
import org.apache.maven.resolver.resolution.ArtifactRequest;
import org.apache.maven.resolver.resolution.ArtifactResolutionException;
import org.apache.maven.resolver.resolution.ArtifactResult;
import org.apache.maven.resolver.resolution.DependencyRequest;
import org.apache.maven.resolver.resolution.DependencyResolutionException;
import org.apache.maven.resolver.resolution.DependencyResult;
import org.apache.maven.resolver.resolution.MetadataRequest;
import org.apache.maven.resolver.resolution.MetadataResult;
import org.apache.maven.resolver.resolution.VersionRangeRequest;
import org.apache.maven.resolver.resolution.VersionRangeResolutionException;
import org.apache.maven.resolver.resolution.VersionRangeResult;
import org.apache.maven.resolver.resolution.VersionRequest;
import org.apache.maven.resolver.resolution.VersionResolutionException;
import org.apache.maven.resolver.resolution.VersionResult;
import org.apache.maven.resolver.util.graph.visitor.FilteringDependencyVisitor;
import org.apache.maven.resolver.util.graph.visitor.TreeDependencyVisitor;

/**
 */
@Singleton
@Named
public class DefaultRepositorySystem
    implements RepositorySystem
{

    private VersionResolver versionResolver;

    private VersionRangeResolver versionRangeResolver;

    private ArtifactResolver artifactResolver;

    private MetadataResolver metadataResolver;

    private ArtifactDescriptorReader artifactDescriptorReader;

    private DependencyCollector dependencyCollector;

    private Installer installer;

    private Deployer deployer;

    private LocalRepositoryProvider localRepositoryProvider;

    private SyncContextFactory syncContextFactory;

    private RemoteRepositoryManager remoteRepositoryManager;

    public DefaultRepositorySystem()
    {
        // enables default constructor
    }

    @SuppressWarnings( "checkstyle:parameternumber" )
    @Inject
    DefaultRepositorySystem( VersionResolver versionResolver, VersionRangeResolver versionRangeResolver,
                             ArtifactResolver artifactResolver, MetadataResolver metadataResolver,
                             ArtifactDescriptorReader artifactDescriptorReader,
                             DependencyCollector dependencyCollector, Installer installer, Deployer deployer,
                             LocalRepositoryProvider localRepositoryProvider, SyncContextFactory syncContextFactory,
                             RemoteRepositoryManager remoteRepositoryManager )
    {
        setVersionResolver( versionResolver );
        setVersionRangeResolver( versionRangeResolver );
        setArtifactResolver( artifactResolver );
        setMetadataResolver( metadataResolver );
        setArtifactDescriptorReader( artifactDescriptorReader );
        setDependencyCollector( dependencyCollector );
        setInstaller( installer );
        setDeployer( deployer );
        setLocalRepositoryProvider( localRepositoryProvider );
        setSyncContextFactory( syncContextFactory );
        setRemoteRepositoryManager( remoteRepositoryManager );
    }

    /**
     * @deprecated not used any more since MRESOLVER-36 move to slf4j, added back in MRESOLVER-64 for compatibility
     */
    @Deprecated
    public DefaultRepositorySystem setLoggerFactory( LoggerFactory loggerFactory )
    {
        // this.logger = NullLoggerFactory.getSafeLogger( loggerFactory, getClass() );
        return this;
    }

    public DefaultRepositorySystem setVersionResolver( VersionResolver versionResolver )
    {
        this.versionResolver = requireNonNull( versionResolver, "version resolver cannot be null" );
        return this;
    }

    public DefaultRepositorySystem setVersionRangeResolver( VersionRangeResolver versionRangeResolver )
    {
        this.versionRangeResolver = requireNonNull(
                versionRangeResolver, "version range resolver cannot be null" );
        return this;
    }

    public DefaultRepositorySystem setArtifactResolver( ArtifactResolver artifactResolver )
    {
        this.artifactResolver = requireNonNull( artifactResolver, "artifact resolver cannot be null" );
        return this;
    }

    public DefaultRepositorySystem setMetadataResolver( MetadataResolver metadataResolver )
    {
        this.metadataResolver = requireNonNull( metadataResolver, "metadata resolver cannot be null" );
        return this;
    }

    public DefaultRepositorySystem setArtifactDescriptorReader( ArtifactDescriptorReader artifactDescriptorReader )
    {
        this.artifactDescriptorReader = requireNonNull(
                artifactDescriptorReader, "artifact descriptor reader cannot be null" );
        return this;
    }

    public DefaultRepositorySystem setDependencyCollector( DependencyCollector dependencyCollector )
    {
        this.dependencyCollector = requireNonNull( dependencyCollector, "dependency collector cannot be null" );
        return this;
    }

    public DefaultRepositorySystem setInstaller( Installer installer )
    {
        this.installer = requireNonNull( installer, "installer cannot be null" );
        return this;
    }

    public DefaultRepositorySystem setDeployer( Deployer deployer )
    {
        this.deployer = requireNonNull( deployer, "deployer cannot be null" );
        return this;
    }

    public DefaultRepositorySystem setLocalRepositoryProvider( LocalRepositoryProvider localRepositoryProvider )
    {
        this.localRepositoryProvider = requireNonNull(
                localRepositoryProvider, "local repository provider cannot be null" );
        return this;
    }

    public DefaultRepositorySystem setSyncContextFactory( SyncContextFactory syncContextFactory )
    {
        this.syncContextFactory = requireNonNull( syncContextFactory, "sync context factory cannot be null" );
        return this;
    }

    public DefaultRepositorySystem setRemoteRepositoryManager( RemoteRepositoryManager remoteRepositoryManager )
    {
        this.remoteRepositoryManager = requireNonNull(
                remoteRepositoryManager, "remote repository provider cannot be null" );
        return this;
    }

    public VersionResult resolveVersion( RepositorySystemSession session, VersionRequest request )
        throws VersionResolutionException
    {
        validateSession( session );
        requireNonNull( request, "request cannot be null" );

        return versionResolver.resolveVersion( session, request );
    }

    public VersionRangeResult resolveVersionRange( RepositorySystemSession session, VersionRangeRequest request )
        throws VersionRangeResolutionException
    {
        validateSession( session );
        requireNonNull( request, "request cannot be null" );

        return versionRangeResolver.resolveVersionRange( session, request );
    }

    public ArtifactDescriptorResult readArtifactDescriptor( RepositorySystemSession session,
                                                            ArtifactDescriptorRequest request )
        throws ArtifactDescriptorException
    {
        validateSession( session );
        requireNonNull( request, "request cannot be null" );

        return artifactDescriptorReader.readArtifactDescriptor( session, request );
    }

    public ArtifactResult resolveArtifact( RepositorySystemSession session, ArtifactRequest request )
        throws ArtifactResolutionException
    {
        validateSession( session );
        requireNonNull( session, "session cannot be null" );

        return artifactResolver.resolveArtifact( session, request );
    }

    public List<ArtifactResult> resolveArtifacts( RepositorySystemSession session,
                                                  Collection<? extends ArtifactRequest> requests )
        throws ArtifactResolutionException
    {
        validateSession( session );
        requireNonNull( requests, "requests cannot be null" );

        return artifactResolver.resolveArtifacts( session, requests );
    }

    public List<MetadataResult> resolveMetadata( RepositorySystemSession session,
                                                 Collection<? extends MetadataRequest> requests )
    {
        validateSession( session );
        requireNonNull( requests, "requests cannot be null" );

        return metadataResolver.resolveMetadata( session, requests );
    }

    public CollectResult collectDependencies( RepositorySystemSession session, CollectRequest request )
        throws DependencyCollectionException
    {
        validateSession( session );
        requireNonNull( request, "request cannot be null" );

        return dependencyCollector.collectDependencies( session, request );
    }

    public DependencyResult resolveDependencies( RepositorySystemSession session, DependencyRequest request )
        throws DependencyResolutionException
    {
        validateSession( session );
        requireNonNull( request, "request cannot be null" );

        RequestTrace trace = RequestTrace.newChild( request.getTrace(), request );

        DependencyResult result = new DependencyResult( request );

        DependencyCollectionException dce = null;
        ArtifactResolutionException are = null;

        if ( request.getRoot() != null )
        {
            result.setRoot( request.getRoot() );
        }
        else if ( request.getCollectRequest() != null )
        {
            CollectResult collectResult;
            try
            {
                request.getCollectRequest().setTrace( trace );
                collectResult = dependencyCollector.collectDependencies( session, request.getCollectRequest() );
            }
            catch ( DependencyCollectionException e )
            {
                dce = e;
                collectResult = e.getResult();
            }
            result.setRoot( collectResult.getRoot() );
            result.setCycles( collectResult.getCycles() );
            result.setCollectExceptions( collectResult.getExceptions() );
        }
        else
        {
            throw new NullPointerException( "dependency node and collect request cannot be null" );
        }

        ArtifactRequestBuilder builder = new ArtifactRequestBuilder( trace );
        DependencyFilter filter = request.getFilter();
        DependencyVisitor visitor = ( filter != null ) ? new FilteringDependencyVisitor( builder, filter ) : builder;
        visitor = new TreeDependencyVisitor( visitor );

        if ( result.getRoot() != null )
        {
            result.getRoot().accept( visitor );
        }

        List<ArtifactRequest> requests = builder.getRequests();

        List<ArtifactResult> results;
        try
        {
            results = artifactResolver.resolveArtifacts( session, requests );
        }
        catch ( ArtifactResolutionException e )
        {
            are = e;
            results = e.getResults();
        }
        result.setArtifactResults( results );

        updateNodesWithResolvedArtifacts( results );

        if ( dce != null )
        {
            throw new DependencyResolutionException( result, dce );
        }
        else if ( are != null )
        {
            throw new DependencyResolutionException( result, are );
        }

        return result;
    }

    private void updateNodesWithResolvedArtifacts( List<ArtifactResult> results )
    {
        for ( ArtifactResult result : results )
        {
            Artifact artifact = result.getArtifact();
            if ( artifact != null )
            {
                result.getRequest().getDependencyNode().setArtifact( artifact );
            }
        }
    }

    public InstallResult install( RepositorySystemSession session, InstallRequest request )
        throws InstallationException
    {
        validateSession( session );
        requireNonNull( request, "request cannot be null" );

        return installer.install( session, request );
    }

    public DeployResult deploy( RepositorySystemSession session, DeployRequest request )
        throws DeploymentException
    {
        validateSession( session );
        requireNonNull( request, "request cannot be null" );

        return deployer.deploy( session, request );
    }

    public LocalRepositoryManager newLocalRepositoryManager( RepositorySystemSession session,
                                                             LocalRepository localRepository )
    {
        requireNonNull( session, "session cannot be null" );
        requireNonNull( localRepository, "localRepository cannot be null" );

        try
        {
            return localRepositoryProvider.newLocalRepositoryManager( session, localRepository );
        }
        catch ( NoLocalRepositoryManagerException e )
        {
            throw new IllegalArgumentException( e.getMessage(), e );
        }
    }

    public SyncContext newSyncContext( RepositorySystemSession session, boolean shared )
    {
        validateSession( session );
        return syncContextFactory.newInstance( session, shared );
    }

    public List<RemoteRepository> newResolutionRepositories( RepositorySystemSession session,
                                                             List<RemoteRepository> repositories )
    {
        validateSession( session );
        validateRepositories( repositories );

        repositories =
            remoteRepositoryManager.aggregateRepositories( session, new ArrayList<RemoteRepository>(), repositories,
                                                           true );
        return repositories;
    }

    public RemoteRepository newDeploymentRepository( RepositorySystemSession session, RemoteRepository repository )
    {
        validateSession( session );
        requireNonNull( repository, "repository cannot be null" );

        RemoteRepository.Builder builder = new RemoteRepository.Builder( repository );
        Authentication auth = session.getAuthenticationSelector().getAuthentication( repository );
        builder.setAuthentication( auth );
        Proxy proxy = session.getProxySelector().getProxy( repository );
        builder.setProxy( proxy );
        return builder.build();
    }

    private void validateSession( RepositorySystemSession session )
    {
        requireNonNull( session, "repository system session cannot be null" );
        invalidSession( session.getLocalRepositoryManager(), "local repository manager" );
        invalidSession( session.getSystemProperties(), "system properties" );
        invalidSession( session.getUserProperties(), "user properties" );
        invalidSession( session.getConfigProperties(), "config properties" );
        invalidSession( session.getMirrorSelector(), "mirror selector" );
        invalidSession( session.getProxySelector(), "proxy selector" );
        invalidSession( session.getAuthenticationSelector(), "authentication selector" );
        invalidSession( session.getArtifactTypeRegistry(), "artifact type registry" );
        invalidSession( session.getData(), "data" );
    }

    private void validateRepositories( List<RemoteRepository> repositories )
    {
        requireNonNull( repositories, "repositories cannot be null" );
        for ( RemoteRepository repository: repositories )
        {
            requireNonNull( repository, "repository cannot be null" );
        }
    }

    private void invalidSession( Object obj, String name )
    {
        requireNonNull( obj, "repository system session's " + name + " cannot be null" );
    }

}
