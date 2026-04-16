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
import org.eclipse.aether.util.graph.version.GenericQualifiersVersionFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

public class GenericQualifiersVersionFilterTest extends AbstractVersionFilterTest {

    @Test
    void testPreviewVersionFilter() {
        GenericQualifiersVersionFilter filter = GenericQualifiersVersionFilter.previewVersionFilter();
        VersionFilterContext ctx = newContext(
                "g:a:[1,9]",
                "1-M1",
                "1",
                "2-SNAPSHOT",
                "3.0-GA",
                "3.1",
                "4.0-SNAPSHOT",
                "5.0.0.rc",
                "5.0.0",
                "5.0.0-sp");
        filter.filterVersions(ctx);
        assertVersions(ctx, "1", "2-SNAPSHOT", "3.0-GA", "3.1", "4.0-SNAPSHOT", "5.0.0", "5.0.0-sp");
    }

    @Test
    void testAnyQualifierVersionFilter() {
        GenericQualifiersVersionFilter filter = GenericQualifiersVersionFilter.anyQualifierVersionFilter();
        VersionFilterContext ctx = newContext(
                "g:a:[1,9]",
                "1-M1",
                "1",
                "2-SNAPSHOT",
                "3.0-GA",
                "3.1",
                "4.0-SNAPSHOT",
                "5.0.0.rc",
                "5.0.0",
                "5.0.0-sp");
        filter.filterVersions(ctx);
        assertVersions(ctx, "1", "3.1", "5.0.0");
    }

    @Test
    void testDeriveChildFilter() {
        GenericQualifiersVersionFilter filter = GenericQualifiersVersionFilter.previewVersionFilter();
        assertSame(filter, derive(filter, "g:a:1"));
    }
}
