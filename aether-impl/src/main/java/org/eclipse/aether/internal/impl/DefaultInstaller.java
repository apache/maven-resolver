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
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.RepositoryEvent.EventType;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.impl.Installer;
import org.eclipse.aether.impl.MetadataGenerator;
import org.eclipse.aether.impl.MetadataGeneratorFactory;
import org.eclipse.aether.impl.RepositoryEventDispatcher;
import org.eclipse.aether.impl.SyncContextFactory;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallResult;
import org.eclipse.aether.installation.InstallationException;
import org.eclipse.aether.metadata.MergeableMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.LocalArtifactRegistration;
import org.eclipse.aether.repository.LocalMetadataRegistration;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.spi.io.FileProcessor;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.spi.log.LoggerFactory;
import org.eclipse.aether.spi.log.NullLoggerFactory;
import org.eclipse.aether.util.DefaultRequestTrace;
import org.eclipse.aether.util.listener.DefaultRepositoryEvent;

/**
 */
@Component( role = Installer.class )
public class DefaultInstaller
    implements Installer, Service
{

    @Requirement( role = LoggerFactory.class )
    private Logger logger = NullLoggerFactory.LOGGER;

    @Requirement
    private FileProcessor fileProcessor;

    @Requirement
    private RepositoryEventDispatcher repositoryEventDispatcher;

    @Requirement( role = MetadataGeneratorFactory.class )
    private Collection<MetadataGeneratorFactory> metadataFactories = new ArrayList<MetadataGeneratorFactory>();

    @Requirement
    private SyncContextFactory syncContextFactory;

    public DefaultInstaller()
    {
        // enables default constructor
    }

    DefaultInstaller( FileProcessor fileProcessor, RepositoryEventDispatcher repositoryEventDispatcher,
                      Set<MetadataGeneratorFactory> metadataFactories, SyncContextFactory syncContextFactory )
    {
        setFileProcessor( fileProcessor );
        setRepositoryEventDispatcher( repositoryEventDispatcher );
        setMetadataGeneratorFactories( metadataFactories );
        setSyncContextFactory( syncContextFactory );
    }

    public void initService( ServiceLocator locator )
    {
        setLoggerFactory( locator.getService( LoggerFactory.class ) );
        setFileProcessor( locator.getService( FileProcessor.class ) );
        setRepositoryEventDispatcher( locator.getService( RepositoryEventDispatcher.class ) );
        setMetadataGeneratorFactories( locator.getServices( MetadataGeneratorFactory.class ) );
        setSyncContextFactory( locator.getService( SyncContextFactory.class ) );
    }

    public DefaultInstaller setLoggerFactory( LoggerFactory loggerFactory )
    {
        this.logger = NullLoggerFactory.getSafeLogger( loggerFactory, getClass() );
        return this;
    }

    void setLogger( LoggerFactory loggerFactory )
    {
        // plexus support
        setLoggerFactory( loggerFactory );
    }

    public DefaultInstaller setFileProcessor( FileProcessor fileProcessor )
    {
        if ( fileProcessor == null )
        {
            throw new IllegalArgumentException( "file processor has not been specified" );
        }
        this.fileProcessor = fileProcessor;
        return this;
    }

    public DefaultInstaller setRepositoryEventDispatcher( RepositoryEventDispatcher repositoryEventDispatcher )
    {
        if ( repositoryEventDispatcher == null )
        {
            throw new IllegalArgumentException( "repository event dispatcher has not been specified" );
        }
        this.repositoryEventDispatcher = repositoryEventDispatcher;
        return this;
    }

    public DefaultInstaller addMetadataGeneratorFactory( MetadataGeneratorFactory factory )
    {
        if ( factory == null )
        {
            throw new IllegalArgumentException( "metadata generator factory has not been specified" );
        }
        metadataFactories.add( factory );
        return this;
    }

    public DefaultInstaller setMetadataGeneratorFactories( Collection<MetadataGeneratorFactory> metadataFactories )
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

    DefaultInstaller setMetadataFactories( List<MetadataGeneratorFactory> metadataFactories )
    {
        // plexus support
        return setMetadataGeneratorFactories( metadataFactories );
    }

    public DefaultInstaller setSyncContextFactory( SyncContextFactory syncContextFactory )
    {
        if ( syncContextFactory == null )
        {
            throw new IllegalArgumentException( "sync context factory has not been specified" );
        }
        this.syncContextFactory = syncContextFactory;
        return this;
    }

    public InstallResult install( RepositorySystemSession session, InstallRequest request )
        throws InstallationException
    {
        SyncContext syncContext = syncContextFactory.newInstance( session, false );

        try
        {
            return install( syncContext, session, request );
        }
        finally
        {
            syncContext.close();
        }
    }

    private InstallResult install( SyncContext syncContext, RepositorySystemSession session, InstallRequest request )
        throws InstallationException
    {
        InstallResult result = new InstallResult( request );

        RequestTrace trace = DefaultRequestTrace.newChild( request.getTrace(), request );

        List<MetadataGenerator> generators = getMetadataGenerators( session, request );

        List<Artifact> artifacts = new ArrayList<Artifact>( request.getArtifacts() );

        IdentityHashMap<Metadata, Object> processedMetadata = new IdentityHashMap<Metadata, Object>();

        List<Metadata> metadatas = Utils.prepareMetadata( generators, artifacts );

        syncContext.acquire( artifacts, Utils.combine( request.getMetadata(), metadatas ) );

        for ( Metadata metadata : metadatas )
        {
            install( session, trace, metadata );
            processedMetadata.put( metadata, null );
            result.addMetadata( metadata );
        }

        for ( int i = 0; i < artifacts.size(); i++ )
        {
            Artifact artifact = artifacts.get( i );

            for ( MetadataGenerator generator : generators )
            {
                artifact = generator.transformArtifact( artifact );
            }

            artifacts.set( i, artifact );

            install( session, trace, artifact );
            result.addArtifact( artifact );
        }

        metadatas = Utils.finishMetadata( generators, artifacts );

        syncContext.acquire( null, metadatas );

        for ( Metadata metadata : metadatas )
        {
            install( session, trace, metadata );
            processedMetadata.put( metadata, null );
            result.addMetadata( metadata );
        }

        for ( Metadata metadata : request.getMetadata() )
        {
            if ( !processedMetadata.containsKey( metadata ) )
            {
                install( session, trace, metadata );
                result.addMetadata( metadata );
            }
        }

        return result;
    }

    private List<MetadataGenerator> getMetadataGenerators( RepositorySystemSession session, InstallRequest request )
    {
        List<MetadataGeneratorFactory> factories = Utils.sortMetadataGeneratorFactories( this.metadataFactories );

        List<MetadataGenerator> generators = new ArrayList<MetadataGenerator>();

        for ( MetadataGeneratorFactory factory : factories )
        {
            MetadataGenerator generator = factory.newInstance( session, request );
            if ( generator != null )
            {
                generators.add( generator );
            }
        }

        return generators;
    }

    private void install( RepositorySystemSession session, RequestTrace trace, Artifact artifact )
        throws InstallationException
    {
        LocalRepositoryManager lrm = session.getLocalRepositoryManager();

        File srcFile = artifact.getFile();

        File dstFile = new File( lrm.getRepository().getBasedir(), lrm.getPathForLocalArtifact( artifact ) );

        artifactInstalling( session, trace, artifact, dstFile );

        Exception exception = null;
        try
        {
            boolean copy =
                "pom".equals( artifact.getExtension() ) || srcFile.lastModified() != dstFile.lastModified()
                    || srcFile.length() != dstFile.length();

            if ( copy )
            {
                fileProcessor.copy( srcFile, dstFile );
                dstFile.setLastModified( srcFile.lastModified() );
            }
            else
            {
                logger.debug( "Skipped re-installing " + srcFile + " to " + dstFile + ", seems unchanged" );
            }

            lrm.add( session, new LocalArtifactRegistration( artifact ) );
        }
        catch ( Exception e )
        {
            exception = e;
            throw new InstallationException( "Failed to install artifact " + artifact + ": " + e.getMessage(), e );
        }
        finally
        {
            artifactInstalled( session, trace, artifact, dstFile, exception );
        }
    }

    private void install( RepositorySystemSession session, RequestTrace trace, Metadata metadata )
        throws InstallationException
    {
        LocalRepositoryManager lrm = session.getLocalRepositoryManager();

        File dstFile = new File( lrm.getRepository().getBasedir(), lrm.getPathForLocalMetadata( metadata ) );

        metadataInstalling( session, trace, metadata, dstFile );

        Exception exception = null;
        try
        {
            if ( metadata instanceof MergeableMetadata )
            {
                ( (MergeableMetadata) metadata ).merge( dstFile, dstFile );
            }
            else
            {
                fileProcessor.copy( metadata.getFile(), dstFile );
            }

            lrm.add( session, new LocalMetadataRegistration( metadata ) );
        }
        catch ( Exception e )
        {
            exception = e;
            throw new InstallationException( "Failed to install metadata " + metadata + ": " + e.getMessage(), e );
        }
        finally
        {
            metadataInstalled( session, trace, metadata, dstFile, exception );
        }
    }

    private void artifactInstalling( RepositorySystemSession session, RequestTrace trace, Artifact artifact,
                                     File dstFile )
    {
        DefaultRepositoryEvent event = new DefaultRepositoryEvent( EventType.ARTIFACT_INSTALLING, session, trace );
        event.setArtifact( artifact );
        event.setRepository( session.getLocalRepositoryManager().getRepository() );
        event.setFile( dstFile );

        repositoryEventDispatcher.dispatch( event );
    }

    private void artifactInstalled( RepositorySystemSession session, RequestTrace trace, Artifact artifact,
                                    File dstFile, Exception exception )
    {
        DefaultRepositoryEvent event = new DefaultRepositoryEvent( EventType.ARTIFACT_INSTALLED, session, trace );
        event.setArtifact( artifact );
        event.setRepository( session.getLocalRepositoryManager().getRepository() );
        event.setFile( dstFile );
        event.setException( exception );

        repositoryEventDispatcher.dispatch( event );
    }

    private void metadataInstalling( RepositorySystemSession session, RequestTrace trace, Metadata metadata,
                                     File dstFile )
    {
        DefaultRepositoryEvent event = new DefaultRepositoryEvent( EventType.METADATA_INSTALLING, session, trace );
        event.setMetadata( metadata );
        event.setRepository( session.getLocalRepositoryManager().getRepository() );
        event.setFile( dstFile );

        repositoryEventDispatcher.dispatch( event );
    }

    private void metadataInstalled( RepositorySystemSession session, RequestTrace trace, Metadata metadata,
                                    File dstFile, Exception exception )
    {
        DefaultRepositoryEvent event = new DefaultRepositoryEvent( EventType.METADATA_INSTALLED, session, trace );
        event.setMetadata( metadata );
        event.setRepository( session.getLocalRepositoryManager().getRepository() );
        event.setFile( dstFile );
        event.setException( exception );

        repositoryEventDispatcher.dispatch( event );
    }

}
