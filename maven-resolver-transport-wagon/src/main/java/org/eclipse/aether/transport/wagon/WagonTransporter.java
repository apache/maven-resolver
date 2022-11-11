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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.maven.wagon.ConnectionException;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.StreamingWagon;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.WagonException;
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
import org.eclipse.aether.util.FileUtils;
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
        if ( wagonHint.isEmpty() )
        {
            throw new NoTransporterException( repository );
        }

        try
        {
            wagons.add( lookupWagon() );
        }
        catch ( Exception e )
        {
            LOGGER.debug( "No transport", e );
            throw new NoTransporterException( repository, e );
        }

        repoAuthContext = AuthenticationContext.forRepository( session, repository );
        proxyAuthContext = AuthenticationContext.forProxy( session, repository );

        wagonAuth = getAuthenticationInfo( repoAuthContext );
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

    private AuthenticationInfo getAuthenticationInfo( final AuthenticationContext authContext )
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

            proxy = protocol -> prox;
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
            throws WagonException
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
            catch ( InvocationTargetException | IllegalAccessException | RuntimeException e )
            {
                LOGGER.debug( "Could not set user agent for Wagon {}", wagon.getClass().getName(), e );
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
                LOGGER.warn( "Could not apply configuration for {} to Wagon {}",
                        repository.getId(), wagon.getClass().getName(), e );
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
        catch ( ConnectionException e )
        {
            LOGGER.debug( "Could not disconnect Wagon {}", wagon, e );
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

    @Override
    public int classify( Throwable error )
    {
        if ( error instanceof ResourceDoesNotExistException )
        {
            return ERROR_NOT_FOUND;
        }
        return ERROR_OTHER;
    }

    @Override
    public void peek( PeekTask task )
            throws Exception
    {
        execute( task, new PeekTaskRunner( task ) );
    }

    @Override
    public void get( GetTask task )
            throws Exception
    {
        execute( task, new GetTaskRunner( task ) );
    }

    @Override
    public void put( PutTask task )
            throws Exception
    {
        execute( task, new PutTaskRunner( task ) );
    }

    private void execute( TransportTask task, TaskRunner runner )
            throws Exception
    {
        Objects.requireNonNull( task, "task cannot be null" );

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
        catch ( RuntimeException e )
        {
            throw WagonCancelledException.unwrap( e );
        }
    }

    @Override
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
                throws IOException, WagonException;

    }

    private static class PeekTaskRunner
            implements TaskRunner
    {

        private final PeekTask task;

        PeekTaskRunner( PeekTask task )
        {
            this.task = task;
        }

        @Override
        public void run( Wagon wagon )
                throws WagonException
        {
            String src = task.getLocation().toString();
            if ( !wagon.resourceExists( src ) )
            {
                throw new ResourceDoesNotExistException( "Could not find " + src + " in "
                        + wagon.getRepository().getUrl() );
            }
        }

    }

    private static class GetTaskRunner
            implements TaskRunner
    {

        private final GetTask task;

        GetTaskRunner( GetTask task )
        {
            this.task = task;
        }

        @Override
        public void run( Wagon wagon )
                throws IOException, WagonException
        {
            final String src = task.getLocation().toString();
            final File file = task.getDataFile();
            if ( file == null && wagon instanceof StreamingWagon )
            {
                try ( OutputStream dst = task.newOutputStream() )
                {
                    ( (StreamingWagon) wagon ).getToStream( src, dst );
                }
            }
            else
            {
                // if file == null -> $TMP used, otherwise we place tmp file next to file
                try ( FileUtils.TempFile tempFile = file == null ? FileUtils.newTempFile()
                        : FileUtils.newTempFile( file.toPath() ) )
                {
                    File dst = tempFile.getPath().toFile();
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

                    if ( file != null )
                    {
                        ( (FileUtils.CollocatedTempFile) tempFile ).move();
                    }
                    else
                    {
                        try ( OutputStream outputStream = task.newOutputStream() )
                        {
                            Files.copy( dst.toPath(), outputStream );
                        }
                    }
                }
            }
        }
    }

    private static class PutTaskRunner
            implements TaskRunner
    {

        private final PutTask task;

        PutTaskRunner( PutTask task )
        {
            this.task = task;
        }

        @Override
        public void run( Wagon wagon )
                throws WagonException, IOException
        {
            final String dst = task.getLocation().toString();
            final File file = task.getDataFile();
            if ( file == null && wagon instanceof StreamingWagon )
            {
                try ( InputStream src = task.newInputStream() )
                {
                    // StreamingWagon uses an internal buffer on src input stream.
                    ( (StreamingWagon) wagon ).putFromStream( src, dst, task.getDataLength(), -1 );
                }
            }
            else if ( file == null )
            {
                try ( FileUtils.TempFile tempFile = FileUtils.newTempFile() )
                {
                    try ( InputStream inputStream = task.newInputStream() )
                    {
                        Files.copy( inputStream, tempFile.getPath(), StandardCopyOption.REPLACE_EXISTING );
                    }
                    wagon.put( tempFile.getPath().toFile(), dst );
                }
            }
            else
            {
                wagon.put( file, dst );
            }
        }
    }
}
