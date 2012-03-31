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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Named;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.RepositoryEvent.EventType;
import org.eclipse.aether.impl.MetadataResolver;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.impl.RepositoryConnectorProvider;
import org.eclipse.aether.impl.RepositoryEventDispatcher;
import org.eclipse.aether.impl.SyncContextFactory;
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
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.spi.log.LoggerFactory;
import org.eclipse.aether.spi.log.NullLoggerFactory;
import org.eclipse.aether.transfer.MetadataNotFoundException;
import org.eclipse.aether.transfer.MetadataTransferException;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.concurrency.RunnableErrorForwarder;

/**
 */
@Named
@Component( role = MetadataResolver.class )
public class DefaultMetadataResolver
    implements MetadataResolver, Service
{

    @SuppressWarnings( "unused" )
    @Requirement( role = LoggerFactory.class )
    private Logger logger = NullLoggerFactory.LOGGER;

    @Requirement
    private RepositoryEventDispatcher repositoryEventDispatcher;

    @Requirement
    private UpdateCheckManager updateCheckManager;

    @Requirement
    private RepositoryConnectorProvider repositoryConnectorProvider;

    @Requirement
    private RemoteRepositoryManager remoteRepositoryManager;

    @Requirement
    private SyncContextFactory syncContextFactory;

    public DefaultMetadataResolver()
    {
        // enables default constructor
    }

    @Inject
    DefaultMetadataResolver( RepositoryEventDispatcher repositoryEventDispatcher,
                             UpdateCheckManager updateCheckManager,
                             RepositoryConnectorProvider repositoryConnectorProvider,
                             RemoteRepositoryManager remoteRepositoryManager, SyncContextFactory syncContextFactory,
                             LoggerFactory loggerFactory )
    {
        setRepositoryEventDispatcher( repositoryEventDispatcher );
        setUpdateCheckManager( updateCheckManager );
        setRepositoryConnectorProvider( repositoryConnectorProvider );
        setRemoteRepositoryManager( remoteRepositoryManager );
        setSyncContextFactory( syncContextFactory );
        setLoggerFactory( loggerFactory );
    }

    public void initService( ServiceLocator locator )
    {
        setLoggerFactory( locator.getService( LoggerFactory.class ) );
        setRepositoryEventDispatcher( locator.getService( RepositoryEventDispatcher.class ) );
        setUpdateCheckManager( locator.getService( UpdateCheckManager.class ) );
        setRepositoryConnectorProvider( locator.getService( RepositoryConnectorProvider.class ) );
        setRemoteRepositoryManager( locator.getService( RemoteRepositoryManager.class ) );
        setSyncContextFactory( locator.getService( SyncContextFactory.class ) );
    }

    public DefaultMetadataResolver setLoggerFactory( LoggerFactory loggerFactory )
    {
        this.logger = NullLoggerFactory.getSafeLogger( loggerFactory, getClass() );
        return this;
    }

    void setLogger( LoggerFactory loggerFactory )
    {
        // plexus support
        setLoggerFactory( loggerFactory );
    }

    public DefaultMetadataResolver setRepositoryEventDispatcher( RepositoryEventDispatcher repositoryEventDispatcher )
    {
        if ( repositoryEventDispatcher == null )
        {
            throw new IllegalArgumentException( "repository event dispatcher has not been specified" );
        }
        this.repositoryEventDispatcher = repositoryEventDispatcher;
        return this;
    }

    public DefaultMetadataResolver setUpdateCheckManager( UpdateCheckManager updateCheckManager )
    {
        if ( updateCheckManager == null )
        {
            throw new IllegalArgumentException( "update check manager has not been specified" );
        }
        this.updateCheckManager = updateCheckManager;
        return this;
    }

    public DefaultMetadataResolver setRepositoryConnectorProvider( RepositoryConnectorProvider repositoryConnectorProvider )
    {
        if ( repositoryConnectorProvider == null )
        {
            throw new IllegalArgumentException( "repository connector provider has not been specified" );
        }
        this.repositoryConnectorProvider = repositoryConnectorProvider;
        return this;
    }

    public DefaultMetadataResolver setRemoteRepositoryManager( RemoteRepositoryManager remoteRepositoryManager )
    {
        if ( remoteRepositoryManager == null )
        {
            throw new IllegalArgumentException( "remote repository manager has not been specified" );
        }
        this.remoteRepositoryManager = remoteRepositoryManager;
        return this;
    }

    public DefaultMetadataResolver setSyncContextFactory( SyncContextFactory syncContextFactory )
    {
        if ( syncContextFactory == null )
        {
            throw new IllegalArgumentException( "sync context factory has not been specified" );
        }
        this.syncContextFactory = syncContextFactory;
        return this;
    }

    public List<MetadataResult> resolveMetadata( RepositorySystemSession session,
                                                 Collection<? extends MetadataRequest> requests )
    {
        SyncContext syncContext = syncContextFactory.newInstance( session, false );

        try
        {
            Collection<Metadata> metadata = new ArrayList<Metadata>( requests.size() );
            for ( MetadataRequest request : requests )
            {
                metadata.add( request.getMetadata() );
            }

            syncContext.acquire( null, metadata );

            return resolve( session, requests );
        }
        finally
        {
            syncContext.close();
        }
    }

    private List<MetadataResult> resolve( RepositorySystemSession session,
                                          Collection<? extends MetadataRequest> requests )
    {
        List<MetadataResult> results = new ArrayList<MetadataResult>( requests.size() );

        List<ResolveTask> tasks = new ArrayList<ResolveTask>( requests.size() );

        Map<File, Long> localLastUpdates = new HashMap<File, Long>();

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

            if ( session.isOffline() )
            {
                if ( metadataFile != null )
                {
                    metadata = metadata.setFile( metadataFile );
                    result.setMetadata( metadata );
                }
                else
                {
                    String msg =
                        "The repository system is offline but the metadata " + metadata + " from " + repository
                            + " is not available in the local repository.";
                    result.setException( new MetadataNotFoundException( metadata, repository, msg ) );
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
                    localLastUpdate = Long.valueOf( localFile != null ? localFile.lastModified() : 0 );
                    localLastUpdates.put( localFile, localLastUpdate );
                }
            }

            List<UpdateCheck<Metadata, MetadataTransferException>> checks =
                new ArrayList<UpdateCheck<Metadata, MetadataTransferException>>();
            Exception exception = null;
            for ( RemoteRepository repo : repositories )
            {
                UpdateCheck<Metadata, MetadataTransferException> check =
                    new UpdateCheck<Metadata, MetadataTransferException>();
                check.setLocalLastUpdated( ( localLastUpdate != null ) ? localLastUpdate.longValue() : 0 );
                check.setItem( metadata );

                // use 'main' installation file for the check (-> use requested repository)
                File checkFile =
                    new File(
                              session.getLocalRepository().getBasedir(),
                              session.getLocalRepositoryManager().getPathForRemoteMetadata( metadata, repository,
                                                                                            request.getRequestContext() ) );
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
                File installFile =
                    new File(
                              session.getLocalRepository().getBasedir(),
                              session.getLocalRepositoryManager().getPathForRemoteMetadata( metadata,
                                                                                            request.getRepository(),
                                                                                            request.getRequestContext() ) );

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
            int threads = ConfigUtils.getInteger( session, 4, "aether.metadataResolver.threads" );
            Executor executor = getExecutor( Math.min( tasks.size(), threads ) );
            try
            {
                RunnableErrorForwarder errorForwarder = new RunnableErrorForwarder();

                for ( ResolveTask task : tasks )
                {
                    executor.execute( errorForwarder.wrap( task ) );
                }

                errorForwarder.await();

                for ( ResolveTask task : tasks )
                {
                    task.result.setException( task.exception );
                }
            }
            finally
            {
                shutdown( executor );
            }
            for ( ResolveTask task : tasks )
            {
                Metadata metadata = task.request.getMetadata();
                // re-lookup metadata for resolve
                LocalMetadataRequest localRequest =
                    new LocalMetadataRequest( metadata, task.request.getRepository(), task.request.getRequestContext() );
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
        File localFile = localResult.getFile();
        return localFile;
    }

    private List<RemoteRepository> getEnabledSourceRepositories( RemoteRepository repository, Metadata.Nature nature )
    {
        List<RemoteRepository> repositories = new ArrayList<RemoteRepository>();

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

    private Executor getExecutor( int threads )
    {
        if ( threads <= 1 )
        {
            return new Executor()
            {
                public void execute( Runnable command )
                {
                    command.run();
                }
            };
        }
        else
        {
            return new ThreadPoolExecutor( threads, threads, 3, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>() );
        }
    }

    private void shutdown( Executor executor )
    {
        if ( executor instanceof ExecutorService )
        {
            ( (ExecutorService) executor ).shutdown();
        }
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

        public ResolveTask( RepositorySystemSession session, RequestTrace trace, MetadataResult result,
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

        public void run()
        {
            Metadata metadata = request.getMetadata();
            RemoteRepository requestRepository = request.getRepository();

            metadataDownloading( session, trace, metadata, requestRepository );

            try
            {
                List<RemoteRepository> repositories = new ArrayList<RemoteRepository>();
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

                RepositoryConnector connector =
                    repositoryConnectorProvider.newRepositoryConnector( session, requestRepository );
                try
                {
                    connector.get( null, Arrays.asList( download ) );
                }
                finally
                {
                    connector.close();
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

            for ( UpdateCheck<Metadata, MetadataTransferException> check : checks )
            {
                updateCheckManager.touchMetadata( session, check.setException( exception ) );
            }

            metadataDownloaded( session, trace, metadata, requestRepository, metadataFile, exception );
        }

    }

}
