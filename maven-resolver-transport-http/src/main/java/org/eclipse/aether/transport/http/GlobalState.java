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
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.http.HttpHost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.eclipse.aether.RepositoryCache;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.util.ConfigUtils;

/**
 * Container for HTTP-related state that can be shared across incarnations of the transporter to optimize the
 * communication with servers.
 */
final class GlobalState
    implements Closeable
{

    static class CompoundKey
    {

        private final Object[] keys;

        CompoundKey( Object... keys )
        {
            this.keys = keys;
        }

        @Override
        public boolean equals( Object obj )
        {
            if ( this == obj )
            {
                return true;
            }
            if ( obj == null || !getClass().equals( obj.getClass() ) )
            {
                return false;
            }
            CompoundKey that = (CompoundKey) obj;
            return Arrays.equals( keys, that.keys );
        }

        @Override
        public int hashCode()
        {
            int hash = 17;
            hash = hash * 31 + Arrays.hashCode( keys );
            return hash;
        }

        @Override
        public String toString()
        {
            return Arrays.toString( keys );
        }
    }

    private static final String KEY = GlobalState.class.getName();

    private static final String CONFIG_PROP_CACHE_STATE = "aether.connector.http.cacheState";

    private final ConcurrentMap<SslConfig, ClientConnectionManager> connectionManagers;

    private final ConcurrentMap<CompoundKey, Object> userTokens;

    private final ConcurrentMap<HttpHost, AuthSchemePool> authSchemePools;

    private final ConcurrentMap<CompoundKey, Boolean> expectContinues;

    public static GlobalState get( RepositorySystemSession session )
    {
        GlobalState cache;
        RepositoryCache repoCache = session.getCache();
        if ( repoCache == null || !ConfigUtils.getBoolean( session, true, CONFIG_PROP_CACHE_STATE ) )
        {
            cache = null;
        }
        else
        {
            Object tmp = repoCache.get( session, KEY );
            if ( tmp instanceof GlobalState )
            {
                cache = (GlobalState) tmp;
            }
            else
            {
                synchronized ( GlobalState.class )
                {
                    tmp = repoCache.get( session, KEY );
                    if ( tmp instanceof GlobalState )
                    {
                        cache = (GlobalState) tmp;
                    }
                    else
                    {
                        cache = new GlobalState();
                        repoCache.put( session, KEY, cache );
                    }
                }
            }
        }
        return cache;
    }

    private GlobalState()
    {
        connectionManagers = new ConcurrentHashMap<>();
        userTokens = new ConcurrentHashMap<>();
        authSchemePools = new ConcurrentHashMap<>();
        expectContinues = new ConcurrentHashMap<>();
    }

    public void close()
    {
        for ( Iterator<Map.Entry<SslConfig, ClientConnectionManager>> it = connectionManagers.entrySet().iterator();
              it.hasNext(); )
        {
            ClientConnectionManager connMgr = it.next().getValue();
            it.remove();
            connMgr.shutdown();
        }
    }

    public ClientConnectionManager getConnectionManager( SslConfig config )
    {
        ClientConnectionManager manager = connectionManagers.get( config );
        if ( manager == null )
        {
            ClientConnectionManager connMgr = newConnectionManager( config );
            manager = connectionManagers.putIfAbsent( config, connMgr );
            if ( manager != null )
            {
                connMgr.shutdown();
            }
            else
            {
                manager = connMgr;
            }
        }
        return manager;
    }

    @SuppressWarnings( "checkstyle:magicnumber" )
    public static ClientConnectionManager newConnectionManager( SslConfig sslConfig )
    {
        SchemeRegistry schemeReg = new SchemeRegistry();
        schemeReg.register( new Scheme( "http", 80, new PlainSocketFactory() ) );
        schemeReg.register( new Scheme( "https", 443, new SslSocketFactory( sslConfig ) ) );

        PoolingClientConnectionManager connMgr = new PoolingClientConnectionManager( schemeReg );
        connMgr.setMaxTotal( 100 );
        connMgr.setDefaultMaxPerRoute( 50 );
        return connMgr;
    }

    public Object getUserToken( CompoundKey key )
    {
        return userTokens.get( key );
    }

    public void setUserToken( CompoundKey key, Object userToken )
    {
        if ( userToken != null )
        {
            userTokens.put( key, userToken );
        }
        else
        {
            userTokens.remove( key );
        }
    }

    public ConcurrentMap<HttpHost, AuthSchemePool> getAuthSchemePools()
    {
        return authSchemePools;
    }

    public Boolean getExpectContinue( CompoundKey key )
    {
        return expectContinues.get( key );
    }

    public void setExpectContinue( CompoundKey key, boolean enabled )
    {
        expectContinues.put( key, enabled );
    }

}
