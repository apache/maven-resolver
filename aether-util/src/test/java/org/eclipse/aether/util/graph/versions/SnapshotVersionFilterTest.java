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

import org.eclipse.aether.collection.VersionFilter.VersionFilterContext;
import org.eclipse.aether.util.graph.version.SnapshotVersionFilter;
import org.junit.Test;

public class SnapshotVersionFilterTest
    extends AbstractVersionFilterTest
{

    @Test
    public void testFilterVersions()
    {
        SnapshotVersionFilter filter = new SnapshotVersionFilter();
        VersionFilterContext ctx = newContext( "g:a:[1,9]", "1", "2-SNAPSHOT", "3.1", "4.0-SNAPSHOT", "5.0.0" );
        filter.filterVersions( ctx );
        assertVersions( ctx, "1", "3.1", "5.0.0" );
    }

    @Test
    public void testDeriveChildFilter()
    {
        SnapshotVersionFilter filter = new SnapshotVersionFilter();
        assertSame( filter, derive( filter, "g:a:1" ) );
    }

    @Test
    public void testEquals()
    {
        SnapshotVersionFilter filter = new SnapshotVersionFilter();
        assertFalse( filter.equals( null ) );
        assertTrue( filter.equals( filter ) );
        assertTrue( filter.equals( new SnapshotVersionFilter() ) );
    }

}
