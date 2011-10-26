/*******************************************************************************
 * Copyright (c) 2010, 2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.connector.wagon;

import java.util.Map;

import org.apache.maven.wagon.ConnectionException;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.providers.http.LightweightHttpWagon;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.connector.wagon.WagonProvider;
import org.eclipse.aether.connector.wagon.WagonRepositoryConnector;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.log.NullLogger;
import org.eclipse.aether.test.impl.TestFileProcessor;
import org.eclipse.aether.test.util.connector.suite.ConnectorTestSuite;
import org.eclipse.aether.test.util.connector.suite.ConnectorTestSetup.AbstractConnectorTestSetup;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;
import org.sonatype.tests.http.server.jetty.behaviour.ResourceServer;
import org.sonatype.tests.http.server.jetty.impl.JettyServerProvider;

/**
 */
public class TestSuiteHttpWagon
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
            return new RemoteRepository( "jetty-repo", "default", provider.getUrl().toString() + "/repo" );
        }

        public RepositoryConnectorFactory factory()
        {
            return new RepositoryConnectorFactory()
            {

                public RepositoryConnector newInstance( RepositorySystemSession session, RemoteRepository repository )
                    throws NoRepositoryConnectorException
                {
                    return new WagonRepositoryConnector( new WagonProvider()
                    {

                        public void release( Wagon wagon )
                        {
                            try
                            {
                                wagon.disconnect();
                            }
                            catch ( ConnectionException e )
                            {
                                throw new RuntimeException( e.getMessage(), e );
                            }
                        }

                        public Wagon lookup( String roleHint )
                            throws Exception
                        {
                            return new LightweightHttpWagon();
                        }

                    }, null, repository, session, TestFileProcessor.INSTANCE, NullLogger.INSTANCE );
                }

                public float getPriority()
                {
                    return 0;
                }
            };
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

    public TestSuiteHttpWagon()
    {
        super( new JettyConnectorTestSetup() );
    }

}
