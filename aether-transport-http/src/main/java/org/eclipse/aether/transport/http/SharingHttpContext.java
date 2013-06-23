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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.http.client.protocol.ClientContext;
import org.apache.http.protocol.BasicHttpContext;

/**
 * HTTP context that shares certain (thread-safe) attributes globally.
 * 
 * @see <a href="http://hc.apache.org/httpcomponents-client-ga/tutorial/html/advanced.html#stateful_conn">Stateful HTTP
 *      connections</a>
 */
final class SharingHttpContext
    extends BasicHttpContext
{

    private final Map<String, Object> globals;

    public SharingHttpContext( Map<String, Object> globals )
    {
        this.globals = globals;
    }

    private boolean isGlobalAttribute( String id )
    {
        return ClientContext.USER_TOKEN.equals( id ) || ClientContext.AUTH_CACHE.equals( id );
    }

    @Override
    public Object getAttribute( String id )
    {
        if ( isGlobalAttribute( id ) )
        {
            return globals.get( id );
        }
        return super.getAttribute( id );
    }

    @Override
    public void setAttribute( String id, Object obj )
    {
        if ( isGlobalAttribute( id ) )
        {
            if ( obj != null )
            {
                globals.put( id, obj );
            }
            else
            {
                globals.remove( id );
            }
        }
        else
        {
            super.setAttribute( id, obj );
        }
    }

    @Override
    public Object removeAttribute( String id )
    {
        if ( isGlobalAttribute( id ) )
        {
            return globals.remove( id );
        }
        return super.removeAttribute( id );
    }

    public static Map<String, Object> newGlobals()
    {
        Map<String, Object> globals = new ConcurrentHashMap<String, Object>();
        globals.put( ClientContext.AUTH_CACHE, new ConcurrentAuthCache() );
        return globals;
    }

}
