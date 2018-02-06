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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.eclipse.aether.repository.AuthenticationContext;

/**
 * Credentials provider that defers calls into the auth context until authentication is actually requested.
 */
final class DeferredCredentialsProvider
    implements CredentialsProvider
{

    private final CredentialsProvider delegate;

    private final Map<AuthScope, Factory> factories;

    DeferredCredentialsProvider()
    {
        delegate = new BasicCredentialsProvider();
        factories = new HashMap<AuthScope, Factory>();
    }

    public void setCredentials( AuthScope authScope, Factory factory )
    {
        factories.put( authScope, factory );
    }

    public void setCredentials( AuthScope authScope, Credentials credentials )
    {
        delegate.setCredentials( authScope, credentials );
    }

    public Credentials getCredentials( AuthScope authScope )
    {
        synchronized ( factories )
        {
            for ( Iterator<Map.Entry<AuthScope, Factory>> it = factories.entrySet().iterator(); it.hasNext(); )
            {
                Map.Entry<AuthScope, Factory> entry = it.next();
                if ( authScope.match( entry.getKey() ) >= 0 )
                {
                    it.remove();
                    delegate.setCredentials( entry.getKey(), entry.getValue().newCredentials() );
                }
            }
        }
        return delegate.getCredentials( authScope );
    }

    public void clear()
    {
        delegate.clear();
    }

    interface Factory
    {

        Credentials newCredentials();

    }

    static class BasicFactory
        implements Factory
    {

        private final AuthenticationContext authContext;

        BasicFactory( AuthenticationContext authContext )
        {
            this.authContext = authContext;
        }

        public Credentials newCredentials()
        {
            String username = authContext.get( AuthenticationContext.USERNAME );
            if ( username == null )
            {
                return null;
            }
            String password = authContext.get( AuthenticationContext.PASSWORD );
            return new UsernamePasswordCredentials( username, password );
        }

    }

    static class NtlmFactory
        implements Factory
    {

        private final AuthenticationContext authContext;

        NtlmFactory( AuthenticationContext authContext )
        {
            this.authContext = authContext;
        }

        public Credentials newCredentials()
        {
            String username = authContext.get( AuthenticationContext.USERNAME );
            if ( username == null )
            {
                return null;
            }
            String password = authContext.get( AuthenticationContext.PASSWORD );
            String domain = authContext.get( AuthenticationContext.NTLM_DOMAIN );
            String workstation = authContext.get( AuthenticationContext.NTLM_WORKSTATION );

            if ( domain == null )
            {
                int backslash = username.indexOf( '\\' );
                if ( backslash < 0 )
                {
                    domain = guessDomain();
                }
                else
                {
                    domain = username.substring( 0, backslash );
                    username = username.substring( backslash + 1 );
                }
            }
            if ( workstation == null )
            {
                workstation = guessWorkstation();
            }

            return new NTCredentials( username, password, workstation, domain );
        }

        private static String guessDomain()
        {
            return safeNtlmString( System.getProperty( "http.auth.ntlm.domain" ), System.getenv( "USERDOMAIN" ) );
        }

        private static String guessWorkstation()
        {
            String localHost = null;
            try
            {
                localHost = InetAddress.getLocalHost().getHostName();
            }
            catch ( UnknownHostException e )
            {
                // well, we have other options to try
            }
            return safeNtlmString( System.getProperty( "http.auth.ntlm.host" ), System.getenv( "COMPUTERNAME" ),
                                   localHost );
        }

        private static String safeNtlmString( String... strings )
        {
            for ( String string : strings )
            {
                if ( string != null )
                {
                    return string;
                }
            }
            // avoid NPE from httpclient and trigger proper auth failure instead
            return "";
        }

    }

}
