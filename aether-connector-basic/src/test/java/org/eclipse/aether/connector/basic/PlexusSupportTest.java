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
package org.eclipse.aether.connector.basic;

import org.codehaus.plexus.ContainerConfiguration;
import org.codehaus.plexus.PlexusTestCase;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.internal.test.util.TestFileProcessor;
import org.eclipse.aether.internal.test.util.TestLoggerFactory;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.layout.NoRepositoryLayoutException;
import org.eclipse.aether.spi.connector.layout.RepositoryLayout;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutProvider;
import org.eclipse.aether.spi.connector.transport.NoTransporterException;
import org.eclipse.aether.spi.connector.transport.Transporter;
import org.eclipse.aether.spi.connector.transport.TransporterProvider;
import org.eclipse.aether.spi.io.FileProcessor;
import org.eclipse.aether.spi.log.LoggerFactory;

/**
 */
public class PlexusSupportTest
    extends PlexusTestCase
{

    @Override
    protected void customizeContainerConfiguration( ContainerConfiguration containerConfiguration )
    {
        containerConfiguration.setClassPathScanning( "cache" );
    }

    public void testExistenceOfPlexusComponentMetadata()
        throws Exception
    {
        RepositoryLayoutProvider layoutProvider = new RepositoryLayoutProvider()
        {
            public RepositoryLayout newRepositoryLayout( RepositorySystemSession session, RemoteRepository repository )
                throws NoRepositoryLayoutException
            {
                return null;
            }
        };
        TransporterProvider transporterProvider = new TransporterProvider()
        {
            public Transporter newTransporter( RepositorySystemSession session, RemoteRepository repository )
                throws NoTransporterException
            {
                return null;
            }
        };
        getContainer().addComponent( new TestLoggerFactory(), LoggerFactory.class, null );
        getContainer().addComponent( new TestFileProcessor(), FileProcessor.class, null );
        getContainer().addComponent( layoutProvider, RepositoryLayoutProvider.class, null );
        getContainer().addComponent( transporterProvider, TransporterProvider.class, null );

        RepositoryConnectorFactory factory = lookup( RepositoryConnectorFactory.class, "basic" );
        assertNotNull( factory );
        assertEquals( BasicRepositoryConnectorFactory.class, factory.getClass() );
    }

}
