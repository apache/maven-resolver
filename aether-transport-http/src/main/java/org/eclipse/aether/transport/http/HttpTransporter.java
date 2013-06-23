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
package org.eclipse.aether.transport.http;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpMessage;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.params.AuthParams;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.conn.params.ConnRouteParams;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.SchemeSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DecompressingHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.util.EntityUtils;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.TransportListener;
import org.eclipse.aether.spi.connector.transport.TransportTask;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.util.ConfigUtils;

/**
 * A transporter for HTTP/HTTPS.
 */
final class HttpTransporter
    implements Transporter
{

    private final AuthenticationContext repoAuthContext;

    private final AuthenticationContext proxyAuthContext;

    private final AtomicBoolean closed;

    private final URI baseUri;

    private final HttpClient client;

    private final Map<?, ?> headers;

    private final Map<String, Object> context;

    public HttpTransporter( RemoteRepository repository, RepositorySystemSession session, Logger logger )
        throws NoTransporterException
    {
        if ( !"http".equalsIgnoreCase( repository.getProtocol() )
            && !"https".equalsIgnoreCase( repository.getProtocol() ) )
        {
            throw new NoTransporterException( repository );
        }
        try
        {
            baseUri = new URI( repository.getUrl() );
            if ( baseUri.isOpaque() )
            {
                throw new URISyntaxException( repository.getUrl(), "URL must not be opaque" );
            }
        }
        catch ( URISyntaxException e )
        {
            throw new NoTransporterException( repository, e );
        }

        repoAuthContext = AuthenticationContext.forRepository( session, repository );
        proxyAuthContext = AuthenticationContext.forProxy( session, repository );
        closed = new AtomicBoolean();

        headers =
            ConfigUtils.getMap( session, Collections.emptyMap(), ConfigurationProperties.HTTP_HEADERS + "."
                + repository.getId(), ConfigurationProperties.HTTP_HEADERS );
        context = SharingHttpContext.newGlobals();
        client = newClient( session, repository, repoAuthContext, proxyAuthContext );
    }

    private static HttpClient newClient( RepositorySystemSession session, RemoteRepository repository,
                                         AuthenticationContext repoAuthContext, AuthenticationContext proxyAuthContext )
    {
        SchemeRegistry schemeReg = new SchemeRegistry();
        schemeReg.register( new Scheme( "http", 80, PlainSocketFactory.getSocketFactory() ) );
        schemeReg.register( new Scheme( "https", 443, newSSLSocketFactory( repoAuthContext ) ) );

        PoolingClientConnectionManager connMgr = new PoolingClientConnectionManager( schemeReg );
        connMgr.setDefaultMaxPerRoute( connMgr.getMaxTotal() );

        DefaultHttpClient client = new DefaultHttpClient( connMgr );

        HttpParams params = client.getParams();
        AuthParams.setCredentialCharset( params,
                                         ConfigUtils.getString( session,
                                                                ConfigurationProperties.DEFAULT_HTTP_CREDENTIAL_ENCODING,
                                                                ConfigurationProperties.HTTP_CREDENTIAL_ENCODING + "."
                                                                    + repository.getId(),
                                                                ConfigurationProperties.HTTP_CREDENTIAL_ENCODING ) );
        HttpHost proxy = toHost( repository.getProxy() );
        ConnRouteParams.setDefaultProxy( params, proxy );
        HttpConnectionParams.setConnectionTimeout( params,
                                                   ConfigUtils.getInteger( session,
                                                                           ConfigurationProperties.DEFAULT_CONNECT_TIMEOUT,
                                                                           ConfigurationProperties.CONNECT_TIMEOUT
                                                                               + "." + repository.getId(),
                                                                           ConfigurationProperties.CONNECT_TIMEOUT ) );
        HttpConnectionParams.setSoTimeout( params,
                                           ConfigUtils.getInteger( session,
                                                                   ConfigurationProperties.DEFAULT_REQUEST_TIMEOUT,
                                                                   ConfigurationProperties.REQUEST_TIMEOUT + "."
                                                                       + repository.getId(),
                                                                   ConfigurationProperties.REQUEST_TIMEOUT ) );
        HttpProtocolParams.setUserAgent( params, ConfigUtils.getString( session,
                                                                        ConfigurationProperties.DEFAULT_USER_AGENT,
                                                                        ConfigurationProperties.USER_AGENT ) );

        DeferredCredentialsProvider credsProvider = new DeferredCredentialsProvider();
        addCredentials( credsProvider, URI.create( repository.getUrl() ).getHost(), repoAuthContext );
        if ( proxy != null )
        {
            addCredentials( credsProvider, proxy.getHostName(), proxyAuthContext );
        }
        client.setCredentialsProvider( credsProvider );

        return new DecompressingHttpClient( client );
    }

    private static SchemeSocketFactory newSSLSocketFactory( AuthenticationContext authContext )
    {
        SSLContext sslContext =
            ( authContext != null ) ? authContext.get( AuthenticationContext.SSL_CONTEXT, SSLContext.class ) : null;
        SSLSocketFactory socketFactory;
        if ( sslContext != null )
        {
            socketFactory = sslContext.getSocketFactory();
        }
        else
        {
            socketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        }

        HostnameVerifier verifier =
            ( authContext != null ) ? authContext.get( AuthenticationContext.SSL_HOSTNAME_VERIFIER,
                                                       HostnameVerifier.class ) : null;
        X509HostnameVerifier hostnameVerifier;
        if ( verifier != null )
        {
            hostnameVerifier = X509HostnameVerifierAdapter.adapt( verifier );
        }
        else
        {
            hostnameVerifier = org.apache.http.conn.ssl.SSLSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER;
        }

        return new org.apache.http.conn.ssl.SSLSocketFactory( socketFactory, hostnameVerifier );
    }

    private static HttpHost toHost( Proxy proxy )
    {
        HttpHost host = null;
        if ( proxy != null )
        {
            host = new HttpHost( proxy.getHost(), proxy.getPort() );
        }
        return host;
    }

    private static void addCredentials( DeferredCredentialsProvider provider, String host, AuthenticationContext ctx )
    {
        if ( ctx != null )
        {
            AuthScope basicScope = new AuthScope( host, AuthScope.ANY_PORT );
            provider.setCredentials( basicScope, new DeferredCredentialsProvider.BasicFactory( ctx ) );

            AuthScope ntlmScope = new AuthScope( host, AuthScope.ANY_PORT, AuthScope.ANY_REALM, "ntlm" );
            provider.setCredentials( ntlmScope, new DeferredCredentialsProvider.NtlmFactory( ctx ) );
        }
    }

    private URI resolve( TransportTask task )
    {
        return resolve( baseUri, task.getLocation() );
    }

    static URI resolve( URI base, URI ref )
    {
        String path = ref.getRawPath();
        if ( path != null && path.length() > 0 )
        {
            path = base.getRawPath();
            if ( path == null || !path.endsWith( "/" ) )
            {
                try
                {
                    base = new URI( base.getScheme(), base.getAuthority(), base.getPath() + '/', null, null );
                }
                catch ( URISyntaxException e )
                {
                    throw new IllegalStateException( e );
                }
            }
        }
        return URIUtils.resolve( base, ref );
    }

    public int classify( Throwable error )
    {
        if ( error instanceof HttpResponseException && ( (HttpResponseException) error ).getStatusCode() == 404 )
        {
            return ERROR_NOT_FOUND;
        }
        return ERROR_OTHER;
    }

    public void peek( PeekTask task )
        throws Exception
    {
        failIfClosed( task );

        HttpHead request = new HttpHead( resolve( task ) );
        execute( request, null );
    }

    public void get( GetTask task )
        throws Exception
    {
        failIfClosed( task );

        HttpGet request = new HttpGet( resolve( task ) );
        execute( request, new EntityGetter( task ) );
    }

    public void put( PutTask task )
        throws Exception
    {
        failIfClosed( task );

        HttpPut request = new HttpPut( resolve( task ) );
        request.setEntity( new PutTaskEntity( task ) );
        execute( request, null );
    }

    private void execute( HttpUriRequest request, EntityGetter getter )
        throws Exception
    {
        try
        {
            applyHeaders( request );
            HttpResponse response = client.execute( request, new SharingHttpContext( context ) );
            try
            {
                handleStatus( response );
                if ( getter != null )
                {
                    getter.handle( response.getEntity() );
                }
            }
            finally
            {
                EntityUtils.consumeQuietly( response.getEntity() );
            }
        }
        catch ( IOException e )
        {
            if ( e.getCause() instanceof TransferCancelledException )
            {
                throw (Exception) e.getCause();
            }
            throw e;
        }
    }

    private void applyHeaders( HttpMessage msg )
    {
        msg.setHeader( "Cache-Control", "no-cache, no-store" );
        msg.setHeader( "Pragma", "no-cache" );
        for ( Map.Entry<?, ?> entry : headers.entrySet() )
        {
            msg.setHeader( entry.getKey().toString(), entry.getValue().toString() );
        }
    }

    private void handleStatus( HttpResponse response )
        throws HttpResponseException
    {
        int status = response.getStatusLine().getStatusCode();
        if ( status >= 300 )
        {
            throw new HttpResponseException( status, response.getStatusLine().getReasonPhrase() + " (" + status + ")" );
        }
    }

    private static void copy( OutputStream os, InputStream is, TransportListener listener )
        throws IOException, TransferCancelledException
    {
        ByteBuffer buffer = ByteBuffer.allocate( 1024 * 32 );
        byte[] array = buffer.array();
        for ( int read = is.read( array ); read >= 0; read = is.read( array ) )
        {
            os.write( array, 0, read );
            buffer.rewind();
            buffer.limit( read );
            listener.transportProgressed( buffer );
        }
    }

    private static void close( Closeable file )
    {
        if ( file != null )
        {
            try
            {
                file.close();
            }
            catch ( IOException e )
            {
                // irrelevant
            }
        }
    }

    private void failIfClosed( TransportTask task )
    {
        if ( closed.get() )
        {
            throw new IllegalStateException( "transporter closed, cannot execute task " + task );
        }
    }

    public void close()
    {
        if ( closed.compareAndSet( false, true ) )
        {
            AuthenticationContext.close( repoAuthContext );
            AuthenticationContext.close( proxyAuthContext );

            client.getConnectionManager().shutdown();
        }
    }

    private class EntityGetter
    {

        private final GetTask task;

        public EntityGetter( GetTask task )
        {
            this.task = task;
        }

        public void handle( HttpEntity entity )
            throws IOException, TransferCancelledException
        {
            if ( entity == null )
            {
                entity = new ByteArrayEntity( new byte[0] );
            }
            InputStream is = entity.getContent();
            try
            {
                task.getListener().transportStarted( 0, entity.getContentLength() );
                OutputStream os = task.newOutputStream();
                try
                {
                    copy( os, is, task.getListener() );
                    os.close();
                }
                finally
                {
                    close( os );
                }
            }
            finally
            {
                close( is );
            }
        }

    }

    private class PutTaskEntity
        extends AbstractHttpEntity
    {

        private final PutTask task;

        public PutTaskEntity( PutTask task )
        {
            this.task = task;
        }

        public boolean isRepeatable()
        {
            return true;
        }

        public boolean isStreaming()
        {
            return false;
        }

        public long getContentLength()
        {
            return task.getDataLength();
        }

        public InputStream getContent()
            throws IOException
        {
            return task.newInputStream();
        }

        public void writeTo( OutputStream os )
            throws IOException
        {
            try
            {
                task.getListener().transportStarted( 0, task.getDataLength() );
                InputStream is = task.newInputStream();
                try
                {
                    copy( os, is, task.getListener() );
                    os.flush();
                }
                finally
                {
                    close( is );
                }
            }
            catch ( TransferCancelledException e )
            {
                throw (IOException) new InterruptedIOException().initCause( e );
            }
        }

    }

}
