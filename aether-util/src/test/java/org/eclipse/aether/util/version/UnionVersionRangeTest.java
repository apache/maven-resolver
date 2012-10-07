/*******************************************************************************
 * Copyright (c) 2012 Sonatype, Inc.
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

import java.util.Collections;

import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.VersionRange;
import org.junit.Test;

public class UnionVersionRangeTest
{

    private VersionRange newRange( String range )
    {
        try
        {
            return new GenericVersionScheme().parseVersionRange( range );
        }
        catch ( InvalidVersionSpecificationException e )
        {
            throw new IllegalArgumentException( e );
        }
    }

    private void assertBound( String version, boolean inclusive, VersionRange.Bound bound )
    {
        if ( version == null )
        {
            assertNull( bound );
        }
        else
        {
            assertNotNull( bound );
            assertNotNull( bound.getVersion() );
            assertEquals( inclusive, bound.isInclusive() );
            try
            {
                assertEquals( new GenericVersionScheme().parseVersion( version ), bound.getVersion() );
            }
            catch ( InvalidVersionSpecificationException e )
            {
                throw new IllegalArgumentException( e );
            }
        }
    }

    @Test
    public void testGetLowerBound()
    {
        VersionRange range = UnionVersionRange.from( Collections.<VersionRange> emptySet() );
        assertBound( null, false, range.getLowerBound() );

        range = UnionVersionRange.from( newRange( "[1,2]" ), newRange( "[3,4]" ) );
        assertBound( "1", true, range.getLowerBound() );

        range = UnionVersionRange.from( newRange( "[1,2]" ), newRange( "(,4]" ) );
        assertBound( null, false, range.getLowerBound() );

        range = UnionVersionRange.from( newRange( "[1,2]" ), newRange( "(1,4]" ) );
        assertBound( "1", true, range.getLowerBound() );

        range = UnionVersionRange.from( newRange( "[1,2]" ), newRange( "(0,4]" ) );
        assertBound( "0", false, range.getLowerBound() );
    }

    @Test
    public void testGetUpperBound()
    {
        VersionRange range = UnionVersionRange.from( Collections.<VersionRange> emptySet() );
        assertBound( null, false, range.getUpperBound() );

        range = UnionVersionRange.from( newRange( "[1,2]" ), newRange( "[3,4]" ) );
        assertBound( "4", true, range.getUpperBound() );

        range = UnionVersionRange.from( newRange( "[1,2]" ), newRange( "[3,)" ) );
        assertBound( null, false, range.getUpperBound() );

        range = UnionVersionRange.from( newRange( "[1,2]" ), newRange( "[1,2)" ) );
        assertBound( "2", true, range.getUpperBound() );

        range = UnionVersionRange.from( newRange( "[1,2]" ), newRange( "[1,3)" ) );
        assertBound( "3", false, range.getUpperBound() );
    }

}
