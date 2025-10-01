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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Objects.requireNonNull;

/**
 * Utility class serving as base of {@link PathProcessor} implementations. This class can be extended or replaced
 * (as component) when needed. Also, this class is published in Resolver implementation for path processor interface.
 *
 * @since 2.0.13
 */
public class PathProcessorSupport implements PathProcessor {
    /**
     * Logic borrowed from Commons-Lang3: we really need only this, to decide do we NIO2 file ops or not.
     * For some reason non-NIO2 works better on Windows.
     */
    protected static final boolean IS_WINDOWS =
            System.getProperty("os.name", "unknown").startsWith("Windows");

    /**
     * Escape hatch if atomic move is not desired on system we run on.
     */
    protected static final boolean ATOMIC_MOVE =
            Boolean.parseBoolean(System.getProperty(PathProcessor.class.getName() + "ATOMIC_MOVE", "true"));

    @Override
    public boolean setLastModified(Path path, long value) throws IOException {
        try {
            Files.setLastModifiedTime(path, FileTime.fromMillis(value));
            return true;
        } catch (FileSystemException e) {
            // MRESOLVER-536: Java uses generic FileSystemException for some weird cases,
            // but some subclasses like AccessDeniedEx should be re-thrown
            if (e instanceof AccessDeniedException) {
                throw e;
            }
            return false;
        }
    }

    @Override
    public void write(Path target, String data) throws IOException {
        writeFile(target, p -> Files.write(p, data.getBytes(StandardCharsets.UTF_8)), false);
    }

    @Override
    public void write(Path target, InputStream source) throws IOException {
        writeFile(target, p -> Files.copy(source, p, StandardCopyOption.REPLACE_EXISTING), false);
    }

    @Override
    public void writeWithBackup(Path target, String data) throws IOException {
        writeFile(target, p -> Files.write(p, data.getBytes(StandardCharsets.UTF_8)), true);
    }

    @Override
    public void writeWithBackup(Path target, InputStream source) throws IOException {
        writeFile(target, p -> Files.copy(source, p, StandardCopyOption.REPLACE_EXISTING), true);
    }

    /**
     * A file writer, that accepts a {@link Path} to write some content to. Note: the file denoted by path may exist,
     * hence implementation have to ensure it is able to achieve its goal ("replace existing" option or equivalent
     * should be used).
     */
    @FunctionalInterface
    public interface FileWriter {
        void write(Path path) throws IOException;
    }

    /**
     * Utility method to write out file to disk in "atomic" manner, with optional backups (".bak") if needed. This
     * ensures that no other thread or process will be able to read not fully written files. Finally, this method
     * may create the needed parent directories, if the passed in target parents does not exist.
     *
     * @param target   that is the target file (must be an existing or non-existing file, the path must have parent)
     * @param writer   the writer that will accept a {@link Path} to write content to
     * @param doBackup if {@code true}, and target file is about to be overwritten, a ".bak" file with old contents will
     *                 be created/overwritten
     * @throws IOException if at any step IO problem occurs
     */
    public void writeFile(Path target, FileWriter writer, boolean doBackup) throws IOException {
        requireNonNull(target, "target is null");
        requireNonNull(writer, "writer is null");
        Path parent = requireNonNull(target.getParent(), "target must have parent");

        try (CollocatedTempFile tempFile = newTempFile(target)) {
            writer.write(tempFile.getPath());
            if (doBackup && Files.isRegularFile(target)) {
                Files.copy(target, parent.resolve(target.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            }
            tempFile.move();
        }
    }

    @Override
    public long copy(Path source, Path target, ProgressListener listener) throws IOException {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(source));
                CollocatedTempFile tempTarget = newTempFile(target);
                OutputStream out = new BufferedOutputStream(Files.newOutputStream(tempTarget.getPath()))) {
            long result = copy(out, in, listener);
            tempTarget.move();
            return result;
        }
    }

    private long copy(OutputStream os, InputStream is, ProgressListener listener) throws IOException {
        long total = 0L;
        byte[] buffer = new byte[1024 * 32];
        while (true) {
            int bytes = is.read(buffer);
            if (bytes < 0) {
                break;
            }

            os.write(buffer, 0, bytes);

            total += bytes;

            if (listener != null && bytes > 0) {
                try {
                    listener.progressed(ByteBuffer.wrap(buffer, 0, bytes));
                } catch (Exception e) {
                    // too bad
                }
            }
        }

        return total;
    }

    @Override
    public void move(Path source, Path target) throws IOException {
        final StandardCopyOption[] copyOption = ATOMIC_MOVE
                ? new StandardCopyOption[] {
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES
                }
                : new StandardCopyOption[] {StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES};
        if (IS_WINDOWS) {
            classicCopy(source, target);
        } else {
            Files.move(source, target, copyOption);
        }
        Files.deleteIfExists(source);
    }

    // Temp files

    @Override
    public TempFile newTempFile() throws IOException {
        Path tempFile = Files.createTempFile("resolver", "tmp");
        return new TempFile() {
            @Override
            public Path getPath() {
                return tempFile;
            }

            @Override
            public void close() throws IOException {
                Files.deleteIfExists(tempFile);
            }
        };
    }

    @Override
    public CollocatedTempFile newTempFile(Path file) throws IOException {
        Path parent = requireNonNull(file.getParent(), "file must have parent");
        Files.createDirectories(parent);
        Path tempFile = parent.resolve(file.getFileName() + "."
                + Long.toUnsignedString(ThreadLocalRandom.current().nextLong()) + ".tmp");
        return new CollocatedTempFile() {
            private final AtomicBoolean wantsMove = new AtomicBoolean(false);
            private final StandardCopyOption[] copyOption = ATOMIC_MOVE
                    ? new StandardCopyOption[] {StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING}
                    : new StandardCopyOption[] {StandardCopyOption.REPLACE_EXISTING};

            @Override
            public Path getPath() {
                return tempFile;
            }

            @Override
            public void move() {
                wantsMove.set(true);
            }

            @Override
            public void close() throws IOException {
                if (wantsMove.get()) {
                    if (IS_WINDOWS) {
                        classicCopy(tempFile, file);
                    } else {
                        Files.move(tempFile, file, copyOption);
                    }
                }
                Files.deleteIfExists(tempFile);
            }
        };
    }

    /**
     * On Windows we use pre-NIO2 way to copy files, as for some reason it works. Beat me why.
     */
    protected void classicCopy(Path source, Path target) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(1024 * 32);
        byte[] array = buffer.array();
        try (InputStream is = Files.newInputStream(source);
                OutputStream os = Files.newOutputStream(target)) {
            while (true) {
                int bytes = is.read(array);
                if (bytes < 0) {
                    break;
                }
                os.write(array, 0, bytes);
            }
        }
    }
}
