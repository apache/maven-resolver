/*******************************************************************************
 * Copyright (c) 2010, 2011 Sonatype, Inc.
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.RepositoryEvent.EventType;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.impl.RepositoryEventDispatcher;
import org.eclipse.aether.impl.SyncContextFactory;
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
import org.eclipse.aether.resolution.VersionRequest;
import org.eclipse.aether.resolution.VersionResolutionException;
import org.eclipse.aether.resolution.VersionResult;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.io.FileProcessor;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.spi.log.NullLogger;
import org.eclipse.aether.transfer.ArtifactNotFoundException;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.DefaultRequestTrace;
import org.eclipse.aether.util.artifact.ArtifactProperties;
import org.eclipse.aether.util.listener.DefaultRepositoryEvent;

/**
 */
@Component( role = ArtifactResolver.class )
public class DefaultArtifactResolver
    implements ArtifactResolver, Service
{

    @Requirement
    private Logger logger = NullLogger.INSTANCE;

    @Requirement
    private FileProcessor fileProcessor;

    @Requirement
    private RepositoryEventDispatcher repositoryEventDispatcher;

    @Requirement
    private VersionResolver versionResolver;

    @Requirement
    private UpdateCheckManager updateCheckManager;

    @Requirement
    private RemoteRepositoryManager remoteRepositoryManager;

    @Requirement
    private SyncContextFactory syncContextFactory;

    public DefaultArtifactResolver()
    {
        // enables default constructor
    }

    public DefaultArtifactResolver( FileProcessor fileProcessor, RepositoryEventDispatcher repositoryEventDispatcher,
                                    VersionResolver versionResolver, UpdateCheckManager updateCheckManager,
                                    RemoteRepositoryManager remoteRepositoryManager,
                                    SyncContextFactory syncContextFactory )
    {
        setFileProcessor( fileProcessor );
        setRepositoryEventDispatcher( repositoryEventDispatcher );
        setVersionResolver( versionResolver );
        setUpdateCheckManager( updateCheckManager );
        setRemoteRepositoryManager( remoteRepositoryManager );
        setSyncContextFactory( syncContextFactory );
    }

    public void initService( ServiceLocator locator )
    {
        setLogger( locator.getService( Logger.class ) );
        setFileProcessor( locator.getService( FileProcessor.class ) );
        setRepositoryEventDispatcher( locator.getService( RepositoryEventDispatcher.class ) );
        setVersionResolver( locator.getService( VersionResolver.class ) );
        setUpdateCheckManager( locator.getService( UpdateCheckManager.class ) );
        setRemoteRepositoryManager( locator.getService( RemoteRepositoryManager.class ) );
        setSyncContextFactory( locator.getService( SyncContextFactory.class ) );
    }

    public DefaultArtifactResolver setLogger( Logger logger )
    {
        this.logger = ( logger != null ) ? logger : NullLogger.INSTANCE;
        return this;
    }

    public DefaultArtifactResolver setFileProcessor( FileProcessor fileProcessor )
    {
        if ( fileProcessor == null )
        {
            throw new IllegalArgumentException( "file processor has not been specified" );
        }
        this.fileProcessor = fileProcessor;
        return this;
    }

    public DefaultArtifactResolver setRepositoryEventDispatcher( RepositoryEventDispatcher repositoryEventDispatcher )
    {
        if ( repositoryEventDispatcher == null )
        {
            throw new IllegalArgumentException( "repository event dispatcher has not been specified" );
        }
        this.repositoryEventDispatcher = repositoryEventDispatcher;
        return this;
    }

    public DefaultArtifactResolver setVersionResolver( VersionResolver versionResolver )
    {
        if ( versionResolver == null )
        {
            throw new IllegalArgumentException( "version resolver has not been specified" );
        }
        this.versionResolver = versionResolver;
        return this;
    }

    public DefaultArtifactResolver setUpdateCheckManager( UpdateCheckManager updateCheckManager )
    {
        if ( updateCheckManager == null )
        {
            throw new IllegalArgumentException( "update check manager has not been specified" );
        }
        this.updateCheckManager = updateCheckManager;
        return this;
    }

    public DefaultArtifactResolver setRemoteRepositoryManager( RemoteRepositoryManager remoteRepositoryManager )
    {
        if ( remoteRepositoryManager == null )
        {
            throw new IllegalArgumentException( "remote repository manager has not been specified" );
        }
        this.remoteRepositoryManager = remoteRepositoryManager;
        return this;
    }

    public DefaultArtifactResolver setSyncContextFactory( SyncContextFactory syncContextFactory )
    {
        if ( syncContextFactory == null )
        {
            throw new IllegalArgumentException( "sync context factory has not been specified" );
        }
        this.syncContextFactory = syncContextFactory;
        return this;
    }

    public ArtifactResult resolveArtifact( RepositorySystemSession session, ArtifactRequest request )
        throws ArtifactResolutionException
    {
        return resolveArtifacts( session, Collections.singleton( request ) ).get( 0 );
    }

    public List<ArtifactResult> resolveArtifacts( RepositorySystemSession session,
                                                  Collection<? extends ArtifactRequest> requests )
        throws ArtifactResolutionException
    {
        SyncContext syncContext = syncContextFactory.newInstance( session, false );

        try
        {
            Collection<Artifact> artifacts = new ArrayList<Artifact>( requests.size() );
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
        finally
        {
            syncContext.close();
        }
    }

    private List<ArtifactResult> resolve( RepositorySystemSession session,
                                          Collection<? extends ArtifactRequest> requests )
        throws ArtifactResolutionException
    {
        List<ArtifactResult> results = new ArrayList<ArtifactResult>( requests.size() );
        boolean failures = false;

        LocalRepositoryManager lrm = session.getLocalRepositoryManager();
        WorkspaceReader workspace = session.getWorkspaceReader();

        List<ResolutionGroup> groups = new ArrayList<ResolutionGroup>();

        for ( ArtifactRequest request : requests )
        {
            RequestTrace trace = DefaultRequestTrace.newChild( request.getTrace(), request );

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
                logger.debug( "Verifying availability of " + local.getFile() + " from " + repos );
            }

            if ( session.isOffline() )
            {
                Exception exception =
                    new ArtifactNotFoundException( artifact, null, "The repository system is offline but the artifact "
                        + artifact + " is not available in the local repository." );
                result.addException( exception );
                artifactResolved( session, trace, artifact, null, result.getExceptions() );
                continue;
            }

            AtomicBoolean resolved = new AtomicBoolean( false );
            Iterator<ResolutionGroup> groupIt = groups.iterator();
            for ( RemoteRepository repo : repos )
            {
                if ( !repo.getPolicy( artifact.isSnapshot() ).isEnabled() )
                {
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
                    groupIt = Collections.<ResolutionGroup> emptyList().iterator();
                }
                group.items.add( new ResolutionItem( trace, artifact, resolved, result, local, repo ) );
            }
        }

        for ( ResolutionGroup group : groups )
        {
            List<ArtifactDownload> downloads = new ArrayList<ArtifactDownload>();
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

                if ( session.isNotFoundCachingEnabled() || session.isTransferErrorCachingEnabled() )
                {
                    UpdateCheck<Artifact, ArtifactTransferException> check =
                        new UpdateCheck<Artifact, ArtifactTransferException>();
                    check.setItem( artifact );
                    check.setFile( download.getFile() );
                    check.setFileValid( !download.isExistenceCheck() );
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

            if ( downloads.isEmpty() )
            {
                continue;
            }

            for ( ArtifactDownload download : downloads )
            {
                artifactDownloading( session, download.getTrace(), download.getArtifact(), group.repository );
            }

            try
            {
                RepositoryConnector connector =
                    remoteRepositoryManager.getRepositoryConnector( session, group.repository );
                try
                {
                    connector.get( downloads, null );
                }
                finally
                {
                    connector.close();
                }
            }
            catch ( NoRepositoryConnectorException e )
            {
                for ( ArtifactDownload download : downloads )
                {
                    download.setException( new ArtifactTransferException( download.getArtifact(), group.repository, e ) );
                }
            }

            for ( ResolutionItem item : group.items )
            {
                ArtifactDownload download = item.download;
                if ( download == null )
                {
                    continue;
                }

                if ( item.updateCheck != null )
                {
                    item.updateCheck.setException( download.getException() );
                    updateCheckManager.touchArtifact( session, item.updateCheck );
                }

                if ( download.getException() == null )
                {
                    item.resolved.set( true );
                    item.result.setRepository( group.repository );
                    Artifact artifact = download.getArtifact();
                    try
                    {
                        artifact = artifact.setFile( getFile( session, artifact, download.getFile() ) );
                        item.result.setArtifact( artifact );
                    }
                    catch ( ArtifactTransferException e )
                    {
                        item.result.addException( e );
                        continue;
                    }
                    lrm.add( session,
                             new LocalArtifactRegistration( artifact, group.repository, download.getSupportedContexts() ) );

                    artifactDownloaded( session, download.getTrace(), artifact, group.repository, null );

                    artifactResolved( session, download.getTrace(), artifact, group.repository, null );
                }
                else
                {
                    item.result.addException( download.getException() );

                    artifactDownloaded( session, download.getTrace(), download.getArtifact(), group.repository,
                                        download.getException() );
                }
            }
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
                RequestTrace trace = DefaultRequestTrace.newChild( request.getTrace(), request );
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
            && ConfigUtils.getBoolean( session, true, "aether.artifactResolver.snapshotNormalization" ) )
        {
            String name = file.getName().replace( artifact.getVersion(), artifact.getBaseVersion() );
            File dst = new File( file.getParent(), name );

            boolean copy = dst.length() != file.length() || dst.lastModified() != file.lastModified();
            if ( copy )
            {
                try
                {
                    fileProcessor.copy( file, dst, null );
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

    private void artifactResolving( RepositorySystemSession session, RequestTrace trace, Artifact artifact )
    {
        DefaultRepositoryEvent event = new DefaultRepositoryEvent( EventType.ARTIFACT_RESOLVING, session, trace );
        event.setArtifact( artifact );

        repositoryEventDispatcher.dispatch( event );
    }

    private void artifactResolved( RepositorySystemSession session, RequestTrace trace, Artifact artifact,
                                   ArtifactRepository repository, List<Exception> exceptions )
    {
        DefaultRepositoryEvent event = new DefaultRepositoryEvent( EventType.ARTIFACT_RESOLVED, session, trace );
        event.setArtifact( artifact );
        event.setRepository( repository );
        event.setExceptions( exceptions );
        if ( artifact != null )
        {
            event.setFile( artifact.getFile() );
        }

        repositoryEventDispatcher.dispatch( event );
    }

    private void artifactDownloading( RepositorySystemSession session, RequestTrace trace, Artifact artifact,
                                      RemoteRepository repository )
    {
        DefaultRepositoryEvent event = new DefaultRepositoryEvent( EventType.ARTIFACT_DOWNLOADING, session, trace );
        event.setArtifact( artifact );
        event.setRepository( repository );

        repositoryEventDispatcher.dispatch( event );
    }

    private void artifactDownloaded( RepositorySystemSession session, RequestTrace trace, Artifact artifact,
                                     RemoteRepository repository, Exception exception )
    {
        DefaultRepositoryEvent event = new DefaultRepositoryEvent( EventType.ARTIFACT_DOWNLOADED, session, trace );
        event.setArtifact( artifact );
        event.setRepository( repository );
        event.setException( exception );
        if ( artifact != null )
        {
            event.setFile( artifact.getFile() );
        }

        repositoryEventDispatcher.dispatch( event );
    }

    static class ResolutionGroup
    {

        final RemoteRepository repository;

        final List<ResolutionItem> items = new ArrayList<ResolutionItem>();

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
