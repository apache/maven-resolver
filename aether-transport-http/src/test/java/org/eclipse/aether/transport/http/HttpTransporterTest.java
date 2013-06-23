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

import static org.junit.Assert.*;

import java.io.FileNotFoundException;
import java.net.URI;

import org.apache.http.client.HttpResponseException;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.internal.test.util.TestLoggerFactory;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 */
public class HttpTransporterTest
{

    private DefaultRepositorySystemSession session;

    private TransporterFactory factory;

    private Transporter transporter;

    private RemoteRepository newRepo( String url )
    {
        return new RemoteRepository.Builder( "test", "default", url ).build();
    }

    private void newTransporter( String url )
        throws Exception
    {
        if ( transporter != null )
        {
            transporter.close();
            transporter = null;
        }
        transporter = factory.newInstance( session, newRepo( url ) );
    }

    @Before
    public void setUp()
        throws Exception
    {
        session = TestUtils.newSession();
        factory = new HttpTransporterFactory( new TestLoggerFactory() );
        newTransporter( "http://test" );
    }

    @After
    public void tearDown()
    {
        if ( transporter != null )
        {
            transporter.close();
            transporter = null;
        }
        factory = null;
        session = null;
    }

    private String resolve( URI base, String ref )
    {
        return HttpTransporter.resolve( base, URI.create( ref ) ).toString();
    }

    @Test
    public void testResolve_BaseEmptyPath()
    {
        URI base = URI.create( "http://host" );
        assertEquals( "http://host/file.jar", resolve( base, "file.jar" ) );
        assertEquals( "http://host/dir/file.jar", resolve( base, "dir/file.jar" ) );
        assertEquals( "http://host?arg=val", resolve( base, "?arg=val" ) );
        assertEquals( "http://host/file?arg=val", resolve( base, "file?arg=val" ) );
        assertEquals( "http://host/dir/file?arg=val", resolve( base, "dir/file?arg=val" ) );
    }

    @Test
    public void testResolve_BaseRootPath()
    {
        URI base = URI.create( "http://host/" );
        assertEquals( "http://host/file.jar", resolve( base, "file.jar" ) );
        assertEquals( "http://host/dir/file.jar", resolve( base, "dir/file.jar" ) );
        assertEquals( "http://host/?arg=val", resolve( base, "?arg=val" ) );
        assertEquals( "http://host/file?arg=val", resolve( base, "file?arg=val" ) );
        assertEquals( "http://host/dir/file?arg=val", resolve( base, "dir/file?arg=val" ) );
    }

    @Test
    public void testResolve_BasePathTrailingSlash()
    {
        URI base = URI.create( "http://host/sub/dir/" );
        assertEquals( "http://host/sub/dir/file.jar", resolve( base, "file.jar" ) );
        assertEquals( "http://host/sub/dir/dir/file.jar", resolve( base, "dir/file.jar" ) );
        assertEquals( "http://host/sub/dir/?arg=val", resolve( base, "?arg=val" ) );
        assertEquals( "http://host/sub/dir/file?arg=val", resolve( base, "file?arg=val" ) );
        assertEquals( "http://host/sub/dir/dir/file?arg=val", resolve( base, "dir/file?arg=val" ) );
    }

    @Test
    public void testResolve_BasePathNoTrailingSlash()
    {
        URI base = URI.create( "http://host/sub/d%20r" );
        assertEquals( "http://host/sub/d%20r/file.jar", resolve( base, "file.jar" ) );
        assertEquals( "http://host/sub/d%20r/dir/file.jar", resolve( base, "dir/file.jar" ) );
        assertEquals( "http://host/sub/d%20r?arg=val", resolve( base, "?arg=val" ) );
        assertEquals( "http://host/sub/d%20r/file?arg=val", resolve( base, "file?arg=val" ) );
        assertEquals( "http://host/sub/d%20r/dir/file?arg=val", resolve( base, "dir/file?arg=val" ) );
    }

    @Test
    public void testClassify()
    {
        assertEquals( Transporter.ERROR_OTHER, transporter.classify( new FileNotFoundException() ) );
        assertEquals( Transporter.ERROR_OTHER, transporter.classify( new HttpResponseException( 403, "Forbidden" ) ) );
        assertEquals( Transporter.ERROR_NOT_FOUND, transporter.classify( new HttpResponseException( 404, "Not Found" ) ) );
    }

}
