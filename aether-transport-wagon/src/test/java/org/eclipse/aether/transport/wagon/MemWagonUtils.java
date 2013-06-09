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
package org.eclipse.aether.transport.wagon;

import java.net.URI;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.maven.wagon.ConnectionException;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.authentication.AuthenticationException;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.apache.maven.wagon.proxy.ProxyInfo;

/**
 */
class MemWagonUtils
{

    private static final ConcurrentMap<String, Map<String, String>> mounts =
        new ConcurrentHashMap<String, Map<String, String>>();

    public static Map<String, String> getFilesystem( String id )
    {
        Map<String, String> fs = mounts.get( id );
        if ( fs == null )
        {
            fs = new ConcurrentHashMap<String, String>();
            Map<String, String> prev = mounts.putIfAbsent( id, fs );
            if ( prev != null )
            {
                fs = prev;
            }
        }
        return fs;
    }

    public static Map<String, String> openConnection( Wagon wagon, AuthenticationInfo auth, ProxyInfo proxy,
                                                      Properties headers )
        throws ConnectionException, AuthenticationException
    {
        URI uri = URI.create( wagon.getRepository().getUrl() );

        String query = uri.getQuery();
        if ( query != null )
        {
            verify( query, "config", String.valueOf( ( (Configurable) wagon ).getConfiguration() ) );

            verify( query, "userAgent", ( headers != null ) ? headers.getProperty( "User-Agent" ) : null );
            verify( query, "requestTimeout", Integer.toString( wagon.getTimeout() ) );

            verify( query, "serverUsername", ( auth != null ) ? auth.getUserName() : null );
            verify( query, "serverPassword", ( auth != null ) ? auth.getPassword() : null );
            verify( query, "serverPrivateKey", ( auth != null ) ? auth.getPrivateKey() : null );
            verify( query, "serverPassphrase", ( auth != null ) ? auth.getPassphrase() : null );

            verify( query, "proxyHost", ( proxy != null ) ? proxy.getHost() : null );
            verify( query, "proxyPort", ( proxy != null ) ? Integer.toString( proxy.getPort() ) : null );
            verify( query, "proxyUsername", ( proxy != null ) ? proxy.getUserName() : null );
            verify( query, "proxyPassword", ( proxy != null ) ? proxy.getPassword() : null );
        }

        return getFilesystem( uri.getHost() );
    }

    private static void verify( String query, String key, String value )
        throws ConnectionException
    {
        int index = query.indexOf( key + "=" );
        if ( index < 0 )
        {
            return;
        }
        String expected = query.substring( index + key.length() + 1 );
        index = expected.indexOf( "&" );
        if ( index >= 0 )
        {
            expected = expected.substring( 0, index );
        }

        if ( expected.length() == 0 )
        {
            if ( value != null )
            {
                throw new ConnectionException( "Bad " + key + ": " + value );
            }
        }
        else if ( !expected.equals( value ) )
        {
            throw new ConnectionException( "Bad " + key + ": " + value );
        }
    }

}
