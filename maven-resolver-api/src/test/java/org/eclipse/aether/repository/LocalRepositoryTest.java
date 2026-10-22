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
package org.eclipse.aether.repository;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LocalRepositoryTest {
    @Test
    void cwd() {
        doIt(new File("."));
    }

    @Test
    void allRoots() {
        FileSystems.getDefault().getRootDirectories().forEach(p -> doIt(new File(p.toUri())));
    }

    @Test
    void root() {
        doIt(new File(File.separator));
    }

    @Test
    void allSomePaths() {
        FileSystems.getDefault()
                .getRootDirectories()
                .forEach(p -> doIt(new File(p.resolve("some/path").toUri())));
    }

    private void doIt(File file) {
        new LocalRepository(file.toPath().toUri().toASCIIString());
    }

    /**
     * Verifies that a plain Windows path string (e.g. {@code C:\Users\foo\repo}) passed to
     * {@link LocalRepository#LocalRepository(String)} preserves the drive letter and does not
     * resolve against the JVM working directory. Previously, {@code RepositoryUriUtils.basedir()}
     * treated the drive letter as a URL scheme and stripped it, producing a drive-relative path
     * ({@code \Users\foo\repo}) that {@code Path.of(...).toUri()} then resolved against the JVM
     * working directory — which can be on a different drive on Windows CI runners.
     */
    @Test
    @EnabledOnOs(OS.WINDOWS)
    void windowsPlainPathPreservesDriveLetter() {
        // Use the current drive letter so the path is always valid on any Windows machine.
        String currentDrive = Paths.get(".").toAbsolutePath().getRoot().toString(); // e.g. "C:\"
        char driveLetter = currentDrive.charAt(0);
        String plainPath = driveLetter + ":\\Users\\runneradmin\\AppData\\Local\\Temp\\my-repo";

        LocalRepository repo = new LocalRepository(plainPath);
        String repoPath = repo.getBasePath().toString();

        // The resolved path must start with the same drive letter, not a different one.
        assertEquals(
                String.valueOf(driveLetter),
                repoPath.substring(0, 1).toUpperCase(),
                "Drive letter must be preserved when parsing a plain Windows path: " + repoPath);
        // The full absolute path must round-trip unchanged.
        assertEquals(
                plainPath, repoPath, "Plain Windows path must round-trip through LocalRepository(String) unchanged");
    }
}
