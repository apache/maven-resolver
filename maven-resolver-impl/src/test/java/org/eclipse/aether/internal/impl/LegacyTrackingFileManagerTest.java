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

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class LegacyTrackingFileManagerTest extends TrackingFileManagerTestSupport {
    @Override
    protected TrackingFileManager createTrackingFileManager(FS fs) {
        return new LegacyTrackingFileManager();
    }

    @Test
    void testReadUnreadableFails(@TempDir Path dir) throws Exception {
        Path file = Files.createFile(dir.resolve("file.properties"));
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("---------"));
        } catch (UnsupportedOperationException e) {
            assumeTrue(false, "POSIX permissions not supported");
        }
        assumeTrue(!Files.isReadable(file), "file still readable (running as root?)");
        // an unreadable tracking file must not be reported as missing (i.e. "untracked")
        assertThrows(UncheckedIOException.class, () -> new LegacyTrackingFileManager().read(file));
    }
}
