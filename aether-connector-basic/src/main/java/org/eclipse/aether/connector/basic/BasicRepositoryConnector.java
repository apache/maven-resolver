/*******************************************************************************
 * Copyright (c) 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.connector.basic;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.ArtifactUpload;
import org.eclipse.aether.spi.connector.MetadataDownload;
import org.eclipse.aether.spi.connector.MetadataUpload;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.connector.transport.GetRequest;
import org.eclipse.aether.spi.connector.transport.NoTransporterException;
import org.eclipse.aether.spi.connector.transport.PeekRequest;
import org.eclipse.aether.spi.connector.transport.PutRequest;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.TransporterProvider;
import org.eclipse.aether.spi.io.FileProcessor;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.transfer.ChecksumFailureException;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferResource;
import org.eclipse.aether.util.ChecksumUtils;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.concurrency.RunnableErrorForwarder;
import org.eclipse.aether.util.repository.layout.MavenDefaultLayout;
import org.eclipse.aether.util.repository.layout.RepositoryLayout;

/**
 */
final class BasicRepositoryConnector
    implements RepositoryConnector
{

    private static final String PROP_THREADS = "aether.connector.basic.threads";

    private final Logger logger;

    private final FileProcessor fileProcessor;

    private final RemoteRepository repository;

    private final RepositorySystemSession session;

    private final Transporter transporter;

    private final RepositoryLayout layout;

    private final Executor executor;

    private final Map<String, String> checksumAlgos;

    private boolean closed;

    public BasicRepositoryConnector( RepositorySystemSession session, RemoteRepository repository,
                                     TransporterProvider transporterProvider, FileProcessor fileProcessor, Logger logger )
        throws NoRepositoryConnectorException
    {
        if ( !"default".equals( repository.getContentType() ) )
        {
            throw new NoRepositoryConnectorException( repository );
        }
        layout = new MavenDefaultLayout();

        try
        {
            transporter = transporterProvider.newTransporter( session, repository );
        }
        catch ( NoTransporterException e )
        {
            throw new NoRepositoryConnectorException( repository, e );
        }

        this.session = session;
        this.repository = repository;
        this.fileProcessor = fileProcessor;
        this.logger = logger;

        int threads = ConfigUtils.getInteger( session, 5, PROP_THREADS, "maven.artifact.threads" );
        executor = getExecutor( threads );

        checksumAlgos = new LinkedHashMap<String, String>();
        checksumAlgos.put( "SHA-1", ".sha1" );
        checksumAlgos.put( "MD5", ".md5" );
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

    @Override
    protected void finalize()
        throws Throwable
    {
        try
        {
            close();
        }
        finally
        {
            super.finalize();
        }
    }

    public void close()
    {
        if ( !closed )
        {
            closed = true;
            if ( executor instanceof ExecutorService )
            {
                ( (ExecutorService) executor ).shutdown();
            }
            transporter.close();
        }
    }

    public void get( Collection<? extends ArtifactDownload> artifactDownloads,
                     Collection<? extends MetadataDownload> metadataDownloads )
    {
        if ( closed )
        {
            throw new IllegalStateException( "connector closed" );
        }

        RunnableErrorForwarder errorForwarder = new RunnableErrorForwarder();

        for ( MetadataDownload transfer : safe( metadataDownloads ) )
        {
            URI path = layout.getPath( transfer.getMetadata() );

            TransferEvent.Builder builder = newEventBuilder( path, transfer.getFile(), false, transfer.getTrace() );
            MetadataTransportListener listener =
                new MetadataTransportListener( transfer, repository, session.getTransferListener(), builder );

            GetTask task = new GetTask( path, transfer.getFile(), false, transfer.getChecksumPolicy(), listener );
            executor.execute( errorForwarder.wrap( task ) );
        }

        for ( ArtifactDownload transfer : safe( artifactDownloads ) )
        {
            URI path = layout.getPath( transfer.getArtifact() );

            TransferEvent.Builder builder = newEventBuilder( path, transfer.getFile(), false, transfer.getTrace() );
            ArtifactTransportListener listener =
                new ArtifactTransportListener( transfer, repository, session.getTransferListener(), builder );

            GetTask task =
                new GetTask( path, transfer.getFile(), transfer.isExistenceCheck(), transfer.getChecksumPolicy(),
                             listener );
            executor.execute( errorForwarder.wrap( task ) );
        }

        errorForwarder.await();
    }

    public void put( Collection<? extends ArtifactUpload> artifactUploads,
                     Collection<? extends MetadataUpload> metadataUploads )
    {
        if ( closed )
        {
            throw new IllegalStateException( "connector closed" );
        }

        for ( ArtifactUpload transfer : safe( artifactUploads ) )
        {
            URI path = layout.getPath( transfer.getArtifact() );

            TransferEvent.Builder builder = newEventBuilder( path, transfer.getFile(), true, transfer.getTrace() );
            ArtifactTransportListener listener =
                new ArtifactTransportListener( transfer, repository, session.getTransferListener(), builder );

            PutTask task = new PutTask( path, transfer.getFile(), listener );
            task.run();
        }

        for ( MetadataUpload transfer : safe( metadataUploads ) )
        {
            URI path = layout.getPath( transfer.getMetadata() );

            TransferEvent.Builder builder = newEventBuilder( path, transfer.getFile(), true, transfer.getTrace() );
            MetadataTransportListener listener =
                new MetadataTransportListener( transfer, repository, session.getTransferListener(), builder );

            PutTask task = new PutTask( path, transfer.getFile(), listener );
            task.run();
        }
    }

    private <T> Collection<T> safe( Collection<T> items )
    {
        return ( items != null ) ? items : Collections.<T> emptyList();
    }

    private TransferEvent.Builder newEventBuilder( URI path, File file, boolean upload, RequestTrace trace )
    {
        TransferResource resource = new TransferResource( repository.getUrl(), path.toString(), file, trace );
        TransferEvent.Builder builder = new TransferEvent.Builder( session, resource );
        if ( upload )
        {
            builder.setRequestType( TransferEvent.RequestType.PUT );
        }
        else if ( file != null )
        {
            builder.setRequestType( TransferEvent.RequestType.GET );
        }
        else
        {
            builder.setRequestType( TransferEvent.RequestType.GET_EXISTENCE );
        }
        return builder;
    }

    @Override
    public String toString()
    {
        return String.valueOf( repository );
    }

    class GetTask
        implements Runnable
    {

        private final URI path;

        private final File file;

        private final boolean peek;

        private final String checksumPolicy;

        private final TransferTransportListener<?> listener;

        public GetTask( URI path, File file, boolean peek, String checksumPolicy, TransferTransportListener<?> listener )
        {
            this.path = path;
            this.file = file;
            this.peek = peek;
            this.checksumPolicy = checksumPolicy;
            this.listener = listener;
        }

        public void run()
        {
            try
            {
                listener.transferInitiated();
                if ( peek )
                {
                    transporter.peek( new PeekRequest( path ) );
                }
                else
                {
                    if ( file == null )
                    {
                        throw new IllegalArgumentException( "destination file has not been specified" );
                    }
                    if ( !RepositoryPolicy.CHECKSUM_POLICY_IGNORE.equals( checksumPolicy ) )
                    {
                        listener.setDigests( checksumAlgos.keySet() );
                    }
                    fileProcessor.mkdirs( file.getParentFile() );
                    File tmp = newTempFile( file );
                    try
                    {
                        for ( int trial = 1; trial >= 0; trial-- )
                        {
                            transporter.get( new GetRequest( path ).setDataFile( tmp ).setListener( listener ) );
                            if ( RepositoryPolicy.CHECKSUM_POLICY_IGNORE.equals( checksumPolicy ) )
                            {
                                break;
                            }
                            else
                            {
                                try
                                {
                                    if ( !verifyChecksum() )
                                    {
                                        trial = 0;
                                        throw new ChecksumFailureException( "Checksum validation failed"
                                            + ", no checksums available from the repository" );
                                    }
                                    break;
                                }
                                catch ( ChecksumFailureException e )
                                {
                                    if ( trial <= 0 && RepositoryPolicy.CHECKSUM_POLICY_FAIL.equals( checksumPolicy ) )
                                    {
                                        throw e;
                                    }
                                    listener.transferCorrupted( e );
                                }
                            }
                        }
                        fileProcessor.move( tmp, file );
                    }
                    finally
                    {
                        delTempFile( tmp );
                    }
                }
                listener.transferSucceeded();
            }
            catch ( Exception e )
            {
                listener.transferFailed( e, transporter.classify( e ) );
            }
        }

        private File newTempFile( File path )
            throws IOException
        {
            return File.createTempFile( path.getName() + "-" + UUID.randomUUID().toString().replace( "-", "" ), ".tmp",
                                        path.getParentFile() );
        }

        private void delTempFile( File path )
        {
            if ( path != null && !path.delete() && path.exists() )
            {
                logger.debug( "Could not delete temorary file " + path );
            }
        }

        private boolean verifyChecksum()
            throws ChecksumFailureException
        {
            for ( Map.Entry<String, String> entry : checksumAlgos.entrySet() )
            {
                String algo = entry.getKey(), ext = entry.getValue();
                String actual = listener.getDigest( algo );
                if ( actual != null && verifyChecksum( actual, ext ) )
                {
                    return true;
                }
            }
            return false;
        }

        private boolean verifyChecksum( String actual, String ext )
            throws ChecksumFailureException
        {
            File checksumFile = new File( file.getPath() + ext );
            File tmp = null;

            try
            {
                tmp = newTempFile( checksumFile );
                try
                {
                    transporter.get( new GetRequest( new URI( path.toString() + ext ) ).setDataFile( tmp ) );
                }
                catch ( Exception e )
                {
                    if ( transporter.classify( e ) == Transporter.ERROR_NOT_FOUND )
                    {
                        return false;
                    }
                    throw new ChecksumFailureException( e );
                }

                String expected = ChecksumUtils.read( tmp );
                if ( expected.equalsIgnoreCase( actual ) )
                {
                    try
                    {
                        fileProcessor.move( tmp, checksumFile );
                    }
                    catch ( IOException e )
                    {
                        logger.debug( "Failed to write checksum file " + file.getPath() + ext + ": " + e.getMessage(),
                                      e );
                    }
                }
                else
                {
                    throw new ChecksumFailureException( expected, actual );
                }
            }
            catch ( IOException e )
            {
                throw new ChecksumFailureException( e );
            }
            finally
            {
                delTempFile( tmp );
            }

            return true;
        }

    }

    class PutTask
        implements Runnable
    {

        private final URI path;

        private final File file;

        private final TransferTransportListener<?> listener;

        public PutTask( URI path, File file, TransferTransportListener<?> listener )
        {
            this.path = path;
            this.file = file;
            this.listener = listener;
        }

        public void run()
        {
            try
            {
                listener.transferInitiated();
                if ( file == null )
                {
                    throw new IllegalArgumentException( "source file has not been specified" );
                }
                transporter.put( new PutRequest( path ).setDataFile( file ).setListener( listener ) );
                uploadChecksums( file, path );
                listener.transferSucceeded();
            }
            catch ( Exception e )
            {
                listener.transferFailed( e, transporter.classify( e ) );
            }
        }

        private void uploadChecksums( File file, URI path )
        {
            try
            {
                Map<String, Object> checksums = ChecksumUtils.calc( file, checksumAlgos.keySet() );
                for ( Map.Entry<String, Object> entry : checksums.entrySet() )
                {
                    uploadChecksum( file, path, entry.getKey(), entry.getValue() );
                }
            }
            catch ( IOException e )
            {
                String msg = "Failed to upload checksums for " + file + ": " + e.getMessage();
                if ( logger.isDebugEnabled() )
                {
                    logger.warn( msg, e );
                }
                else
                {
                    logger.warn( msg );
                }
            }
        }

        private void uploadChecksum( File file, URI path, String algo, Object checksum )
        {
            try
            {
                if ( checksum instanceof Exception )
                {
                    throw (Exception) checksum;
                }

                String ext = checksumAlgos.get( algo );
                URI dst = new URI( path.toString() + ext );
                String sum = String.valueOf( checksum );

                transporter.put( new PutRequest( dst ).setDataString( sum ) );
            }
            catch ( Exception e )
            {
                String msg = "Failed to upload " + algo + " checksum for " + file + ": " + e.getMessage();
                if ( logger.isDebugEnabled() )
                {
                    logger.warn( msg, e );
                }
                else
                {
                    logger.warn( msg );
                }
            }
        }

    }

}
