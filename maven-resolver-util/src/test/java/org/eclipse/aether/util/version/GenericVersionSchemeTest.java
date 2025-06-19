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
package org.eclipse.aether.util.version;

import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.VersionConstraint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 */
public class GenericVersionSchemeTest {

    private GenericVersionScheme scheme;

    @BeforeEach
    void setUp() {
        scheme = new GenericVersionScheme();
    }

    private InvalidVersionSpecificationException parseInvalid(String constraint) {
        try {
            scheme.parseVersionConstraint(constraint);
            fail("expected exception for constraint " + constraint);
            return null;
        } catch (InvalidVersionSpecificationException e) {
            return e;
        }
    }

    @Test
    void testEnumeratedVersions() throws InvalidVersionSpecificationException {
        VersionConstraint c = scheme.parseVersionConstraint("1.0");
        assertEquals("1.0", c.getVersion().toString());
        assertTrue(c.containsVersion(new GenericVersion("1.0")));

        c = scheme.parseVersionConstraint("[1.0]");
        assertNull(c.getVersion());
        assertTrue(c.containsVersion(new GenericVersion("1.0")));

        c = scheme.parseVersionConstraint("[1.0],[2.0]");
        assertTrue(c.containsVersion(new GenericVersion("1.0")));
        assertTrue(c.containsVersion(new GenericVersion("2.0")));

        c = scheme.parseVersionConstraint("[1.0],[2.0],[3.0]");
        assertContains(c, "1.0", "2.0", "3.0");
        assertNotContains(c, "1.5");

        c = scheme.parseVersionConstraint("[1,3),(3,5)");
        assertContains(c, "1", "2", "4");
        assertNotContains(c, "3", "5");

        c = scheme.parseVersionConstraint("[1,3),(3,)");
        assertContains(c, "1", "2", "4");
        assertNotContains(c, "3");
    }

    private void assertNotContains(VersionConstraint c, String... versions) {
        assertContains(String.format("%s: %%s should not be contained\n", c.toString()), c, false, versions);
    }

    private void assertContains(String msg, VersionConstraint c, boolean b, String... versions) {
        for (String v : versions) {
            assertEquals(b, c.containsVersion(new GenericVersion(v)), String.format(msg, v));
        }
    }

    private void assertContains(VersionConstraint c, String... versions) {
        assertContains(String.format("%s: %%s should be contained\n", c.toString()), c, true, versions);
    }

    @Test
    void testInvalid() {
        parseInvalid("[1,");
        parseInvalid("[1,2],(3,");
        parseInvalid("[1,2],3");
    }

    @Test
    void testSameUpperAndLowerBound() throws InvalidVersionSpecificationException {
        VersionConstraint c = scheme.parseVersionConstraint("[1.0]");
        assertEquals("[1.0,1.0]", c.toString());
        VersionConstraint c2 = scheme.parseVersionConstraint(c.toString());
        assertEquals(c, c2);
        assertTrue(c.containsVersion(new GenericVersion("1.0")));
    }

    @Test
    void testVersionCaching() throws InvalidVersionSpecificationException {
        // Test that parsing the same version string returns the same instance (cached)
        GenericVersion v1 = scheme.parseVersion("1.0.0");
        GenericVersion v2 = scheme.parseVersion("1.0.0");

        // Should return the same cached instance
        assertSame(v1, v2, "Parsing the same version string should return the same cached instance");

        // Test that different version strings create different instances
        GenericVersion v3 = scheme.parseVersion("2.0.0");
        assertNotSame(v1, v3, "Different version strings should create different instances");

        // Test that parsing the first version again still returns the cached instance
        GenericVersion v4 = scheme.parseVersion("1.0.0");
        assertSame(v1, v4, "Re-parsing the first version should still return the cached instance");

        // Test with various version formats
        GenericVersion snapshot1 = scheme.parseVersion("1.0.0-SNAPSHOT");
        GenericVersion snapshot2 = scheme.parseVersion("1.0.0-SNAPSHOT");
        assertSame(snapshot1, snapshot2, "Snapshot versions should also be cached");

        // Test that semantically equivalent but different strings are treated as different
        GenericVersion v5 = scheme.parseVersion("1.0");
        GenericVersion v6 = scheme.parseVersion("1.0.0");
        assertNotSame(v5, v6, "Different string representations should not be cached together");
    }
}
