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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.ArtifactUpload;
import org.eclipse.aether.spi.connector.MetadataDownload;
import org.eclipse.aether.spi.connector.MetadataUpload;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.connector.layout.RepositoryLayout;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutProvider;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.TransporterProvider;
import org.eclipse.aether.spi.io.FileProcessor;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.transfer.ChecksumFailureException;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;
import org.eclipse.aether.transfer.NoRepositoryLayoutException;
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferResource;
import org.eclipse.aether.util.ChecksumUtils;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.concurrency.RunnableErrorForwarder;
import org.eclipse.aether.util.concurrency.WorkerThreadFactory;

/**
 */
final class BasicRepositoryConnector
    implements RepositoryConnector
{

    private static final String PROP_THREADS = "aether.connector.basic.threads";

    private static final String PROP_RESUME = "aether.connector.resumeDownloads";

    private static final String PROP_RESUME_THRESHOLD = "aether.connector.resumeThreshold";

    private final Logger logger;

    private final FileProcessor fileProcessor;

    private final RemoteRepository repository;

    private final RepositorySystemSession session;

    private final Transporter transporter;

    private final RepositoryLayout layout;

    private final Executor executor;

    private final PartialFile.Factory partialFileFactory;

    private boolean closed;

    public BasicRepositoryConnector( RepositorySystemSession session, RemoteRepository repository,
                                     TransporterProvider transporterProvider, RepositoryLayoutProvider layoutProvider,
                                     FileProcessor fileProcessor, Logger logger )
        throws NoRepositoryConnectorException
    {
        try
        {
            layout = layoutProvider.newRepositoryLayout( session, repository );
        }
        catch ( NoRepositoryLayoutException e )
        {
            throw new NoRepositoryConnectorException( repository, e );
        }
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
        executor = getExecutor( threads, repository );

        boolean resumeDownloads =
            ConfigUtils.getBoolean( session, true, PROP_RESUME + '.' + repository.getId(), PROP_RESUME );
        long resumeThreshold =
            ConfigUtils.getLong( session, 64 * 1024, PROP_RESUME_THRESHOLD + '.' + repository.getId(),
                                 PROP_RESUME_THRESHOLD );
        int requestTimeout =
            ConfigUtils.getInteger( session, ConfigurationProperties.DEFAULT_REQUEST_TIMEOUT,
                                    ConfigurationProperties.REQUEST_TIMEOUT + '.' + repository.getId(),
                                    ConfigurationProperties.REQUEST_TIMEOUT );
        partialFileFactory = new PartialFile.Factory( resumeDownloads, resumeThreshold, requestTimeout, logger );
    }

    private Executor getExecutor( int threads, RemoteRepository repository )
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
            return new ThreadPoolExecutor( threads, threads, 3, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(),
                                           new WorkerThreadFactory( getClass().getSimpleName() + '-'
                                               + repository.getHost() + '-' ) );
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
            URI location = layout.getLocation( transfer.getMetadata(), false );
            List<RepositoryLayout.Checksum> checksums;
            if ( RepositoryPolicy.CHECKSUM_POLICY_IGNORE.equals( transfer.getChecksumPolicy() ) )
            {
                checksums = null;
            }
            else
            {
                checksums = layout.getChecksums( transfer.getMetadata(), false, location );
            }

            TransferEvent.Builder builder = newEventBuilder( location, transfer.getFile(), false, transfer.getTrace() );
            MetadataTransportListener listener =
                new MetadataTransportListener( transfer, repository, session.getTransferListener(), builder );

            Runnable task =
                new GetTaskRunner( location, transfer.getFile(), transfer.getChecksumPolicy(), checksums, listener );
            executor.execute( errorForwarder.wrap( task ) );
        }

        for ( ArtifactDownload transfer : safe( artifactDownloads ) )
        {
            URI location = layout.getLocation( transfer.getArtifact(), false );
            List<RepositoryLayout.Checksum> checksums;
            if ( RepositoryPolicy.CHECKSUM_POLICY_IGNORE.equals( transfer.getChecksumPolicy() ) )
            {
                checksums = null;
            }
            else
            {
                checksums = layout.getChecksums( transfer.getArtifact(), false, location );
            }

            TransferEvent.Builder builder = newEventBuilder( location, transfer.getFile(), false, transfer.getTrace() );
            ArtifactTransportListener listener =
                new ArtifactTransportListener( transfer, repository, session.getTransferListener(), builder );

            Runnable task;
            if ( transfer.isExistenceCheck() )
            {
                task = new PeekTaskRunner( location, listener );
            }
            else
            {
                task =
                    new GetTaskRunner( location, transfer.getFile(), transfer.getChecksumPolicy(), checksums, listener );
            }
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
            URI location = layout.getLocation( transfer.getArtifact(), true );
            List<RepositoryLayout.Checksum> checksums = layout.getChecksums( transfer.getArtifact(), true, location );

            TransferEvent.Builder builder = newEventBuilder( location, transfer.getFile(), true, transfer.getTrace() );
            ArtifactTransportListener listener =
                new ArtifactTransportListener( transfer, repository, session.getTransferListener(), builder );

            Runnable task = new PutTaskRunner( location, transfer.getFile(), checksums, listener );
            task.run();
        }

        for ( MetadataUpload transfer : safe( metadataUploads ) )
        {
            URI location = layout.getLocation( transfer.getMetadata(), true );
            List<RepositoryLayout.Checksum> checksums = layout.getChecksums( transfer.getMetadata(), true, location );

            TransferEvent.Builder builder = newEventBuilder( location, transfer.getFile(), true, transfer.getTrace() );
            MetadataTransportListener listener =
                new MetadataTransportListener( transfer, repository, session.getTransferListener(), builder );

            Runnable task = new PutTaskRunner( location, transfer.getFile(), checksums, listener );
            task.run();
        }
    }

    private static <T> Collection<T> safe( Collection<T> items )
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

    abstract class TaskRunner
        implements Runnable
    {

        protected final URI path;

        protected final TransferTransportListener<?> listener;

        public TaskRunner( URI path, TransferTransportListener<?> listener )
        {
            this.path = path;
            this.listener = listener;
        }

        public void run()
        {
            try
            {
                listener.transferInitiated();
                runTask();
                listener.transferSucceeded();
            }
            catch ( Exception e )
            {
                listener.transferFailed( e, transporter.classify( e ) );
            }
        }

        protected abstract void runTask()
            throws Exception;

    }

    class PeekTaskRunner
        extends TaskRunner
    {

        public PeekTaskRunner( URI path, TransferTransportListener<?> listener )
        {
            super( path, listener );
        }

        protected void runTask()
            throws Exception
        {
            transporter.peek( new PeekTask( path ) );
        }

    }

    class GetTaskRunner
        extends TaskRunner
    {

        private final File file;

        private final String checksumPolicy;

        private final Collection<RepositoryLayout.Checksum> checksums;

        public GetTaskRunner( URI path, File file, String checksumPolicy, List<RepositoryLayout.Checksum> checksums,
                              TransferTransportListener<?> listener )
        {
            super( path, listener );
            this.file = file;
            this.checksumPolicy = checksumPolicy;
            this.checksums = safe( checksums );
        }

        protected void runTask()
            throws Exception
        {
            if ( file == null )
            {
                throw new IllegalArgumentException( "destination file has not been specified" );
            }
            fileProcessor.mkdirs( file.getParentFile() );

            PartialFile partFile = partialFileFactory.newInstance( file );
            if ( partFile == null )
            {
                logger.debug( "Concurrent download of " + file + " just finished, skipping download" );
                return;
            }

            try
            {
                File tmp = partFile.getFile();
                listener.setChecksums( ChecksumCalculator.newInstance( tmp, checksums ) );
                for ( int firstTrial = 0, lastTrial = 1, trial = firstTrial; trial <= lastTrial; trial++ )
                {
                    boolean resume = partFile.isResume() && trial <= firstTrial;
                    transporter.get( new GetTask( path ).setDataFile( tmp, resume ).setListener( listener ) );
                    try
                    {
                        if ( !verifyChecksum() )
                        {
                            trial = lastTrial;
                            throw new ChecksumFailureException( "Checksum validation failed"
                                + ", no checksums available from the repository" );
                        }
                        break;
                    }
                    catch ( ChecksumFailureException e )
                    {
                        if ( trial >= lastTrial && RepositoryPolicy.CHECKSUM_POLICY_FAIL.equals( checksumPolicy ) )
                        {
                            throw e;
                        }
                        listener.transferCorrupted( e );
                    }
                }
                fileProcessor.move( tmp, file );
            }
            finally
            {
                partFile.close();
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
            ChecksumCalculator calculator = listener.getChecksums();
            if ( calculator == null )
            {
                return true;
            }
            Map<String, Object> sumsByAlgo = calculator.get();
            for ( RepositoryLayout.Checksum checksum : checksums )
            {
                Object actual = sumsByAlgo.get( checksum.getAlgorithm() );
                if ( actual != null && verifyChecksum( checksum, actual ) )
                {
                    return true;
                }
            }
            return false;
        }

        private boolean verifyChecksum( RepositoryLayout.Checksum checksum, Object actual )
            throws ChecksumFailureException
        {
            String ext = checksum.getAlgorithm().replace( "-", "" ).toLowerCase( Locale.ENGLISH );
            File checksumFile = new File( file.getPath() + '.' + ext );
            File tmp = null;

            try
            {
                if ( actual instanceof Exception )
                {
                    throw new ChecksumFailureException( (Exception) actual );
                }

                tmp = newTempFile( checksumFile );
                try
                {
                    transporter.get( new GetTask( checksum.getLocation() ).setDataFile( tmp ) );
                }
                catch ( Exception e )
                {
                    if ( transporter.classify( e ) == Transporter.ERROR_NOT_FOUND )
                    {
                        return false;
                    }
                    throw new ChecksumFailureException( e );
                }

                String act = String.valueOf( actual );
                String expected = ChecksumUtils.read( tmp );
                if ( expected.equalsIgnoreCase( act ) )
                {
                    try
                    {
                        fileProcessor.move( tmp, checksumFile );
                    }
                    catch ( IOException e )
                    {
                        logger.debug( "Failed to write checksum file " + checksumFile + ": " + e.getMessage(), e );
                    }
                }
                else
                {
                    throw new ChecksumFailureException( expected, act );
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

    class PutTaskRunner
        extends TaskRunner
    {

        private final File file;

        private final Collection<RepositoryLayout.Checksum> checksums;

        public PutTaskRunner( URI path, File file, List<RepositoryLayout.Checksum> checksums,
                              TransferTransportListener<?> listener )
        {
            super( path, listener );
            this.file = file;
            this.checksums = safe( checksums );
        }

        protected void runTask()
            throws Exception
        {
            if ( file == null )
            {
                throw new IllegalArgumentException( "source file has not been specified" );
            }
            transporter.put( new PutTask( path ).setDataFile( file ).setListener( listener ) );
            uploadChecksums( file, path );
        }

        private void uploadChecksums( File file, URI location )
        {
            if ( checksums.isEmpty() )
            {
                return;
            }
            try
            {
                Set<String> algos = new HashSet<String>();
                for ( RepositoryLayout.Checksum checksum : checksums )
                {
                    algos.add( checksum.getAlgorithm() );
                }
                Map<String, Object> sumsByAlgo = ChecksumUtils.calc( file, algos );
                for ( RepositoryLayout.Checksum checksum : checksums )
                {
                    uploadChecksum( checksum.getLocation(), sumsByAlgo.get( checksum.getAlgorithm() ) );
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

        private void uploadChecksum( URI location, Object checksum )
        {
            try
            {
                if ( checksum instanceof Exception )
                {
                    throw (Exception) checksum;
                }
                transporter.put( new PutTask( location ).setDataString( (String) checksum ) );
            }
            catch ( Exception e )
            {
                String msg = "Failed to upload checksum " + location + ": " + e.getMessage();
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
