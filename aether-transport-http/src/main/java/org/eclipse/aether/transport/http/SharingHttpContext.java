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

import org.apache.http.client.protocol.ClientContext;
import org.apache.http.protocol.BasicHttpContext;

/**
 * HTTP context that shares certain attributes among requests to optimize the communication with the server.
 * 
 * @see <a href="http://hc.apache.org/httpcomponents-client-ga/tutorial/html/advanced.html#stateful_conn">Stateful HTTP
 *      connections</a>
 */
final class SharingHttpContext
    extends BasicHttpContext
    implements Closeable
{

    private final LocalState state;

    private final SharingAuthCache authCache;

    public SharingHttpContext( LocalState state )
    {
        this.state = state;
        authCache = new SharingAuthCache( state );
        super.setAttribute( ClientContext.AUTH_CACHE, authCache );
    }

    @Override
    public Object getAttribute( String id )
    {
        if ( ClientContext.USER_TOKEN.equals( id ) )
        {
            return state.getUserToken();
        }
        return super.getAttribute( id );
    }

    @Override
    public void setAttribute( String id, Object obj )
    {
        if ( ClientContext.USER_TOKEN.equals( id ) )
        {
            state.setUserToken( obj );
        }
        else
        {
            super.setAttribute( id, obj );
        }
    }

    @Override
    public Object removeAttribute( String id )
    {
        if ( ClientContext.USER_TOKEN.equals( id ) )
        {
            state.setUserToken( null );
            return null;
        }
        return super.removeAttribute( id );
    }

    public void close()
    {
        authCache.clear();
    }

}
