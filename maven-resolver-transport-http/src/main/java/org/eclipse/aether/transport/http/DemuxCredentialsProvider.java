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

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.client.CredentialsProvider;

/**
 * Credentials provider that helps to isolate server from proxy credentials. Apache HttpClient uses a single provider
 * for both server and proxy auth, using the auth scope (host, port, etc.) to select the proper credentials. With regard
 * to redirects, we use an auth scope for server credentials that's not specific enough to not be mistaken for proxy
 * auth. This provider helps to maintain the proper isolation.
 */
final class DemuxCredentialsProvider
    implements CredentialsProvider
{

    private final CredentialsProvider serverCredentialsProvider;

    private final CredentialsProvider proxyCredentialsProvider;

    private final HttpHost proxy;

    DemuxCredentialsProvider( CredentialsProvider serverCredentialsProvider,
                                     CredentialsProvider proxyCredentialsProvider, HttpHost proxy )
    {
        this.serverCredentialsProvider = serverCredentialsProvider;
        this.proxyCredentialsProvider = proxyCredentialsProvider;
        this.proxy = proxy;
    }

    private CredentialsProvider getDelegate( AuthScope authScope )
    {
        if ( proxy.getPort() == authScope.getPort() && proxy.getHostName().equalsIgnoreCase( authScope.getHost() ) )
        {
            return proxyCredentialsProvider;
        }
        return serverCredentialsProvider;
    }

    public Credentials getCredentials( AuthScope authScope )
    {
        return getDelegate( authScope ).getCredentials( authScope );
    }

    public void setCredentials( AuthScope authScope, Credentials credentials )
    {
        getDelegate( authScope ).setCredentials( authScope, credentials );
    }

    public void clear()
    {
        serverCredentialsProvider.clear();
        proxyCredentialsProvider.clear();
    }

}
