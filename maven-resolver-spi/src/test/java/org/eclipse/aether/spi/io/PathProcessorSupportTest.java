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
package org.eclipse.aether.spi.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UT for {@link PathProcessorSupport}, in particular the never-truncate-the-target contract of
 * {@link PathProcessorSupport#retryingMove(Path, Path, StandardCopyOption[])} used for the final move on Windows.
 */
class PathProcessorSupportTest {
    private static final StandardCopyOption[] ATOMIC_OPTIONS = {
        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
    };

    private Path write(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    @Test
    void retryingMoveMovesFile(@TempDir Path tempDir) throws IOException {
        PathProcessorSupport subject = new PathProcessorSupport();
        Path source = write(tempDir, "source.txt", "content");
        Path target = tempDir.resolve("target.txt");

        subject.retryingMove(source, target, ATOMIC_OPTIONS);

        assertEquals("content", read(target));
        assertFalse(Files.exists(source));
    }

    @Test
    void retryingMoveReplacesExistingTarget(@TempDir Path tempDir) throws IOException {
        PathProcessorSupport subject = new PathProcessorSupport();
        Path source = write(tempDir, "source.txt", "new content");
        Path target = write(tempDir, "target.txt", "old content");

        subject.retryingMove(source, target, ATOMIC_OPTIONS);

        assertEquals("new content", read(target));
        assertFalse(Files.exists(source));
    }

    @Test
    void retryingMoveRetriesOnTransientLock(@TempDir Path tempDir) throws IOException {
        AtomicInteger attempts = new AtomicInteger();
        PathProcessorSupport subject = new PathProcessorSupport() {
            @Override
            protected void fileSystemMove(Path source, Path target, StandardCopyOption... copyOptions)
                    throws IOException {
                if (attempts.incrementAndGet() < 3) {
                    // simulate a virus scanner / concurrent reader transiently holding the target
                    throw new AccessDeniedException(target.toString());
                }
                super.fileSystemMove(source, target, copyOptions);
            }
        };
        Path source = write(tempDir, "source.txt", "new content");
        Path target = write(tempDir, "target.txt", "old content");

        subject.retryingMove(source, target, ATOMIC_OPTIONS);

        assertEquals(3, attempts.get());
        assertEquals("new content", read(target));
        assertFalse(Files.exists(source));
    }

    @Test
    void retryingMoveNeverTruncatesTargetOnFailure(@TempDir Path tempDir) throws IOException {
        AtomicInteger attempts = new AtomicInteger();
        PathProcessorSupport subject = new PathProcessorSupport() {
            @Override
            protected void fileSystemMove(Path source, Path target, StandardCopyOption... copyOptions)
                    throws IOException {
                attempts.incrementAndGet();
                throw new AccessDeniedException(target.toString());
            }
        };
        Path source = write(tempDir, "source.txt", "new content");
        Path target = write(tempDir, "target.txt", "old content");

        assertThrows(AccessDeniedException.class, () -> subject.retryingMove(source, target, ATOMIC_OPTIONS));

        assertEquals(PathProcessorSupport.WINDOWS_MOVE_ATTEMPTS, attempts.get());
        // the crucial guarantee: a failed move leaves the previously published content fully intact,
        // it never opens the final path with truncation
        assertEquals("old content", read(target));
        assertEquals("new content", read(source));
    }

    @Test
    void retryingMoveStagesCollocatedCopyWhenAtomicMoveNotSupported(@TempDir Path tempDir) throws IOException {
        PathProcessorSupport subject = new PathProcessorSupport() {
            @Override
            protected void fileSystemMove(Path source, Path target, StandardCopyOption... copyOptions)
                    throws IOException {
                if (source.getFileName().toString().equals("source.txt")) {
                    // simulate source and target residing on different stores
                    throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "test");
                }
                super.fileSystemMove(source, target, copyOptions);
            }
        };
        Path source = write(tempDir, "source.txt", "new content");
        Path target = write(tempDir, "target.txt", "old content");

        subject.retryingMove(source, target, ATOMIC_OPTIONS);

        assertEquals("new content", read(target));
        // no staging leftovers next to the target
        try (Stream<Path> files = Files.list(tempDir)) {
            assertEquals(
                    0,
                    files.filter(p -> p.getFileName().toString().endsWith(".tmp"))
                            .count());
        }
    }

    @Test
    void writeFilePublishesCompleteContent(@TempDir Path tempDir) throws IOException {
        PathProcessorSupport subject = new PathProcessorSupport();
        Path target = write(tempDir, "target.txt", "old content");

        subject.write(target, "new content");

        assertEquals("new content", read(target));
        // the collocated temp file used for staging is cleaned up
        try (Stream<Path> files = Files.list(tempDir)) {
            assertTrue(files.allMatch(p -> p.getFileName().toString().equals("target.txt")));
        }
    }
}
