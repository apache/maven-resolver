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
package org.eclipse.aether.transport.file;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.internal.test.util.TestFileProcessor;
import org.eclipse.aether.internal.test.util.TestFileUtils;
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

/**
 */
public class TestSuite
    extends ConnectorTestSuite
{

    private static final class FileConnectorTestSetup
        extends AbstractConnectorTestSetup
    {
        private File repoFile;

        public RepositoryConnectorFactory factory()
        {
            BasicRepositoryConnectorFactory factory = new BasicRepositoryConnectorFactory();
            factory.setFileProcessor( new TestFileProcessor() );
            factory.setTransporterProvider( new TransporterProvider()
            {
                public Transporter newTransporter( RepositorySystemSession session, RemoteRepository repository )
                    throws NoTransporterException
                {
                    return new FileTransporter( repository, new TestLoggerFactory().getLogger( "file" ) );
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
            TestFileUtils.deleteFile( repoFile );
        }

        public RemoteRepository before( RepositorySystemSession session, Map<String, Object> context )
            throws IOException
        {
            RemoteRepository repo = null;
            repoFile = TestFileUtils.createTempDir( "test-repo" );
            try
            {
                repo =
                    new RemoteRepository.Builder( "test-file", "default", repoFile.toURI().toURL().toString() ).build();
            }
            catch ( MalformedURLException e )
            {
                throw new UnsupportedOperationException( "File.toURI().toURL() failed" );
            }
            return repo;
        }
    }

    public TestSuite()
    {
        super( new FileConnectorTestSetup() );
    }

}
