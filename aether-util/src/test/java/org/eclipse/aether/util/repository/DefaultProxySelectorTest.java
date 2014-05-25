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
    public void testIsNonProxyHost()
    {
        assertFalse( isNonProxyHost( "www.sonatype.org", null ) );
        assertFalse( isNonProxyHost( "www.sonatype.org", "" ) );

        assertTrue( isNonProxyHost( "www.sonatype.org", "*" ) );
        assertTrue( isNonProxyHost( "www.sonatype.org", "*.org" ) );
        assertTrue( isNonProxyHost( "www.sonatype.org", "www.*" ) );
        assertTrue( isNonProxyHost( "www.sonatype.org", "www.*.org" ) );

        assertFalse( isNonProxyHost( "www.sonatype.org", "www.sonatype.com" ) );
        assertFalse( isNonProxyHost( "www.sonatype.org", "*.com" ) );
        assertFalse( isNonProxyHost( "www.sonatype.org", "sonatype.org" ) );

        assertTrue( isNonProxyHost( "www.sonatype.org", "*.com|*.org" ) );
    }

}
