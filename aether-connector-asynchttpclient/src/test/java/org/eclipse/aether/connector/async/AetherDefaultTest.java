/*******************************************************************************
 * Copyright (c) 2010, 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.connector.async;

import java.util.Map;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.connector.async.AsyncRepositoryConnectorFactory;
import org.eclipse.aether.internal.test.util.TestFileProcessor;
import org.eclipse.aether.internal.test.util.connector.suite.ConnectorTestSuite;
import org.eclipse.aether.internal.test.util.connector.suite.ConnectorTestSetup.AbstractConnectorTestSetup;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.sonatype.tests.http.server.jetty.behaviour.ResourceServer;
import org.sonatype.tests.http.server.jetty.impl.JettyServerProvider;

/**
 */
public class AetherDefaultTest
    extends ConnectorTestSuite
{

    private static class JettyConnectorTestSetup
        extends AbstractConnectorTestSetup
    {

        private JettyServerProvider provider;

        public RemoteRepository before( RepositorySystemSession session, Map<String, Object> context )
            throws Exception
        {
            provider = new JettyServerProvider();
            provider.initServer();
            provider.addBehaviour( "/*", new ResourceServer() );
            provider.start();
            return new RemoteRepository.Builder( "jetty-repo", "default", provider.getUrl().toString() + "/repo" ).build();
        }

        public RepositoryConnectorFactory factory()
        {
            AsyncRepositoryConnectorFactory factory = new AsyncRepositoryConnectorFactory();
            factory.setFileProcessor( new TestFileProcessor() );
            return factory;
        }

        @Override
        public void after( RepositorySystemSession session, RemoteRepository repository, Map<String, Object> context )
            throws Exception
        {
            if ( provider != null )
            {
                provider.stop();
                provider = null;
            }
        }

    }

    /**
     * @param setup
     */
    public AetherDefaultTest()
    {
        super( new JettyConnectorTestSetup() );
    }

}
