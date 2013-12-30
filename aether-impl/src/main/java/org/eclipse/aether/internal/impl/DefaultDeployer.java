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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.RepositoryEvent.EventType;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeployResult;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.impl.Deployer;
import org.eclipse.aether.impl.MetadataGenerator;
import org.eclipse.aether.impl.MetadataGeneratorFactory;
import org.eclipse.aether.impl.OfflineController;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.impl.RepositoryConnectorProvider;
import org.eclipse.aether.impl.RepositoryEventDispatcher;
import org.eclipse.aether.impl.SyncContextFactory;
import org.eclipse.aether.impl.UpdateCheck;
import org.eclipse.aether.impl.UpdateCheckManager;
import org.eclipse.aether.metadata.MergeableMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.spi.connector.ArtifactUpload;
import org.eclipse.aether.spi.connector.MetadataDownload;
import org.eclipse.aether.spi.connector.MetadataUpload;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.io.FileProcessor;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.spi.log.LoggerFactory;
import org.eclipse.aether.spi.log.NullLoggerFactory;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.transfer.MetadataNotFoundException;
import org.eclipse.aether.transfer.MetadataTransferException;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;
import org.eclipse.aether.transfer.RepositoryOfflineException;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferEvent;

/**
 */
@Named
@Component( role = Deployer.class )
public class DefaultDeployer
    implements Deployer, Service
{

    @Requirement( role = LoggerFactory.class )
    private Logger logger = NullLoggerFactory.LOGGER;

    @Requirement
    private FileProcessor fileProcessor;

    @Requirement
    private RepositoryEventDispatcher repositoryEventDispatcher;

    @Requirement
    private RepositoryConnectorProvider repositoryConnectorProvider;

    @Requirement
    private RemoteRepositoryManager remoteRepositoryManager;

    @Requirement
    private UpdateCheckManager updateCheckManager;

    @Requirement( role = MetadataGeneratorFactory.class )
    private Collection<MetadataGeneratorFactory> metadataFactories = new ArrayList<MetadataGeneratorFactory>();

    @Requirement
    private SyncContextFactory syncContextFactory;

    @Requirement
    private OfflineController offlineController;

    public DefaultDeployer()
    {
        // enables default constructor
    }

    @Inject
    DefaultDeployer( FileProcessor fileProcessor, RepositoryEventDispatcher repositoryEventDispatcher,
                     RepositoryConnectorProvider repositoryConnectorProvider,
                     RemoteRepositoryManager remoteRepositoryManager, UpdateCheckManager updateCheckManager,
                     Set<MetadataGeneratorFactory> metadataFactories, SyncContextFactory syncContextFactory,
                     OfflineController offlineController, LoggerFactory loggerFactory )
    {
        setFileProcessor( fileProcessor );
        setRepositoryEventDispatcher( repositoryEventDispatcher );
        setRepositoryConnectorProvider( repositoryConnectorProvider );
        setRemoteRepositoryManager( remoteRepositoryManager );
        setUpdateCheckManager( updateCheckManager );
        setMetadataGeneratorFactories( metadataFactories );
        setSyncContextFactory( syncContextFactory );
        setLoggerFactory( loggerFactory );
        setOfflineController( offlineController );
    }

    public void initService( ServiceLocator locator )
    {
        setLoggerFactory( locator.getService( LoggerFactory.class ) );
        setFileProcessor( locator.getService( FileProcessor.class ) );
        setRepositoryEventDispatcher( locator.getService( RepositoryEventDispatcher.class ) );
        setRepositoryConnectorProvider( locator.getService( RepositoryConnectorProvider.class ) );
        setRemoteRepositoryManager( locator.getService( RemoteRepositoryManager.class ) );
        setUpdateCheckManager( locator.getService( UpdateCheckManager.class ) );
        setMetadataGeneratorFactories( locator.getServices( MetadataGeneratorFactory.class ) );
        setSyncContextFactory( locator.getService( SyncContextFactory.class ) );
        setOfflineController( locator.getService( OfflineController.class ) );
    }

    public DefaultDeployer setLoggerFactory( LoggerFactory loggerFactory )
    {
        this.logger = NullLoggerFactory.getSafeLogger( loggerFactory, getClass() );
        return this;
    }

    void setLogger( LoggerFactory loggerFactory )
    {
        // plexus support
        setLoggerFactory( loggerFactory );
    }

    public DefaultDeployer setFileProcessor( FileProcessor fileProcessor )
    {
        if ( fileProcessor == null )
        {
            throw new IllegalArgumentException( "file processor has not been specified" );
        }
        this.fileProcessor = fileProcessor;
        return this;
    }

    public DefaultDeployer setRepositoryEventDispatcher( RepositoryEventDispatcher repositoryEventDispatcher )
    {
        if ( repositoryEventDispatcher == null )
        {
            throw new IllegalArgumentException( "repository event dispatcher has not been specified" );
        }
        this.repositoryEventDispatcher = repositoryEventDispatcher;
        return this;
    }

    public DefaultDeployer setRepositoryConnectorProvider( RepositoryConnectorProvider repositoryConnectorProvider )
    {
        if ( repositoryConnectorProvider == null )
        {
            throw new IllegalArgumentException( "repository connector provider has not been specified" );
        }
        this.repositoryConnectorProvider = repositoryConnectorProvider;
        return this;
    }

    public DefaultDeployer setRemoteRepositoryManager( RemoteRepositoryManager remoteRepositoryManager )
    {
        if ( remoteRepositoryManager == null )
        {
            throw new IllegalArgumentException( "remote repository manager has not been specified" );
        }
        this.remoteRepositoryManager = remoteRepositoryManager;
        return this;
    }

    public DefaultDeployer setUpdateCheckManager( UpdateCheckManager updateCheckManager )
    {
        if ( updateCheckManager == null )
        {
            throw new IllegalArgumentException( "update check manager has not been specified" );
        }
        this.updateCheckManager = updateCheckManager;
        return this;
    }

    public DefaultDeployer addMetadataGeneratorFactory( MetadataGeneratorFactory factory )
    {
        if ( factory == null )
        {
            throw new IllegalArgumentException( "metadata generator factory has not been specified" );
        }
        metadataFactories.add( factory );
        return this;
    }

    public DefaultDeployer setMetadataGeneratorFactories( Collection<MetadataGeneratorFactory> metadataFactories )
    {
        if ( metadataFactories == null )
        {
            this.metadataFactories = new ArrayList<MetadataGeneratorFactory>();
        }
        else
        {
            this.metadataFactories = metadataFactories;
        }
        return this;
    }

    DefaultDeployer setMetadataFactories( List<MetadataGeneratorFactory> metadataFactories )
    {
        // plexus support
        return setMetadataGeneratorFactories( metadataFactories );
    }

    public DefaultDeployer setSyncContextFactory( SyncContextFactory syncContextFactory )
    {
        if ( syncContextFactory == null )
        {
            throw new IllegalArgumentException( "sync context factory has not been specified" );
        }
        this.syncContextFactory = syncContextFactory;
        return this;
    }

    public DefaultDeployer setOfflineController( OfflineController offlineController )
    {
        if ( offlineController == null )
        {
            throw new IllegalArgumentException( "offline controller has not been specified" );
        }
        this.offlineController = offlineController;
        return this;
    }

    public DeployResult deploy( RepositorySystemSession session, DeployRequest request )
        throws DeploymentException
    {
        try
        {
            offlineController.checkOffline( session, request.getRepository() );
        }
        catch ( RepositoryOfflineException e )
        {
            throw new DeploymentException( "Cannot deploy while " + request.getRepository().getId() + " ("
                + request.getRepository().getUrl() + ") is in offline mode", e );
        }

        SyncContext syncContext = syncContextFactory.newInstance( session, false );

        try
        {
            return deploy( syncContext, session, request );
        }
        finally
        {
            syncContext.close();
        }
    }

    private DeployResult deploy( SyncContext syncContext, RepositorySystemSession session, DeployRequest request )
        throws DeploymentException
    {
        DeployResult result = new DeployResult( request );

        RequestTrace trace = RequestTrace.newChild( request.getTrace(), request );

        RemoteRepository repository = request.getRepository();

        RepositoryConnector connector;
        try
        {
            connector = repositoryConnectorProvider.newRepositoryConnector( session, repository );
        }
        catch ( NoRepositoryConnectorException e )
        {
            throw new DeploymentException( "Failed to deploy artifacts/metadata: " + e.getMessage(), e );
        }

        try
        {
            List<? extends MetadataGenerator> generators = getMetadataGenerators( session, request );

            List<ArtifactUpload> artifactUploads = new ArrayList<ArtifactUpload>();
            List<MetadataUpload> metadataUploads = new ArrayList<MetadataUpload>();
            IdentityHashMap<Metadata, Object> processedMetadata = new IdentityHashMap<Metadata, Object>();

            EventCatapult catapult = new EventCatapult( session, trace, repository, repositoryEventDispatcher );

            List<Artifact> artifacts = new ArrayList<Artifact>( request.getArtifacts() );

            List<Metadata> metadatas = Utils.prepareMetadata( generators, artifacts );

            syncContext.acquire( artifacts, Utils.combine( request.getMetadata(), metadatas ) );

            for ( Metadata metadata : metadatas )
            {
                upload( metadataUploads, session, metadata, repository, connector, catapult );
                processedMetadata.put( metadata, null );
            }

            for ( int i = 0; i < artifacts.size(); i++ )
            {
                Artifact artifact = artifacts.get( i );

                for ( MetadataGenerator generator : generators )
                {
                    artifact = generator.transformArtifact( artifact );
                }

                artifacts.set( i, artifact );

                ArtifactUpload upload = new ArtifactUpload( artifact, artifact.getFile() );
                upload.setTrace( trace );
                upload.setListener( new ArtifactUploadListener( catapult, upload, logger ) );
                artifactUploads.add( upload );
            }

            connector.put( artifactUploads, null );

            for ( ArtifactUpload upload : artifactUploads )
            {
                if ( upload.getException() != null )
                {
                    throw new DeploymentException( "Failed to deploy artifacts: " + upload.getException().getMessage(),
                                                   upload.getException() );
                }
                result.addArtifact( upload.getArtifact() );
            }

            metadatas = Utils.finishMetadata( generators, artifacts );

            syncContext.acquire( null, metadatas );

            for ( Metadata metadata : metadatas )
            {
                upload( metadataUploads, session, metadata, repository, connector, catapult );
                processedMetadata.put( metadata, null );
            }

            for ( Metadata metadata : request.getMetadata() )
            {
                if ( !processedMetadata.containsKey( metadata ) )
                {
                    upload( metadataUploads, session, metadata, repository, connector, catapult );
                    processedMetadata.put( metadata, null );
                }
            }

            connector.put( null, metadataUploads );

            for ( MetadataUpload upload : metadataUploads )
            {
                if ( upload.getException() != null )
                {
                    throw new DeploymentException( "Failed to deploy metadata: " + upload.getException().getMessage(),
                                                   upload.getException() );
                }
                result.addMetadata( upload.getMetadata() );
            }
        }
        finally
        {
            connector.close();
        }

        return result;
    }

    private List<? extends MetadataGenerator> getMetadataGenerators( RepositorySystemSession session,
                                                                     DeployRequest request )
    {
        PrioritizedComponents<MetadataGeneratorFactory> factories =
            Utils.sortMetadataGeneratorFactories( session, this.metadataFactories );

        List<MetadataGenerator> generators = new ArrayList<MetadataGenerator>();

        for ( PrioritizedComponent<MetadataGeneratorFactory> factory : factories.getEnabled() )
        {
            MetadataGenerator generator = factory.getComponent().newInstance( session, request );
            if ( generator != null )
            {
                generators.add( generator );
            }
        }

        return generators;
    }

    private void upload( Collection<MetadataUpload> metadataUploads, RepositorySystemSession session,
                         Metadata metadata, RemoteRepository repository, RepositoryConnector connector,
                         EventCatapult catapult )
        throws DeploymentException
    {
        LocalRepositoryManager lrm = session.getLocalRepositoryManager();
        File basedir = lrm.getRepository().getBasedir();

        File dstFile = new File( basedir, lrm.getPathForRemoteMetadata( metadata, repository, "" ) );

        if ( metadata instanceof MergeableMetadata )
        {
            if ( !( (MergeableMetadata) metadata ).isMerged() )
            {
                {
                    RepositoryEvent.Builder event = new RepositoryEvent.Builder( session, EventType.METADATA_RESOLVING );
                    event.setTrace( catapult.getTrace() );
                    event.setMetadata( metadata );
                    event.setRepository( repository );
                    repositoryEventDispatcher.dispatch( event.build() );

                    event = new RepositoryEvent.Builder( session, EventType.METADATA_DOWNLOADING );
                    event.setTrace( catapult.getTrace() );
                    event.setMetadata( metadata );
                    event.setRepository( repository );
                    repositoryEventDispatcher.dispatch( event.build() );
                }

                RepositoryPolicy policy = getPolicy( session, repository, metadata.getNature() );
                MetadataDownload download = new MetadataDownload();
                download.setMetadata( metadata );
                download.setFile( dstFile );
                download.setChecksumPolicy( policy.getChecksumPolicy() );
                download.setListener( SafeTransferListener.wrap( session, logger ) );
                download.setTrace( catapult.getTrace() );
                connector.get( null, Arrays.asList( download ) );

                Exception error = download.getException();

                if ( error instanceof MetadataNotFoundException )
                {
                    dstFile.delete();
                }

                {
                    RepositoryEvent.Builder event =
                        new RepositoryEvent.Builder( session, EventType.METADATA_DOWNLOADED );
                    event.setTrace( catapult.getTrace() );
                    event.setMetadata( metadata );
                    event.setRepository( repository );
                    event.setException( error );
                    event.setFile( dstFile );
                    repositoryEventDispatcher.dispatch( event.build() );

                    event = new RepositoryEvent.Builder( session, EventType.METADATA_RESOLVED );
                    event.setTrace( catapult.getTrace() );
                    event.setMetadata( metadata );
                    event.setRepository( repository );
                    event.setException( error );
                    event.setFile( dstFile );
                    repositoryEventDispatcher.dispatch( event.build() );
                }

                if ( error != null && !( error instanceof MetadataNotFoundException ) )
                {
                    throw new DeploymentException( "Failed to retrieve remote metadata " + metadata + ": "
                        + error.getMessage(), error );
                }
            }

            try
            {
                ( (MergeableMetadata) metadata ).merge( dstFile, dstFile );
            }
            catch ( RepositoryException e )
            {
                throw new DeploymentException( "Failed to update metadata " + metadata + ": " + e.getMessage(), e );
            }
        }
        else
        {
            if ( metadata.getFile() == null )
            {
                throw new DeploymentException( "Failed to update metadata " + metadata + ": No file attached." );
            }
            try
            {
                fileProcessor.copy( metadata.getFile(), dstFile );
            }
            catch ( IOException e )
            {
                throw new DeploymentException( "Failed to update metadata " + metadata + ": " + e.getMessage(), e );
            }
        }

        UpdateCheck<Metadata, MetadataTransferException> check = new UpdateCheck<Metadata, MetadataTransferException>();
        check.setItem( metadata );
        check.setFile( dstFile );
        check.setRepository( repository );
        check.setAuthoritativeRepository( repository );
        updateCheckManager.touchMetadata( session, check );

        MetadataUpload upload = new MetadataUpload( metadata, dstFile );
        upload.setTrace( catapult.getTrace() );
        upload.setListener( new MetadataUploadListener( catapult, upload, logger ) );
        metadataUploads.add( upload );
    }

    private RepositoryPolicy getPolicy( RepositorySystemSession session, RemoteRepository repository,
                                        Metadata.Nature nature )
    {
        boolean releases = !Metadata.Nature.SNAPSHOT.equals( nature );
        boolean snapshots = !Metadata.Nature.RELEASE.equals( nature );
        return remoteRepositoryManager.getPolicy( session, repository, releases, snapshots );
    }

    static final class EventCatapult
    {

        private final RepositorySystemSession session;

        private final RequestTrace trace;

        private final RemoteRepository repository;

        private final RepositoryEventDispatcher dispatcher;

        public EventCatapult( RepositorySystemSession session, RequestTrace trace, RemoteRepository repository,
                              RepositoryEventDispatcher dispatcher )
        {
            this.session = session;
            this.trace = trace;
            this.repository = repository;
            this.dispatcher = dispatcher;
        }

        public RepositorySystemSession getSession()
        {
            return session;
        }

        public RequestTrace getTrace()
        {
            return trace;
        }

        public void artifactDeploying( Artifact artifact, File file )
        {
            RepositoryEvent.Builder event = new RepositoryEvent.Builder( session, EventType.ARTIFACT_DEPLOYING );
            event.setTrace( trace );
            event.setArtifact( artifact );
            event.setRepository( repository );
            event.setFile( file );

            dispatcher.dispatch( event.build() );
        }

        public void artifactDeployed( Artifact artifact, File file, ArtifactTransferException exception )
        {
            RepositoryEvent.Builder event = new RepositoryEvent.Builder( session, EventType.ARTIFACT_DEPLOYED );
            event.setTrace( trace );
            event.setArtifact( artifact );
            event.setRepository( repository );
            event.setFile( file );
            event.setException( exception );

            dispatcher.dispatch( event.build() );
        }

        public void metadataDeploying( Metadata metadata, File file )
        {
            RepositoryEvent.Builder event = new RepositoryEvent.Builder( session, EventType.METADATA_DEPLOYING );
            event.setTrace( trace );
            event.setMetadata( metadata );
            event.setRepository( repository );
            event.setFile( file );

            dispatcher.dispatch( event.build() );
        }

        public void metadataDeployed( Metadata metadata, File file, Exception exception )
        {
            RepositoryEvent.Builder event = new RepositoryEvent.Builder( session, EventType.METADATA_DEPLOYED );
            event.setTrace( trace );
            event.setMetadata( metadata );
            event.setRepository( repository );
            event.setFile( file );
            event.setException( exception );

            dispatcher.dispatch( event.build() );
        }

    }

    static final class ArtifactUploadListener
        extends SafeTransferListener
    {

        private final EventCatapult catapult;

        private final ArtifactUpload transfer;

        public ArtifactUploadListener( EventCatapult catapult, ArtifactUpload transfer, Logger logger )
        {
            super( catapult.getSession(), logger );
            this.catapult = catapult;
            this.transfer = transfer;
        }

        @Override
        public void transferInitiated( TransferEvent event )
            throws TransferCancelledException
        {
            super.transferInitiated( event );
            catapult.artifactDeploying( transfer.getArtifact(), transfer.getFile() );
        }

        @Override
        public void transferFailed( TransferEvent event )
        {
            super.transferFailed( event );
            catapult.artifactDeployed( transfer.getArtifact(), transfer.getFile(), transfer.getException() );
        }

        @Override
        public void transferSucceeded( TransferEvent event )
        {
            super.transferSucceeded( event );
            catapult.artifactDeployed( transfer.getArtifact(), transfer.getFile(), null );
        }

    }

    static final class MetadataUploadListener
        extends SafeTransferListener
    {

        private final EventCatapult catapult;

        private final MetadataUpload transfer;

        public MetadataUploadListener( EventCatapult catapult, MetadataUpload transfer, Logger logger )
        {
            super( catapult.getSession(), logger );
            this.catapult = catapult;
            this.transfer = transfer;
        }

        @Override
        public void transferInitiated( TransferEvent event )
            throws TransferCancelledException
        {
            super.transferInitiated( event );
            catapult.metadataDeploying( transfer.getMetadata(), transfer.getFile() );
        }

        @Override
        public void transferFailed( TransferEvent event )
        {
            super.transferFailed( event );
            catapult.metadataDeployed( transfer.getMetadata(), transfer.getFile(), transfer.getException() );
        }

        @Override
        public void transferSucceeded( TransferEvent event )
        {
            super.transferSucceeded( event );
            catapult.metadataDeployed( transfer.getMetadata(), transfer.getFile(), null );
        }

    }

}
