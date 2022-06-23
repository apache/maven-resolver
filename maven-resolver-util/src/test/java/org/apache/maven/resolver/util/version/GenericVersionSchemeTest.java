package org.apache.maven.resolver.util.version;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *  http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import static org.junit.Assert.*;

import org.apache.maven.resolver.version.InvalidVersionSpecificationException;
import org.apache.maven.resolver.version.VersionConstraint;
import org.junit.Before;
import org.junit.Test;

/**
 */
public class GenericVersionSchemeTest
{

    private GenericVersionScheme scheme;

    @Before
    public void setUp()
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
        assertNull( c.getVersion() );
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
