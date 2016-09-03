package org.eclipse.aether.util.version;

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
