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

import static java.util.Objects.requireNonNull;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

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
import org.eclipse.aether.spi.synccontext.SyncContextFactory;
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
import org.eclipse.aether.transform.FileTransformer;
import org.eclipse.aether.transform.InstallRequestTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 */
@Singleton
@Named
public class DefaultInstaller
    implements Installer, Service
{

    private static final Logger LOGGER = LoggerFactory.getLogger( DefaultInstaller.class );

    private FileProcessor fileProcessor;

    private RepositoryEventDispatcher repositoryEventDispatcher;

    private Collection<MetadataGeneratorFactory> metadataFactories = new ArrayList<>();

    private SyncContextFactory syncContextFactory;

    public DefaultInstaller()
    {
        // enables default constructor
    }

    @Inject
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
        setFileProcessor( locator.getService( FileProcessor.class ) );
        setRepositoryEventDispatcher( locator.getService( RepositoryEventDispatcher.class ) );
        setMetadataGeneratorFactories( locator.getServices( MetadataGeneratorFactory.class ) );
        setSyncContextFactory( locator.getService( SyncContextFactory.class ) );
    }

    public DefaultInstaller setFileProcessor( FileProcessor fileProcessor )
    {
        this.fileProcessor = requireNonNull( fileProcessor, "file processor cannot be null" );
        return this;
    }

    public DefaultInstaller setRepositoryEventDispatcher( RepositoryEventDispatcher repositoryEventDispatcher )
    {
        this.repositoryEventDispatcher = requireNonNull( repositoryEventDispatcher,
                "repository event dispatcher cannot be null" );
        return this;
    }

    public DefaultInstaller addMetadataGeneratorFactory( MetadataGeneratorFactory factory )
    {
        metadataFactories.add( requireNonNull( factory, "metadata generator factory cannot be null" ) );
        return this;
    }

    public DefaultInstaller setMetadataGeneratorFactories( Collection<MetadataGeneratorFactory> metadataFactories )
    {
        if ( metadataFactories == null )
        {
            this.metadataFactories = new ArrayList<>();
        }
        else
        {
            this.metadataFactories = metadataFactories;
        }
        return this;
    }

    public DefaultInstaller setSyncContextFactory( SyncContextFactory syncContextFactory )
    {
        this.syncContextFactory = requireNonNull( syncContextFactory, "sync context factory cannot be null" );
        return this;
    }

    public InstallResult install( RepositorySystemSession session, InstallRequest request )
        throws InstallationException
    {
        requireNonNull( session, "session cannot be null" );
        requireNonNull( request, "request cannot be null" );
        try ( SyncContext syncContext = syncContextFactory.newInstance( session, false ) )
        {
            return install( syncContext, session, request );
        }
    }

    private InstallResult install( SyncContext syncContext, RepositorySystemSession session,
                                   InstallRequest origRequest )
        throws InstallationException
    {
        InstallRequest request = origRequest;
        InstallRequestTransformer transformer =
                (InstallRequestTransformer) session.getData().get( InstallRequestTransformer.KEY );
        if ( transformer != null )
        {
            request = requireNonNull( transformer.transformInstallRequest( session, request ) );
        }

        InstallResult result = new InstallResult( request );

        RequestTrace trace = RequestTrace.newChild( request.getTrace(), request );

        List<? extends MetadataGenerator> generators = getMetadataGenerators( session, request );

        List<Artifact> artifacts = new ArrayList<>( request.getArtifacts() );

        IdentityHashMap<Metadata, Object> processedMetadata = new IdentityHashMap<>();

        List<Metadata> metadatas = Utils.prepareMetadata( generators, artifacts );

        syncContext.acquire( artifacts, Utils.combine( request.getMetadata(), metadatas ) );

        for ( Metadata metadata : metadatas )
        {
            install( session, trace, metadata );
            processedMetadata.put( metadata, null );
            result.addMetadata( metadata );
        }

        for ( ListIterator<Artifact> iterator = artifacts.listIterator(); iterator.hasNext(); )
        {
            Artifact artifact = iterator.next();

            for ( MetadataGenerator generator : generators )
            {
                artifact = generator.transformArtifact( artifact );
            }

            iterator.set( artifact );

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

        List<MetadataGenerator> generators = new ArrayList<>();

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

        Collection<FileTransformer> fileTransformers = session.getFileTransformerManager()
                .getTransformersForArtifact( artifact );
        if ( fileTransformers.isEmpty() )
        {
            install( session, trace, artifact, lrm, srcFile, null );
        }
        else
        {
            for ( FileTransformer fileTransformer : fileTransformers )
            {
                install( session, trace, artifact, lrm, srcFile, fileTransformer );
            }
        }
    }

    private void install( RepositorySystemSession session, RequestTrace trace, Artifact artifact,
                          LocalRepositoryManager lrm, File srcFile, FileTransformer fileTransformer )
        throws InstallationException
    {
        final Artifact targetArtifact;
        if ( fileTransformer != null )
        {
            targetArtifact = fileTransformer.transformArtifact( artifact );
        }
        else
        {
            targetArtifact = artifact;
        }

        File dstFile = new File( lrm.getRepository().getBasedir(), lrm.getPathForLocalArtifact( targetArtifact ) );

        artifactInstalling( session, trace, targetArtifact, dstFile );

        Exception exception = null;
        try
        {
            if ( dstFile.equals( srcFile ) )
            {
                throw new IllegalStateException( "cannot install " + dstFile + " to same path" );
            }

            boolean copy =
                "pom".equals( targetArtifact.getExtension() ) || srcFile.lastModified() != dstFile.lastModified()
                    || srcFile.length() != dstFile.length() || !srcFile.exists();

            if ( !copy )
            {
                LOGGER.debug( "Skipped re-installing {} to {}, seems unchanged", srcFile, dstFile );
            }
            else if ( fileTransformer != null ) 
            {
                try ( InputStream is = fileTransformer.transformData( srcFile ) )
                {
                    fileProcessor.write( dstFile, is );
                    dstFile.setLastModified( srcFile.lastModified() );
                }
            }
            else
            {
                fileProcessor.copy( srcFile, dstFile );
                dstFile.setLastModified( srcFile.lastModified() );
            }

            lrm.add( session, new LocalArtifactRegistration( targetArtifact ) );
        }
        catch ( Exception e )
        {
            exception = e;
            throw new InstallationException( "Failed to install artifact " + targetArtifact + ": " + e.getMessage(),
                    e );
        }
        finally
        {
            artifactInstalled( session, trace, targetArtifact, dstFile, exception );
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
