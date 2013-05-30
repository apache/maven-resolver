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
package org.eclipse.aether.transport.classpath;

import org.codehaus.plexus.ContainerConfiguration;
import org.codehaus.plexus.PlexusTestCase;
import org.eclipse.aether.internal.test.util.TestLoggerFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.spi.log.LoggerFactory;
import org.eclipse.aether.transport.classpath.ClasspathTransporterFactory;

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
        getContainer().addComponent( new TestLoggerFactory(), LoggerFactory.class, null );

        TransporterFactory factory = lookup( TransporterFactory.class, "classpath" );
        assertNotNull( factory );
        assertEquals( ClasspathTransporterFactory.class, factory.getClass() );
    }

}
