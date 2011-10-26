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
package org.eclipse.aether.connector.async;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.sonatype.tests.http.server.api.Behaviour;

/**
 * A behavior that writes a sequence of fixed length to the client upon a GET request.
 */
public class Generate
    implements Behaviour
{

    private static final byte[] bytes = new byte[1024];

    private final Map<String, Long> lengths = new ConcurrentHashMap<String, Long>();

    public void addContent( String path, long length )
    {
        if ( !path.startsWith( "/" ) )
        {
            path = '/' + path;
        }
        lengths.put( path, Long.valueOf( length ) );
    }

    public boolean execute( HttpServletRequest request, HttpServletResponse response, Map<Object, Object> ctx )
        throws Exception
    {
        if ( "GET".equals( request.getMethod() ) )
        {
            String path = request.getPathInfo();

            Long length = lengths.get( path );

            if ( length != null )
            {
                response.setContentType( "application/octet-stream" );
                response.setContentLength( length.intValue() );

                ServletOutputStream out = response.getOutputStream();
                for ( int i = length.intValue(); i > 0; )
                {
                    int n = Math.min( i, bytes.length );
                    i -= n;
                    out.write( bytes, 0, n );
                }
                out.close();

                return false;
            }
        }

        return true;
    }

}
