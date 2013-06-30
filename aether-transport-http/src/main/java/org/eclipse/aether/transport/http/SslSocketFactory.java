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

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.util.ConfigUtils;

/**
 * Specialized SSL socket factory to more closely resemble the JRE's HttpsClient and respect well-known SSL-related
 * configuration properties.
 * 
 * @see <a href="http://docs.oracle.com/javase/1.5.0/docs/guide/security/jsse/JSSERefGuide.html#Customization">JSSE
 *      Reference Guide, Customization</a>
 */
final class SslSocketFactory
    extends org.apache.http.conn.ssl.SSLSocketFactory
{

    private static final String CIPHER_SUITES = "https.cipherSuites";

    private static final String PROTOCOLS = "https.protocols";

    private final String[] cipherSuites;

    private final String[] protocols;

    public static SslSocketFactory newInstance( RepositorySystemSession session, AuthenticationContext authContext )
    {
        SSLContext sslContext =
            ( authContext != null ) ? authContext.get( AuthenticationContext.SSL_CONTEXT, SSLContext.class ) : null;
        SSLSocketFactory socketFactory;
        if ( sslContext != null )
        {
            socketFactory = sslContext.getSocketFactory();
        }
        else
        {
            socketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        }

        HostnameVerifier verifier =
            ( authContext != null ) ? authContext.get( AuthenticationContext.SSL_HOSTNAME_VERIFIER,
                                                       HostnameVerifier.class ) : null;
        X509HostnameVerifier hostnameVerifier;
        if ( verifier != null )
        {
            hostnameVerifier = X509HostnameVerifierAdapter.adapt( verifier );
        }
        else
        {
            hostnameVerifier = org.apache.http.conn.ssl.SSLSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER;
        }

        String[] cipherSuites = split( get( session, CIPHER_SUITES ) );
        String[] protocols = split( get( session, PROTOCOLS ) );

        return new SslSocketFactory( socketFactory, hostnameVerifier, cipherSuites, protocols );
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

    private SslSocketFactory( SSLSocketFactory socketfactory, X509HostnameVerifier hostnameVerifier,
                              String[] cipherSuites, String[] protocols )
    {
        super( socketfactory, hostnameVerifier );

        this.cipherSuites = cipherSuites;
        this.protocols = protocols;
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
