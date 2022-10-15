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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

public class DirectoryUtilsTest {
    @Rule
    public TestName testName = new TestName();

    @Test
    public void expectedCasesRelative() throws IOException {
        // hack for surefire: sets the property but directory may not exist
        Files.createDirectories(Paths.get(System.getProperty("java.io.tmpdir")));

        Path tmpDir = Files.createTempDirectory(testName.getMethodName());
        Path result;

        result = DirectoryUtils.resolveDirectory("foo", tmpDir, false);
        assertThat(result, equalTo(tmpDir.resolve("foo")));

        result = DirectoryUtils.resolveDirectory("foo/bar", tmpDir, false);
        assertThat(result, equalTo(tmpDir.resolve("foo/bar")));

        result = DirectoryUtils.resolveDirectory("foo/./bar/..", tmpDir, false);
        assertThat(result, equalTo(tmpDir.resolve("foo")));
    }

    @Test
    public void expectedCasesAbsolute() throws IOException {
        // TODO: this test is skipped on Windows, as it is not clear which drive letter
        // will `new File("/foo")`
        // path get. According to Windows (and assuming Java Path does separator change
        // OK), "\foo" file should
        // get resolved to CWD drive + "\foo" path, but seems Java 17 is different from
        // 11 and 8 in this respect.
        // This below WORKS on win + Java8 abd win + Java11 but FAILS on win + Java17
        assumeTrue(
                !"WindowsFileSystem".equals(FileSystems.getDefault().getClass().getSimpleName()));

        // hack for surefire: sets the property but directory may not exist
        Files.createDirectories(Paths.get(System.getProperty("java.io.tmpdir")));

        Path tmpDir = Files.createTempDirectory(testName.getMethodName());
        Path result;

        result = DirectoryUtils.resolveDirectory("/foo", tmpDir, false);
        assertThat(result, equalTo(FileSystems.getDefault().getPath("/foo").toAbsolutePath()));

        result = DirectoryUtils.resolveDirectory("/foo/bar", tmpDir, false);
        assertThat(result, equalTo(FileSystems.getDefault().getPath("/foo/bar").toAbsolutePath()));

        result = DirectoryUtils.resolveDirectory("/foo/./bar/..", tmpDir, false);
        assertThat(result, equalTo(FileSystems.getDefault().getPath("/foo").toAbsolutePath()));
    }

    @Test
    public void existsButIsADirectory() throws IOException {
        // hack for surefire: sets the property but directory may not exist
        Files.createDirectories(Paths.get(System.getProperty("java.io.tmpdir")));

        Path tmpDir = Files.createTempDirectory(testName.getMethodName());
        Files.createDirectories(tmpDir.resolve("foo"));
        Path result = DirectoryUtils.resolveDirectory("foo", tmpDir, false);
        assertThat(result, equalTo(tmpDir.resolve("foo")));
    }

    @Test
    public void existsButNotADirectory() throws IOException {
        // hack for surefire: sets the property but directory may not exist
        Files.createDirectories(Paths.get(System.getProperty("java.io.tmpdir")));

        Path tmpDir = Files.createTempDirectory(testName.getMethodName());
        Files.createFile(tmpDir.resolve("foo"));
        try {
            DirectoryUtils.resolveDirectory("foo", tmpDir, false);
        } catch (IOException e) {
            assertThat(e.getMessage(), startsWith("Path exists, but is not a directory:"));
        }
    }

    @Test
    public void notExistsAndIsCreated() throws IOException {
        // hack for surefire: sets the property but directory may not exist
        Files.createDirectories(Paths.get(System.getProperty("java.io.tmpdir")));

        Path tmpDir = Files.createTempDirectory(testName.getMethodName());
        Files.createDirectories(tmpDir.resolve("foo"));
        Path result = DirectoryUtils.resolveDirectory("foo", tmpDir, true);
        assertThat(result, equalTo(tmpDir.resolve("foo")));
        assertThat(Files.isDirectory(tmpDir.resolve("foo")), equalTo(true));
    }
}
