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
package org.eclipse.aether.connector.async;

import org.codehaus.plexus.PlexusTestCase;
import org.eclipse.aether.connector.async.AsyncRepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.io.FileProcessor;
import org.eclipse.aether.spi.log.Logger;
import org.eclipse.aether.test.impl.SysoutLogger;
import org.eclipse.aether.test.impl.TestFileProcessor;

/**
 */
public class PlexusSupportTest
    extends PlexusTestCase
{

    public void testExistenceOfPlexusComponentMetadata()
        throws Exception
    {
        getContainer().addComponent( new SysoutLogger(), Logger.class, null );
        getContainer().addComponent( new TestFileProcessor(), FileProcessor.class, null );

        RepositoryConnectorFactory factory = lookup( RepositoryConnectorFactory.class, "async-http" );
        assertNotNull( factory );
        assertEquals( AsyncRepositoryConnectorFactory.class, factory.getClass() );
    }

}
