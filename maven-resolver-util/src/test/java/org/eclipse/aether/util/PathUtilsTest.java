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
package org.eclipse.aether.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class PathUtilsTest {
    @Test
    public void testStringToPathSegmentDotTokens() {
        // "." and ".." contain no illegal character, but carry path meaning when used as a path segment
        assertEquals("-DOT-", PathUtils.stringToPathSegment("."));
        assertEquals("-DOTDOT-", PathUtils.stringToPathSegment(".."));
        // dotted names and longer dot runs are inert as single path segments and stay untouched
        assertEquals("...", PathUtils.stringToPathSegment("..."));
        assertEquals("my.repo", PathUtils.stringToPathSegment("my.repo"));
        assertEquals("repo..id", PathUtils.stringToPathSegment("repo..id"));
        assertEquals("..id", PathUtils.stringToPathSegment("..id"));
    }

    @Test
    public void testValidatePathComponentRejectsSeparatorsAndColon() {
        assertRejected("..", "version");
        assertRejected("1.0/../etc", "version");
        assertRejected("1.0\\..\\etc", "version");
        assertRejected("a:b", "groupId");
    }

    @Test
    public void testValidatePathComponentAcceptsNormalValues() {
        PathUtils.validatePathComponent(null, "version");
        PathUtils.validatePathComponent("", "version");
        PathUtils.validatePathComponent("commons-io", "artifactId");
        PathUtils.validatePathComponent("1.0-SNAPSHOT", "version");
        // version "1.." is a valid version string, and is not dot-expanded into path segments
        PathUtils.validatePathComponent("1..", "version");
    }

    @Test
    public void testValidateDotSeparatedPathComponentRejectsEmptyDotSegments() {
        // every dot-separated segment must be non-empty
        assertDotSeparatedRejected(".a.b");
        assertDotSeparatedRejected("..a");
        assertDotSeparatedRejected("com..example");
        assertDotSeparatedRejected("com.example.");
        assertDotSeparatedRejected(".");
        // checks of validatePathComponent still apply
        assertDotSeparatedRejected("a:b");
        assertDotSeparatedRejected("a/b");
    }

    @Test
    public void testValidateDotSeparatedPathComponentAcceptsNormalValues() {
        PathUtils.validateDotSeparatedPathComponent(null, "groupId");
        PathUtils.validateDotSeparatedPathComponent("", "groupId");
        PathUtils.validateDotSeparatedPathComponent("org.apache.maven", "groupId");
        PathUtils.validateDotSeparatedPathComponent("commons-io", "groupId");
    }

    private static void assertRejected(String value, String label) {
        try {
            PathUtils.validatePathComponent(value, label);
            fail("expected IllegalArgumentException for " + value);
        } catch (IllegalArgumentException expected) {
        }
    }

    private static void assertDotSeparatedRejected(String value) {
        try {
            PathUtils.validateDotSeparatedPathComponent(value, "groupId");
            fail("expected IllegalArgumentException for " + value);
        } catch (IllegalArgumentException expected) {
        }
    }
}
