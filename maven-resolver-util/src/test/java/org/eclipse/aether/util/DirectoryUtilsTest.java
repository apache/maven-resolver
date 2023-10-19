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
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class DirectoryUtilsTest {
    @Test
    public void expectedCasesRelative(TestInfo testInfo) throws IOException {
        // hack for surefire: sets the property but directory may not exist
        Files.createDirectories(Paths.get(System.getProperty("java.io.tmpdir")));

        Path tmpDir = Files.createTempDirectory(testInfo.getDisplayName());
        Path result;

        result = DirectoryUtils.resolveDirectory("foo", tmpDir, false);
        assertEquals(result, tmpDir.resolve("foo"));

        result = DirectoryUtils.resolveDirectory("foo/bar", tmpDir, false);
        assertEquals(result, tmpDir.resolve("foo/bar"));

        result = DirectoryUtils.resolveDirectory("foo/./bar/..", tmpDir, false);
        assertEquals(result, tmpDir.resolve("foo"));
    }

    @Test
    public void expectedCasesAbsolute(TestInfo testInfo) throws IOException {
        // TODO: this test is skipped on Windows, as it is not clear which drive letter will `new File("/foo")`
        // path get. According to Windows (and  assuming Java Path does separator change OK), "\foo" file should
        // get resolved to CWD drive + "\foo" path, but seems Java 17 is different from 11 and 8 in this respect.
        // This below WORKS on win + Java8 abd win + Java11 but FAILS on win + Java17
        assumeTrue(
                !"WindowsFileSystem".equals(FileSystems.getDefault().getClass().getSimpleName()));

        // hack for surefire: sets the property but directory may not exist
        Files.createDirectories(Paths.get(System.getProperty("java.io.tmpdir")));

        Path tmpDir = Files.createTempDirectory(testInfo.getDisplayName());
        Path result;

        result = DirectoryUtils.resolveDirectory("/foo", tmpDir, false);
        assertEquals(result, FileSystems.getDefault().getPath("/foo").toAbsolutePath());

        result = DirectoryUtils.resolveDirectory("/foo/bar", tmpDir, false);
        assertEquals(result, FileSystems.getDefault().getPath("/foo/bar").toAbsolutePath());

        result = DirectoryUtils.resolveDirectory("/foo/./bar/..", tmpDir, false);
        assertEquals(result, FileSystems.getDefault().getPath("/foo").toAbsolutePath());
    }

    @Test
    public void existsButIsADirectory(TestInfo testInfo) throws IOException {
        // hack for surefire: sets the property but directory may not exist
        Files.createDirectories(Paths.get(System.getProperty("java.io.tmpdir")));

        Path tmpDir = Files.createTempDirectory(testInfo.getDisplayName());
        Files.createDirectories(tmpDir.resolve("foo"));
        Path result = DirectoryUtils.resolveDirectory("foo", tmpDir, false);
        assertEquals(result, tmpDir.resolve("foo"));
    }

    @Test
    public void existsButNotADirectory(TestInfo testInfo) throws IOException {
        // hack for surefire: sets the property but directory may not exist
        Files.createDirectories(Paths.get(System.getProperty("java.io.tmpdir")));

        Path tmpDir = Files.createTempDirectory(testInfo.getDisplayName());
        Files.createFile(tmpDir.resolve("foo"));
        try {
            DirectoryUtils.resolveDirectory("foo", tmpDir, false);
        } catch (IOException e) {
            assertTrue(e.getMessage().startsWith("Path exists, but is not a directory:"), e.getMessage());
        }
    }

    @Test
    public void notExistsAndIsCreated(TestInfo testInfo) throws IOException {
        // hack for surefire: sets the property but directory may not exist
        Files.createDirectories(Paths.get(System.getProperty("java.io.tmpdir")));

        Path tmpDir = Files.createTempDirectory(testInfo.getDisplayName());
        Files.createDirectories(tmpDir.resolve("foo"));
        Path result = DirectoryUtils.resolveDirectory("foo", tmpDir, true);
        assertEquals(result, tmpDir.resolve("foo"));
        assumeTrue(Files.isDirectory(tmpDir.resolve("foo")));
    }
}
