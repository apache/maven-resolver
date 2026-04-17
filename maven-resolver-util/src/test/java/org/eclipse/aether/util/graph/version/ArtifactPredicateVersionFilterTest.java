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
package org.eclipse.aether.util.graph.version;

import java.util.function.Predicate;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.VersionFilter.VersionFilterContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ArtifactPredicateVersionFilterTest extends AbstractVersionFilterTest {

    @Test
    void testFilterVersions() {
        Predicate<Artifact> oddVersions = a -> Integer.parseInt(a.getVersion()) % 2 != 0;
        ArtifactPredicateVersionFilter filter = new ArtifactPredicateVersionFilter(oddVersions);
        VersionFilterContext ctx = newContext("g:a:[1,9]", "1", "2", "3", "4", "5", "6", "7", "8", "9");
        filter.filterVersions(ctx);
        assertVersions(ctx, "1", "3", "5", "7", "9");
    }

    @Test
    void testDeriveChildFilter() {
        ArtifactPredicateVersionFilter filter = new ArtifactPredicateVersionFilter(a -> true);
        assertSame(filter, derive(filter, "g:a:1"));
    }

    @Test
    void testEquals() {
        Predicate<Artifact> falsePredicate = a -> false;
        Predicate<Artifact> truePredicate = a -> true;
        ArtifactPredicateVersionFilter filter1 = new ArtifactPredicateVersionFilter(falsePredicate);
        ArtifactPredicateVersionFilter filter2 = new ArtifactPredicateVersionFilter(falsePredicate);
        ArtifactPredicateVersionFilter filter3 = new ArtifactPredicateVersionFilter(truePredicate);
        assertNotEquals(null, filter1);
        assertEquals(filter1, filter2);
        assertNotEquals(filter2, filter3);
        assertEquals(filter1, new ArtifactPredicateVersionFilter(falsePredicate));
    }
}
