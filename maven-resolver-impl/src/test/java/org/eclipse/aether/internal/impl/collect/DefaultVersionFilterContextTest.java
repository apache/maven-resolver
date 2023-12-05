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
package org.eclipse.aether.internal.impl.collect;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.internal.test.util.TestUtils;
import org.eclipse.aether.internal.test.util.TestVersion;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DefaultVersionFilterContextTest {
    private static final Dependency FOO_DEPENDENCY = new Dependency(new DefaultArtifact("group-id:foo:1.0"), "test");
    private static final Dependency BAR_DEPENDENCY = new Dependency(new DefaultArtifact("group-id:bar:1.0"), "test");

    @Test
    void iteratorOneItem() {
        DefaultVersionFilterContext context = new DefaultVersionFilterContext(TestUtils.newSession());
        VersionRangeResult result = new VersionRangeResult(new VersionRangeRequest());
        result.addVersion(new TestVersion("1.0"));
        context.set(FOO_DEPENDENCY, result);

        Iterator<Version> iterator = context.iterator();
        assertTrue(iterator.hasNext());
        assertEquals(new TestVersion("1.0"), iterator.next());
    }

    @Test
    void getCountOneItem() {
        DefaultVersionFilterContext context = new DefaultVersionFilterContext(TestUtils.newSession());
        VersionRangeResult result = new VersionRangeResult(new VersionRangeRequest());
        result.addVersion(new TestVersion("1.0"));
        context.set(FOO_DEPENDENCY, result);

        assertEquals(1, context.getCount());
    }

    @Test
    void getOneItem() {
        DefaultVersionFilterContext context = new DefaultVersionFilterContext(TestUtils.newSession());
        VersionRangeResult result = new VersionRangeResult(new VersionRangeRequest());
        result.addVersion(new TestVersion("1.0"));
        context.set(FOO_DEPENDENCY, result);

        assertEquals(Collections.singletonList(new TestVersion("1.0")), context.get());
    }

    @Test
    void iteratorDelete() {
        DefaultVersionFilterContext context = new DefaultVersionFilterContext(TestUtils.newSession());
        VersionRangeResult result = new VersionRangeResult(new VersionRangeRequest());
        result.addVersion(new TestVersion("1.0"));
        context.set(FOO_DEPENDENCY, result);

        Iterator<Version> iterator = context.iterator();
        iterator.next();
        iterator.remove();

        assertEquals(0, context.getCount());
    }

    @Test
    void nextBeyondEnd() {
        DefaultVersionFilterContext context = new DefaultVersionFilterContext(TestUtils.newSession());
        VersionRangeResult result = new VersionRangeResult(new VersionRangeRequest());
        result.addVersion(new TestVersion("1.0"));
        context.set(FOO_DEPENDENCY, result);

        Iterator<Version> iterator = context.iterator();
        iterator.next();
        assertThrows(NoSuchElementException.class, iterator::next);
    }

    @Test
    void removeOneOfOne() {
        DefaultVersionFilterContext context = new DefaultVersionFilterContext(TestUtils.newSession());
        VersionRangeResult result = new VersionRangeResult(new VersionRangeRequest());
        result.addVersion(new TestVersion("1.0"));
        context.set(FOO_DEPENDENCY, result);

        Iterator<Version> iterator = context.iterator();
        iterator.next();
        iterator.remove();

        assertEquals(Collections.emptyList(), context.get());
    }

    @Test
    void removeOneOfTwo() {
        DefaultVersionFilterContext context = new DefaultVersionFilterContext(TestUtils.newSession());
        VersionRangeResult result = new VersionRangeResult(new VersionRangeRequest());
        result.addVersion(new TestVersion("1.0"));
        result.addVersion(new TestVersion("2.0"));
        context.set(FOO_DEPENDENCY, result);

        Iterator<Version> iterator = context.iterator();
        iterator.next();
        iterator.remove();

        assertEquals(Collections.singletonList(new TestVersion("2.0")), context.get());
    }

    @Test
    void removeOneOfThree() {
        DefaultVersionFilterContext context = new DefaultVersionFilterContext(TestUtils.newSession());
        VersionRangeResult result = new VersionRangeResult(new VersionRangeRequest());
        result.addVersion(new TestVersion("1.0"));
        result.addVersion(new TestVersion("2.0"));
        result.addVersion(new TestVersion("3.0"));
        context.set(FOO_DEPENDENCY, result);

        Iterator<Version> iterator = context.iterator();
        iterator.next();
        iterator.remove();

        assertEquals(Arrays.asList(new TestVersion("2.0"), new TestVersion("3.0")), context.get());
    }

    @Test
    void setTwice() {
        DefaultVersionFilterContext context = new DefaultVersionFilterContext(TestUtils.newSession());
        VersionRangeResult fooResult = new VersionRangeResult(new VersionRangeRequest());
        fooResult.addVersion(new TestVersion("1.0"));
        context.set(FOO_DEPENDENCY, fooResult);

        VersionRangeResult barResult = new VersionRangeResult(new VersionRangeRequest());
        barResult.addVersion(new TestVersion("1.0"));
        barResult.addVersion(new TestVersion("2.0"));
        context.set(BAR_DEPENDENCY, barResult);

        assertEquals(Arrays.asList(new TestVersion("1.0"), new TestVersion("2.0")), context.get());
    }
}
