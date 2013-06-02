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
package org.eclipse.aether.transport.wagon;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.maven.wagon.ConnectionException;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.providers.http.LightweightHttpWagon;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.internal.test.util.TestFileProcessor;
import org.eclipse.aether.internal.test.util.TestLoggerFactory;
import org.eclipse.aether.internal.test.util.connector.suite.ConnectorTestSuite;
import org.eclipse.aether.internal.test.util.connector.suite.ConnectorTestSetup.AbstractConnectorTestSetup;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.layout.NoRepositoryLayoutException;
import org.eclipse.aether.spi.connector.layout.RepositoryLayout;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutProvider;
import org.eclipse.aether.spi.connector.transport.NoTransporterException;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.TransporterProvider;
import org.eclipse.aether.util.repository.layout.MavenDefaultLayout;
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
            return new RemoteRepository.Builder( "jetty-repo", "default", provider.getUrl().toString() + "/repo" ).build();
        }

        public RepositoryConnectorFactory factory()
        {
            final WagonProvider wagonProvider = new WagonProvider()
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
            };
            BasicRepositoryConnectorFactory factory = new BasicRepositoryConnectorFactory();
            factory.setFileProcessor( new TestFileProcessor() );
            factory.setTransporterProvider( new TransporterProvider()
            {
                public Transporter newTransporter( RepositorySystemSession session, RemoteRepository repository )
                    throws NoTransporterException
                {
                    return new WagonTransporter( wagonProvider, null, repository, session,
                                                 new TestLoggerFactory().getLogger( "wagon" ) );
                }
            } );
            factory.setRepositoryLayoutProvider( new RepositoryLayoutProvider()
            {

                public RepositoryLayout newRepositoryLayout( RepositorySystemSession session,
                                                             RemoteRepository repository )
                    throws NoRepositoryLayoutException
                {
                    return new RepositoryLayout()
                    {
                        private final MavenDefaultLayout layout = new MavenDefaultLayout();

                        public URI getLocation( Metadata metadata )
                        {
                            return layout.getPath( metadata );
                        }

                        public URI getLocation( Artifact artifact )
                        {
                            return layout.getPath( artifact );
                        }

                        public List<Checksum> getChecksums( Metadata metadata, URI location, boolean create )
                        {
                            return Collections.emptyList();
                        }

                        public List<Checksum> getChecksums( Artifact artifact, URI location, boolean create )
                        {
                            return Collections.emptyList();
                        }
                    };
                }
            } );
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

    public TestSuiteHttpWagon()
    {
        super( new JettyConnectorTestSetup() );
    }

}
