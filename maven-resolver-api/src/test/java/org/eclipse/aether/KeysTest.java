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
package org.eclipse.aether;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class KeysTest {
    @Test
    void noElement() {
        assertThrows(IllegalArgumentException.class, Keys::of);
    }

    @Test
    void nullVararg() {
        assertThrows(NullPointerException.class, () -> Keys.of((Object[]) null));
    }

    @Test
    void globalKeys() {
        Object key1 = Keys.of("one", "two", "three");
        Object key2 = Keys.of("one", "two", "three");
        Object key3 = Keys.of("one", "two");
        assertNotSame(key1, key2);
        assertNotSame(key2, key3);
        assertNotSame(key1, key3);

        assertEquals(key1, key2);
        assertNotEquals(key1, key3);
        assertEquals(key1.hashCode(), key2.hashCode());
        assertNotEquals(key1.hashCode(), key3.hashCode());
    }

    @Test
    void classLoaderAwareKeys() throws Exception {
        String classBinaryName = Keys.class.getName();
        try (URLClassLoader altLoader = new URLClassLoader(
                new URL[] {Paths.get("target/classes").toUri().toURL()}, null)) {
            Class<?> thisClass = Keys.class.getClassLoader().loadClass(classBinaryName);
            Class<?> otherClass = altLoader.loadClass(classBinaryName);

            Object key1 = Keys.of(thisClass, "one", "two");
            Object key2 = Keys.of(thisClass, "one", "two");
            Object key3 = Keys.of(otherClass, "one", "two");
            assertNotSame(key1, key2);
            assertNotSame(key2, key3);
            assertNotSame(key1, key3);

            assertEquals(key1, key2);
            assertNotEquals(key1, key3);
            assertEquals(key1.hashCode(), key2.hashCode());
            assertNotEquals(key1.hashCode(), key3.hashCode());
        }
    }

    @Test
    void privateKeys() {
        Object one = new Object();
        Object two = new Object();
        Object key1 = Keys.of(one, "one", "two");
        Object key2 = Keys.of(one, "one", "two");
        Object key3 = Keys.of(two, "one", "two");
        assertNotSame(key1, key2);
        assertNotSame(key2, key3);
        assertNotSame(key1, key3);

        assertEquals(key1, key2);
        assertNotEquals(key1, key3);
        assertEquals(key1.hashCode(), key2.hashCode());
        assertNotEquals(key1.hashCode(), key3.hashCode());
    }

    @Test
    void subKey() {
        Object key1 = Keys.of("one", "two", "three");
        Object key2 = Keys.of("one", "two");
        assertNotSame(key1, key2);

        assertNotEquals(key1, key2);
        assertNotEquals(key1.hashCode(), key2.hashCode());

        Object key3 = Keys.of(key2, "three");
        assertNotSame(key1, key3);

        assertEquals(key1, key3);
        assertEquals(key1.hashCode(), key3.hashCode());
    }
}
