package org.eclipse.aether.transport.wagon;

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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.StreamingWagon;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.apache.maven.wagon.proxy.ProxyInfo;
import org.apache.maven.wagon.proxy.ProxyInfoProvider;
import org.apache.maven.wagon.repository.Repository;
import org.apache.maven.wagon.repository.RepositoryPermissions;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.TransportTask;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.util.ConfigUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A transporter using Maven Wagon.
 */
final class WagonTransporter
    implements Transporter
{

    private static final String CONFIG_PROP_CONFIG = "aether.connector.wagon.config";

    private static final String CONFIG_PROP_FILE_MODE = "aether.connector.perms.fileMode";

    private static final String CONFIG_PROP_DIR_MODE = "aether.connector.perms.dirMode";

    private static final String CONFIG_PROP_GROUP = "aether.connector.perms.group";

    private static final Logger LOGGER = LoggerFactory.getLogger( WagonTransporter.class );

    private final RemoteRepository repository;

    private final RepositorySystemSession session;

    private final AuthenticationContext repoAuthContext;

    private final AuthenticationContext proxyAuthContext;

    private final WagonProvider wagonProvider;

    private final WagonConfigurator wagonConfigurator;

    private final String wagonHint;

    private final Repository wagonRepo;

    private final AuthenticationInfo wagonAuth;

    private final ProxyInfoProvider wagonProxy;

    private final Properties headers;

    private final Queue<Wagon> wagons = new ConcurrentLinkedQueue<>();

    private final AtomicBoolean closed = new AtomicBoolean();

    WagonTransporter( WagonProvider wagonProvider, WagonConfigurator wagonConfigurator,
                             RemoteRepository repository, RepositorySystemSession session )
        throws NoTransporterException
    {
        this.wagonProvider = wagonProvider;
        this.wagonConfigurator = wagonConfigurator;
        this.repository = repository;
        this.session = session;

        wagonRepo = new Repository( repository.getId(), repository.getUrl() );
        wagonRepo.setPermissions( getPermissions( repository.getId(), session ) );

        wagonHint = wagonRepo.getProtocol().toLowerCase( Locale.ENGLISH );
        if ( wagonHint == null || wagonHint.length() <= 0 )
        {
            throw new NoTransporterException( repository );
        }

        try
        {
            wagons.add( lookupWagon() );
        }
        catch ( Exception e )
        {
            LOGGER.debug( "No transport {}", e.getMessage(), e );
            throw new NoTransporterException( repository, e.getMessage(), e );
        }

        repoAuthContext = AuthenticationContext.forRepository( session, repository );
        proxyAuthContext = AuthenticationContext.forProxy( session, repository );

        wagonAuth = getAuthenticationInfo( repository, repoAuthContext );
        wagonProxy = getProxy( repository, proxyAuthContext );

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

    private static RepositoryPermissions getPermissions( String repoId, RepositorySystemSession session )
    {
        RepositoryPermissions result = null;

        RepositoryPermissions perms = new RepositoryPermissions();

        String suffix = '.' + repoId;

        String fileMode = ConfigUtils.getString( session, null, CONFIG_PROP_FILE_MODE + suffix );
        if ( fileMode != null )
        {
            perms.setFileMode( fileMode );
            result = perms;
        }

        String dirMode = ConfigUtils.getString( session, null, CONFIG_PROP_DIR_MODE + suffix );
        if ( dirMode != null )
        {
            perms.setDirectoryMode( dirMode );
            result = perms;
        }

        String group = ConfigUtils.getString( session, null, CONFIG_PROP_GROUP + suffix );
        if ( group != null )
        {
            perms.setGroup( group );
            result = perms;
        }

        return result;
    }

    private AuthenticationInfo getAuthenticationInfo( RemoteRepository repository,
                                                      final AuthenticationContext authContext )
    {
        AuthenticationInfo auth = null;

        if ( authContext != null )
        {
            auth = new AuthenticationInfo()
            {
                @Override
                public String getUserName()
                {
                    return authContext.get( AuthenticationContext.USERNAME );
                }

                @Override
                public String getPassword()
                {
                    return authContext.get( AuthenticationContext.PASSWORD );
                }

                @Override
                public String getPrivateKey()
                {
                    return authContext.get( AuthenticationContext.PRIVATE_KEY_PATH );
                }

                @Override
                public String getPassphrase()
                {
                    return authContext.get( AuthenticationContext.PRIVATE_KEY_PASSPHRASE );
                }
            };
        }

        return auth;
    }

    private ProxyInfoProvider getProxy( RemoteRepository repository, final AuthenticationContext authContext )
    {
        ProxyInfoProvider proxy = null;

        Proxy p = repository.getProxy();
        if ( p != null )
        {
            final ProxyInfo prox;
            if ( authContext != null )
            {
                prox = new ProxyInfo()
                {
                    @Override
                    public String getUserName()
                    {
                        return authContext.get( AuthenticationContext.USERNAME );
                    }

                    @Override
                    public String getPassword()
                    {
                        return authContext.get( AuthenticationContext.PASSWORD );
                    }

                    @Override
                    public String getNtlmDomain()
                    {
                        return authContext.get( AuthenticationContext.NTLM_DOMAIN );
                    }

                    @Override
                    public String getNtlmHost()
                    {
                        return authContext.get( AuthenticationContext.NTLM_WORKSTATION );
                    }
                };
            }
            else
            {
                prox = new ProxyInfo();
            }
            prox.setType( p.getType() );
            prox.setHost( p.getHost() );
            prox.setPort( p.getPort() );

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
                LOGGER.debug( "Could not set user agent for wagon {}: {}", wagon.getClass().getName(), e.getMessage() );
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

        Object configuration = ConfigUtils.getObject( session, null, CONFIG_PROP_CONFIG + "." + repository.getId() );
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
                if ( LOGGER.isDebugEnabled() )
                {
                    LOGGER.warn( msg, e );
                }
                else
                {
                    LOGGER.warn( msg );
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
            LOGGER.debug( "Could not disconnect wagon {}", wagon, e );
        }
    }

    private Wagon pollWagon()
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

    public int classify( Throwable error )
    {
        if ( error instanceof ResourceDoesNotExistException )
        {
            return ERROR_NOT_FOUND;
        }
        return ERROR_OTHER;
    }

    public void peek( PeekTask task )
        throws Exception
    {
        execute( task, new PeekTaskRunner( task ) );
    }

    public void get( GetTask task )
        throws Exception
    {
        execute( task, new GetTaskRunner( task ) );
    }

    public void put( PutTask task )
        throws Exception
    {
        execute( task, new PutTaskRunner( task ) );
    }

    private void execute( TransportTask task, TaskRunner runner )
        throws Exception
    {
        if ( closed.get() )
        {
            throw new IllegalStateException( "transporter closed, cannot execute task " + task );
        }
        try
        {
            WagonTransferListener listener = new WagonTransferListener( task.getListener() );
            Wagon wagon = pollWagon();
            try
            {
                wagon.addTransferListener( listener );
                runner.run( wagon );
            }
            finally
            {
                wagon.removeTransferListener( listener );
                wagons.add( wagon );
            }
        }
        catch ( Exception e )
        {
            throw WagonCancelledException.unwrap( e );
        }
    }

    private static File newTempFile()
        throws IOException
    {
        return File.createTempFile( "wagon-" + UUID.randomUUID().toString().replace( "-", "" ), ".tmp" );
    }

    private void delTempFile( File path )
    {
        if ( path != null && !path.delete() && path.exists() )
        {
            LOGGER.debug( "Could not delete temporary file {}", path );
            path.deleteOnExit();
        }
    }

    private static void copy( OutputStream os, InputStream is )
        throws IOException
    {
        byte[] buffer = new byte[1024 * 32];
        for ( int read = is.read( buffer ); read >= 0; read = is.read( buffer ) )
        {
            os.write( buffer, 0, read );
        }
    }

    public void close()
    {
        if ( closed.compareAndSet( false, true ) )
        {
            AuthenticationContext.close( repoAuthContext );
            AuthenticationContext.close( proxyAuthContext );

            for ( Wagon wagon = wagons.poll(); wagon != null; wagon = wagons.poll() )
            {
                disconnectWagon( wagon );
                releaseWagon( wagon );
            }
        }
    }

    private interface TaskRunner
    {

        void run( Wagon wagon )
            throws Exception;

    }

    private static class PeekTaskRunner
        implements TaskRunner
    {

        private final PeekTask task;

        PeekTaskRunner( PeekTask task )
        {
            this.task = task;
        }

        public void run( Wagon wagon )
            throws Exception
        {
            String src = task.getLocation().toString();
            if ( !wagon.resourceExists( src ) )
            {
                throw new ResourceDoesNotExistException( "Could not find " + src + " in "
                    + wagon.getRepository().getUrl() );
            }
        }

    }

    private class GetTaskRunner
        implements TaskRunner
    {

        private final GetTask task;

        GetTaskRunner( GetTask task )
        {
            this.task = task;
        }

        public void run( Wagon wagon )
            throws Exception
        {
            String src = task.getLocation().toString();
            File file = task.getDataFile();
            if ( file == null && wagon instanceof StreamingWagon )
            {
                OutputStream dst = null;
                try
                {
                    dst = task.newOutputStream();
                    ( (StreamingWagon) wagon ).getToStream( src, dst );
                    dst.close();
                    dst = null;
                }
                finally
                {
                    try
                    {
                        if ( dst != null )
                        {
                            dst.close();
                        }
                    }
                    catch ( final IOException e )
                    {
                        // Suppressed due to an exception already thrown in the try block.
                    }
                }
            }
            else
            {
                File dst = ( file != null ) ? file : newTempFile();
                try
                {
                    wagon.get( src, dst );
                    /*
                     * NOTE: Wagon (1.0-beta-6) doesn't create the destination file when transferring a 0-byte
                     * resource. So if the resource we asked for didn't cause any exception but doesn't show up in
                     * the dst file either, Wagon tells us in its weird way the file is empty.
                     */
                    if ( !dst.exists() && !dst.createNewFile() )
                    {
                        throw new IOException( String.format( "Failure creating file '%s'.", dst.getAbsolutePath() ) );
                    }
                    if ( file == null )
                    {
                        readTempFile( dst );
                    }
                }
                finally
                {
                    if ( file == null )
                    {
                        delTempFile( dst );
                    }
                }
            }
        }

        private void readTempFile( File dst )
            throws IOException
        {
            FileInputStream in = null;
            OutputStream out = null;
            try
            {
                in = new FileInputStream( dst );
                out = task.newOutputStream();
                copy( out, in );
                out.close();
                out = null;
                in.close();
                in = null;
            }
            finally
            {
                try
                {
                    if ( out != null )
                    {
                        out.close();
                    }
                }
                catch ( final IOException e )
                {
                    // Suppressed due to an exception already thrown in the try block.
                }
                finally
                {
                    try
                    {
                        if ( in != null )
                        {
                            in.close();
                        }
                    }
                    catch ( final IOException e )
                    {
                        // Suppressed due to an exception already thrown in the try block.
                    }
                }
            }
        }

    }

    private class PutTaskRunner
        implements TaskRunner
    {

        private final PutTask task;

        PutTaskRunner( PutTask task )
        {
            this.task = task;
        }

        public void run( Wagon wagon )
            throws Exception
        {
            String dst = task.getLocation().toString();
            File file = task.getDataFile();
            if ( file == null && wagon instanceof StreamingWagon )
            {
                InputStream src = null;
                try
                {
                    src = task.newInputStream();
                    // StreamingWagon uses an internal buffer on src input stream.
                    ( (StreamingWagon) wagon ).putFromStream( src, dst, task.getDataLength(), -1 );
                    src.close();
                    src = null;
                }
                finally
                {
                    try
                    {
                        if ( src != null )
                        {
                            src.close();
                        }
                    }
                    catch ( final IOException e )
                    {
                        // Suppressed due to an exception already thrown in the try block.
                    }
                }
            }
            else
            {
                File src = ( file != null ) ? file : createTempFile();
                try
                {
                    wagon.put( src, dst );
                }
                finally
                {
                    if ( file == null )
                    {
                        delTempFile( src );
                    }
                }
            }
        }

        private File createTempFile()
            throws IOException
        {
            File tmp = newTempFile();
            OutputStream out = null;
            InputStream in = null;
            try
            {
                in = task.newInputStream();
                out = new FileOutputStream( tmp );
                copy( out, in );
                out.close();
                out = null;
                in.close();
                in = null;
            }
            catch ( IOException e )
            {
                delTempFile( tmp );
                throw e;
            }
            finally
            {
                try
                {
                    if ( out != null )
                    {
                        out.close();
                    }
                }
                catch ( final IOException e )
                {
                    // Suppressed due to an exception already thrown in the try block.
                }
                finally
                {
                    try
                    {
                        if ( in != null )
                        {
                            in.close();
                        }
                    }
                    catch ( final IOException e )
                    {
                        // Suppressed due to an exception already thrown in the try block.
                    }
                }
            }

            return tmp;
        }

    }

}
