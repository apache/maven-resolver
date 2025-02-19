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
package org.eclipse.aether.util.graph.transformer;

import org.eclipse.aether.util.graph.transformer.ConflictIdSorter.ConflictId;
import org.eclipse.aether.util.graph.transformer.ConflictIdSorter.RootQueue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RootQueueTest {

    @Test
    void testIsEmpty() {
        ConflictId id = new ConflictId("a", 0);
        RootQueue queue = new RootQueue(10);
        assertTrue(queue.isEmpty());
        queue.add(id);
        assertFalse(queue.isEmpty());
        assertSame(id, queue.remove());
        assertTrue(queue.isEmpty());
    }

    @Test
    void testAddSortsByDepth() {
        ConflictId id1 = new ConflictId("a", 0);
        ConflictId id2 = new ConflictId("b", 1);
        ConflictId id3 = new ConflictId("c", 2);
        ConflictId id4 = new ConflictId("d", 3);

        RootQueue queue = new RootQueue(10);
        queue.add(id1);
        queue.add(id2);
        queue.add(id3);
        queue.add(id4);
        assertSame(id1, queue.remove());
        assertSame(id2, queue.remove());
        assertSame(id3, queue.remove());
        assertSame(id4, queue.remove());

        queue = new RootQueue(10);
        queue.add(id4);
        queue.add(id3);
        queue.add(id2);
        queue.add(id1);
        assertSame(id1, queue.remove());
        assertSame(id2, queue.remove());
        assertSame(id3, queue.remove());
        assertSame(id4, queue.remove());
    }

    @Test
    void testAddWithArrayCompact() {
        ConflictId id = new ConflictId("a", 0);

        RootQueue queue = new RootQueue(10);
        assertTrue(queue.isEmpty());
        queue.add(id);
        assertFalse(queue.isEmpty());
        assertSame(id, queue.remove());
        assertTrue(queue.isEmpty());
        queue.add(id);
        assertFalse(queue.isEmpty());
        assertSame(id, queue.remove());
        assertTrue(queue.isEmpty());
    }

    @Test
    void testAddMinimumAfterSomeRemoves() {
        ConflictId id1 = new ConflictId("a", 0);
        ConflictId id2 = new ConflictId("b", 1);
        ConflictId id3 = new ConflictId("c", 2);

        RootQueue queue = new RootQueue(10);
        queue.add(id2);
        queue.add(id3);
        assertSame(id2, queue.remove());
        queue.add(id1);
        assertSame(id1, queue.remove());
        assertSame(id3, queue.remove());
        assertTrue(queue.isEmpty());
    }
}
