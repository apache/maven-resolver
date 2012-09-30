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
package org.eclipse.aether.connector.file;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Map;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.connector.file.FileRepositoryConnectorFactory;
import org.eclipse.aether.internal.test.impl.TestFileProcessor;
import org.eclipse.aether.internal.test.util.TestFileUtils;
import org.eclipse.aether.internal.test.util.connector.suite.ConnectorTestSuite;
import org.eclipse.aether.internal.test.util.connector.suite.ConnectorTestSetup.AbstractConnectorTestSetup;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;

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
            return new FileRepositoryConnectorFactory().setFileProcessor( TestFileProcessor.INSTANCE );
        }

        @Override
        public void after( RepositorySystemSession session, RemoteRepository repository, Map<String, Object> context )
            throws Exception
        {
            TestFileUtils.delete( repoFile );
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
