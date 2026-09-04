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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FileUtilsTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testRetryingMoveReplacesExistingTarget() throws IOException {
        Path dir = temporaryFolder.newFolder().toPath();
        Path source = dir.resolve("file.txt.tmp");
        Path target = dir.resolve("file.txt");
        Files.write(source, "new".getBytes(StandardCharsets.UTF_8));
        Files.write(target, "old".getBytes(StandardCharsets.UTF_8));

        FileUtils.retryingMove(source, target);

        assertFalse(Files.exists(source));
        assertArrayEquals("new".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(target));
    }

    @Test
    public void testRetryingMoveCreatesMissingTarget() throws IOException {
        Path dir = temporaryFolder.newFolder().toPath();
        Path source = dir.resolve("file.txt.tmp");
        Path target = dir.resolve("file.txt");
        Files.write(source, "new".getBytes(StandardCharsets.UTF_8));

        FileUtils.retryingMove(source, target);

        assertFalse(Files.exists(source));
        assertArrayEquals("new".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(target));
    }

    @Test
    public void testRetryingMoveMissingSourceFailsWithoutTouchingTarget() throws IOException {
        Path dir = temporaryFolder.newFolder().toPath();
        Path source = dir.resolve("missing.tmp");
        Path target = dir.resolve("file.txt");
        Files.write(target, "old".getBytes(StandardCharsets.UTF_8));

        try {
            FileUtils.retryingMove(source, target);
            fail("Expected NoSuchFileException");
        } catch (NoSuchFileException expected) {
            // the target must be left as it was
        }
        assertArrayEquals("old".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(target));
    }

    @Test
    public void testWriteFileReplacesExistingTarget() throws IOException {
        Path dir = temporaryFolder.newFolder().toPath();
        Path target = dir.resolve("file.txt");
        Files.write(target, "old".getBytes(StandardCharsets.UTF_8));

        FileUtils.writeFile(target, p -> Files.write(p, "new".getBytes(StandardCharsets.UTF_8)));

        assertEquals("new", new String(Files.readAllBytes(target), StandardCharsets.UTF_8));
        try (java.util.stream.Stream<Path> files = Files.list(dir)) {
            assertEquals(1L, files.count());
        }
        assertTrue(Files.isRegularFile(target));
    }
}
