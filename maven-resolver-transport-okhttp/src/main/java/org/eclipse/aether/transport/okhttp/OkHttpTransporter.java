package org.eclipse.aether.transport.okhttp;

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

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Authenticator;
import okhttp3.ConnectionSpec;
import okhttp3.Credentials;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.internal.tls.OkHostnameVerifier;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.AbstractTransporter;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.TransportTask;
import org.eclipse.aether.transfer.NoTransporterException;
import org.eclipse.aether.util.ConfigUtils;
import org.eclipse.aether.util.FileUtils;

/**
 * A transporter for HTTP/HTTPS.
 *
 * @since TBD
 */
final class OkHttpTransporter
        extends AbstractTransporter
{
    private static final int MULTIPLE_CHOICES = 300;

    private static final int NOT_FOUND = 404;

    private static final int PROXY_AUTH = 407;

    private static final int PRECONDITION_FAILED = 412;

    private static final long MODIFICATION_THRESHOLD = 60L * 1000L;

    private static final String ACCEPT_ENCODING = "Accept-Encoding";

    private static final String AUTHORIZATION = "Authorization";

    private static final String CONTENT_RANGE = "Content-Range";

    private static final String IF_UNMODIFIED_SINCE = "If-Unmodified-Since";

    private static final String PROXY_AUTHORIZATION = "Proxy-Authorization";

    private static final String RANGE = "Range";

    private static final String USER_AGENT = "User-Agent";

    private static final Pattern CONTENT_RANGE_PATTERN =
            Pattern.compile( "\\s*bytes\\s+([0-9]+)\\s*-\\s*([0-9]+)\\s*/.*" );

    private final URI baseUri;

    private final OkHttpClient client;

    private final Map<String, String> headers;

    OkHttpTransporter( RepositorySystemSession session, RemoteRepository repository ) throws NoTransporterException
    {
        try
        {
            URI uri = new URI( repository.getUrl() ).parseServerAuthority();
            if ( uri.isOpaque() )
            {
                throw new URISyntaxException( repository.getUrl(), "URL must not be opaque" );
            }
            if ( uri.getRawFragment() != null || uri.getRawQuery() != null )
            {
                throw new URISyntaxException( repository.getUrl(), "URL must not have fragment or query" );
            }
            String path = uri.getPath();
            if ( path == null )
            {
                path = "/";
            }
            if ( !path.startsWith( "/" ) )
            {
                path = "/" + path;
            }
            if ( !path.endsWith( "/" ) )
            {
                path = path + "/";
            }
            this.baseUri = URI.create( uri.getScheme() + "://" + uri.getRawAuthority() + path );
        }
        catch ( URISyntaxException e )
        {
            throw new NoTransporterException( repository, e.getMessage(), e );
        }

        HashMap<String, String> headers = new HashMap<>();
        String userAgent = ConfigUtils.getString( session,
                ConfigurationProperties.DEFAULT_USER_AGENT,
                ConfigurationProperties.USER_AGENT );
        if ( userAgent != null )
        {
            headers.put( USER_AGENT, userAgent );
        }
        @SuppressWarnings( "unchecked" )
        Map<Object, Object> configuredHeaders =
                (Map<Object, Object>) ConfigUtils.getMap( session, Collections.emptyMap(),
                        ConfigurationProperties.HTTP_HEADERS + "." + repository.getId(),
                        ConfigurationProperties.HTTP_HEADERS );
        if ( configuredHeaders != null )
        {
            configuredHeaders.forEach(
                    ( k, v ) -> headers.put( String.valueOf( k ), v != null ? String.valueOf( v ) : null ) );
        }

        this.headers = headers;
        this.client = getOrCreateClient( session, repository );
    }

    private HttpUrl resolve( TransportTask task )
    {
        return HttpUrl.get( baseUri.resolve( task.getLocation() ) );
    }

    @Override
    public int classify( Throwable error )
    {
        if ( error instanceof OkHttpException
                && ( (OkHttpException) error ).getStatusCode() == NOT_FOUND )
        {
            return ERROR_NOT_FOUND;
        }
        return ERROR_OTHER;
    }

    @Override
    protected void implPeek( PeekTask task )
            throws Exception
    {
        Request.Builder request = new Request.Builder()
                .url( resolve( task ) )
                .head();
        Headers.Builder requestHeaders = new Headers.Builder();
        headers.forEach( requestHeaders::add );
        request.headers( requestHeaders.build() );
        try ( Response response = client.newCall( request.build() ).execute() )
        {
            if ( response.code() >= MULTIPLE_CHOICES )
            {
                throw new OkHttpException( response.code() );
            }
        }
    }

    @Override
    protected void implGet( GetTask task )
            throws Exception
    {
        boolean resume = task.getResumeOffset() > 0L && task.getDataFile() != null;
        Response maybeRetriedResponse = null;

        while ( true )
        {
            if ( maybeRetriedResponse != null )
            {
                maybeRetriedResponse.close();
            }

            Request.Builder request = new Request.Builder()
                    .url( resolve( task ) )
                    .get();
            Headers.Builder requestHeaders = new Headers.Builder();
            headers.forEach( requestHeaders::add );

            if ( resume )
            {
                long resumeOffset = task.getResumeOffset();
                requestHeaders.add( RANGE, "bytes=" + resumeOffset + '-' );
                requestHeaders.add( IF_UNMODIFIED_SINCE, Instant.ofEpochMilli(
                        task.getDataFile().lastModified() - MODIFICATION_THRESHOLD ) );
                requestHeaders.removeAll( ACCEPT_ENCODING );
                requestHeaders.add( ACCEPT_ENCODING, "identity" );
            }

            request.headers( requestHeaders.build() );
            maybeRetriedResponse = client.newCall( request.build() ).execute();
            if ( maybeRetriedResponse.code() >= MULTIPLE_CHOICES )
            {
                if ( resume && maybeRetriedResponse.code() == PRECONDITION_FAILED )
                {
                    resume = false;
                    continue;
                }
                maybeRetriedResponse.close();
                throw new OkHttpException( maybeRetriedResponse.code() );
            }
            break;
        }

        try ( Response response = maybeRetriedResponse )
        {
            ResponseBody responseBody = response.body();
            if ( responseBody == null )
            {
                responseBody = ResponseBody.create( new byte[0], OCTET_STREAM );
            }

            try ( ResponseBody body = responseBody )
            {
                long offset = 0L, length = body.contentLength();
                if ( resume )
                {
                    String range = response.headers().get( CONTENT_RANGE );
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
                }

                final boolean downloadResumed = offset > 0L;
                final File dataFile = task.getDataFile();
                if ( dataFile == null )
                {
                    try ( InputStream is = body.byteStream() )
                    {
                        utilGet( task, is, true, length, downloadResumed );
                    }
                }
                else
                {
                    try ( FileUtils.CollocatedTempFile tempFile = FileUtils.newTempFile( dataFile.toPath() ) )
                    {
                        task.setDataFile( tempFile.getPath().toFile(), downloadResumed );
                        if ( downloadResumed && Files.isRegularFile( dataFile.toPath() ) )
                        {
                            try ( InputStream inputStream = Files.newInputStream( dataFile.toPath() ) )
                            {
                                Files.copy( inputStream, tempFile.getPath(), StandardCopyOption.REPLACE_EXISTING );
                            }
                        }
                        try ( InputStream is = body.byteStream() )
                        {
                            utilGet( task, is, true, length, downloadResumed );
                        }
                        tempFile.move();
                    }
                    finally
                    {
                        task.setDataFile( dataFile );
                    }
                }
            }
            Map<String, String> checksums = extractXChecksums( response );
            if ( checksums != null )
            {
                checksums.forEach( task::setChecksum );
                return;
            }
            checksums = extractNexus2Checksums( response );
            if ( checksums != null )
            {
                checksums.forEach( task::setChecksum );
            }
        }
    }

    private static Map<String, String> extractXChecksums( Response response )
    {
        String value;
        HashMap<String, String> result = new HashMap<>();
        // Central style: x-checksum-sha1: c74edb60ca2a0b57ef88d9a7da28f591e3d4ce7b
        value = response.header( "x-checksum-sha1" );
        if ( value != null )
        {
            result.put( "SHA-1", value );
        }
        // Central style: x-checksum-md5: 9ad0d8e3482767c122e85f83567b8ce6
        value = response.header( "x-checksum-md5" );
        if ( value != null )
        {
            result.put( "MD5", value );
        }
        if ( !result.isEmpty() )
        {
            return result;
        }
        // Google style: x-goog-meta-checksum-sha1: c74edb60ca2a0b57ef88d9a7da28f591e3d4ce7b
        value = response.header( "x-goog-meta-checksum-sha1" );
        if ( value != null )
        {
            result.put( "SHA-1", value );
        }
        // Central style: x-goog-meta-checksum-sha1: 9ad0d8e3482767c122e85f83567b8ce6
        value = response.header( "x-goog-meta-checksum-md5" );
        if ( value != null )
        {
            result.put( "MD5", value );
        }

        return result.isEmpty() ? null : result;
    }

    private static Map<String, String> extractNexus2Checksums( Response response )
    {
        // Nexus-style, ETag: "{SHA1{d40d68ba1f88d8e9b0040f175a6ff41928abd5e7}}"
        String etag = response.header( "ETag" );
        if ( etag != null )
        {
            int start = etag.indexOf( "SHA1{" ), end = etag.indexOf( "}", start + 5 );
            if ( start >= 0 && end > start )
            {
                return Collections.singletonMap( "SHA-1", etag.substring( start + 5, end ) );
            }
        }
        return null;
    }

    @Override
    protected void implPut( PutTask task )
            throws Exception
    {
        try ( FileUtils.TempFile tempFile = FileUtils.newTempFile() )
        {
            utilPut( task, Files.newOutputStream( tempFile.getPath() ), true );

            Request.Builder request = new Request.Builder()
                    .url( resolve( task ) )
                    .put( RequestBody.create( tempFile.getPath().toFile(), OCTET_STREAM ) );
            Headers.Builder requestHeaders = new Headers.Builder();
            headers.forEach( requestHeaders::add );
            request.headers( requestHeaders.build() );

            try ( Response response = client.newCall( request.build() ).execute() )
            {
                if ( response.code() >= MULTIPLE_CHOICES )
                {
                    throw new OkHttpException( response.code() );
                }
            }
        }
    }

    @Override
    protected void implClose()
    {
        // nop
    }

    private static final MediaType OCTET_STREAM = MediaType.get( "application/octet-stream" );

    static final String OKHTTP_INSTANCE_KEY_PREFIX = OkHttpTransporterFactory.class.getName() + ".okhttp.";

    private OkHttpClient getOrCreateClient( RepositorySystemSession session, RemoteRepository repository )
            throws NoTransporterException
    {
        final String instanceKey = OKHTTP_INSTANCE_KEY_PREFIX + repository.getId();

        try
        {
            return (OkHttpClient) session.getData().computeIfAbsent( instanceKey, () ->
            {
                Authenticator authenticator = null;
                SSLContext sslContext = null;
                HostnameVerifier hostnameVerifier = null;
                X509TrustManager trustManager = null;
                try
                {
                    try ( AuthenticationContext repoAuthContext = AuthenticationContext.forRepository( session,
                            repository ) )
                    {
                        if ( repoAuthContext != null )
                        {
                            sslContext = repoAuthContext.get( AuthenticationContext.SSL_CONTEXT, SSLContext.class );
                            hostnameVerifier = repoAuthContext.get( AuthenticationContext.SSL_HOSTNAME_VERIFIER,
                                    HostnameVerifier.class );
                            trustManager = repoAuthContext.get( TrustManager.class.getName(), X509TrustManager.class );

                            String username = repoAuthContext.get( AuthenticationContext.USERNAME );
                            String password = repoAuthContext.get( AuthenticationContext.PASSWORD );

                            authenticator = ( route, response ) ->
                            {
                                if ( response.request().header( AUTHORIZATION ) != null )
                                {
                                    return null; // Give up, we've already attempted to authenticate.
                                }

                                String credential = Credentials.basic( username, password );
                                // TODO: memoize auth to reuse
                                return response.request().newBuilder()
                                        .header( AUTHORIZATION, credential )
                                        .build();
                            };
                        }
                    }

                    if ( sslContext == null )
                    {
                        sslContext = SSLContext.getDefault();
                    }
                    if ( hostnameVerifier == null )
                    {
                        hostnameVerifier = ( hostname, session1 ) ->
                        {
                            if ( "localhost".equalsIgnoreCase( hostname ) )
                            {
                                return true;
                            }
                            return OkHostnameVerifier.INSTANCE.verify( hostname, session1 );
                        };
                    }
                    if ( trustManager == null )
                    {
                        trustManager = getX509TrustManager();
                    }

                    int connectTimeout = ConfigUtils.getInteger( session,
                            ConfigurationProperties.DEFAULT_CONNECT_TIMEOUT,
                            ConfigurationProperties.CONNECT_TIMEOUT + "." + repository.getId(),
                            ConfigurationProperties.CONNECT_TIMEOUT );
                    int requestTimeout = ConfigUtils.getInteger( session,
                            ConfigurationProperties.DEFAULT_REQUEST_TIMEOUT,
                            ConfigurationProperties.REQUEST_TIMEOUT + "." + repository.getId(),
                            ConfigurationProperties.REQUEST_TIMEOUT );

                    OkHttpClient.Builder builder = new OkHttpClient.Builder() //
                            .connectionSpecs( Arrays.asList( ConnectionSpec.COMPATIBLE_TLS,
                                    ConnectionSpec.CLEARTEXT ) )
                            .connectTimeout( connectTimeout, TimeUnit.MILLISECONDS ) //
                            .callTimeout( requestTimeout, TimeUnit.MILLISECONDS )
                            .followRedirects( true );

                    if ( authenticator != null )
                    {
                        builder.authenticator( authenticator );
                    }
                    builder.sslSocketFactory( sslContext.getSocketFactory(), trustManager );
                    builder.hostnameVerifier( hostnameVerifier );

                    if ( repository.getProxy() != null )
                    {
                        builder.proxy( new Proxy( Proxy.Type.HTTP, new InetSocketAddress(
                                repository.getProxy().getHost(), repository.getProxy().getPort() ) ) );
                        try ( AuthenticationContext proxyAuthContext = AuthenticationContext.forProxy( session,
                                repository ) )
                        {
                            if ( proxyAuthContext != null )
                            {
                                String username = proxyAuthContext.get( AuthenticationContext.USERNAME );
                                String password = proxyAuthContext.get( AuthenticationContext.PASSWORD );

                                builder.proxyAuthenticator( ( route, response ) ->
                                {
                                    if ( response.request().header( PROXY_AUTHORIZATION ) != null )
                                    {
                                        return null; // Give up, we've already attempted to authenticate.
                                    }
                                    if ( response.code() == PROXY_AUTH )
                                    {
                                        String credential = Credentials.basic( username, password );
                                        // TODO: memoize proxy auth to reuse
                                        return response.request().newBuilder()
                                                .header( PROXY_AUTHORIZATION, credential )
                                                .build();
                                    }
                                    return null;
                                } );
                            }
                        }
                    }

                    return builder.build();
                }
                catch ( NoSuchAlgorithmException e )
                {
                    throw new WrapperEx( e );
                }
            } );
        }
        catch ( WrapperEx e )
        {
            throw new NoTransporterException( repository, e.getCause() );
        }
    }

    private static X509TrustManager getX509TrustManager()
    {
        try
        {
            TrustManagerFactory trustManagerFactory =
                    TrustManagerFactory.getInstance( TrustManagerFactory.getDefaultAlgorithm() );
            KeyStore keyStore = KeyStore.getInstance( KeyStore.getDefaultType() );
            trustManagerFactory.init( keyStore );

            for ( TrustManager trustManager : trustManagerFactory.getTrustManagers() )
            {
                if ( trustManager instanceof X509TrustManager )
                {
                    return (X509TrustManager) trustManager;
                }
            }
            return null;
        }
        catch ( NoSuchAlgorithmException | KeyStoreException e )
        {
            return null;
        }
    }

    private static final class WrapperEx extends RuntimeException
    {
        private WrapperEx( Throwable cause )
        {
            super( cause );
        }
    }
}
