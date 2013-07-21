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

    private final String[] cipherSuites;

    private final String[] protocols;

    public SslSocketFactory( SslConfig config )
    {
        this( getSocketFactory( config.context ), getHostnameVerifier( config.verifier ), config.cipherSuites,
              config.protocols );
    }

    private static SSLSocketFactory getSocketFactory( SSLContext context )
    {
        return ( context != null ) ? context.getSocketFactory() : (SSLSocketFactory) SSLSocketFactory.getDefault();
    }

    private static X509HostnameVerifier getHostnameVerifier( HostnameVerifier verifier )
    {
        return ( verifier != null ) ? X509HostnameVerifierAdapter.adapt( verifier )
                        : org.apache.http.conn.ssl.SSLSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER;
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
