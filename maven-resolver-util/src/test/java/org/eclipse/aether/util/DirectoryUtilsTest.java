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
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DirectoryUtilsTest {

    @Test
    public void expectedCasesRelative(@TempDir Path tmpDir) throws IOException {
        Path result;

        result = DirectoryUtils.resolveDirectory("foo", tmpDir, false);
        assertEquals(tmpDir.resolve("foo"), result);

        result = DirectoryUtils.resolveDirectory("foo/bar", tmpDir, false);
        assertEquals(tmpDir.resolve("foo/bar"), result);

        result = DirectoryUtils.resolveDirectory("foo/./bar/..", tmpDir, false);
        assertEquals(tmpDir.resolve("foo"), result);
    }

    @Test
    public void expectedCasesAbsolute(@TempDir Path tmpDir) throws IOException {
        // absolute on every platform: "/foo" on Unix, "C:\foo" (the drive of tmpDir) on Windows
        Path root = tmpDir.getRoot();
        Path result;

        result = DirectoryUtils.resolveDirectory(root.resolve("foo").toString(), tmpDir, false);
        assertEquals(root.resolve("foo"), result);

        result = DirectoryUtils.resolveDirectory(root.resolve("foo/bar").toString(), tmpDir, false);
        assertEquals(root.resolve("foo/bar"), result);

        result = DirectoryUtils.resolveDirectory(root.resolve("foo/./bar/..").toString(), tmpDir, false);
        assertEquals(root.resolve("foo"), result);
    }

    /**
     * On Windows "/foo" has a root but no drive, so it is not absolute: it resolves against the drive of the base.
     */
    @Test
    @EnabledOnOs(OS.WINDOWS)
    public void rootedWithoutDriveOnWindows(@TempDir Path tmpDir) throws IOException {
        Path result = DirectoryUtils.resolveDirectory("/foo", tmpDir, false);
        assertEquals(tmpDir.getRoot().resolve("foo"), result);
    }

    @Test
    public void existsButIsADirectory(@TempDir Path tmpDir) throws IOException {
        Files.createDirectories(tmpDir.resolve("foo"));
        Path result = DirectoryUtils.resolveDirectory("foo", tmpDir, false);
        assertEquals(tmpDir.resolve("foo"), result);
    }

    @Test
    public void existsButNotADirectory(@TempDir Path tmpDir) throws IOException {
        Files.createFile(tmpDir.resolve("foo"));
        try {
            DirectoryUtils.resolveDirectory("foo", tmpDir, false);
        } catch (IOException e) {
            assertTrue(e.getMessage().startsWith("Path exists, but is not a directory:"));
        }
    }

    @Test
    public void notExistsAndIsCreated(@TempDir Path tmpDir) throws IOException {
        assertFalse(Files.exists(tmpDir.resolve("foo")));
        Path result = DirectoryUtils.resolveDirectory("foo", tmpDir, true);
        assertEquals(tmpDir.resolve("foo"), result);
        assertTrue(Files.isDirectory(tmpDir.resolve("foo")));
    }
}
