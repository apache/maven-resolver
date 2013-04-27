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
import org.eclipse.aether.util.graph.version.HighestVersionFilter;
import org.junit.Test;

public class HighestVersionFilterTest
    extends AbstractVersionFilterTest
{

    @Test
    public void testFilterVersions()
    {
        HighestVersionFilter filter = new HighestVersionFilter();
        VersionFilterContext ctx = newContext( "g:a:[1,9]", "1", "2", "3", "4", "5", "6", "7", "8", "9" );
        filter.filterVersions( ctx );
        assertVersions( ctx, "9" );
    }

    @Test
    public void testDeriveChildFilter()
    {
        HighestVersionFilter filter = new HighestVersionFilter();
        assertSame( filter, derive( filter, "g:a:1" ) );
    }

    @Test
    public void testEquals()
    {
        HighestVersionFilter filter = new HighestVersionFilter();
        assertFalse( filter.equals( null ) );
        assertTrue( filter.equals( filter ) );
        assertTrue( filter.equals( new HighestVersionFilter() ) );
    }

}
