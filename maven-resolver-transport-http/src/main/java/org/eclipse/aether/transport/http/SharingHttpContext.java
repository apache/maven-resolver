package org.eclipse.aether.transport.http;

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

    SharingHttpContext( LocalState state )
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
