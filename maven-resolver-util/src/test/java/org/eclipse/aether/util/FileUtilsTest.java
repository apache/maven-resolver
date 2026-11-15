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
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.TimeUnit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class FileUtilsTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    /**
     * A time in the past, in whole seconds, as file systems differ in the precision they store.
     */
    private static final FileTime LAST_MODIFIED =
            FileTime.from(System.currentTimeMillis() / 1000 - TimeUnit.DAYS.toSeconds(12), TimeUnit.SECONDS);

    @Test
    public void collocatedTempFileMoveKeepsLastModifiedOfNewFile() throws IOException {
        Path target = temporaryFolder.getRoot().toPath().resolve("target.txt");

        moveWithLastModified(target, "content");

        assertEquals("content", read(target));
        assertEquals(LAST_MODIFIED, Files.getLastModifiedTime(target));
    }

    @Test
    public void collocatedTempFileMoveKeepsLastModifiedOfReplacedFile() throws IOException {
        Path target = temporaryFolder.newFile("target.txt").toPath();
        Files.write(target, "old content".getBytes(StandardCharsets.UTF_8));

        moveWithLastModified(target, "new content");

        assertEquals("new content", read(target));
        assertEquals(LAST_MODIFIED, Files.getLastModifiedTime(target));
    }

    /**
     * The move uses the copy only on Windows: this test calls it directly to cover it on every system.
     */
    @Test
    public void copyKeepsLastModifiedOfNewFile() throws IOException {
        Path source = sourceWithLastModified("content");
        Path target = temporaryFolder.getRoot().toPath().resolve("target.txt");

        FileUtils.copy(source, target);

        assertEquals("content", read(target));
        assertEquals(LAST_MODIFIED, Files.getLastModifiedTime(target));
    }

    /**
     * The move uses the copy only on Windows: this test calls it directly to cover it on every system.
     */
    @Test
    public void copyKeepsLastModifiedOfReplacedFile() throws IOException {
        Path source = sourceWithLastModified("new content");
        Path target = temporaryFolder.newFile("target.txt").toPath();
        Files.write(target, "old content".getBytes(StandardCharsets.UTF_8));

        FileUtils.copy(source, target);

        assertEquals("new content", read(target));
        assertEquals(LAST_MODIFIED, Files.getLastModifiedTime(target));
    }

    /**
     * Writes the content the way the connector and the transports do: into a collocated temp file, that gets the
     * remote last modified time, and is then moved onto the target.
     */
    private static void moveWithLastModified(Path target, String content) throws IOException {
        Path tempPath;
        try (FileUtils.CollocatedTempFile tempFile = FileUtils.newTempFile(target)) {
            tempPath = tempFile.getPath();
            Files.write(tempPath, content.getBytes(StandardCharsets.UTF_8));
            Files.setLastModifiedTime(tempPath, LAST_MODIFIED);
            tempFile.move();
        }
        assertFalse(Files.exists(tempPath));
    }

    private Path sourceWithLastModified(String content) throws IOException {
        Path source = temporaryFolder.newFile("source.txt").toPath();
        Files.write(source, content.getBytes(StandardCharsets.UTF_8));
        Files.setLastModifiedTime(source, LAST_MODIFIED);
        return source;
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }
}
