/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.eclipse.aether.util.graph.versions;

import org.eclipse.aether.collection.VersionFilter.VersionFilterContext;
import org.eclipse.aether.util.graph.version.LowestVersionFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class LowestVersionFilterTest extends AbstractVersionFilterTest {

    @Test
    void testFilterVersions() {
        LowestVersionFilter filter = new LowestVersionFilter();
        VersionFilterContext ctx = newContext("g:a:[1,9]", "1", "2", "3", "4", "5", "6", "7", "8", "9");
        filter.filterVersions(ctx);
        assertVersions(ctx, "1");
    }

    @Test
    void testFilterVersions3() {
        LowestVersionFilter filter = new LowestVersionFilter(3);
        VersionFilterContext ctx = newContext("g:a:[1,9]", "1", "2", "3", "4", "5", "6", "7", "8", "9");
        filter.filterVersions(ctx);
        assertVersions(ctx, "1", "2", "3");
    }

    @Test
    void testDeriveChildFilter() {
        LowestVersionFilter filter = new LowestVersionFilter();
        assertSame(filter, derive(filter, "g:a:1"));
    }

    @SuppressWarnings("EqualsWithItself")
    @Test
    void testEquals() {
        LowestVersionFilter filter = new LowestVersionFilter();
        assertNotEquals(null, filter);
        assertEquals(filter, filter);
        assertEquals(filter, new LowestVersionFilter());
    }
}
