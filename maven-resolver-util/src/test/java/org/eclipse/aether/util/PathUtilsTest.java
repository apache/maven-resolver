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

import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PathUtilsTest {
    @Test
    void stringToPathSegment_fixes() {
        UnaryOperator<String> safeId = PathUtils::stringToPathSegment;
        String good = "good";
        String bad = "bad:id";

        String goodFixedId = safeId.apply(good);
        assertEquals(good, goodFixedId);

        String badFixedId = safeId.apply(bad);
        assertNotEquals(bad, badFixedId);
        assertEquals("bad-COLON-id", badFixedId);
    }

    @Test
    void stringToPathSegment_dotSegments() {
        // "." and ".." contain no illegal character, but carry path meaning: a repository id of ".." used as a
        // path segment escapes the intended base directory of every sink that splices it into a path
        // (origin-aware trusted checksums, split local repository prefixes)
        assertEquals("-DOTDOT-", PathUtils.stringToPathSegment(".."));
        assertEquals("-DOT-", PathUtils.stringToPathSegment("."));
        // dotted names and longer dot runs are inert as single path segments and stay untouched
        assertEquals("...", PathUtils.stringToPathSegment("..."));
        assertEquals("my.repo", PathUtils.stringToPathSegment("my.repo"));
        assertEquals("repo..id", PathUtils.stringToPathSegment("repo..id"));
        assertEquals("..id", PathUtils.stringToPathSegment("..id"));
    }

    @Test
    void stringToPathSegment_allCharsBad() {
        String veryBad = "\\/:\"<>|?*";
        String badFixedId = PathUtils.stringToPathSegment(veryBad);
        assertNotEquals(veryBad, badFixedId);
        assertEquals("-BACKSLASH--SLASH--COLON--QUOTE--LT--GT--PIPE--QMARK--ASTERISK-", badFixedId);
    }

    @Test
    void validatePathComponent_rejectsTraversalSeparatorsAndColon() {
        assertThrows(IllegalArgumentException.class, () -> PathUtils.validatePathComponent("..", "version"));
        assertThrows(IllegalArgumentException.class, () -> PathUtils.validatePathComponent("1.0/../etc", "version"));
        assertThrows(IllegalArgumentException.class, () -> PathUtils.validatePathComponent("1.0\\..\\etc", "version"));
        assertThrows(IllegalArgumentException.class, () -> PathUtils.validatePathComponent("C:", "groupId"));
        assertThrows(IllegalArgumentException.class, () -> PathUtils.validatePathComponent("1.0:evil", "version"));
    }

    @Test
    void validatePathComponent_acceptsNormalValues() {
        assertDoesNotThrow(() -> PathUtils.validatePathComponent(null, "version"));
        assertDoesNotThrow(() -> PathUtils.validatePathComponent("", "version"));
        assertDoesNotThrow(() -> PathUtils.validatePathComponent("commons-io", "artifactId"));
        assertDoesNotThrow(() -> PathUtils.validatePathComponent("1.0-SNAPSHOT", "version"));
        // version "1.." is a valid version string, and is not dot-expanded into path segments
        assertDoesNotThrow(() -> PathUtils.validatePathComponent("1..", "version"));
    }

    @Test
    void validateDotSeparatedPathComponent_rejectsEmptyDotSegments() {
        // leading dot: ".home.ci" would expand to absolute path "/home/ci"
        assertThrows(
                IllegalArgumentException.class,
                () -> PathUtils.validateDotSeparatedPathComponent(".home.ci", "groupId"));
        // leading double dot: "..evilhost" would expand to authority URI "//evilhost"
        assertThrows(
                IllegalArgumentException.class,
                () -> PathUtils.validateDotSeparatedPathComponent("..evilhost", "groupId"));
        // consecutive dots
        assertThrows(
                IllegalArgumentException.class,
                () -> PathUtils.validateDotSeparatedPathComponent("com..example", "groupId"));
        // trailing dot
        assertThrows(
                IllegalArgumentException.class,
                () -> PathUtils.validateDotSeparatedPathComponent("com.example.", "groupId"));
        // dot(s) only
        assertThrows(IllegalArgumentException.class, () -> PathUtils.validateDotSeparatedPathComponent(".", "groupId"));
        // checks of validatePathComponent still apply
        assertThrows(
                IllegalArgumentException.class, () -> PathUtils.validateDotSeparatedPathComponent("a:b", "groupId"));
        assertThrows(
                IllegalArgumentException.class, () -> PathUtils.validateDotSeparatedPathComponent("a/b", "groupId"));
    }

    @Test
    void validateDotSeparatedPathComponent_acceptsNormalValues() {
        assertDoesNotThrow(() -> PathUtils.validateDotSeparatedPathComponent(null, "groupId"));
        assertDoesNotThrow(() -> PathUtils.validateDotSeparatedPathComponent("", "groupId"));
        assertDoesNotThrow(() -> PathUtils.validateDotSeparatedPathComponent("org.apache.maven", "groupId"));
        assertDoesNotThrow(() -> PathUtils.validateDotSeparatedPathComponent("commons-io", "groupId"));
    }
}
