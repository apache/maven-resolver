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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.eclipse.aether.version.Version;

/**
 */
abstract class AbstractVersionTest {

    protected static final int X_LT_Y = -1;

    protected static final int X_EQ_Y = 0;

    protected static final int X_GT_Y = 1;

    protected abstract Version newVersion(String version);

    protected void assertOrder(int expected, String version1, String version2) {
        Version v1 = newVersion(version1);
        Version v2 = newVersion(version2);

        if (expected > 0) {
            assertEquals("expected " + v1 + " > " + v2, 1, Integer.signum(v1.compareTo(v2)));
            assertEquals("expected " + v2 + " < " + v1, -1, Integer.signum(v2.compareTo(v1)));
            assertFalse("expected " + v1 + " != " + v2, v1.equals(v2));
            assertFalse("expected " + v2 + " != " + v1, v2.equals(v1));
        } else if (expected < 0) {
            assertEquals("expected " + v1 + " < " + v2, -1, Integer.signum(v1.compareTo(v2)));
            assertEquals("expected " + v2 + " > " + v1, 1, Integer.signum(v2.compareTo(v1)));
            assertFalse("expected " + v1 + " != " + v2, v1.equals(v2));
            assertFalse("expected " + v2 + " != " + v1, v2.equals(v1));
        } else {
            assertEquals("expected " + v1 + " == " + v2, 0, v1.compareTo(v2));
            assertEquals("expected " + v2 + " == " + v1, 0, v2.compareTo(v1));
            assertTrue("expected " + v1 + " == " + v2, v1.equals(v2));
            assertTrue("expected " + v2 + " == " + v1, v2.equals(v1));
            assertEquals("expected #(" + v1 + ") == #(" + v1 + ")", v1.hashCode(), v2.hashCode());
        }
    }

    protected void assertSequence(String... versions) {
        for (int i = 0; i < versions.length - 1; i++) {
            for (int j = i + 1; j < versions.length; j++) {
                assertOrder(X_LT_Y, versions[i], versions[j]);
            }
        }
    }
}
