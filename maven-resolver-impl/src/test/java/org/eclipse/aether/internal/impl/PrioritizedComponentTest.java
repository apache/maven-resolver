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
package org.eclipse.aether.internal.impl;

import org.junit.jupiter.api.Test;

import static org.junit.Assert.*;

public class PrioritizedComponentTest {

    @Test
    public void testIsDisabled() {
        assertTrue(new PrioritizedComponent<>("", String.class, Float.NaN, 0).isDisabled());
        assertFalse(new PrioritizedComponent<>("", String.class, 0, 0).isDisabled());
        assertFalse(new PrioritizedComponent<>("", String.class, 1, 0).isDisabled());
        assertFalse(new PrioritizedComponent<>("", String.class, -1, 0).isDisabled());
    }

    @Test
    public void testCompareTo() {
        assertCompare(0, Float.NaN, Float.NaN);
        assertCompare(0, 0, 0);

        assertCompare(1, 0, 1);
        assertCompare(1, 2, Float.POSITIVE_INFINITY);
        assertCompare(1, Float.NEGATIVE_INFINITY, -3);

        assertCompare(1, Float.NaN, 0);
        assertCompare(1, Float.NaN, -1);
        assertCompare(1, Float.NaN, Float.NEGATIVE_INFINITY);
        assertCompare(1, Float.NaN, Float.POSITIVE_INFINITY);

        assertCompare(-1, Float.NaN, 0, 1);
        assertCompare(-1, 10, 0, 1);
    }

    private void assertCompare(int expected, float priority1, float priority2) {
        PrioritizedComponent<?> one = new PrioritizedComponent<>("", String.class, priority1, 0);
        PrioritizedComponent<?> two = new PrioritizedComponent<>("", String.class, priority2, 0);
        assertEquals(expected, one.compareTo(two));
        assertEquals(-expected, two.compareTo(one));
    }

    private void assertCompare(int expected, float priority, int index1, int index2) {
        PrioritizedComponent<?> one = new PrioritizedComponent<>("", String.class, priority, index1);
        PrioritizedComponent<?> two = new PrioritizedComponent<>("", String.class, priority, index2);
        assertEquals(expected, one.compareTo(two));
        assertEquals(-expected, two.compareTo(one));
    }
}
