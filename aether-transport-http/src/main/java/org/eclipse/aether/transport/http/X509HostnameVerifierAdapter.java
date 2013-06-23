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
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import org.apache.http.conn.ssl.X509HostnameVerifier;

/**
 * Makes a standard hostname verifier compatible with Apache HttpClient's API.
 */
final class X509HostnameVerifierAdapter
    implements X509HostnameVerifier
{

    private final HostnameVerifier verifier;

    public static X509HostnameVerifier adapt( HostnameVerifier verifier )
    {
        if ( verifier instanceof X509HostnameVerifier )
        {
            return (X509HostnameVerifier) verifier;
        }
        return new X509HostnameVerifierAdapter( verifier );
    }

    private X509HostnameVerifierAdapter( HostnameVerifier verifier )
    {
        this.verifier = verifier;
    }

    public boolean verify( String hostname, SSLSession session )
    {
        return verifier.verify( hostname, session );
    }

    public void verify( String host, SSLSocket socket )
        throws IOException
    {
        if ( !verify( host, socket.getSession() ) )
        {
            throw new SSLException( "<" + host + "> does not pass hostname verification" );
        }
    }

    public void verify( String host, X509Certificate cert )
        throws SSLException
    {
        throw new UnsupportedOperationException();
    }

    public void verify( String host, String[] cns, String[] subjectAlts )
        throws SSLException
    {
        throw new UnsupportedOperationException();
    }

}
