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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScheme;
import org.apache.http.conn.ClientConnectionManager;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.transport.http.GlobalState.CompoundKey;

/**
 * Container for HTTP-related state that can be shared across invocations of the transporter to optimize the
 * communication with server.
 */
final class LocalState
    implements Closeable
{

    private final GlobalState global;

    private final ClientConnectionManager connMgr;

    private final CompoundKey userTokenKey;

    private volatile Object userToken;

    private final CompoundKey expectContinueKey;

    private volatile Boolean expectContinue;

    private volatile Boolean webDav;

    private final ConcurrentMap<HttpHost, AuthSchemePool> authSchemePools;

    LocalState( RepositorySystemSession session, RemoteRepository repo, SslConfig sslConfig )
    {
        global = GlobalState.get( session );
        userToken = this;
        if ( global == null )
        {
            connMgr = GlobalState.newConnectionManager( sslConfig );
            userTokenKey = null;
            expectContinueKey = null;
            authSchemePools = new ConcurrentHashMap<>();
        }
        else
        {
            connMgr = global.getConnectionManager( sslConfig );
            userTokenKey = new CompoundKey( repo.getId(), repo.getUrl(), repo.getAuthentication(), repo.getProxy() );
            expectContinueKey = new CompoundKey( repo.getUrl(), repo.getProxy() );
            authSchemePools = global.getAuthSchemePools();
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

    public Boolean getWebDav()
    {
        return webDav;
    }

    public void setWebDav( boolean webDav )
    {
        this.webDav = webDav;
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
