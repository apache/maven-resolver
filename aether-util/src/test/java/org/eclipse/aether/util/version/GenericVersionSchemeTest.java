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
package org.eclipse.aether.util.version;

import static org.junit.Assert.*;

import org.eclipse.aether.util.version.GenericVersion;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.VersionConstraint;
import org.junit.Before;
import org.junit.Test;

/**
 */
public class GenericVersionSchemeTest
{

    private GenericVersionScheme scheme;

    @Before
    public void setUp()
        throws Exception
    {
        scheme = new GenericVersionScheme();
    }

    private InvalidVersionSpecificationException parseInvalid( String constraint )
    {
        try
        {
            scheme.parseVersionConstraint( constraint );
            fail( "expected exception for constraint " + constraint );
            return null;
        }
        catch ( InvalidVersionSpecificationException e )
        {
            return e;
        }
    }


    @Test
    public void testEnumeratedVersions()
        throws InvalidVersionSpecificationException
    {
        VersionConstraint c = scheme.parseVersionConstraint( "1.0" );
        assertEquals( "1.0", c.getVersion().toString() );
        assertTrue( c.containsVersion( new GenericVersion( "1.0" ) ) );

        c = scheme.parseVersionConstraint( "[1.0]" );
        assertEquals( null, c.getVersion() );
        assertTrue( c.containsVersion( new GenericVersion( "1.0" ) ) );

        c = scheme.parseVersionConstraint( "[1.0],[2.0]" );
        assertTrue( c.containsVersion( new GenericVersion( "1.0" ) ) );
        assertTrue( c.containsVersion( new GenericVersion( "2.0" ) ) );

        c = scheme.parseVersionConstraint( "[1.0],[2.0],[3.0]" );
        assertContains( c, "1.0", "2.0", "3.0" );
        assertNotContains( c, "1.5" );

        c = scheme.parseVersionConstraint( "[1,3),(3,5)" );
        assertContains( c, "1", "2", "4" );
        assertNotContains( c, "3", "5" );

        c = scheme.parseVersionConstraint( "[1,3),(3,)" );
        assertContains( c, "1", "2", "4" );
        assertNotContains( c, "3" );
    }

    private void assertNotContains( VersionConstraint c, String... versions )
    {
        assertContains( String.format( "%s: %%s should not be contained\n", c.toString() ), c, false, versions );
    }

    private void assertContains( String msg, VersionConstraint c, boolean b, String... versions )
    {
        for ( String v : versions )
        {
            assertEquals( String.format( msg, v ), b, c.containsVersion( new GenericVersion( v ) ) );
        }
    }

    private void assertContains( VersionConstraint c, String... versions )
    {
        assertContains( String.format( "%s: %%s should be contained\n", c.toString() ), c, true, versions );
    }
    
    @Test
    public void testInvalid()
    {
        parseInvalid( "[1," );
        parseInvalid( "[1,2],(3," );
        parseInvalid( "[1,2],3" );
    }
}
