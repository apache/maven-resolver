/*******************************************************************************
 * Copyright (c) 2014 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.transport.http;

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

    public DemuxCredentialsProvider( CredentialsProvider serverCredentialsProvider,
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
