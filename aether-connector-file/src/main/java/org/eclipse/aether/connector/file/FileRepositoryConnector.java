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
package org.eclipse.aether.connector.file;

import java.util.Collection;
import java.util.Collections;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.ArtifactUpload;
import org.eclipse.aether.spi.connector.MetadataDownload;
import org.eclipse.aether.spi.connector.MetadataUpload;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.io.FileProcessor;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.spi.log.NullLogger;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;
import org.eclipse.aether.util.concurrency.RunnableErrorForwarder;

/**
 * A connector for file://-URLs.
 */
class FileRepositoryConnector
    extends ParallelRepositoryConnector
    implements RepositoryConnector
{

    private RemoteRepository repository;

    private RepositorySystemSession session;

    private Logger logger = NullLogger.INSTANCE;

    private FileProcessor fileProcessor;

    public FileRepositoryConnector( RepositorySystemSession session, RemoteRepository repository,
                                    FileProcessor fileProcessor, Logger logger )
        throws NoRepositoryConnectorException
    {
        if ( !"default".equals( repository.getContentType() ) )
        {
            throw new NoRepositoryConnectorException( repository );
        }

        this.session = session;
        this.repository = repository;
        this.fileProcessor = fileProcessor;
        this.logger = logger;

        initExecutor( session.getConfigProperties() );
    }

    public void get( Collection<? extends ArtifactDownload> artifactDownloads,
                     Collection<? extends MetadataDownload> metadataDownloads )
    {
        checkClosed();

        artifactDownloads = notNull( artifactDownloads );
        metadataDownloads = notNull( metadataDownloads );

        RunnableErrorForwarder errorForwarder = new RunnableErrorForwarder();

        for ( ArtifactDownload artifactDownload : artifactDownloads )
        {
            FileRepositoryWorker worker = new FileRepositoryWorker( artifactDownload, repository, session );
            worker.setLogger( logger );
            worker.setFileProcessor( fileProcessor );
            executor.execute( errorForwarder.wrap( worker ) );
        }

        for ( MetadataDownload metadataDownload : metadataDownloads )
        {
            FileRepositoryWorker worker = new FileRepositoryWorker( metadataDownload, repository, session );
            worker.setLogger( logger );
            worker.setFileProcessor( fileProcessor );
            executor.execute( errorForwarder.wrap( worker ) );
        }

        errorForwarder.await();
    }

    private <E> Collection<E> notNull( Collection<E> col )
    {
        return col == null ? Collections.<E> emptyList() : col;
    }

    public void put( Collection<? extends ArtifactUpload> artifactUploads,
                     Collection<? extends MetadataUpload> metadataUploads )
    {
        checkClosed();

        artifactUploads = notNull( artifactUploads );
        metadataUploads = notNull( metadataUploads );

        RunnableErrorForwarder errorForwarder = new RunnableErrorForwarder();

        for ( ArtifactUpload artifactUpload : artifactUploads )
        {
            FileRepositoryWorker worker = new FileRepositoryWorker( artifactUpload, repository, session );
            worker.setLogger( logger );
            worker.setFileProcessor( fileProcessor );
            executor.execute( errorForwarder.wrap( worker ) );
        }
        for ( MetadataUpload metadataUpload : metadataUploads )
        {
            FileRepositoryWorker worker = new FileRepositoryWorker( metadataUpload, repository, session );
            worker.setLogger( logger );
            worker.setFileProcessor( fileProcessor );
            executor.execute( errorForwarder.wrap( worker ) );
        }

        errorForwarder.await();
    }

    @Override
    public String toString()
    {
        return String.valueOf( repository );
    }

}
