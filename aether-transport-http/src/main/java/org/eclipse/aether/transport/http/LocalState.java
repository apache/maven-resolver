/*******************************************************************************
 * Copyright (c) 2013, 2014 Sonatype, Inc.
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScheme;
import org.apache.http.conn.ClientConnectionManager;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.transport.http.GlobalState.CompoundKey;
import org.eclipse.aether.util.ConfigUtils;

/**
 * Container for HTTP-related state that can be shared across invocations of the transporter to optimize the
 * communication with server.
 */
final class LocalState
    implements Closeable
{

    private static final String CONFIG_PROP_WEBDAV = "aether.connector.http.webDav";

    private final GlobalState global;

    private final ClientConnectionManager connMgr;

    private final CompoundKey userTokenKey;

    private volatile Object userToken;

    private final CompoundKey expectContinueKey;

    private volatile boolean probed;

    private volatile Boolean expectContinue;

    private volatile Boolean webDav;

    private final ConcurrentMap<HttpHost, AuthSchemePool> authSchemePools;

    public LocalState( RepositorySystemSession session, RemoteRepository repo, SslConfig sslConfig )
    {
        global = GlobalState.get( session );
        userToken = this;
        if ( global == null )
        {
            connMgr = GlobalState.newConnectionManager( sslConfig );
            userTokenKey = null;
            expectContinueKey = null;
            authSchemePools = new ConcurrentHashMap<HttpHost, AuthSchemePool>();
        }
        else
        {
            connMgr = global.getConnectionManager( sslConfig );
            userTokenKey = new CompoundKey( repo.getId(), repo.getUrl(), repo.getAuthentication(), repo.getProxy() );
            expectContinueKey = new CompoundKey( repo.getUrl(), repo.getProxy() );
            authSchemePools = global.getAuthSchemePools();
        }
        if ( !ConfigUtils.getBoolean( session, true, CONFIG_PROP_WEBDAV + '.' + repo.getId(), CONFIG_PROP_WEBDAV ) )
        {
            webDav = false;
        }
    }

    public ClientConnectionManager getConnectionManager()
    {
        return connMgr;
    }

    public Object getUserToken()
    {
        if ( userToken == this )
        {
            userToken = ( global != null ) ? global.getUserToken( userTokenKey ) : null;
        }
        return userToken;
    }

    public void setUserToken( Object userToken )
    {
        this.userToken = userToken;
        if ( global != null )
        {
            global.setUserToken( userTokenKey, userToken );
        }
    }

    public boolean isProbed()
    {
        return probed;
    }

    public void setProbed()
    {
        probed = true;
    }

    public boolean isExpectContinue()
    {
        if ( expectContinue == null )
        {
            expectContinue =
                !Boolean.FALSE.equals( ( global != null ) ? global.getExpectContinue( expectContinueKey ) : null );
        }
        return expectContinue;
    }

    public void setExpectContinue( boolean enabled )
    {
        expectContinue = enabled;
        if ( global != null )
        {
            global.setExpectContinue( expectContinueKey, enabled );
        }
    }

    public boolean isWebDav()
    {
        return Boolean.TRUE.equals( webDav );
    }

    public void setWebDav( boolean webDav )
    {
        if ( this.webDav == null )
        {
            this.webDav = webDav;
        }
    }

    public AuthScheme getAuthScheme( HttpHost host )
    {
        AuthSchemePool pool = authSchemePools.get( host );
        if ( pool != null )
        {
            return pool.get();
        }
        return null;
    }

    public void setAuthScheme( HttpHost host, AuthScheme authScheme )
    {
        AuthSchemePool pool = authSchemePools.get( host );
        if ( pool == null )
        {
            AuthSchemePool p = new AuthSchemePool();
            pool = authSchemePools.putIfAbsent( host, p );
            if ( pool == null )
            {
                pool = p;
            }
        }
        pool.put( authScheme );
    }

    public void close()
    {
        if ( global == null )
        {
            connMgr.shutdown();
        }
    }

}
