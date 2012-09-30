/*******************************************************************************
 * Copyright (c) 2010, 2012 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.connector.async;

import java.net.MalformedURLException;
import java.net.URL;

import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.sonatype.tests.http.runner.annotations.Configurators;
import org.sonatype.tests.http.runner.junit.ConfigurationRunner;
import org.sonatype.tests.http.server.jetty.configurations.HttpProxyAuthConfigurator;
import org.sonatype.tests.http.server.jetty.configurations.HttpProxyConfigurator;

/**
 */
@RunWith( ConfigurationRunner.class )
@Configurators( { HttpProxyConfigurator.class, HttpProxyAuthConfigurator.class } )
public class ProxyGetTest
    extends GetTest
{

    @Override
    @Before
    public void before()
        throws Exception
    {
        super.before();

        Authentication auth = new AuthenticationBuilder().username( "puser" ).password( "password" ).build();
        Proxy proxy = new Proxy( "http", "localhost", provider().getPort(), auth );
        repository = new RemoteRepository.Builder( repository() ).setProxy( proxy ).build();
    }

    @Override
    public String url()
    {
        URL orig;
        try
        {
            orig = new URL( super.url() );
            return new URL( orig.getProtocol(), "proxiedhost", orig.getPort(), "" ).toString();
        }
        catch ( MalformedURLException e )
        {
            e.printStackTrace();
            throw new IllegalStateException( e.getMessage(), e );
        }
    }

}
