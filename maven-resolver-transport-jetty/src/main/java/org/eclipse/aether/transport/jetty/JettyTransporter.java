package org.eclipse.aether.transport.jetty;

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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.FileUtils;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpProxy;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.util.InputStreamRequestContent;
import org.eclipse.jetty.client.util.InputStreamResponseListener;
import org.eclipse.jetty.http.DateGenerator;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 * A transporter for HTTP/HTTPS.
 */
final class JettyTransporter
        extends AbstractTransporter
{

    private static final Pattern CONTENT_RANGE_PATTERN =
            Pattern.compile( "\\s*bytes\\s+([0-9]+)\\s*-\\s*([0-9]+)\\s*/.*" );

    private static final Logger LOGGER = LoggerFactory.getLogger( JettyTransporter.class );

    private final Map<String, ChecksumExtractor> checksumExtractors;

    private final AuthenticationContext repoAuthContext;

    private final AuthenticationContext proxyAuthContext;

    private final URI baseUri;

    private final String server;

    private final HttpProxy proxy;

    private final HttpClient client;

    private final Map<?, ?> headers;

    private final long requestTimeout;

    private final LocalState state;

    JettyTransporter( Map<String, ChecksumExtractor> checksumExtractors,
                      HttpClient httpClient,
                      RemoteRepository repository,
                      RepositorySystemSession session )
            throws NoTransporterException
    {
        this.checksumExtractors = requireNonNull( checksumExtractors, "checksum extractors must not be null" );
        try
        {
            this.baseUri = new URI( repository.getUrl() ).parseServerAuthority();
            if ( baseUri.isOpaque() )
            {
                throw new URISyntaxException( repository.getUrl(), "URL must not be opaque" );
            }
            this.server = baseUri.getHost();
            if ( server == null )
            {
                throw new URISyntaxException( repository.getUrl(), "URL lacks host name" );
            }
        }
        catch ( URISyntaxException e )
        {
            throw new NoTransporterException( repository, e.getMessage(), e );
        }
        this.proxy = toProxy( repository.getProxy() );

        this.repoAuthContext = AuthenticationContext.forRepository( session, repository );
        this.proxyAuthContext = AuthenticationContext.forProxy( session, repository );

        this.state = new LocalState( session, repository );

        this.headers = ConfigUtils.getMap( session, Collections.emptyMap(),
                ConfigurationProperties.HTTP_HEADERS + "." + repository.getId(),
                ConfigurationProperties.HTTP_HEADERS );

        String credentialEncoding = ConfigUtils.getString( session,
                ConfigurationProperties.DEFAULT_HTTP_CREDENTIAL_ENCODING,
                ConfigurationProperties.HTTP_CREDENTIAL_ENCODING + "." + repository.getId(),
                ConfigurationProperties.HTTP_CREDENTIAL_ENCODING );
        this.requestTimeout = ConfigUtils.getLong( session,
                ConfigurationProperties.DEFAULT_REQUEST_TIMEOUT,
                ConfigurationProperties.REQUEST_TIMEOUT + "." + repository.getId(),
                ConfigurationProperties.REQUEST_TIMEOUT );

        Charset credentialsCharset = Charset.forName( credentialEncoding );

        this.client = requireNonNull( httpClient );

        //        Registry<AuthSchemeProvider> authSchemeRegistry = RegistryBuilder.<AuthSchemeProvider>create()
        //                .register( AuthSchemes.BASIC, new BasicSchemeFactory( credentialsCharset ) )
        //                .register( AuthSchemes.DIGEST, new DigestSchemeFactory( credentialsCharset ) )
        //                .register( AuthSchemes.NTLM, new NTLMSchemeFactory() )
        //                .register( AuthSchemes.SPNEGO, new SPNegoSchemeFactory() )
        //                .register( AuthSchemes.KERBEROS, new KerberosSchemeFactory() )
        //                .build();
        //
        //        SocketConfig socketConfig = SocketConfig.custom()
        //                 .setSoTimeout( requestTimeout ).build();
        //
        //        RequestConfig requestConfig = RequestConfig.custom()
        //                .setConnectTimeout( connectTimeout )
        //                .setConnectionRequestTimeout( connectTimeout )
        //                .setSocketTimeout( requestTimeout ).build();
        //
        //        HttpClient client = new HttpClient(transport);
        //
        //        this.client = HttpClientBuilder.create()
        //                .setUserAgent( userAgent )
        //                .setDefaultSocketConfig( socketConfig )
        //                .setDefaultRequestConfig( requestConfig )
        //                .setDefaultAuthSchemeRegistry( authSchemeRegistry )
        //                .setConnectionManager( state.getConnectionManager() )
        //                .setConnectionManagerShared( true )
        //                .setDefaultCredentialsProvider(
        //                       toCredentialsProvider( server, repoAuthContext, proxy, proxyAuthContext )
        //                )
        //                .setProxy( proxy )
        //                .build();
    }

    private static HttpProxy toProxy( Proxy proxy )
    {
        HttpProxy httpProxy = null;
        if ( proxy != null )
        {
            httpProxy = new HttpProxy( proxy.getHost(), proxy.getPort() );
        }
        return httpProxy;
    }

    LocalState getState()
    {
        return state;
    }

//    private static CredentialsProvider toCredentialsProvider( HttpHost server, AuthenticationContext serverAuthCtx,
//                                                              HttpHost proxy, AuthenticationContext proxyAuthCtx )
//    {
//      CredentialsProvider provider = toCredentialsProvider( server.getHostName(), AuthScope.ANY_PORT, serverAuthCtx );
//        if ( proxy != null )
//        {
//            CredentialsProvider p = toCredentialsProvider( proxy.getHostName(), proxy.getPort(), proxyAuthCtx );
//            provider = new DemuxCredentialsProvider( provider, p, proxy );
//        }
//        return provider;
//    }
//
//    private static CredentialsProvider toCredentialsProvider( String host, int port, AuthenticationContext ctx )
//    {
//        DeferredCredentialsProvider provider = new DeferredCredentialsProvider();
//        if ( ctx != null )
//        {
//            AuthScope basicScope = new AuthScope( host, port );
//            provider.setCredentials( basicScope, new DeferredCredentialsProvider.BasicFactory( ctx ) );
//
//            AuthScope ntlmScope = new AuthScope( host, port, AuthScope.ANY_REALM, "ntlm" );
//            provider.setCredentials( ntlmScope, new DeferredCredentialsProvider.NtlmFactory( ctx ) );
//        }
//        return provider;
//    }
//
//    LocalState getState()
//    {
//        return state;
//    }

    private URI resolve( TransportTask task )
    {
        return UriUtils.resolve( baseUri, task.getLocation() );
    }

    @Override
    public int classify( Throwable error )
    {
        if ( error instanceof HttpResponseException
                && ( (HttpResponseException) error ).getStatusCode() == HttpStatus.NOT_FOUND_404 )
        {
            return ERROR_NOT_FOUND;
        }
        return ERROR_OTHER;
    }

    @Override
    protected void implPeek( PeekTask task )
            throws Exception
    {
        Request request = commonHeaders( client.newRequest( resolve( task ) ).method( HttpMethod.HEAD ) );
        execute( request, null );
    }

    @Override
    protected void implGet( GetTask task )
            throws Exception
    {
        boolean resume = true;
        boolean applyChecksumExtractors = true;

        final ResponseProcessor getter = new ResponseProcessor( task );
        final URI uri = resolve( task );
        Request request = commonHeaders( client.newRequest( uri ).method( HttpMethod.GET ) );
        while ( true )
        {
            try
            {
                if ( resume )
                {
                    resume( request, task );
                }
                if ( applyChecksumExtractors )
                {
                    for ( ChecksumExtractor checksumExtractor : checksumExtractors.values() )
                    {
                        checksumExtractor.prepareRequest( request );
                    }
                }
                execute( request, getter );
                break;
            }
            catch ( HttpResponseException e )
            {
                if ( resume && e.getStatusCode() == HttpStatus.PRECONDITION_FAILED_412
                        && request.getHeaders().contains( HttpHeader.RANGE ) )
                {
                    request = commonHeaders( client.newRequest( uri ).method( HttpMethod.GET ) );
                    resume = false;
                    continue;
                }
                if ( applyChecksumExtractors )
                {
                    boolean retryWithoutExtractors = false;
                    for ( ChecksumExtractor checksumExtractor : checksumExtractors.values() )
                    {
                        if ( checksumExtractor.retryWithoutExtractor( e ) )
                        {
                            retryWithoutExtractors = true;
                            break;
                        }
                    }
                    if ( retryWithoutExtractors )
                    {
                        request = commonHeaders( client.newRequest( uri ).method( HttpMethod.GET ) );
                        applyChecksumExtractors = false;
                        continue;
                    }
                }
                throw e;
            }
        }
    }

    @Override
    protected void implPut( PutTask task )
            throws Exception
    {
        final URI uri = resolve( task );
        Request request = commonHeaders( client.newRequest( uri ).method( HttpMethod.PUT ) );
        try
        {
            try ( InputStream payload = task.newInputStream() )
            {
                execute( request.body( new InputStreamRequestContent( payload ) ), null );
            }
        }
        catch ( HttpResponseException e )
        {
            if ( e.getStatusCode() == HttpStatus.EXPECTATION_FAILED_417 && request.getHeaders()
                    .contains( HttpHeader.EXPECT ) )
            {
                state.setExpectContinue( false );
                request = commonHeaders( client.newRequest( uri ).method( HttpMethod.PUT ) );
                try ( InputStream payload = task.newInputStream() )
                {
                    execute( request.body( new InputStreamRequestContent( payload ) ), null );
                }
                return;
            }
            throw e;
        }
    }

    private void execute( Request request, ResponseProcessor getter )
            throws Exception
    {
        try
        {
            prepare( request, state );
            Response response;
            if ( getter == null )
            {
                response = request.send();
            }
            else
            {
                request.send( getter.listener );
                response = getter.listener.get( requestTimeout, TimeUnit.MILLISECONDS );
            }
            try
            {
                handleStatus( response, getter );
            }
            catch ( Exception e )
            {
                response.abort( e );
                throw e;
            }
            if ( getter != null )
            {
                getter.handle( response );
            }
        }
        catch ( InterruptedException e )
        {
            throw new TransferCancelledException( "Transfer interrupted", e );
        }
        catch ( TimeoutException e )
        {
            throw new IOException( "Transfer timeout", e );
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

    private void prepare( Request request, LocalState state )
    {
        boolean put = HttpMethod.PUT.asString().equalsIgnoreCase( request.getMethod() );
        if ( state.getWebDav() == null && ( put || isPayloadPresent( request ) ) )
        {
            try
            {
                Response response = commonHeaders( client.newRequest( request.getURI() )
                        .method( HttpMethod.OPTIONS ) ).send();
                state.setWebDav( isWebDav( response ) );
            }
            catch ( Exception e )
            {
                LOGGER.debug( "Failed to prepare HTTP context", e );
            }
        }
        if ( put && Boolean.TRUE.equals( state.getWebDav() ) )
        {
            mkdirs( request.getURI(), state );
        }
    }

    private boolean isWebDav( Response response )
    {
        return response.getHeaders().contains( "Dav" );
    }

    @SuppressWarnings( "checkstyle:magicnumber" )
    private void mkdirs( URI uri, LocalState state )
    {
        List<URI> dirs = UriUtils.getDirectories( baseUri, uri );
        int index = 0;
        for ( ; index < dirs.size(); index++ )
        {
            try
            {
                Response response = commonHeaders(
                        client.newRequest( dirs.get( index ) ).method( HttpMethod.MKCOL ) ).send();
                int status = response.getStatus();
                if ( status < 300 || status == HttpStatus.METHOD_NOT_ALLOWED_405 )
                {
                    break;
                }
                else if ( status == HttpStatus.CONFLICT_409 )
                {
                    continue;
                }
                handleStatus( response, null );
            }
            catch ( Exception e )
            {
                LOGGER.debug( "Failed to create parent directory {}", dirs.get( index ), e );
                return;
            }
        }
        for ( index--; index >= 0; index-- )
        {
            try
            {
                Response response = commonHeaders(
                        client.newRequest( dirs.get( index ) ).method( HttpMethod.MKCOL ) ).send();
                handleStatus( response, null );
            }
            catch ( Exception e )
            {
                LOGGER.debug( "Failed to create parent directory {}", dirs.get( index ), e );
                return;
            }
        }
    }

    private boolean isPayloadPresent( Request request )
    {
        Request.Content content = request.getBody();
        return content != null && content.getLength() != 0;
    }

    private Request commonHeaders( Request request )
    {
        request.headers( h ->
        {
            h.add( HttpHeader.CACHE_CONTROL, "no-cache, no-store" );

            if ( state.isExpectContinue() && isPayloadPresent( request ) )
            {
                h.add( HttpHeader.EXPECT, "100-continue" );
            }

            for ( Map.Entry<?, ?> entry : headers.entrySet() )
            {
                if ( !( entry.getKey() instanceof String ) )
                {
                    continue;
                }
                if ( entry.getValue() instanceof String )
                {
                    h.add( entry.getKey().toString(), entry.getValue().toString() );
                }
                else
                {
                    h.remove( entry.getKey().toString() );
                }
            }

            if ( !state.isExpectContinue() )
            {
                h.remove( HttpHeader.EXPECT );
            }
        } );
        return request;
    }

    @SuppressWarnings( "checkstyle:magicnumber" )
    private void resume( Request request, GetTask task )
    {
        long resumeOffset = task.getResumeOffset();
        if ( resumeOffset > 0L && task.getDataFile() != null )
        {
            request.headers( h ->
            {
                h.add( HttpHeader.RANGE, "bytes=" + resumeOffset + "-" );
                h.add( HttpHeader.IF_UNMODIFIED_SINCE,
                        DateGenerator.formatDate( task.getDataFile().lastModified() - 60L * 1000L ) );
                h.add( HttpHeader.ACCEPT_ENCODING, "identity" );
            } );
        }
    }

    private void handleStatus( Response response, ResponseProcessor getter )
            throws HttpResponseException
    {
        int status = response.getStatus();
        if ( status >= HttpStatus.MULTIPLE_CHOICES_300 )
        {
            if ( getter != null )
            {
                try
                {
                    try ( InputStream inputStream = getter.listener.getInputStream() )
                    {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        inputStream.transferTo( baos );
                        LOGGER.info( "Response: {}", new String( baos.toByteArray(), StandardCharsets.UTF_8 ) );
                    }
                }
                catch ( IOException e )
                {
                    LOGGER.warn( "Count not consume body", e );
                }
            }
            throw new HttpResponseException( response, response.getReason() + " (" + status + ")" );
        }
    }

    @Override
    protected void implClose()
    {
        AuthenticationContext.close( repoAuthContext );
        AuthenticationContext.close( proxyAuthContext );
    }

    private class ResponseProcessor
    {
        private final GetTask task;

        private final InputStreamResponseListener listener;

        ResponseProcessor( GetTask task )
        {
            this.task = task;
            this.listener = new InputStreamResponseListener();
        }

        public void handle( Response response )
                throws IOException, TransferCancelledException
        {
            final long contentLength = response.getHeaders().getLongField( HttpHeader.CONTENT_LENGTH );
            long offset = 0L, length = contentLength;
            String range = response.getHeaders().get( HttpHeader.CONTENT_RANGE );
            if ( range != null )
            {
                Matcher m = CONTENT_RANGE_PATTERN.matcher( range );
                if ( !m.matches() )
                {
                    throw new IOException( "Invalid Content-Range header for partial download: " + range );
                }
                offset = Long.parseLong( m.group( 1 ) );
                length = Long.parseLong( m.group( 2 ) ) + 1L;
                if ( offset < 0L || offset >= length || ( offset > 0L && offset != task.getResumeOffset() ) )
                {
                    throw new IOException( "Invalid Content-Range header for partial download from offset "
                            + task.getResumeOffset() + ": " + range );
                }
            }

            final boolean resume = offset > 0L;
            final File dataFile = task.getDataFile();
            if ( dataFile == null )
            {
                try ( InputStream is = listener.getInputStream() )
                {
                    utilGet( task, is, true, length, resume );
                    extractChecksums( response );
                }
            }
            else
            {
                try ( FileUtils.CollocatedTempFile tempFile = FileUtils.newTempFile( dataFile.toPath() ) )
                {
                    task.setDataFile( tempFile.getPath().toFile(), resume );
                    if ( resume && Files.isRegularFile( dataFile.toPath() ) )
                    {
                        try ( InputStream inputStream = Files.newInputStream( dataFile.toPath() ) )
                        {
                            Files.copy( inputStream, tempFile.getPath(), StandardCopyOption.REPLACE_EXISTING );
                        }
                    }
                    try ( InputStream is = listener.getInputStream() )
                    {
                        utilGet( task, is, true, length, resume );
                    }
                    tempFile.move();
                }
                finally
                {
                    task.setDataFile( dataFile );
                }
            }
            extractChecksums( response );
        }

        private void extractChecksums( Response response )
        {
            for ( Map.Entry<String, ChecksumExtractor> extractorEntry : checksumExtractors.entrySet() )
            {
                Map<String, String> checksums = extractorEntry.getValue().extractChecksums( response );
                if ( checksums != null )
                {
                    checksums.forEach( task::setChecksum );
                    return;
                }
            }
        }
    }
}
