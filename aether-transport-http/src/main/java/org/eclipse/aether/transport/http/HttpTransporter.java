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

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.params.AuthParams;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.conn.params.ConnRouteParams;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DecompressingHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.util.EntityUtils;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.AbstractTransporter;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.TransportTask;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.util.ConfigUtils;

/**
 * A transporter for HTTP/HTTPS.
 */
final class HttpTransporter
    extends AbstractTransporter
{

    private static final Pattern CONTENT_RANGE_PATTERN =
        Pattern.compile( "\\s*bytes\\s+([0-9]+)\\s*-\\s*([0-9]+)\\s*/.*" );

    private final Logger logger;

    private final AuthenticationContext repoAuthContext;

    private final AuthenticationContext proxyAuthContext;

    private final URI baseUri;

    private final HttpHost server;

    private final HttpHost proxy;

    private final HttpClient client;

    private final Map<?, ?> headers;

    private final LocalState state;

    public HttpTransporter( RemoteRepository repository, RepositorySystemSession session, Logger logger )
        throws NoTransporterException
    {
        if ( !"http".equalsIgnoreCase( repository.getProtocol() )
            && !"https".equalsIgnoreCase( repository.getProtocol() ) )
        {
            throw new NoTransporterException( repository );
        }
        this.logger = logger;
        try
        {
            baseUri = new URI( repository.getUrl() ).parseServerAuthority();
            if ( baseUri.isOpaque() )
            {
                throw new URISyntaxException( repository.getUrl(), "URL must not be opaque" );
            }
            server = URIUtils.extractHost( baseUri );
            if ( server == null )
            {
                throw new URISyntaxException( repository.getUrl(), "URL lacks host name" );
            }
        }
        catch ( URISyntaxException e )
        {
            throw new NoTransporterException( repository, e.getMessage(), e );
        }
        proxy = toHost( repository.getProxy() );

        repoAuthContext = AuthenticationContext.forRepository( session, repository );
        proxyAuthContext = AuthenticationContext.forProxy( session, repository );

        state = new LocalState( session, repository, new SslConfig( session, repoAuthContext ) );

        headers =
            ConfigUtils.getMap( session, Collections.emptyMap(), ConfigurationProperties.HTTP_HEADERS + "."
                + repository.getId(), ConfigurationProperties.HTTP_HEADERS );

        DefaultHttpClient client = new DefaultHttpClient( state.getConnectionManager() );

        configureClient( client.getParams(), session, repository, proxy );

        DeferredCredentialsProvider credsProvider = new DeferredCredentialsProvider();
        addCredentials( credsProvider, server.getHostName(), AuthScope.ANY_PORT, repoAuthContext );
        if ( proxy != null )
        {
            addCredentials( credsProvider, proxy.getHostName(), proxy.getPort(), proxyAuthContext );
        }
        client.setCredentialsProvider( credsProvider );

        this.client = new DecompressingHttpClient( client );
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

    private static void configureClient( HttpParams params, RepositorySystemSession session,
                                         RemoteRepository repository, HttpHost proxy )
    {
        AuthParams.setCredentialCharset( params,
                                         ConfigUtils.getString( session,
                                                                ConfigurationProperties.DEFAULT_HTTP_CREDENTIAL_ENCODING,
                                                                ConfigurationProperties.HTTP_CREDENTIAL_ENCODING + "."
                                                                    + repository.getId(),
                                                                ConfigurationProperties.HTTP_CREDENTIAL_ENCODING ) );
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
    }

    private static void addCredentials( DeferredCredentialsProvider provider, String host, int port,
                                        AuthenticationContext ctx )
    {
        if ( ctx != null )
        {
            AuthScope basicScope = new AuthScope( host, port );
            provider.setCredentials( basicScope, new DeferredCredentialsProvider.BasicFactory( ctx ) );

            AuthScope ntlmScope = new AuthScope( host, port, AuthScope.ANY_REALM, "ntlm" );
            provider.setCredentials( ntlmScope, new DeferredCredentialsProvider.NtlmFactory( ctx ) );
        }
    }

    LocalState getState()
    {
        return state;
    }

    private URI resolve( TransportTask task )
    {
        return UriUtils.resolve( baseUri, task.getLocation() );
    }

    public int classify( Throwable error )
    {
        if ( error instanceof HttpResponseException
            && ( (HttpResponseException) error ).getStatusCode() == HttpStatus.SC_NOT_FOUND )
        {
            return ERROR_NOT_FOUND;
        }
        return ERROR_OTHER;
    }

    @Override
    protected void implPeek( PeekTask task )
        throws Exception
    {
        HttpHead request = commonHeaders( new HttpHead( resolve( task ) ) );
        execute( request, null );
    }

    @Override
    protected void implGet( GetTask task )
        throws Exception
    {
        EntityGetter getter = new EntityGetter( task );
        HttpGet request = commonHeaders( new HttpGet( resolve( task ) ) );
        resume( request, task );
        try
        {
            execute( request, getter );
        }
        catch ( HttpResponseException e )
        {
            if ( e.getStatusCode() == HttpStatus.SC_PRECONDITION_FAILED && request.containsHeader( HttpHeaders.RANGE ) )
            {
                request = commonHeaders( new HttpGet( request.getURI() ) );
                execute( request, getter );
                return;
            }
            throw e;
        }
    }

    @Override
    protected void implPut( PutTask task )
        throws Exception
    {
        PutTaskEntity entity = new PutTaskEntity( task );
        HttpPut request = commonHeaders( entity( new HttpPut( resolve( task ) ), entity ) );
        try
        {
            execute( request, null );
        }
        catch ( HttpResponseException e )
        {
            if ( e.getStatusCode() == HttpStatus.SC_EXPECTATION_FAILED && request.containsHeader( HttpHeaders.EXPECT ) )
            {
                state.setExpectContinue( false );
                request = commonHeaders( entity( new HttpPut( request.getURI() ), entity ) );
                execute( request, null );
                return;
            }
            throw e;
        }
    }

    private void execute( HttpUriRequest request, EntityGetter getter )
        throws Exception
    {
        try
        {
            SharingHttpContext context = new SharingHttpContext( state );
            prepare( request, context );
            HttpResponse response = client.execute( server, request, context );
            try
            {
                context.close();
                handleStatus( response );
                if ( getter != null )
                {
                    getter.handle( response );
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

    private void prepare( HttpUriRequest request, SharingHttpContext context )
    {
        boolean put = HttpPut.METHOD_NAME.equalsIgnoreCase( request.getMethod() );
        if ( state.getWebDav() == null && ( put || isPayloadPresent( request ) ) )
        {
            try
            {
                HttpOptions req = commonHeaders( new HttpOptions( request.getURI() ) );
                HttpResponse response = client.execute( server, req, context );
                state.setWebDav( isWebDav( response ) );
                EntityUtils.consumeQuietly( response.getEntity() );
            }
            catch ( IOException e )
            {
                logger.debug( "Failed to prepare HTTP context", e );
            }
        }
        if ( put && Boolean.TRUE.equals( state.getWebDav() ) )
        {
            mkdirs( request.getURI(), context );
        }
    }

    private boolean isWebDav( HttpResponse response )
    {
        return response.containsHeader( HttpHeaders.DAV );
    }

    private void mkdirs( URI uri, SharingHttpContext context )
    {
        List<URI> dirs = UriUtils.getDirectories( baseUri, uri );
        int index = 0;
        for ( ; index < dirs.size(); index++ )
        {
            try
            {
                HttpResponse response =
                    client.execute( server, commonHeaders( new HttpMkCol( dirs.get( index ) ) ), context );
                try
                {
                    int status = response.getStatusLine().getStatusCode();
                    if ( status < 300 || status == HttpStatus.SC_METHOD_NOT_ALLOWED )
                    {
                        // directory was created or already existed
                        break;
                    }
                    else if ( status == HttpStatus.SC_CONFLICT )
                    {
                        // parent directory needs to be created first
                        continue;
                    }
                    handleStatus( response );
                }
                finally
                {
                    EntityUtils.consumeQuietly( response.getEntity() );
                }
            }
            catch ( IOException e )
            {
                logger.debug( "Failed to create parent directory " + dirs.get( index ), e );
                return;
            }
        }
        for ( index--; index >= 0; index-- )
        {
            try
            {
                HttpResponse response =
                    client.execute( server, commonHeaders( new HttpMkCol( dirs.get( index ) ) ), context );
                try
                {
                    handleStatus( response );
                }
                finally
                {
                    EntityUtils.consumeQuietly( response.getEntity() );
                }
            }
            catch ( IOException e )
            {
                logger.debug( "Failed to create parent directory " + dirs.get( index ), e );
                return;
            }
        }
    }

    private <T extends HttpEntityEnclosingRequest> T entity( T request, HttpEntity entity )
    {
        request.setEntity( entity );
        return request;
    }

    private boolean isPayloadPresent( HttpUriRequest request )
    {
        if ( request instanceof HttpEntityEnclosingRequest )
        {
            HttpEntity entity = ( (HttpEntityEnclosingRequest) request ).getEntity();
            return entity != null && entity.getContentLength() != 0;
        }
        return false;
    }

    private <T extends HttpUriRequest> T commonHeaders( T request )
    {
        request.setHeader( HttpHeaders.CACHE_CONTROL, "no-cache, no-store" );
        request.setHeader( HttpHeaders.PRAGMA, "no-cache" );

        if ( state.isExpectContinue() && isPayloadPresent( request ) )
        {
            request.setHeader( HttpHeaders.EXPECT, "100-continue" );
        }

        for ( Map.Entry<?, ?> entry : headers.entrySet() )
        {
            if ( !( entry.getKey() instanceof String ) )
            {
                continue;
            }
            if ( entry.getValue() instanceof String )
            {
                request.setHeader( entry.getKey().toString(), entry.getValue().toString() );
            }
            else
            {
                request.removeHeaders( entry.getKey().toString() );
            }
        }

        if ( !state.isExpectContinue() )
        {
            request.removeHeaders( HttpHeaders.EXPECT );
        }

        return request;
    }

    private <T extends HttpUriRequest> T resume( T request, GetTask task )
    {
        long resumeOffset = task.getResumeOffset();
        if ( resumeOffset > 0 && task.getDataFile() != null )
        {
            request.setHeader( HttpHeaders.RANGE, "bytes=" + Long.toString( resumeOffset ) + '-' );
            request.setHeader( HttpHeaders.IF_UNMODIFIED_SINCE,
                               DateUtils.formatDate( new Date( task.getDataFile().lastModified() - 60 * 1000 ) ) );
            request.setHeader( HttpHeaders.ACCEPT_ENCODING, "identity" );
        }
        return request;
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

    @Override
    protected void implClose()
    {
        AuthenticationContext.close( repoAuthContext );
        AuthenticationContext.close( proxyAuthContext );
        state.close();
    }

    private class EntityGetter
    {

        private final GetTask task;

        public EntityGetter( GetTask task )
        {
            this.task = task;
        }

        public void handle( HttpResponse response )
            throws IOException, TransferCancelledException
        {
            HttpEntity entity = response.getEntity();
            if ( entity == null )
            {
                entity = new ByteArrayEntity( new byte[0] );
            }

            long offset = 0, length = entity.getContentLength();
            String range = getHeader( response, HttpHeaders.CONTENT_RANGE );
            if ( range != null )
            {
                Matcher m = CONTENT_RANGE_PATTERN.matcher( range );
                if ( !m.matches() )
                {
                    throw new IOException( "Invalid Content-Range header for partial download: " + range );
                }
                offset = Long.parseLong( m.group( 1 ) );
                length = Long.parseLong( m.group( 2 ) ) + 1;
                if ( offset < 0 || offset >= length || ( offset > 0 && offset != task.getResumeOffset() ) )
                {
                    throw new IOException( "Invalid Content-Range header for partial download from offset "
                        + task.getResumeOffset() + ": " + range );
                }
            }

            InputStream is = entity.getContent();
            utilGet( task, is, true, length, offset > 0 );
            extractChecksums( response );
        }

        private void extractChecksums( HttpResponse response )
        {
            // Nexus-style, ETag: "{SHA1{d40d68ba1f88d8e9b0040f175a6ff41928abd5e7}}"
            String etag = getHeader( response, HttpHeaders.ETAG );
            if ( etag != null )
            {
                int start = etag.indexOf( "SHA1{" ), end = etag.indexOf( "}", start + 5 );
                if ( start >= 0 && end > start )
                {
                    task.setChecksum( "SHA-1", etag.substring( start + 5, end ) );
                }
            }
        }

        private String getHeader( HttpResponse response, String name )
        {
            Header header = response.getFirstHeader( name );
            return ( header != null ) ? header.getValue() : null;
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
                utilPut( task, os, false );
            }
            catch ( TransferCancelledException e )
            {
                throw (IOException) new InterruptedIOException().initCause( e );
            }
        }

    }

}
