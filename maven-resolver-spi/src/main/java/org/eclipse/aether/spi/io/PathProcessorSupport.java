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
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 * Utility class serving as base of {@link PathProcessor} implementations. This class can be extended or replaced
 * (as component) when needed. Also, this class is published in Resolver implementation for path processor interface.
 *
 * @since 2.0.13
 */
public class PathProcessorSupport implements PathProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(PathProcessorSupport.class);

    /**
     * Logic borrowed from Commons-Lang3: we really need only this, to decide do we NIO2 file ops or not.
     * On Windows the final move needs retry/staging logic, see {@link #retryingMove(Path, Path, StandardCopyOption[])}.
     */
    protected static final boolean IS_WINDOWS =
            System.getProperty("os.name", "unknown").startsWith("Windows");

    /**
     * Escape hatch if atomic move is not desired on system we run on.
     */
    protected static final boolean ATOMIC_MOVE =
            Boolean.parseBoolean(System.getProperty(PathProcessor.class.getName() + ".ATOMIC_MOVE", "true"));

    /**
     * The number of attempts for the final move on Windows, where the move target may be transiently locked by a
     * virus scanner, an indexer or a concurrent reader.
     */
    protected static final int WINDOWS_MOVE_ATTEMPTS =
            Math.max(1, Integer.getInteger(PathProcessor.class.getName() + ".WINDOWS_MOVE_ATTEMPTS", 5));

    /**
     * The delay in milliseconds applied between the move attempts on Windows.
     */
    protected static final long WINDOWS_MOVE_RETRY_DELAY =
            Long.getLong(PathProcessor.class.getName() + ".WINDOWS_MOVE_RETRY_DELAY", 50L);

    /**
     * Whether to fall back to a direct (non-atomic) stream copy when all rename attempts on Windows are exhausted
     * with {@link AccessDeniedException} due to a persistent file lock (for example an IDE holding the target
     * {@code .jar} open). When {@code true} (the default), the installer degrades gracefully to the pre-2.0.24
     * behavior — the target is overwritten in-place via a stream, which on Windows succeeds even if another
     * process holds a shared read handle. The atomicity guarantee is lost for this last-resort path, but the
     * build succeeds. Set to {@code false} to keep the strict never-truncate contract and let the build fail
     * with the original {@link AccessDeniedException} instead.
     */
    protected static final boolean WINDOWS_CLASSIC_COPY_FALLBACK = Boolean.parseBoolean(
            System.getProperty(PathProcessor.class.getName() + ".WINDOWS_CLASSIC_COPY_FALLBACK", "true"));

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
            retryingMove(source, target, copyOption);
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
                        retryingMove(tempFile, file, copyOption);
                    } else {
                        Files.move(tempFile, file, copyOption);
                    }
                }
                Files.deleteIfExists(tempFile);
            }
        };
    }

    /**
     * Moves the source file to the target path, preferring atomic rename over truncating the target in place.
     * <p>
     * Normally the content visible at the target path is either the old file or the complete new file, never a
     * partially written one. This method attempts the rename a bounded number of times ({@link #WINDOWS_MOVE_ATTEMPTS};
     * file locking by virus scanners, indexers or concurrent readers is transient on Windows), and if the file system
     * refuses an atomic move (for example when source and target are on different stores), the source is first staged
     * into a collocated temporary file next to the target and then renamed into place.
     * <p>
     * <strong>Last-resort fallback (Windows / IDE lock):</strong> When all rename attempts are exhausted with
     * {@link AccessDeniedException} — typically because an IDE such as IntelliJ keeps the target {@code .jar} open
     * continuously — and {@link #WINDOWS_CLASSIC_COPY_FALLBACK} is {@code true} (the default), the method falls back
     * to a direct stream copy over the target path ({@link #classicCopy(Path, Path)}). On Windows, opening a file for
     * writing via a stream succeeds even if another process holds a shared read handle, so the build completes. The
     * atomicity guarantee is lost for this edge case: a concurrent reader may observe a partially written file. Set
     * the system property {@code org.eclipse.aether.spi.io.PathProcessor.WINDOWS_CLASSIC_COPY_FALLBACK=false} to
     * disable this fallback and propagate the {@link AccessDeniedException} instead.
     */
    protected void retryingMove(Path source, Path target, StandardCopyOption[] copyOptions) throws IOException {
        FileSystemException lastException = null;
        for (int attempt = 0; attempt < WINDOWS_MOVE_ATTEMPTS; attempt++) {
            try {
                fileSystemMove(source, target, copyOptions);
                return;
            } catch (AtomicMoveNotSupportedException e) {
                lastException = e;
                break; // retrying will not help; stage a collocated copy instead, see below
            } catch (FileSystemException e) {
                // covers AccessDeniedException and sharing violations: transient on Windows
                lastException = e;
                try {
                    Thread.sleep(WINDOWS_MOVE_RETRY_DELAY);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    InterruptedIOException interrupted =
                            new InterruptedIOException("Interrupted while moving " + source + " to " + target);
                    interrupted.initCause(ie);
                    interrupted.addSuppressed(e);
                    throw interrupted;
                }
            }
        }
        if (lastException instanceof AtomicMoveNotSupportedException) {
            Path staged = target.resolveSibling(target.getFileName() + "."
                    + Long.toUnsignedString(ThreadLocalRandom.current().nextLong()) + ".tmp");
            try {
                classicCopy(source, staged);
                fileSystemMove(staged, target, copyOptions);
                return;
            } finally {
                Files.deleteIfExists(staged);
            }
        }
        // All rename attempts failed with AccessDeniedException (persistent lock, e.g. IDE holding the jar open).
        // As a last resort, fall back to a direct stream copy over the target. On Windows this succeeds even when
        // another process holds a shared read handle because the stream open uses FILE_SHARE_READ semantics,
        // unlike MoveFileEx/ReplaceFile which require exclusive access. The atomicity guarantee is lost here.
        if (isClassicCopyFallbackEnabled() && (lastException instanceof AccessDeniedException)) {
            LOGGER.warn(
                    "Atomic rename of {} to {} failed after {} attempt(s) ({}); falling back to direct copy."
                            + " Another process may be holding the target file open."
                            + " Set -D{}.WINDOWS_CLASSIC_COPY_FALLBACK=false to disable this fallback.",
                    source,
                    target,
                    WINDOWS_MOVE_ATTEMPTS,
                    lastException.getMessage(),
                    PathProcessor.class.getName());
            classicCopy(source, target);
            return;
        }
        throw lastException;
    }

    /**
     * Testable seam for {@link Files#move(Path, Path, java.nio.file.CopyOption...)}.
     */
    protected void fileSystemMove(Path source, Path target, StandardCopyOption... copyOptions) throws IOException {
        Files.move(source, target, copyOptions);
    }

    /**
     * Returns whether the classic-copy fallback is enabled. Exposed as a protected method to allow subclasses
     * (and tests) to override the behavior controlled by {@link #WINDOWS_CLASSIC_COPY_FALLBACK}.
     */
    protected boolean isClassicCopyFallbackEnabled() {
        return WINDOWS_CLASSIC_COPY_FALLBACK;
    }

    /**
     * Pre-NIO2 way to copy files. Opens the target with truncation; normally callers stage into a temporary file and
     * rename into place to avoid exposing partially written content. Used as a last resort on Windows when all atomic
     * rename attempts are exhausted due to a persistent file lock (see {@link #retryingMove}).
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
