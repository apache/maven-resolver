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
package org.eclipse.aether.internal.impl.filter.prefixes;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.aether.repository.RemoteRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UT for {@link PrefixesSource}.
 */
public class PrefixesSourceTest {
    private final RemoteRepository origin =
            new RemoteRepository.Builder("whatever", "default", "http://localhost/repo").build();
    private final Path prefixes = Paths.get("src/test/resources/prefixes");

    @Test
    void smoke() {
        PrefixesSource source;

        source = PrefixesSource.of(origin, prefixes.resolve("ok.txt"));
        assertTrue(source.valid());
        assertEquals("OK", source.message());
        assertEquals(3, source.entries().size());

        source = PrefixesSource.of(origin, prefixes.resolve("empty.txt"));
        assertFalse(source.valid());
        assertEquals("No expected magic in file", source.message());

        source = PrefixesSource.of(origin, prefixes.resolve("garbage.txt"));
        assertFalse(source.valid());
        assertEquals("No expected magic in file", source.message());

        source = PrefixesSource.of(origin, prefixes.resolve("forbidden.txt"));
        assertFalse(source.valid());
        assertEquals("Contains forbidden characters", source.message());

        source = PrefixesSource.of(origin, prefixes.resolve("illegal.txt"));
        assertFalse(source.valid());
        assertEquals("Contains non-ASCII characters", source.message());

        source = PrefixesSource.of(origin, prefixes.resolve("unsupported.txt"));
        assertFalse(source.valid());
        assertEquals("Declares itself unsupported", source.message());

        source = PrefixesSource.of(origin, prefixes.resolve("nonexistent.txt"));
        assertFalse(source.valid());
        assertEquals("No such file", source.message());
    }
}
