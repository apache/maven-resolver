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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static java.util.Objects.requireNonNull;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositoryEvent.EventType;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.impl.MetadataResolver;
import org.eclipse.aether.impl.OfflineController;
import org.eclipse.aether.impl.RemoteRepositoryFilterManager;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.impl.RepositoryConnectorProvider;
import org.eclipse.aether.impl.RepositoryEventDispatcher;
import org.eclipse.aether.spi.concurrency.ResolverExecutorService;
import org.eclipse.aether.spi.connector.filter.RemoteRepositoryFilter;
import org.eclipse.aether.spi.synccontext.SyncContextFactory;
import org.eclipse.aether.impl.UpdateCheck;
import org.eclipse.aether.impl.UpdateCheckManager;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.repository.LocalMetadataRegistration;
import org.eclipse.aether.repository.LocalMetadataRequest;
import org.eclipse.aether.repository.LocalMetadataResult;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.MetadataRequest;
import org.eclipse.aether.resolution.MetadataResult;
import org.eclipse.aether.spi.connector.MetadataDownload;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.transfer.MetadataNotFoundException;
import org.eclipse.aether.transfer.MetadataTransferException;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;
import org.eclipse.aether.transfer.RepositoryOfflineException;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.concurrency.RunnableErrorForwarder;

/**
 */
@Singleton
@Named
public class DefaultMetadataResolver
    implements MetadataResolver, Service
{

    /**
     * The count of threads to be used when resolving metadata in parallel, default value 4.
     */
    private static final String CONFIG_PROP_THREADS = "aether.metadataResolver.threads";

    /**
     * The default value for {@link #CONFIG_PROP_THREADS}.
     */
    private static final int CONFIG_PROP_THREADS_DEFAULT = 4;

    private RepositoryEventDispatcher repositoryEventDispatcher;

    private UpdateCheckManager updateCheckManager;

    private RepositoryConnectorProvider repositoryConnectorProvider;

    private RemoteRepositoryManager remoteRepositoryManager;

    private SyncContextFactory syncContextFactory;

    private OfflineController offlineController;

    private RemoteRepositoryFilterManager remoteRepositoryFilterManager;

    private ResolverExecutorService resolverExecutorService;

    public DefaultMetadataResolver()
    {
        // enables default constructor
    }

    @SuppressWarnings( "checkstyle:parameternumber" )
    @Inject
    DefaultMetadataResolver( RepositoryEventDispatcher repositoryEventDispatcher,
                             UpdateCheckManager updateCheckManager,
                             RepositoryConnectorProvider repositoryConnectorProvider,
                             RemoteRepositoryManager remoteRepositoryManager, SyncContextFactory syncContextFactory,
                             OfflineController offlineController,
                             RemoteRepositoryFilterManager remoteRepositoryFilterManager,
                             ResolverExecutorService resolverExecutorService )
    {
        setRepositoryEventDispatcher( repositoryEventDispatcher );
        setUpdateCheckManager( updateCheckManager );
        setRepositoryConnectorProvider( repositoryConnectorProvider );
        setRemoteRepositoryManager( remoteRepositoryManager );
        setSyncContextFactory( syncContextFactory );
        setOfflineController( offlineController );
        setRemoteRepositoryFilterManager( remoteRepositoryFilterManager );
        setResolverExecutorService( resolverExecutorService );
    }

    public void initService( ServiceLocator locator )
    {
        setRepositoryEventDispatcher( locator.getService( RepositoryEventDispatcher.class ) );
        setUpdateCheckManager( locator.getService( UpdateCheckManager.class ) );
        setRepositoryConnectorProvider( locator.getService( RepositoryConnectorProvider.class ) );
        setRemoteRepositoryManager( locator.getService( RemoteRepositoryManager.class ) );
        setSyncContextFactory( locator.getService( SyncContextFactory.class ) );
        setOfflineController( locator.getService( OfflineController.class ) );
        setRemoteRepositoryFilterManager( locator.getService( RemoteRepositoryFilterManager.class ) );
        setResolverExecutorService( locator.getService( ResolverExecutorService.class ) );
    }

    public DefaultMetadataResolver setRepositoryEventDispatcher( RepositoryEventDispatcher repositoryEventDispatcher )
    {
        this.repositoryEventDispatcher = requireNonNull(
                repositoryEventDispatcher, "repository event dispatcher cannot be null" );
        return this;
    }

    public DefaultMetadataResolver setUpdateCheckManager( UpdateCheckManager updateCheckManager )
    {
        this.updateCheckManager = requireNonNull( updateCheckManager, "update check manager cannot be null" );
        return this;
    }

    public DefaultMetadataResolver setRepositoryConnectorProvider(
            RepositoryConnectorProvider repositoryConnectorProvider )
    {
        this.repositoryConnectorProvider = requireNonNull(
                repositoryConnectorProvider, "repository connector provider cannot be null" );
        return this;
    }

    public DefaultMetadataResolver setRemoteRepositoryManager( RemoteRepositoryManager remoteRepositoryManager )
    {
        this.remoteRepositoryManager = requireNonNull(
                remoteRepositoryManager, "remote repository provider cannot be null" );
        return this;
    }

    public DefaultMetadataResolver setSyncContextFactory( SyncContextFactory syncContextFactory )
    {
        this.syncContextFactory = requireNonNull( syncContextFactory, "sync context factory cannot be null" );
        return this;
    }

    public DefaultMetadataResolver setOfflineController( OfflineController offlineController )
    {
        this.offlineController = requireNonNull( offlineController, "offline controller cannot be null" );
        return this;
    }

    public DefaultMetadataResolver setRemoteRepositoryFilterManager(
            RemoteRepositoryFilterManager remoteRepositoryFilterManager )
    {
        this.remoteRepositoryFilterManager = requireNonNull( remoteRepositoryFilterManager,
                "remote repository filter manager cannot be null" );
        return this;
    }

    public DefaultMetadataResolver setResolverExecutorService( ResolverExecutorService resolverExecutorService )
    {
        this.resolverExecutorService = requireNonNull( resolverExecutorService,
                "resolver executor service cannot be null" );
        return this;
    }

    @Override
    public List<MetadataResult> resolveMetadata( RepositorySystemSession session,
                                                 Collection<? extends MetadataRequest> requests )
    {
        requireNonNull( session, "session cannot be null" );
        requireNonNull( requests, "requests cannot be null" );
        try ( SyncContext syncContext = syncContextFactory.newInstance( session, false ) )
        {
            Collection<Metadata> metadata = new ArrayList<>( requests.size() );
            for ( MetadataRequest request : requests )
            {
                metadata.add( request.getMetadata() );
            }

            syncContext.acquire( null, metadata );

            return resolve( session, requests );
        }
    }

    @SuppressWarnings( "checkstyle:methodlength" )
    private List<MetadataResult> resolve( RepositorySystemSession session,
                                          Collection<? extends MetadataRequest> requests )
    {
        List<MetadataResult> results = new ArrayList<>( requests.size() );

        List<ResolveTask> tasks = new ArrayList<>( requests.size() );

        Map<File, Long> localLastUpdates = new HashMap<>();

        RemoteRepositoryFilter remoteRepositoryFilter = remoteRepositoryFilterManager
                .getRemoteRepositoryFilter( session );

        for ( MetadataRequest request : requests )
        {
            RequestTrace trace = RequestTrace.newChild( request.getTrace(), request );

            MetadataResult result = new MetadataResult( request );
            results.add( result );

            Metadata metadata = request.getMetadata();
            RemoteRepository repository = request.getRepository();

            if ( repository == null )
            {
                LocalRepository localRepo = session.getLocalRepositoryManager().getRepository();

                metadataResolving( session, trace, metadata, localRepo );

                File localFile = getLocalFile( session, metadata );

                if ( localFile != null )
                {
                    metadata = metadata.setFile( localFile );
                    result.setMetadata( metadata );
                }
                else
                {
                    result.setException( new MetadataNotFoundException( metadata, localRepo ) );
                }

                metadataResolved( session, trace, metadata, localRepo, result.getException() );
                continue;
            }

            if ( remoteRepositoryFilter != null )
            {
                RemoteRepositoryFilter.Result filterResult = remoteRepositoryFilter.acceptMetadata(
                        repository, metadata );
                if ( !filterResult.isAccepted() )
                {
                    result.setException(
                            new MetadataNotFoundException( metadata, repository, filterResult.reasoning() ) );
                    continue;
                }
            }

            List<RemoteRepository> repositories = getEnabledSourceRepositories( repository, metadata.getNature() );

            if ( repositories.isEmpty() )
            {
                continue;
            }

            metadataResolving( session, trace, metadata, repository );
            LocalRepositoryManager lrm = session.getLocalRepositoryManager();
            LocalMetadataRequest localRequest =
                new LocalMetadataRequest( metadata, repository, request.getRequestContext() );
            LocalMetadataResult lrmResult = lrm.find( session, localRequest );

            File metadataFile = lrmResult.getFile();

            try
            {
                Utils.checkOffline( session, offlineController, repository );
            }
            catch ( RepositoryOfflineException e )
            {
                if ( metadataFile != null )
                {
                    metadata = metadata.setFile( metadataFile );
                    result.setMetadata( metadata );
                }
                else
                {
                    String msg =
                        "Cannot access " + repository.getId() + " (" + repository.getUrl()
                            + ") in offline mode and the metadata " + metadata
                            + " has not been downloaded from it before";
                    result.setException( new MetadataNotFoundException( metadata, repository, msg, e ) );
                }

                metadataResolved( session, trace, metadata, repository, result.getException() );
                continue;
            }

            Long localLastUpdate = null;
            if ( request.isFavorLocalRepository() )
            {
                File localFile = getLocalFile( session, metadata );
                localLastUpdate = localLastUpdates.get( localFile );
                if ( localLastUpdate == null )
                {
                    localLastUpdate = localFile != null ? localFile.lastModified() : 0;
                    localLastUpdates.put( localFile, localLastUpdate );
                }
            }

            List<UpdateCheck<Metadata, MetadataTransferException>> checks = new ArrayList<>();
            Exception exception = null;
            for ( RemoteRepository repo : repositories )
            {
                UpdateCheck<Metadata, MetadataTransferException> check = new UpdateCheck<>();
                check.setLocalLastUpdated( ( localLastUpdate != null ) ? localLastUpdate : 0 );
                check.setItem( metadata );

                // use 'main' installation file for the check (-> use requested repository)
                File checkFile = new File(
                        session.getLocalRepository().getBasedir(),
                        session.getLocalRepositoryManager()
                                .getPathForRemoteMetadata( metadata, repository, request.getRequestContext() ) );
                check.setFile( checkFile );
                check.setRepository( repository );
                check.setAuthoritativeRepository( repo );
                check.setPolicy( getPolicy( session, repo, metadata.getNature() ).getUpdatePolicy() );

                if ( lrmResult.isStale() )
                {
                    checks.add( check );
                }
                else
                {
                    updateCheckManager.checkMetadata( session, check );
                    if ( check.isRequired() )
                    {
                        checks.add( check );
                    }
                    else if ( exception == null )
                    {
                        exception = check.getException();
                    }
                }
            }

            if ( !checks.isEmpty() )
            {
                RepositoryPolicy policy = getPolicy( session, repository, metadata.getNature() );

                // install path may be different from lookup path
                File installFile = new File(
                        session.getLocalRepository().getBasedir(),
                        session.getLocalRepositoryManager().getPathForRemoteMetadata(
                                metadata, request.getRepository(), request.getRequestContext() ) );

                metadataDownloading(
                        session, trace, result.getRequest().getMetadata(), result.getRequest().getRepository() );

                ResolveTask task =
                    new ResolveTask( session, trace, result, installFile, checks, policy.getChecksumPolicy() );
                tasks.add( task );
            }
            else
            {
                result.setException( exception );
                if ( metadataFile != null )
                {
                    metadata = metadata.setFile( metadataFile );
                    result.setMetadata( metadata );
                }
                metadataResolved( session, trace, metadata, repository, result.getException() );
            }
        }

        if ( !tasks.isEmpty() )
        {
            RunnableErrorForwarder errorForwarder = new RunnableErrorForwarder();
            ArrayList<Runnable> runnable = new ArrayList<>( tasks.size() );
            for ( ResolveTask task : tasks )
            {
                runnable.add( errorForwarder.wrap( task ) );
            }

            resolverExecutorService.getResolverExecutor( session,
                    resolverExecutorService.getKey( MetadataResolver.class ),
                    ConfigUtils.getInteger( session, CONFIG_PROP_THREADS_DEFAULT, CONFIG_PROP_THREADS ) )
                    .submitBatch( runnable );
            errorForwarder.await();

            for ( ResolveTask task : tasks )
            {
                /*
                 * NOTE: Touch after registration with local repo to ensure concurrent resolution is not
                 * rejected with "already updated" via session data when actual update to local repo is
                 * still pending.
                 */
                for ( UpdateCheck<Metadata, MetadataTransferException> check : task.checks )
                {
                    updateCheckManager.touchMetadata( task.session, check.setException( task.exception ) );
                }

                metadataDownloaded( session, task.trace, task.request.getMetadata(), task.request.getRepository(),
                        task.metadataFile, task.exception );

                task.result.setException( task.exception );
            }

            for ( ResolveTask task : tasks )
            {
                Metadata metadata = task.request.getMetadata();
                // re-lookup metadata for resolve
                LocalMetadataRequest localRequest = new LocalMetadataRequest(
                        metadata, task.request.getRepository(), task.request.getRequestContext() );
                File metadataFile = session.getLocalRepositoryManager().find( session, localRequest ).getFile();
                if ( metadataFile != null )
                {
                    metadata = metadata.setFile( metadataFile );
                    task.result.setMetadata( metadata );
                }
                if ( task.result.getException() == null )
                {
                    task.result.setUpdated( true );
                }
                metadataResolved( session, task.trace, metadata, task.request.getRepository(),
                                  task.result.getException() );
            }
        }

        return results;
    }

    private File getLocalFile( RepositorySystemSession session, Metadata metadata )
    {
        LocalRepositoryManager lrm = session.getLocalRepositoryManager();
        LocalMetadataResult localResult = lrm.find( session, new LocalMetadataRequest( metadata, null, null ) );
        return localResult.getFile();
    }

    private List<RemoteRepository> getEnabledSourceRepositories( RemoteRepository repository, Metadata.Nature nature )
    {
        List<RemoteRepository> repositories = new ArrayList<>();

        if ( repository.isRepositoryManager() )
        {
            for ( RemoteRepository repo : repository.getMirroredRepositories() )
            {
                if ( isEnabled( repo, nature ) )
                {
                    repositories.add( repo );
                }
            }
        }
        else if ( isEnabled( repository, nature ) )
        {
            repositories.add( repository );
        }

        return repositories;
    }

    private boolean isEnabled( RemoteRepository repository, Metadata.Nature nature )
    {
        if ( !Metadata.Nature.SNAPSHOT.equals( nature ) && repository.getPolicy( false ).isEnabled() )
        {
            return true;
        }
        if ( !Metadata.Nature.RELEASE.equals( nature ) && repository.getPolicy( true ).isEnabled() )
        {
            return true;
        }
        return false;
    }

    private RepositoryPolicy getPolicy( RepositorySystemSession session, RemoteRepository repository,
                                        Metadata.Nature nature )
    {
        boolean releases = !Metadata.Nature.SNAPSHOT.equals( nature );
        boolean snapshots = !Metadata.Nature.RELEASE.equals( nature );
        return remoteRepositoryManager.getPolicy( session, repository, releases, snapshots );
    }

    private void metadataResolving( RepositorySystemSession session, RequestTrace trace, Metadata metadata,
                                    ArtifactRepository repository )
    {
        RepositoryEvent.Builder event = new RepositoryEvent.Builder( session, EventType.METADATA_RESOLVING );
        event.setTrace( trace );
        event.setMetadata( metadata );
        event.setRepository( repository );

        repositoryEventDispatcher.dispatch( event.build() );
    }

    private void metadataResolved( RepositorySystemSession session, RequestTrace trace, Metadata metadata,
                                   ArtifactRepository repository, Exception exception )
    {
        RepositoryEvent.Builder event = new RepositoryEvent.Builder( session, EventType.METADATA_RESOLVED );
        event.setTrace( trace );
        event.setMetadata( metadata );
        event.setRepository( repository );
        event.setException( exception );
        event.setFile( metadata.getFile() );

        repositoryEventDispatcher.dispatch( event.build() );
    }

    private void metadataDownloading( RepositorySystemSession session, RequestTrace trace, Metadata metadata,
                                      ArtifactRepository repository )
    {
        RepositoryEvent.Builder event = new RepositoryEvent.Builder( session, EventType.METADATA_DOWNLOADING );
        event.setTrace( trace );
        event.setMetadata( metadata );
        event.setRepository( repository );

        repositoryEventDispatcher.dispatch( event.build() );
    }

    private void metadataDownloaded( RepositorySystemSession session, RequestTrace trace, Metadata metadata,
                                     ArtifactRepository repository, File file, Exception exception )
    {
        RepositoryEvent.Builder event = new RepositoryEvent.Builder( session, EventType.METADATA_DOWNLOADED );
        event.setTrace( trace );
        event.setMetadata( metadata );
        event.setRepository( repository );
        event.setException( exception );
        event.setFile( file );

        repositoryEventDispatcher.dispatch( event.build() );
    }

    class ResolveTask
        implements Runnable
    {
        final RepositorySystemSession session;

        final RequestTrace trace;

        final MetadataResult result;

        final MetadataRequest request;

        final File metadataFile;

        final String policy;

        final List<UpdateCheck<Metadata, MetadataTransferException>> checks;

        volatile MetadataTransferException exception;

        ResolveTask( RepositorySystemSession session, RequestTrace trace, MetadataResult result,
                            File metadataFile, List<UpdateCheck<Metadata, MetadataTransferException>> checks,
                            String policy )
        {
            this.session = session;
            this.trace = trace;
            this.result = result;
            this.request = result.getRequest();
            this.metadataFile = metadataFile;
            this.policy = policy;
            this.checks = checks;
        }

        @Override
        public void run()
        {
            Metadata metadata = request.getMetadata();
            RemoteRepository requestRepository = request.getRepository();

            try
            {
                List<RemoteRepository> repositories = new ArrayList<>();
                for ( UpdateCheck<Metadata, MetadataTransferException> check : checks )
                {
                    repositories.add( check.getAuthoritativeRepository() );
                }

                MetadataDownload download = new MetadataDownload();
                download.setMetadata( metadata );
                download.setRequestContext( request.getRequestContext() );
                download.setFile( metadataFile );
                download.setChecksumPolicy( policy );
                download.setRepositories( repositories );
                download.setListener( SafeTransferListener.wrap( session ) );
                download.setTrace( trace );

                try ( RepositoryConnector connector =
                              repositoryConnectorProvider.newRepositoryConnector( session, requestRepository ) )
                {
                    connector.get( null, Collections.singletonList( download ) );
                }

                exception = download.getException();

                if ( exception == null )
                {

                    List<String> contexts = Collections.singletonList( request.getRequestContext() );
                    LocalMetadataRegistration registration =
                        new LocalMetadataRegistration( metadata, requestRepository, contexts );

                    session.getLocalRepositoryManager().add( session, registration );
                }
                else if ( request.isDeleteLocalCopyIfMissing() && exception instanceof MetadataNotFoundException )
                {
                    download.getFile().delete();
                }
            }
            catch ( NoRepositoryConnectorException e )
            {
                exception = new MetadataTransferException( metadata, requestRepository, e );
            }
        }
    }
}
