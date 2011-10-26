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
package org.eclipse.aether.repository;

import static org.junit.Assert.*;

import org.eclipse.aether.repository.Proxy;
import org.junit.Test;

/**
 */
public class ProxyTest
{

    @Test
    public void testSetHost()
    {
        Proxy p1 = new Proxy( "type", "host", 80, null );

        Proxy p2 = p1.setHost( null );
        assertNotSame( p2, p1 );
        assertEquals( "", p2.getHost() );

        Proxy p3 = p1.setHost( "HOST" );
        assertNotSame( p3, p1 );
        assertEquals( "HOST", p3.getHost() );
    }

    @Test
    public void testSetType()
    {
        Proxy p1 = new Proxy( "type", "host", 80, null );

        Proxy p2 = p1.setType( null );
        assertNotSame( p2, p1 );
        assertEquals( "", p2.getType() );

        Proxy p3 = p1.setType( "TYPE" );
        assertNotSame( p3, p1 );
        assertEquals( "TYPE", p3.getType() );
    }

    @Test
    public void testSetPort()
    {
        Proxy p1 = new Proxy( "type", "host", 80, null );

        Proxy p2 = p1.setPort( 8080 );
        assertNotSame( p2, p1 );
        assertEquals( 8080, p2.getPort() );
    }

}
