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

import org.eclipse.aether.collection.VersionFilter;
import org.eclipse.aether.collection.VersionFilter.VersionFilterContext;
import org.eclipse.aether.util.graph.version.ContextualSnapshotVersionFilter;
import org.eclipse.aether.util.graph.version.SnapshotVersionFilter;
import org.junit.Test;

public class ContextualSnapshotVersionFilterTest
    extends AbstractVersionFilterTest
{

    @Test
    public void testFilterVersions()
        throws Exception
    {
        VersionFilter filter = new ContextualSnapshotVersionFilter();
        VersionFilterContext ctx = newContext( "g:a:[1,9]", "1", "2-SNAPSHOT" );
        filter.filterVersions( ctx );
        assertVersions( ctx, "1", "2-SNAPSHOT" );

        ctx = newContext( "g:a:[1,9]", "1", "2-SNAPSHOT" );
        derive( filter, "g:a:1" ).filterVersions( ctx );
        assertVersions( ctx, "1" );

        ctx = newContext( "g:a:[1,9]", "1", "2-SNAPSHOT" );
        session.setConfigProperty( ContextualSnapshotVersionFilter.CONFIG_PROP_ENABLE, "true" );
        derive( filter, "g:a:1-SNAPSHOT" ).filterVersions( ctx );
        assertVersions( ctx, "1" );
    }

    @Test
    public void testDeriveChildFilter()
    {
        ContextualSnapshotVersionFilter filter = new ContextualSnapshotVersionFilter();
        assertTrue( derive( filter, "g:a:1" ) instanceof SnapshotVersionFilter );
        assertSame( null, derive( filter, "g:a:1-SNAPSHOT" ) );
        session.setConfigProperty( ContextualSnapshotVersionFilter.CONFIG_PROP_ENABLE, "true" );
        assertTrue( derive( filter, "g:a:1-SNAPSHOT" ) instanceof SnapshotVersionFilter );
    }

    @Test
    public void testEquals()
    {
        ContextualSnapshotVersionFilter filter = new ContextualSnapshotVersionFilter();
        assertFalse( filter.equals( null ) );
        assertTrue( filter.equals( filter ) );
        assertTrue( filter.equals( new ContextualSnapshotVersionFilter() ) );
    }

}
