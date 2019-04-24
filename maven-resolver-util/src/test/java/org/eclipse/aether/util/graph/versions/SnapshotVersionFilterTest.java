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

    @SuppressWarnings( "EqualsWithItself" )
    @Test
    public void testEquals()
    {
        SnapshotVersionFilter filter = new SnapshotVersionFilter();
        assertFalse( filter.equals( null ) );
        assertTrue( filter.equals( filter ) );
        assertTrue( filter.equals( new SnapshotVersionFilter() ) );
    }

}
