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
package org.eclipse.aether.connector.wagon;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.StreamingWagon;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.WagonException;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.apache.maven.wagon.observers.ChecksumObserver;
import org.apache.maven.wagon.proxy.ProxyInfo;
import org.apache.maven.wagon.proxy.ProxyInfoProvider;
import org.apache.maven.wagon.repository.Repository;
import org.apache.maven.wagon.repository.RepositoryPermissions;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.spi.connector.ArtifactDownload;
import org.eclipse.aether.spi.connector.ArtifactTransfer;
import org.eclipse.aether.spi.connector.ArtifactUpload;
import org.eclipse.aether.spi.connector.MetadataDownload;
import org.eclipse.aether.spi.connector.MetadataTransfer;
import org.eclipse.aether.spi.connector.MetadataUpload;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.connector.Transfer;
import org.eclipse.aether.spi.io.FileProcessor;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.transfer.ArtifactNotFoundException;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.transfer.ChecksumFailureException;
import org.eclipse.aether.transfer.MetadataNotFoundException;
import org.eclipse.aether.transfer.MetadataTransferException;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferListener;
import org.eclipse.aether.util.ChecksumUtils;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.concurrency.RunnableErrorForwarder;
import org.eclipse.aether.util.layout.MavenDefaultLayout;
import org.eclipse.aether.util.layout.RepositoryLayout;
import org.eclipse.aether.util.listener.DefaultTransferEvent;

/**
 * A repository connector that uses Maven Wagon for the transfer.
 */
class WagonRepositoryConnector
    implements RepositoryConnector
{

    private static final String PROP_THREADS = "aether.connector.wagon.threads";

    private static final String PROP_CONFIG = "aether.connector.wagon.config";

    private static final String PROP_FILE_MODE = "aether.connector.perms.fileMode";

    private static final String PROP_DIR_MODE = "aether.connector.perms.dirMode";

    private static final String PROP_GROUP = "aether.connector.perms.group";

    private final Logger logger;

    private final FileProcessor fileProcessor;

    private final RemoteRepository repository;

    private final RepositorySystemSession session;

    private final WagonProvider wagonProvider;

    private final WagonConfigurator wagonConfigurator;

    private final String wagonHint;

    private final Repository wagonRepo;

    private final AuthenticationInfo wagonAuth;

    private final ProxyInfoProvider wagonProxy;

    private final RepositoryLayout layout = new MavenDefaultLayout();

    private final TransferListener listener;

    private final Queue<Wagon> wagons = new ConcurrentLinkedQueue<Wagon>();

    private final Executor executor;

    private boolean closed;

    private final Map<String, String> checksumAlgos;

    private final Properties headers;

    public WagonRepositoryConnector( WagonProvider wagonProvider, WagonConfigurator wagonConfigurator,
                                     RemoteRepository repository, RepositorySystemSession session,
                                     FileProcessor fileProcessor, Logger logger )
        throws NoRepositoryConnectorException
    {
        this.logger = logger;
        this.fileProcessor = fileProcessor;
        this.wagonProvider = wagonProvider;
        this.wagonConfigurator = wagonConfigurator;
        this.repository = repository;
        this.session = session;
        this.listener = session.getTransferListener();

        if ( !"default".equals( repository.getContentType() ) )
        {
            throw new NoRepositoryConnectorException( repository );
        }

        wagonRepo = new Repository( repository.getId(), repository.getUrl() );
        wagonRepo.setPermissions( getPermissions( repository.getId(), session ) );

        wagonHint = wagonRepo.getProtocol().toLowerCase( Locale.ENGLISH );
        if ( wagonHint == null || wagonHint.length() <= 0 )
        {
            throw new NoRepositoryConnectorException( repository );
        }

        try
        {
            wagons.add( lookupWagon() );
        }
        catch ( Exception e )
        {
            logger.debug( e.getMessage(), e );
            throw new NoRepositoryConnectorException( repository );
        }

        wagonAuth = getAuthenticationInfo( repository );
        wagonProxy = getProxy( repository );

        int threads = ConfigUtils.getInteger( session, 5, PROP_THREADS, "maven.artifact.threads" );
        executor = getExecutor( threads );

        checksumAlgos = new LinkedHashMap<String, String>();
        checksumAlgos.put( "SHA-1", ".sha1" );
        checksumAlgos.put( "MD5", ".md5" );

        headers = new Properties();
        headers.put( "User-Agent", ConfigUtils.getString( session, ConfigurationProperties.DEFAULT_USER_AGENT,
                                                          ConfigurationProperties.USER_AGENT ) );
        Map<?, ?> headers =
            ConfigUtils.getMap( session, null, ConfigurationProperties.HTTP_HEADERS + "." + repository.getId(),
                                ConfigurationProperties.HTTP_HEADERS );
        if ( headers != null )
        {
            this.headers.putAll( headers );
        }
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

    private static RepositoryPermissions getPermissions( String repoId, RepositorySystemSession session )
    {
        RepositoryPermissions result = null;

        RepositoryPermissions perms = new RepositoryPermissions();

        String suffix = '.' + repoId;

        String fileMode = ConfigUtils.getString( session, (String) null, PROP_FILE_MODE + suffix );
        if ( fileMode != null )
        {
            perms.setFileMode( fileMode );
            result = perms;
        }

        String dirMode = ConfigUtils.getString( session, (String) null, PROP_DIR_MODE + suffix );
        if ( dirMode != null )
        {
            perms.setDirectoryMode( dirMode );
            result = perms;
        }

        String group = ConfigUtils.getString( session, (String) null, PROP_GROUP + suffix );
        if ( group != null )
        {
            perms.setGroup( group );
            result = perms;
        }

        return result;
    }

    private AuthenticationInfo getAuthenticationInfo( RemoteRepository repository )
    {
        AuthenticationInfo auth = null;

        Authentication a = repository.getAuthentication();
        if ( a != null )
        {
            auth = new AuthenticationInfo();
            auth.setUserName( a.getUsername() );
            auth.setPassword( a.getPassword() );
            auth.setPrivateKey( a.getPrivateKeyFile() );
            auth.setPassphrase( a.getPassphrase() );
        }

        return auth;
    }

    private ProxyInfoProvider getProxy( RemoteRepository repository )
    {
        ProxyInfoProvider proxy = null;

        Proxy p = repository.getProxy();
        if ( p != null )
        {
            final ProxyInfo prox = new ProxyInfo();
            prox.setType( p.getType() );
            prox.setHost( p.getHost() );
            prox.setPort( p.getPort() );
            if ( p.getAuthentication() != null )
            {
                prox.setUserName( p.getAuthentication().getUsername() );
                prox.setPassword( p.getAuthentication().getPassword() );
            }
            proxy = new ProxyInfoProvider()
            {
                public ProxyInfo getProxyInfo( String protocol )
                {
                    return prox;
                }
            };
        }

        return proxy;
    }

    private Wagon lookupWagon()
        throws Exception
    {
        return wagonProvider.lookup( wagonHint );
    }

    private void releaseWagon( Wagon wagon )
    {
        wagonProvider.release( wagon );
    }

    private void connectWagon( Wagon wagon )
        throws Exception
    {
        if ( !headers.isEmpty() )
        {
            try
            {
                Method setHttpHeaders = wagon.getClass().getMethod( "setHttpHeaders", Properties.class );
                setHttpHeaders.invoke( wagon, headers );
            }
            catch ( NoSuchMethodException e )
            {
                // normal for non-http wagons
            }
            catch ( Exception e )
            {
                logger.debug( "Could not set user agent for wagon " + wagon.getClass().getName() + ": " + e );
            }
        }

        int connectTimeout =
            ConfigUtils.getInteger( session, ConfigurationProperties.DEFAULT_CONNECT_TIMEOUT,
                                    ConfigurationProperties.CONNECT_TIMEOUT );
        int requestTimeout =
            ConfigUtils.getInteger( session, ConfigurationProperties.DEFAULT_REQUEST_TIMEOUT,
                                    ConfigurationProperties.REQUEST_TIMEOUT );

        wagon.setTimeout( Math.max( Math.max( connectTimeout, requestTimeout ), 0 ) );

        wagon.setInteractive( ConfigUtils.getBoolean( session, ConfigurationProperties.DEFAULT_INTERACTIVE,
                                                      ConfigurationProperties.INTERACTIVE ) );

        Object configuration = ConfigUtils.getObject( session, null, PROP_CONFIG + "." + repository.getId() );
        if ( configuration != null && wagonConfigurator != null )
        {
            try
            {
                wagonConfigurator.configure( wagon, configuration );
            }
            catch ( Exception e )
            {
                String msg =
                    "Could not apply configuration for " + repository.getId() + " to wagon "
                        + wagon.getClass().getName() + ":" + e.getMessage();
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

        wagon.connect( wagonRepo, wagonAuth, wagonProxy );
    }

    private void disconnectWagon( Wagon wagon )
    {
        try
        {
            if ( wagon != null )
            {
                wagon.disconnect();
            }
        }
        catch ( Exception e )
        {
            // too bad
        }
    }

    Wagon pollWagon()
        throws Exception
    {
        Wagon wagon = wagons.poll();

        if ( wagon == null )
        {
            try
            {
                wagon = lookupWagon();
                connectWagon( wagon );
            }
            catch ( Exception e )
            {
                releaseWagon( wagon );
                throw e;
            }
        }
        else if ( wagon.getRepository() == null )
        {
            try
            {
                connectWagon( wagon );
            }
            catch ( Exception e )
            {
                wagons.add( wagon );
                throw e;
            }
        }

        return wagon;
    }

    private <T> Collection<T> safe( Collection<T> items )
    {
        return ( items != null ) ? items : Collections.<T> emptyList();
    }

    private File getTmpFile( String path )
    {
        File file;
        do
        {
            file = new File( path + ".tmp" + UUID.randomUUID().toString().replace( "-", "" ).substring( 0, 16 ) );
        }
        while ( file.exists() );
        return file;
    }

    public void get( Collection<? extends ArtifactDownload> artifactDownloads,
                     Collection<? extends MetadataDownload> metadataDownloads )
    {
        if ( closed )
        {
            throw new IllegalStateException( "connector closed" );
        }

        artifactDownloads = safe( artifactDownloads );
        metadataDownloads = safe( metadataDownloads );

        Collection<GetTask<?>> tasks = new ArrayList<GetTask<?>>();

        RunnableErrorForwarder errorForwarder = new RunnableErrorForwarder();

        for ( MetadataDownload download : metadataDownloads )
        {
            String resource = layout.getPath( download.getMetadata() ).getPath();
            GetTask<?> task =
                new GetTask<MetadataTransfer>( resource, download.getFile(), download.getChecksumPolicy(), download,
                                               METADATA );
            tasks.add( task );
            executor.execute( errorForwarder.wrap( task ) );
        }

        for ( ArtifactDownload download : artifactDownloads )
        {
            String resource = layout.getPath( download.getArtifact() ).getPath();
            GetTask<?> task =
                new GetTask<ArtifactTransfer>( resource, download.isExistenceCheck() ? null : download.getFile(),
                                               download.getChecksumPolicy(), download, ARTIFACT );
            tasks.add( task );
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

        artifactUploads = safe( artifactUploads );
        metadataUploads = safe( metadataUploads );

        for ( ArtifactUpload upload : artifactUploads )
        {
            String path = layout.getPath( upload.getArtifact() ).getPath();

            PutTask<?> task = new PutTask<ArtifactTransfer>( path, upload.getFile(), upload, ARTIFACT );
            task.run();
        }

        for ( MetadataUpload upload : metadataUploads )
        {
            String path = layout.getPath( upload.getMetadata() ).getPath();

            PutTask<?> task = new PutTask<MetadataTransfer>( path, upload.getFile(), upload, METADATA );
            task.run();
        }
    }

    public void close()
    {
        closed = true;

        for ( Wagon wagon = wagons.poll(); wagon != null; wagon = wagons.poll() )
        {
            disconnectWagon( wagon );
            releaseWagon( wagon );
        }

        shutdown( executor );
    }

    private void shutdown( Executor executor )
    {
        if ( executor instanceof ExecutorService )
        {
            ( (ExecutorService) executor ).shutdown();
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

    @Override
    public String toString()
    {
        return String.valueOf( repository );
    }

    class GetTask<T extends Transfer>
        implements Runnable
    {

        private final T download;

        private final String path;

        private final File file;

        private final String checksumPolicy;

        private final ExceptionWrapper<T> wrapper;

        public GetTask( String path, File file, String checksumPolicy, T download, ExceptionWrapper<T> wrapper )
        {
            this.path = path;
            this.file = file;
            this.checksumPolicy = checksumPolicy;
            this.download = download;
            this.wrapper = wrapper;
        }

        public T getDownload()
        {
            return download;
        }

        public void run()
        {
            download.setState( Transfer.State.ACTIVE );

            WagonTransferListenerAdapter wagonListener = null;
            if ( listener != null )
            {
                wagonListener =
                    new WagonTransferListenerAdapter( listener, wagonRepo.getUrl(), path, file, download.getTrace(),
                                                      TransferEvent.RequestType.GET );
            }

            try
            {
                if ( listener != null )
                {
                    DefaultTransferEvent event = wagonListener.newEvent( TransferEvent.EventType.INITIATED );
                    listener.transferInitiated( event );
                }

                File tmp = ( file != null ) ? getTmpFile( file.getPath() ) : null;

                Wagon wagon = pollWagon();

                try
                {
                    if ( file == null )
                    {
                        if ( !wagon.resourceExists( path ) )
                        {
                            throw new ResourceDoesNotExistException( "Could not find " + path + " in "
                                + wagonRepo.getUrl() );
                        }
                    }
                    else
                    {
                        for ( int trial = 1; trial >= 0; trial-- )
                        {
                            ChecksumObserver sha1 = new ChecksumObserver( "SHA-1" );
                            ChecksumObserver md5 = new ChecksumObserver( "MD5" );
                            try
                            {
                                wagon.addTransferListener( wagonListener );
                                wagon.addTransferListener( md5 );
                                wagon.addTransferListener( sha1 );

                                /*
                                 * NOTE: AbstractWagon.createParentDirectories() uses File.mkdirs() which is not
                                 * thread-safe in all JREs.
                                 */
                                fileProcessor.mkdirs( tmp.getParentFile() );

                                wagon.get( path, tmp );
                            }
                            finally
                            {
                                wagon.removeTransferListener( wagonListener );
                                wagon.removeTransferListener( md5 );
                                wagon.removeTransferListener( sha1 );
                            }

                            if ( RepositoryPolicy.CHECKSUM_POLICY_IGNORE.equals( checksumPolicy ) )
                            {
                                break;
                            }
                            else
                            {
                                try
                                {
                                    if ( !verifyChecksum( wagon, sha1.getActualChecksum(), ".sha1" )
                                        && !verifyChecksum( wagon, md5.getActualChecksum(), ".md5" ) )
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
                                    if ( listener != null )
                                    {
                                        DefaultTransferEvent event =
                                            wagonListener.newEvent( TransferEvent.EventType.CORRUPTED );
                                        event.setException( e );
                                        listener.transferCorrupted( event );
                                    }
                                }
                            }
                        }

                        rename( tmp, file );
                    }

                    wrapper.wrap( download, null, repository );

                    if ( listener != null )
                    {
                        DefaultTransferEvent event = wagonListener.newEvent( TransferEvent.EventType.SUCCEEDED );
                        listener.transferSucceeded( event );
                    }
                }
                finally
                {
                    if ( tmp != null )
                    {
                        tmp.delete();
                    }
                    wagons.add( wagon );
                }
            }
            catch ( Exception e )
            {
                e = wrapper.wrap( download, e, repository );

                if ( listener != null )
                {
                    DefaultTransferEvent event = wagonListener.newEvent( TransferEvent.EventType.FAILED );
                    event.setException( e );
                    listener.transferFailed( event );
                }
            }
            finally
            {
                download.setState( Transfer.State.DONE );
            }
        }

        private boolean verifyChecksum( Wagon wagon, String actual, String ext )
            throws ChecksumFailureException
        {
            File tmp = getTmpFile( file.getPath() + ext );

            try
            {
                try
                {
                    wagon.get( path + ext, tmp );
                }
                catch ( ResourceDoesNotExistException e )
                {
                    return false;
                }
                catch ( WagonException e )
                {
                    throw new ChecksumFailureException( e );
                }

                String expected;

                try
                {
                    expected = ChecksumUtils.read( tmp );
                }
                catch ( IOException e )
                {
                    throw new ChecksumFailureException( e );
                }

                if ( expected.equalsIgnoreCase( actual ) )
                {
                    try
                    {
                        rename( tmp, new File( file.getPath() + ext ) );
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
            finally
            {
                tmp.delete();
            }

            return true;
        }

        private void rename( File from, File to )
            throws IOException
        {
            if ( !from.exists() )
            {
                /*
                 * NOTE: Wagon (1.0-beta-6) doesn't create the destination file when transferring a 0-byte resource. So
                 * if the resource we asked for didn't cause any exception but doesn't show up in the tmp file either,
                 * Wagon tells us in its weird way the file is empty.
                 */
                fileProcessor.write( to, "" );
            }
            else
            {
                fileProcessor.move( from, to );
            }
        }

    }

    class PutTask<T extends Transfer>
        implements Runnable
    {

        private final T upload;

        private final ExceptionWrapper<T> wrapper;

        private final String path;

        private final File file;

        public PutTask( String path, File file, T upload, ExceptionWrapper<T> wrapper )
        {
            this.path = path;
            this.file = file;
            this.upload = upload;
            this.wrapper = wrapper;
        }

        public void run()
        {
            upload.setState( Transfer.State.ACTIVE );

            WagonTransferListenerAdapter wagonListener = null;
            if ( listener != null )
            {
                wagonListener =
                    new WagonTransferListenerAdapter( listener, wagonRepo.getUrl(), path, file, upload.getTrace(),
                                                      TransferEvent.RequestType.PUT );
            }

            try
            {
                if ( listener != null )
                {
                    DefaultTransferEvent event = wagonListener.newEvent( TransferEvent.EventType.INITIATED );
                    listener.transferInitiated( event );
                }

                Wagon wagon = pollWagon();

                try
                {
                    try
                    {
                        wagon.addTransferListener( wagonListener );

                        wagon.put( file, path );
                    }
                    finally
                    {
                        wagon.removeTransferListener( wagonListener );
                    }

                    uploadChecksums( wagon, file, path );

                    wrapper.wrap( upload, null, repository );

                    if ( listener != null )
                    {
                        DefaultTransferEvent event = wagonListener.newEvent( TransferEvent.EventType.SUCCEEDED );
                        listener.transferSucceeded( event );
                    }
                }
                finally
                {
                    wagons.add( wagon );
                }
            }
            catch ( Exception e )
            {
                e = wrapper.wrap( upload, e, repository );

                if ( listener != null )
                {
                    DefaultTransferEvent event = wagonListener.newEvent( TransferEvent.EventType.FAILED );
                    event.setException( e );
                    listener.transferFailed( event );
                }
            }
            finally
            {
                upload.setState( Transfer.State.DONE );
            }
        }

        private void uploadChecksums( Wagon wagon, File file, String path )
        {
            try
            {
                Map<String, Object> checksums = ChecksumUtils.calc( file, checksumAlgos.keySet() );
                for ( Map.Entry<String, Object> entry : checksums.entrySet() )
                {
                    uploadChecksum( wagon, file, path, entry.getKey(), entry.getValue() );
                }
            }
            catch ( IOException e )
            {
                logger.debug( "Failed to upload checksums for " + file + ": " + e.getMessage(), e );
            }
        }

        private void uploadChecksum( Wagon wagon, File file, String path, String algo, Object checksum )
        {
            try
            {
                if ( checksum instanceof Exception )
                {
                    throw (Exception) checksum;
                }

                String ext = checksumAlgos.get( algo );
                String dst = path + ext;
                String sum = String.valueOf( checksum );

                if ( wagon instanceof StreamingWagon )
                {
                    ( (StreamingWagon) wagon ).putFromStream( new ByteArrayInputStream( sum.getBytes( "UTF-8" ) ), dst );
                }
                else
                {
                    File tmpFile = File.createTempFile( "wagon" + UUID.randomUUID().toString().replace( "-", "" ), ext );
                    try
                    {
                        fileProcessor.write( tmpFile, sum );
                        wagon.put( tmpFile, dst );
                    }
                    finally
                    {
                        tmpFile.delete();
                    }
                }
            }
            catch ( Exception e )
            {
                logger.warn( "Failed to upload " + algo + " checksum for " + file + ": " + e.getMessage(), e );
            }
        }

    }

    static interface ExceptionWrapper<T>
    {

        Exception wrap( T transfer, Exception e, RemoteRepository repository );

    }

    private static final ExceptionWrapper<MetadataTransfer> METADATA = new ExceptionWrapper<MetadataTransfer>()
    {

        public Exception wrap( MetadataTransfer transfer, Exception e, RemoteRepository repository )
        {
            MetadataTransferException ex = null;
            e = WagonCancelledException.unwrap( e );
            if ( e instanceof ResourceDoesNotExistException )
            {
                ex = new MetadataNotFoundException( transfer.getMetadata(), repository );
            }
            else if ( e != null )
            {
                ex = new MetadataTransferException( transfer.getMetadata(), repository, e );
            }
            transfer.setException( ex );
            return ex;
        }

    };

    private static final ExceptionWrapper<ArtifactTransfer> ARTIFACT = new ExceptionWrapper<ArtifactTransfer>()
    {

        public Exception wrap( ArtifactTransfer transfer, Exception e, RemoteRepository repository )
        {
            ArtifactTransferException ex = null;
            e = WagonCancelledException.unwrap( e );
            if ( e instanceof ResourceDoesNotExistException )
            {
                ex = new ArtifactNotFoundException( transfer.getArtifact(), repository );
            }
            else if ( e != null )
            {
                ex = new ArtifactTransferException( transfer.getArtifact(), repository, e );
            }
            transfer.setException( ex );
            return ex;
        }

    };

}
