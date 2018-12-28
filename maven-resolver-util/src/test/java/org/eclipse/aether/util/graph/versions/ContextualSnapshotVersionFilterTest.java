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

    @SuppressWarnings( "EqualsWithItself" )
    @Test
    public void testEquals()
    {
        ContextualSnapshotVersionFilter filter = new ContextualSnapshotVersionFilter();
        assertFalse( filter.equals( null ) );
        assertTrue( filter.equals( filter ) );
        assertTrue( filter.equals( new ContextualSnapshotVersionFilter() ) );
    }

}
