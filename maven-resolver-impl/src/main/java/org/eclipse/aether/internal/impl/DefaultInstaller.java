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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;

import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositoryEvent.EventType;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.SyncContext;
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

/**
 */
@Named
public class DefaultInstaller
    implements Installer, Service
{

    private Logger logger = NullLoggerFactory.LOGGER;

    private FileProcessor fileProcessor;

    private RepositoryEventDispatcher repositoryEventDispatcher;

    private Collection<MetadataGeneratorFactory> metadataFactories = new ArrayList<MetadataGeneratorFactory>();

    private SyncContextFactory syncContextFactory;

    public DefaultInstaller()
    {
        // enables default constructor
    }

    @Inject
    DefaultInstaller( FileProcessor fileProcessor, RepositoryEventDispatcher repositoryEventDispatcher,
                      Set<MetadataGeneratorFactory> metadataFactories, SyncContextFactory syncContextFactory,
                      LoggerFactory loggerFactory )
    {
        setFileProcessor( fileProcessor );
        setRepositoryEventDispatcher( repositoryEventDispatcher );
        setMetadataGeneratorFactories( metadataFactories );
        setSyncContextFactory( syncContextFactory );
        setLoggerFactory( loggerFactory );
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

    public DefaultInstaller setFileProcessor( FileProcessor fileProcessor )
    {
        this.fileProcessor = Objects.requireNonNull( fileProcessor, "file processor cannot be null" );
        return this;
    }

    public DefaultInstaller setRepositoryEventDispatcher( RepositoryEventDispatcher repositoryEventDispatcher )
    {
        this.repositoryEventDispatcher = Objects.requireNonNull( repositoryEventDispatcher, "repository event dispatcher cannot be null" );
        return this;
    }

    public DefaultInstaller addMetadataGeneratorFactory( MetadataGeneratorFactory factory )
    {
        metadataFactories.add( Objects.requireNonNull( factory, "metadata generator factory cannot be null" ) );
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

    public DefaultInstaller setSyncContextFactory( SyncContextFactory syncContextFactory )
    {
        this.syncContextFactory = Objects.requireNonNull( syncContextFactory, "sync context factory cannot be null" );
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

        RequestTrace trace = RequestTrace.newChild( request.getTrace(), request );

        List<? extends MetadataGenerator> generators = getMetadataGenerators( session, request );

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

    private List<? extends MetadataGenerator> getMetadataGenerators( RepositorySystemSession session,
                                                                     InstallRequest request )
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
            if ( dstFile.equals( srcFile ) )
            {
                throw new IllegalStateException( "cannot install " + dstFile + " to same path" );
            }

            boolean copy =
                "pom".equals( artifact.getExtension() ) || srcFile.lastModified() != dstFile.lastModified()
                    || srcFile.length() != dstFile.length() || !srcFile.exists();

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
                if ( dstFile.equals( metadata.getFile() ) )
                {
                    throw new IllegalStateException( "cannot install " + dstFile + " to same path" );
                }
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
        RepositoryEvent.Builder event = new RepositoryEvent.Builder( session, EventType.ARTIFACT_INSTALLING );
        event.setTrace( trace );
        event.setArtifact( artifact );
        event.setRepository( session.getLocalRepositoryManager().getRepository() );
        event.setFile( dstFile );

        repositoryEventDispatcher.dispatch( event.build() );
    }

    private void artifactInstalled( RepositorySystemSession session, RequestTrace trace, Artifact artifact,
                                    File dstFile, Exception exception )
    {
        RepositoryEvent.Builder event = new RepositoryEvent.Builder( session, EventType.ARTIFACT_INSTALLED );
        event.setTrace( trace );
        event.setArtifact( artifact );
        event.setRepository( session.getLocalRepositoryManager().getRepository() );
        event.setFile( dstFile );
        event.setException( exception );

        repositoryEventDispatcher.dispatch( event.build() );
    }

    private void metadataInstalling( RepositorySystemSession session, RequestTrace trace, Metadata metadata,
                                     File dstFile )
    {
        RepositoryEvent.Builder event = new RepositoryEvent.Builder( session, EventType.METADATA_INSTALLING );
        event.setTrace( trace );
        event.setMetadata( metadata );
        event.setRepository( session.getLocalRepositoryManager().getRepository() );
        event.setFile( dstFile );

        repositoryEventDispatcher.dispatch( event.build() );
    }

    private void metadataInstalled( RepositorySystemSession session, RequestTrace trace, Metadata metadata,
                                    File dstFile, Exception exception )
    {
        RepositoryEvent.Builder event = new RepositoryEvent.Builder( session, EventType.METADATA_INSTALLED );
        event.setTrace( trace );
        event.setMetadata( metadata );
        event.setRepository( session.getLocalRepositoryManager().getRepository() );
        event.setFile( dstFile );
        event.setException( exception );

        repositoryEventDispatcher.dispatch( event.build() );
    }

}
