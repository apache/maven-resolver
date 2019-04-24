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
    {
        throw new UnsupportedOperationException();
    }

    public void verify( String host, String[] cns, String[] subjectAlts )
    {
        throw new UnsupportedOperationException();
    }

}
