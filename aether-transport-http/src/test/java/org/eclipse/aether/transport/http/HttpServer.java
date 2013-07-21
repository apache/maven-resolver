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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.aether.util.ChecksumUtils;
import org.eclipse.jetty.http.HttpHeaders;
import org.eclipse.jetty.http.HttpMethods;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.util.B64Code;
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpServer
{

    public static class LogEntry
    {

        public final String method;

        public final String path;

        public final Map<String, String> headers;

        public LogEntry( String method, String path, Map<String, String> headers )
        {
            this.method = method;
            this.path = path;
            this.headers = headers;
        }

        @Override
        public String toString()
        {
            return method + " " + path;
        }

    }

    public enum ChecksumHeader
    {
        NEXUS
    }

    private static final Logger log = LoggerFactory.getLogger( HttpServer.class );

    private File repoDir;

    private boolean rangeSupport = true;

    private boolean expectSupport = true;

    private Server server;

    private Connector httpConnector;

    private Connector httpsConnector;

    private String username;

    private String password;

    private String proxyUsername;

    private String proxyPassword;

    private List<LogEntry> logEntries = Collections.synchronizedList( new ArrayList<LogEntry>() );

    private ChecksumHeader checksumHeader;

    public String getHost()
    {
        return "localhost";
    }

    public int getHttpPort()
    {
        return httpConnector != null ? httpConnector.getLocalPort() : -1;
    }

    public int getHttpsPort()
    {
        return httpsConnector != null ? httpsConnector.getLocalPort() : -1;
    }

    public String getHttpUrl()
    {
        return "http://" + getHost() + ":" + getHttpPort();
    }

    public String getHttpsUrl()
    {
        return "https://" + getHost() + ":" + getHttpsPort();
    }

    public HttpServer addSslConnector()
    {
        if ( httpsConnector == null )
        {
            SslContextFactory ssl = new SslContextFactory();
            ssl.setKeyStorePath( new File( "src/test/resources/ssl/server-store" ).getAbsolutePath() );
            ssl.setKeyStorePassword( "server-pwd" );
            ssl.setTrustStore( new File( "src/test/resources/ssl/client-store" ).getAbsolutePath() );
            ssl.setTrustStorePassword( "client-pwd" );
            ssl.setNeedClientAuth( true );
            httpsConnector = new SslSelectChannelConnector( ssl );
            server.addConnector( httpsConnector );
            try
            {
                httpsConnector.start();
            }
            catch ( Exception e )
            {
                throw new IllegalStateException( e );
            }
        }
        return this;
    }

    public List<LogEntry> getLogEntries()
    {
        return logEntries;
    }

    public HttpServer setRepoDir( File repoDir )
    {
        this.repoDir = repoDir;
        return this;
    }

    public HttpServer setRangeSupport( boolean rangeSupport )
    {
        this.rangeSupport = rangeSupport;
        return this;
    }

    public HttpServer setExpectSupport( boolean expectSupport )
    {
        this.expectSupport = expectSupport;
        return this;
    }

    public HttpServer setAuthentication( String username, String password )
    {
        this.username = username;
        this.password = password;
        return this;
    }

    public HttpServer setProxyAuthentication( String username, String password )
    {
        proxyUsername = username;
        proxyPassword = password;
        return this;
    }

    public HttpServer setChecksumHeader( ChecksumHeader checksumHeader )
    {
        this.checksumHeader = checksumHeader;
        return this;
    }

    public HttpServer start()
        throws Exception
    {
        if ( server != null )
        {
            return this;
        }

        httpConnector = new SelectChannelConnector();

        HandlerList handlers = new HandlerList();
        handlers.addHandler( new LogHandler() );
        handlers.addHandler( new ProxyAuthHandler() );
        handlers.addHandler( new AuthHandler() );
        handlers.addHandler( new RedirectHandler() );
        handlers.addHandler( new RepoHandler() );

        server = new Server();
        server.addConnector( httpConnector );
        server.setHandler( handlers );
        server.start();

        return this;
    }

    public void stop()
        throws Exception
    {
        if ( server != null )
        {
            server.stop();
            server = null;
            httpConnector = null;
            httpsConnector = null;
        }
    }

    private class LogHandler
        extends AbstractHandler
    {

        @SuppressWarnings( "unchecked" )
        public void handle( String target, Request req, HttpServletRequest request, HttpServletResponse response )
            throws IOException
        {
            log.info( "{} {}{}", new Object[] { req.getMethod(), req.getRequestURL(),
                req.getQueryString() != null ? "?" + req.getQueryString() : "" } );

            Map<String, String> headers = new TreeMap<String, String>( String.CASE_INSENSITIVE_ORDER );
            for ( Enumeration<String> en = req.getHeaderNames(); en.hasMoreElements(); )
            {
                String name = en.nextElement();
                StringBuilder buffer = new StringBuilder( 128 );
                for ( Enumeration<String> ien = req.getHeaders( name ); ien.hasMoreElements(); )
                {
                    if ( buffer.length() > 0 )
                    {
                        buffer.append( ", " );
                    }
                    buffer.append( ien.nextElement() );
                }
                headers.put( name, buffer.toString() );
            }
            logEntries.add( new LogEntry( req.getMethod(), req.getPathInfo(), Collections.unmodifiableMap( headers ) ) );
        }

    }

    private class RepoHandler
        extends AbstractHandler
    {

        private final Pattern SIMPLE_RANGE = Pattern.compile( "bytes=([0-9])+-" );

        public void handle( String target, Request req, HttpServletRequest request, HttpServletResponse response )
            throws IOException
        {
            String path = req.getPathInfo().substring( 1 );

            if ( !path.startsWith( "repo/" ) )
            {
                return;
            }
            req.setHandled( true );
            if ( path.endsWith( URIUtil.SLASH ) )
            {
                response.setStatus( HttpServletResponse.SC_NOT_FOUND );
                return;
            }

            if ( !expectSupport && request.getHeader( HttpHeaders.EXPECT ) != null )
            {
                response.setStatus( HttpServletResponse.SC_EXPECTATION_FAILED );
                return;
            }

            File file = new File( repoDir, path.substring( 5 ) );
            if ( HttpMethods.GET.equals( req.getMethod() ) || HttpMethods.HEAD.equals( req.getMethod() ) )
            {
                if ( !file.isFile() )
                {
                    response.setStatus( HttpServletResponse.SC_NOT_FOUND );
                    return;
                }
                long ifUnmodifiedSince = request.getDateHeader( HttpHeaders.IF_UNMODIFIED_SINCE );
                if ( ifUnmodifiedSince != -1 && file.lastModified() > ifUnmodifiedSince )
                {
                    response.setStatus( HttpServletResponse.SC_PRECONDITION_FAILED );
                    return;
                }
                long offset = 0;
                String range = request.getHeader( HttpHeaders.RANGE );
                if ( range != null && rangeSupport )
                {
                    Matcher m = SIMPLE_RANGE.matcher( range );
                    if ( m.matches() )
                    {
                        offset = Long.parseLong( m.group( 1 ) );
                        if ( offset >= file.length() )
                        {
                            response.setStatus( HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE );
                            return;
                        }
                    }
                    String encoding = request.getHeader( HttpHeaders.ACCEPT_ENCODING );
                    if ( ( encoding != null && !"identity".equals( encoding ) ) || ifUnmodifiedSince == -1 )
                    {
                        response.setStatus( HttpServletResponse.SC_BAD_REQUEST );
                        return;
                    }
                }
                response.setStatus( ( offset > 0 ) ? HttpServletResponse.SC_PARTIAL_CONTENT : HttpServletResponse.SC_OK );
                response.setDateHeader( HttpHeaders.LAST_MODIFIED, file.lastModified() );
                response.setHeader( HttpHeaders.CONTENT_LENGTH, Long.toString( file.length() - offset ) );
                if ( offset > 0 )
                {
                    response.setHeader( HttpHeaders.CONTENT_RANGE, "bytes " + offset + "-" + ( file.length() - 1 )
                        + "/" + file.length() );
                }
                if ( checksumHeader != null )
                {
                    Map<String, Object> checksums = ChecksumUtils.calc( file, Collections.singleton( "SHA-1" ) );
                    switch ( checksumHeader )
                    {
                        case NEXUS:
                            response.setHeader( HttpHeaders.ETAG, "{SHA1{" + checksums.get( "SHA-1" ) + "}}" );
                            break;
                    }
                }
                if ( HttpMethods.HEAD.equals( req.getMethod() ) )
                {
                    return;
                }
                FileInputStream is = new FileInputStream( file );
                try
                {
                    if ( offset > 0 )
                    {
                        long skipped = is.skip( offset );
                        while ( skipped < offset && is.read() >= 0 )
                        {
                            skipped++;
                        }
                    }
                    IO.copy( is, response.getOutputStream() );
                }
                finally
                {
                    IO.close( is );
                }
            }
            else if ( HttpMethods.PUT.equals( req.getMethod() ) )
            {
                file.getParentFile().mkdirs();
                try
                {
                    FileOutputStream os = new FileOutputStream( file );
                    try
                    {
                        IO.copy( request.getInputStream(), os );
                    }
                    finally
                    {
                        os.close();
                    }
                }
                catch ( IOException e )
                {
                    file.delete();
                    throw e;
                }
                response.setStatus( HttpServletResponse.SC_NO_CONTENT );
            }
            else
            {
                response.setStatus( HttpServletResponse.SC_METHOD_NOT_ALLOWED );
            }
        }

    }

    private class RedirectHandler
        extends AbstractHandler
    {

        public void handle( String target, Request req, HttpServletRequest request, HttpServletResponse response )
            throws IOException
        {
            String path = req.getPathInfo();
            if ( !path.startsWith( "/redirect/" ) )
            {
                return;
            }
            req.setHandled( true );
            StringBuilder location = new StringBuilder( 128 );
            String scheme = req.getParameter( "scheme" );
            location.append( scheme != null ? scheme : req.getScheme() );
            location.append( "://" );
            location.append( req.getServerName() );
            location.append( ":" );
            if ( "http".equalsIgnoreCase( scheme ) )
            {
                location.append( getHttpPort() );
            }
            else if ( "https".equalsIgnoreCase( scheme ) )
            {
                location.append( getHttpsPort() );
            }
            else
            {
                location.append( req.getServerPort() );
            }
            location.append( "/repo" ).append( path.substring( 9 ) );
            response.setStatus( HttpServletResponse.SC_MOVED_PERMANENTLY );
            response.setHeader( HttpHeaders.LOCATION, location.toString() );
        }

    }

    private class AuthHandler
        extends AbstractHandler
    {

        public void handle( String target, Request req, HttpServletRequest request, HttpServletResponse response )
            throws IOException
        {
            if ( username != null && password != null )
            {
                if ( checkBasicAuth( request.getHeader( HttpHeaders.AUTHORIZATION ), username, password ) )
                {
                    return;
                }
                req.setHandled( true );
                response.setHeader( HttpHeaders.WWW_AUTHENTICATE, "basic realm=\"Test-Realm\"" );
                response.setStatus( HttpServletResponse.SC_UNAUTHORIZED );
            }
        }

    }

    private class ProxyAuthHandler
        extends AbstractHandler
    {

        public void handle( String target, Request req, HttpServletRequest request, HttpServletResponse response )
            throws IOException
        {
            if ( proxyUsername != null && proxyPassword != null )
            {
                if ( checkBasicAuth( request.getHeader( HttpHeaders.PROXY_AUTHORIZATION ), proxyUsername, proxyPassword ) )
                {
                    return;
                }
                req.setHandled( true );
                response.setHeader( HttpHeaders.PROXY_AUTHENTICATE, "basic realm=\"Test-Realm\"" );
                response.setStatus( HttpServletResponse.SC_PROXY_AUTHENTICATION_REQUIRED );
            }
        }

    }

    static boolean checkBasicAuth( String credentials, String username, String password )
    {
        if ( credentials != null )
        {
            int space = credentials.indexOf( ' ' );
            if ( space > 0 )
            {
                String method = credentials.substring( 0, space );
                if ( "basic".equalsIgnoreCase( method ) )
                {
                    credentials = credentials.substring( space + 1 );
                    try
                    {
                        credentials = B64Code.decode( credentials, StringUtil.__ISO_8859_1 );
                    }
                    catch ( UnsupportedEncodingException e )
                    {
                        throw new IllegalStateException( e );
                    }
                    int i = credentials.indexOf( ':' );
                    if ( i > 0 )
                    {
                        String user = credentials.substring( 0, i );
                        String pass = credentials.substring( i + 1 );
                        if ( username.equals( user ) && password.equals( pass ) )
                        {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

}
