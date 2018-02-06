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

    SslSocketFactory( SslConfig config )
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
