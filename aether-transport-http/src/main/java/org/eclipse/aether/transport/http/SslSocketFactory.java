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

import java.io.IOException;

import javax.net.ssl.SSLSocket;

import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.util.ConfigUtils;

/**
 * Specialized SSL socket factory to more closely resemble the JRE's HttpsClient and respect well-known SSL-related
 * configuration properties.
 * 
 * @see <a href="http://docs.oracle.com/javase/1.5.0/docs/guide/security/jsse/JSSERefGuide.html#Customization">JSSE
 *      Reference Guide, Customization</a>
 */
final class SslSocketFactory
    extends SSLSocketFactory
{

    private static final String CIPHER_SUITES = "https.cipherSuites";

    private static final String PROTOCOLS = "https.protocols";

    private final String[] cipherSuites;

    private final String[] protocols;

    public SslSocketFactory( javax.net.ssl.SSLSocketFactory socketfactory, X509HostnameVerifier hostnameVerifier,
                             RepositorySystemSession session )
    {
        super( socketfactory, hostnameVerifier );

        cipherSuites = split( get( session, CIPHER_SUITES ) );
        protocols = split( get( session, PROTOCOLS ) );
    }

    private static String get( RepositorySystemSession session, String key )
    {
        String value = ConfigUtils.getString( session, null, "aether.connector." + key, key );
        if ( value == null )
        {
            value = System.getProperty( key );
        }
        return value;
    }

    private static String[] split( String value )
    {
        if ( value == null || value.length() <= 0 )
        {
            return null;
        }
        return value.split( ",+" );
    }

    @Override
    protected void prepareSocket( SSLSocket socket )
        throws IOException
    {
        super.prepareSocket( socket );
        if ( cipherSuites != null )
        {
            socket.setEnabledCipherSuites( cipherSuites );
        }
        if ( protocols != null )
        {
            socket.setEnabledProtocols( protocols );
        }
    }

}
