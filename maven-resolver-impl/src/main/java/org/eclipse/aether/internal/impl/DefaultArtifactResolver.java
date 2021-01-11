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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import static java.util.Objects.requireNonNull;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;
import javax.inject.Named;

import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositoryEvent.EventType;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactProperties;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.impl.OfflineController;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.impl.RepositoryConnectorProvider;
import org.eclipse.aether.impl.RepositoryEventDispatcher;
import org.eclipse.aether.spi.synccontext.SyncContextFactory;
import org.eclipse.aether.impl.UpdateCheck;
import org.eclipse.aether.impl.UpdateCheckManager;
import org.eclipse.aether.impl.VersionResolver;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.repository.LocalArtifactRegistration;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalArtifactResult;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.ResolutionErrorPolicy;
import org.eclipse.aether.resolution.VersionRequest;
import org.eclipse.aether.resolution.VersionResolutionException;
import org.eclipse.aether.resolution.VersionResult;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.io.FileProcessor;
import org.eclipse.aether.transfer.ArtifactNotFoundException;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;
import org.eclipse.aether.transfer.RepositoryOfflineException;
import org.eclipse.aether.util.ConfigUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 */
@Named
public class DefaultArtifactResolver
    implements ArtifactResolver
{

    private static final String CONFIG_PROP_SNAPSHOT_NORMALIZATION = "aether.artifactResolver.snapshotNormalization";

    private static final Logger LOGGER = LoggerFactory.getLogger( DefaultArtifactResolver.class );

    private final FileProcessor fileProcessor;

    private final RepositoryEventDispatcher repositoryEventDispatcher;

    private final VersionResolver versionResolver;

    private final UpdateCheckManager updateCheckManager;

    private final RepositoryConnectorProvider repositoryConnectorProvider;

    private final RemoteRepositoryManager remoteRepositoryManager;

    private final SyncContextFactory syncContextFactory;

    private final OfflineController offlineController;

    @SuppressWarnings( "checkstyle:parameternumber" )
    @Inject
    public DefaultArtifactResolver( FileProcessor fileProcessor, RepositoryEventDispatcher repositoryEventDispatcher,
                             VersionResolver versionResolver, UpdateCheckManager updateCheckManager,
                             RepositoryConnectorProvider repositoryConnectorProvider,
                             RemoteRepositoryManager remoteRepositoryManager, SyncContextFactory syncContextFactory,
                             OfflineController offlineController )
    {
        this.fileProcessor = requireNonNull( fileProcessor, "file processor cannot be null" );
        this.repositoryEventDispatcher = requireNonNull( repositoryEventDispatcher,
            "repository event dispatcher cannot be null" );
        this.versionResolver = requireNonNull( versionResolver, "version resolver cannot be null" );
        this.updateCheckManager = requireNonNull( updateCheckManager, "update check manager cannot be null" );
        this.repositoryConnectorProvider = requireNonNull( repositoryConnectorProvider,
            "repository connector provider cannot be null" );
        this.remoteRepositoryManager = requireNonNull( remoteRepositoryManager,
            "remote repository provider cannot be null" );
        this.syncContextFactory = requireNonNull( syncContextFactory, "sync context factory cannot be null" );
        this.offlineController = requireNonNull( offlineController, "offline controller cannot be null" );
    }

    @Override
    public ArtifactResult resolveArtifact( RepositorySystemSession session, ArtifactRequest request )
        throws ArtifactResolutionException
    {
        return resolveArtifacts( session, Collections.singleton( request ) ).get( 0 );
    }

    @Override
    public List<ArtifactResult> resolveArtifacts( RepositorySystemSession session,
                                                  Collection<? extends ArtifactRequest> requests )
        throws ArtifactResolutionException
    {

        try ( SyncContext syncContext = syncContextFactory.newInstance( session, false ) )
        {
            Collection<Artifact> artifacts = new ArrayList<>( requests.size() );
            for ( ArtifactRequest request : requests )
            {
                if ( request.getArtifact().getProperty( ArtifactProperties.LOCAL_PATH, null ) != null )
                {
                    continue;
                }
                artifacts.add( request.getArtifact() );
            }

            syncContext.acquire( artifacts, null );

            return resolve( session, requests );
        }
    }

    @SuppressWarnings( "checkstyle:methodlength" )
    private List<ArtifactResult> resolve( RepositorySystemSession session,
                                          Collection<? extends ArtifactRequest> requests )
        throws ArtifactResolutionException
    {
        List<ArtifactResult> results = new ArrayList<>( requests.size() );
        boolean failures = false;

        LocalRepositoryManager lrm = session.getLocalRepositoryManager();
        WorkspaceReader workspace = session.getWorkspaceReader();

        List<ResolutionGroup> groups = new ArrayList<>();

        for ( ArtifactRequest request : requests )
        {
            RequestTrace trace = RequestTrace.newChild( request.getTrace(), request );

            ArtifactResult result = new ArtifactResult( request );
            results.add( result );

            Artifact artifact = request.getArtifact();
            List<RemoteRepository> repos = request.getRepositories();

            artifactResolving( session, trace, artifact );

            String localPath = artifact.getProperty( ArtifactProperties.LOCAL_PATH, null );
            if ( localPath != null )
            {
                // unhosted artifact, just validate file
                File file = new File( localPath );
                if ( !file.isFile() )
                {
                    failures = true;
                    result.addException( new ArtifactNotFoundException( artifact, null ) );
                }
                else
                {
                    artifact = artifact.setFile( file );
                    result.setArtifact( artifact );
                    artifactResolved( session, trace, artifact, null, result.getExceptions() );
                }
                continue;
            }

            VersionResult versionResult;
            try
            {
                VersionRequest versionRequest = new VersionRequest( artifact, repos, request.getRequestContext() );
                versionRequest.setTrace( trace );
                versionResult = versionResolver.resolveVersion( session, versionRequest );
            }
            catch ( VersionResolutionException e )
            {
                result.addException( e );
                continue;
            }

            artifact = artifact.setVersion( versionResult.getVersion() );

            if ( versionResult.getRepository() != null )
            {
                if ( versionResult.getRepository() instanceof RemoteRepository )
                {
                    repos = Collections.singletonList( (RemoteRepository) versionResult.getRepository() );
                }
                else
                {
                    repos = Collections.emptyList();
                }
            }

            if ( workspace != null )
            {
                File file = workspace.findArtifact( artifact );
                if ( file != null )
                {
                    artifact = artifact.setFile( file );
                    result.setArtifact( artifact );
                    result.setRepository( workspace.getRepository() );
                    artifactResolved( session, trace, artifact, result.getRepository(), null );
                    continue;
                }
            }

            LocalArtifactResult local =
                lrm.find( session, new LocalArtifactRequest( artifact, repos, request.getRequestContext() ) );
            if ( isLocallyInstalled( local, versionResult ) )
            {
                if ( local.getRepository() != null )
                {
                    result.setRepository( local.getRepository() );
                }
                else
                {
                    result.setRepository( lrm.getRepository() );
                }
                try
                {
                    artifact = artifact.setFile( getFile( session, artifact, local.getFile() ) );
                    result.setArtifact( artifact );
                    artifactResolved( session, trace, artifact, result.getRepository(), null );
                }
                catch ( ArtifactTransferException e )
                {
                    result.addException( e );
                }
                if ( !local.isAvailable() )
                {
                    /*
                     * NOTE: Interop with simple local repository: An artifact installed by a simple local repo manager
                     * will not show up in the repository tracking file of the enhanced local repository. If however the
                     * maven-metadata-local.xml tells us the artifact was installed locally, we sync the repository
                     * tracking file.
                     */
                    lrm.add( session, new LocalArtifactRegistration( artifact ) );
                }
                continue;
            }
            else if ( local.getFile() != null )
            {
                LOGGER.debug( "Verifying availability of {} from {}", local.getFile(), repos );
            }

            AtomicBoolean resolved = new AtomicBoolean( false );
            Iterator<ResolutionGroup> groupIt = groups.iterator();
            for ( RemoteRepository repo : repos )
            {
                if ( !repo.getPolicy( artifact.isSnapshot() ).isEnabled() )
                {
                    continue;
                }

                try
                {
                    Utils.checkOffline( session, offlineController, repo );
                }
                catch ( RepositoryOfflineException e )
                {
                    Exception exception =
                        new ArtifactNotFoundException( artifact, repo, "Cannot access " + repo.getId() + " ("
                            + repo.getUrl() + ") in offline mode and the artifact " + artifact
                            + " has not been downloaded from it before.", e );
                    result.addException( exception );
                    continue;
                }

                ResolutionGroup group = null;
                while ( groupIt.hasNext() )
                {
                    ResolutionGroup t = groupIt.next();
                    if ( t.matches( repo ) )
                    {
                        group = t;
                        break;
                    }
                }
                if ( group == null )
                {
                    group = new ResolutionGroup( repo );
                    groups.add( group );
                    groupIt = Collections.<ResolutionGroup>emptyList().iterator();
                }
                group.items.add( new ResolutionItem( trace, artifact, resolved, result, local, repo ) );
            }
        }

        for ( ResolutionGroup group : groups )
        {
            performDownloads( session, group );
        }

        for ( ArtifactResult result : results )
        {
            ArtifactRequest request = result.getRequest();

            Artifact artifact = result.getArtifact();
            if ( artifact == null || artifact.getFile() == null )
            {
                failures = true;
                if ( result.getExceptions().isEmpty() )
                {
                    Exception exception = new ArtifactNotFoundException( request.getArtifact(), null );
                    result.addException( exception );
                }
                RequestTrace trace = RequestTrace.newChild( request.getTrace(), request );
                artifactResolved( session, trace, request.getArtifact(), null, result.getExceptions() );
            }
        }

        if ( failures )
        {
            throw new ArtifactResolutionException( results );
        }

        return results;
    }

    private boolean isLocallyInstalled( LocalArtifactResult lar, VersionResult vr )
    {
        if ( lar.isAvailable() )
        {
            return true;
        }
        if ( lar.getFile() != null )
        {
            if ( vr.getRepository() instanceof LocalRepository )
            {
                // resolution of (snapshot) version found locally installed artifact
                return true;
            }
            else if ( vr.getRepository() == null && lar.getRequest().getRepositories().isEmpty() )
            {
                // resolution of version range found locally installed artifact
                return true;
            }
        }
        return false;
    }

    private File getFile( RepositorySystemSession session, Artifact artifact, File file )
        throws ArtifactTransferException
    {
        if ( artifact.isSnapshot() && !artifact.getVersion().equals( artifact.getBaseVersion() )
            && ConfigUtils.getBoolean( session, true, CONFIG_PROP_SNAPSHOT_NORMALIZATION ) )
        {
            String name = file.getName().replace( artifact.getVersion(), artifact.getBaseVersion() );
            File dst = new File( file.getParent(), name );

            boolean copy = dst.length() != file.length() || dst.lastModified() != file.lastModified();
            if ( copy )
            {
                try
                {
                    fileProcessor.copy( file, dst );
                    dst.setLastModified( file.lastModified() );
                }
                catch ( IOException e )
                {
                    throw new ArtifactTransferException( artifact, null, e );
                }
            }

            file = dst;
        }

        return file;
    }

    private void performDownloads( RepositorySystemSession session, ResolutionGroup group )
    {
        List<ArtifactDownload> downloads = gatherDownloads( session, group );
        if ( downloads.isEmpty() )
        {
            return;
        }

        for ( ArtifactDownload download : downloads )
        {
            artifactDownloading( session, download.getTrace(), download.getArtifact(), group.repository );
        }

        try
        {
            try ( RepositoryConnector connector =
                          repositoryConnectorProvider.newRepositoryConnector( session, group.repository ) )
            {
                connector.get( downloads, null );
            }
        }
        catch ( NoRepositoryConnectorException e )
        {
            for ( ArtifactDownload download : downloads )
            {
                download.setException( new ArtifactTransferException( download.getArtifact(), group.repository, e ) );
            }
        }

        evaluateDownloads( session, group );
    }

    private List<ArtifactDownload> gatherDownloads( RepositorySystemSession session, ResolutionGroup group )
    {
        LocalRepositoryManager lrm = session.getLocalRepositoryManager();
        List<ArtifactDownload> downloads = new ArrayList<>();

        for ( ResolutionItem item : group.items )
        {
            Artifact artifact = item.artifact;

            if ( item.resolved.get() )
            {
                // resolved in previous resolution group
                continue;
            }

            ArtifactDownload download = new ArtifactDownload();
            download.setArtifact( artifact );
            download.setRequestContext( item.request.getRequestContext() );
            download.setListener( SafeTransferListener.wrap( session ) );
            download.setTrace( item.trace );
            if ( item.local.getFile() != null )
            {
                download.setFile( item.local.getFile() );
                download.setExistenceCheck( true );
            }
            else
            {
                String path =
                    lrm.getPathForRemoteArtifact( artifact, group.repository, item.request.getRequestContext() );
                download.setFile( new File( lrm.getRepository().getBasedir(), path ) );
            }

            boolean snapshot = artifact.isSnapshot();
            RepositoryPolicy policy =
                remoteRepositoryManager.getPolicy( session, group.repository, !snapshot, snapshot );

            int errorPolicy = Utils.getPolicy( session, artifact, group.repository );
            if ( ( errorPolicy & ResolutionErrorPolicy.CACHE_ALL ) != 0 )
            {
                UpdateCheck<Artifact, ArtifactTransferException> check = new UpdateCheck<>();
                check.setItem( artifact );
                check.setFile( download.getFile() );
                check.setFileValid( false );
                check.setRepository( group.repository );
                check.setPolicy( policy.getUpdatePolicy() );
                item.updateCheck = check;
                updateCheckManager.checkArtifact( session, check );
                if ( !check.isRequired() )
                {
                    item.result.addException( check.getException() );
                    continue;
                }
            }

            download.setChecksumPolicy( policy.getChecksumPolicy() );
            download.setRepositories( item.repository.getMirroredRepositories() );
            downloads.add( download );
            item.download = download;
        }

        return downloads;
    }

    private void evaluateDownloads( RepositorySystemSession session, ResolutionGroup group )
    {
        LocalRepositoryManager lrm = session.getLocalRepositoryManager();

        for ( ResolutionItem item : group.items )
        {
            ArtifactDownload download = item.download;
            if ( download == null )
            {
                continue;
            }

            Artifact artifact = download.getArtifact();
            if ( download.getException() == null )
            {
                item.resolved.set( true );
                item.result.setRepository( group.repository );
                try
                {
                    artifact = artifact.setFile( getFile( session, artifact, download.getFile() ) );
                    item.result.setArtifact( artifact );

                    lrm.add( session, new LocalArtifactRegistration(
                            artifact, group.repository, download.getSupportedContexts() ) );
                }
                catch ( ArtifactTransferException e )
                {
                    download.setException( e );
                    item.result.addException( e );
                }
            }
            else
            {
                item.result.addException( download.getException() );
            }

            /*
             * NOTE: Touch after registration with local repo to ensure concurrent resolution is not rejected with
             * "already updated" via session data when actual update to local repo is still pending.
             */
            if ( item.updateCheck != null )
            {
                item.updateCheck.setException( download.getException() );
                updateCheckManager.touchArtifact( session, item.updateCheck );
            }

            artifactDownloaded( session, download.getTrace(), artifact, group.repository, download.getException() );
            if ( download.getException() == null )
            {
                artifactResolved( session, download.getTrace(), artifact, group.repository, null );
            }
        }
    }

    private void artifactResolving( RepositorySystemSession session, RequestTrace trace, Artifact artifact )
    {
        RepositoryEvent.Builder event = new RepositoryEvent.Builder( session, EventType.ARTIFACT_RESOLVING );
        event.setTrace( trace );
        event.setArtifact( artifact );

        repositoryEventDispatcher.dispatch( event.build() );
    }

    private void artifactResolved( RepositorySystemSession session, RequestTrace trace, Artifact artifact,
                                   ArtifactRepository repository, List<Exception> exceptions )
    {
        RepositoryEvent.Builder event = new RepositoryEvent.Builder( session, EventType.ARTIFACT_RESOLVED );
        event.setTrace( trace );
        event.setArtifact( artifact );
        event.setRepository( repository );
        event.setExceptions( exceptions );
        if ( artifact != null )
        {
            event.setFile( artifact.getFile() );
        }

        repositoryEventDispatcher.dispatch( event.build() );
    }

    private void artifactDownloading( RepositorySystemSession session, RequestTrace trace, Artifact artifact,
                                      RemoteRepository repository )
    {
        RepositoryEvent.Builder event = new RepositoryEvent.Builder( session, EventType.ARTIFACT_DOWNLOADING );
        event.setTrace( trace );
        event.setArtifact( artifact );
        event.setRepository( repository );

        repositoryEventDispatcher.dispatch( event.build() );
    }

    private void artifactDownloaded( RepositorySystemSession session, RequestTrace trace, Artifact artifact,
                                     RemoteRepository repository, Exception exception )
    {
        RepositoryEvent.Builder event = new RepositoryEvent.Builder( session, EventType.ARTIFACT_DOWNLOADED );
        event.setTrace( trace );
        event.setArtifact( artifact );
        event.setRepository( repository );
        event.setException( exception );
        if ( artifact != null )
        {
            event.setFile( artifact.getFile() );
        }

        repositoryEventDispatcher.dispatch( event.build() );
    }

    static class ResolutionGroup
    {

        final RemoteRepository repository;

        final List<ResolutionItem> items = new ArrayList<>();

        ResolutionGroup( RemoteRepository repository )
        {
            this.repository = repository;
        }

        boolean matches( RemoteRepository repo )
        {
            return repository.getUrl().equals( repo.getUrl() )
                && repository.getContentType().equals( repo.getContentType() )
                && repository.isRepositoryManager() == repo.isRepositoryManager();
        }

    }

    static class ResolutionItem
    {

        final RequestTrace trace;

        final ArtifactRequest request;

        final ArtifactResult result;

        final LocalArtifactResult local;

        final RemoteRepository repository;

        final Artifact artifact;

        final AtomicBoolean resolved;

        ArtifactDownload download;

        UpdateCheck<Artifact, ArtifactTransferException> updateCheck;

        ResolutionItem( RequestTrace trace, Artifact artifact, AtomicBoolean resolved, ArtifactResult result,
                        LocalArtifactResult local, RemoteRepository repository )
        {
            this.trace = trace;
            this.artifact = artifact;
            this.resolved = resolved;
            this.result = result;
            this.request = result.getRequest();
            this.local = local;
            this.repository = repository;
        }

    }

}
