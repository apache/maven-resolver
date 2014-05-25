/*******************************************************************************
 * Copyright (c) 2010, 2014 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.util.repository;

import static org.junit.Assert.*;

import org.eclipse.aether.util.repository.DefaultProxySelector;
import org.junit.Test;

/**
 */
public class DefaultProxySelectorTest
{

    private boolean isNonProxyHost( String host, String nonProxyHosts )
    {
        return new DefaultProxySelector.NonProxyHosts( nonProxyHosts ).isNonProxyHost( host );
    }

    @Test
    public void testIsNonProxyHost_Blank()
    {
        assertFalse( isNonProxyHost( "www.eclipse.org", null ) );
        assertFalse( isNonProxyHost( "www.eclipse.org", "" ) );
    }

    @Test
    public void testIsNonProxyHost_Wildcard()
    {
        assertTrue( isNonProxyHost( "www.eclipse.org", "*" ) );
        assertTrue( isNonProxyHost( "www.eclipse.org", "*.org" ) );
        assertFalse( isNonProxyHost( "www.eclipse.org", "*.com" ) );
        assertTrue( isNonProxyHost( "www.eclipse.org", "www.*" ) );
        assertTrue( isNonProxyHost( "www.eclipse.org", "www.*.org" ) );
    }

    @Test
    public void testIsNonProxyHost_Multiple()
    {
        assertTrue( isNonProxyHost( "eclipse.org", "eclipse.org|host2" ) );
        assertTrue( isNonProxyHost( "eclipse.org", "host1|eclipse.org" ) );
        assertTrue( isNonProxyHost( "eclipse.org", "host1|eclipse.org|host2" ) );
    }

    @Test
    public void testIsNonProxyHost_Misc()
    {
        assertFalse( isNonProxyHost( "www.eclipse.org", "www.eclipse.com" ) );
        assertFalse( isNonProxyHost( "www.eclipse.org", "eclipse.org" ) );
    }

    @Test
    public void testIsNonProxyHost_CaseInsensitivity()
    {
        assertTrue( isNonProxyHost( "www.eclipse.org", "www.ECLIPSE.org" ) );
        assertTrue( isNonProxyHost( "www.ECLIPSE.org", "www.eclipse.org" ) );
    }

}
