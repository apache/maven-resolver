package org.eclipse.aether.util.graph.versions;

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

import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.VersionFilter;
import org.eclipse.aether.collection.VersionFilter.VersionFilterContext;
import org.eclipse.aether.util.graph.version.ChainedVersionFilter;
import org.eclipse.aether.util.graph.version.HighestVersionFilter;
import org.eclipse.aether.util.graph.version.SnapshotVersionFilter;
import org.junit.Test;

public class ChainedVersionFilterTest
    extends AbstractVersionFilterTest
{

    @Test
    public void testFilterVersions()
        throws Exception
    {
        VersionFilter filter =
            ChainedVersionFilter.newInstance( new SnapshotVersionFilter(), new HighestVersionFilter() );
        VersionFilterContext ctx = newContext( "g:a:[1,9]", "1", "2", "3-SNAPSHOT" );
        filter.filterVersions( ctx );
        assertVersions( ctx, "2" );
    }

    @Test
    public void testDeriveChildFilter()
    {
        VersionFilter filter1 = new HighestVersionFilter();
        VersionFilter filter2 = new VersionFilter()
        {
            public void filterVersions( VersionFilterContext context )
            {
            }

            public VersionFilter deriveChildFilter( DependencyCollectionContext context )
            {
                return null;
            }
        };

        VersionFilter filter = ChainedVersionFilter.newInstance( filter1 );
        assertSame( filter, derive( filter, "g:a:1" ) );

        filter = ChainedVersionFilter.newInstance( filter2 );
        assertSame( null, derive( filter, "g:a:1" ) );

        filter = ChainedVersionFilter.newInstance( filter1, filter2 );
        assertSame( filter1, derive( filter, "g:a:1" ) );

        filter = ChainedVersionFilter.newInstance( filter2, filter1 );
        assertSame( filter1, derive( filter, "g:a:1" ) );
    }

    @Test
    public void testEquals()
    {
        VersionFilter filter = ChainedVersionFilter.newInstance( new HighestVersionFilter() );
        assertFalse( filter.equals( null ) );
        assertTrue( filter.equals( filter ) );
        assertTrue( filter.equals( ChainedVersionFilter.newInstance( new HighestVersionFilter() ) ) );
    }

}
