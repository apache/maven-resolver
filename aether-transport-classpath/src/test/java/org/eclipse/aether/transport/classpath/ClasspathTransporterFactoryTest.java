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

import static org.junit.Assert.*;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.internal.test.util.TestLoggerFactory;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.NoTransporterException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 */
public class ClasspathTransporterFactoryTest
{

    private DefaultRepositorySystemSession session;

    private ClasspathTransporterFactory factory;

    private RemoteRepository newRepo( String url )
    {
        return new RemoteRepository.Builder( "test", "default", url ).build();
    }

    @Before
    public void setUp()
        throws Exception
    {
        session = TestUtils.newSession();
        factory = new ClasspathTransporterFactory( new TestLoggerFactory() );
    }

    @After
    public void tearDown()
    {
        factory = null;
        session = null;
    }

    @Test
    public void testCaseInsensitiveProtocol()
        throws Exception
    {
        assertNotNull( factory.newInstance( session, newRepo( "classpath:/void" ) ) );
        assertNotNull( factory.newInstance( session, newRepo( "CLASSPATH:/void" ) ) );
        assertNotNull( factory.newInstance( session, newRepo( "ClassPath:/void" ) ) );
    }

    @Test( expected = NoTransporterException.class )
    public void testBadProtocol()
        throws Exception
    {
        factory.newInstance( session, newRepo( "bad:/void" ) );
    }

}
