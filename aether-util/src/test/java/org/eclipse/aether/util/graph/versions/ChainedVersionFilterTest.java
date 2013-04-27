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
package org.eclipse.aether.util.graph.versions;

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
