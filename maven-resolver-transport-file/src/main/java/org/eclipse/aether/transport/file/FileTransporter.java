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
package org.eclipse.aether.transport.file;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.aether.spi.connector.transport.AbstractTransporter;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.TransportTask;

import static java.util.Objects.requireNonNull;

/**
 * A transporter using {@link java.nio.file.Path} that is reading and writing from specified base directory
 * of given {@link java.nio.file.FileSystem}. It supports multiple {@link WriteOp} and obeys read-only property.
 */
final class FileTransporter extends AbstractTransporter {
    /**
     * The write operation transport can use to write contents to the target (usually in local repository) of the
     * file in remote repository reached by this transporter. Historically, and in some special cases (ZIP file system),
     * it is only {@link #COPY} that can be used.
     * <p>
     * In case when contents of remote repository reached by this transport and target (usually Maven local repository)
     * are on same {@link FileSystem}, then {@link #SYMLINK} and {@link #HARDLINK} can be used as well, to reduce
     * redundancy somewhat.
     *
     * @since 2.0.2
     */
    enum WriteOp {
        COPY,
        SYMLINK,
        HARDLINK;
    }

    private final FileSystem fileSystem;
    private final boolean closeFileSystem;
    private final boolean writableFileSystem;
    private final Path basePath;
    private final WriteOp writeOp;

    FileTransporter(
            FileSystem fileSystem,
            boolean closeFileSystem,
            boolean writableFileSystem,
            Path basePath,
            WriteOp writeOp) {
        this.fileSystem = requireNonNull(fileSystem);
        this.closeFileSystem = closeFileSystem;
        this.writableFileSystem = writableFileSystem;
        this.basePath = requireNonNull(basePath);
        this.writeOp = requireNonNull(writeOp);

        // sanity check
        if (basePath.getFileSystem() != fileSystem) {
            throw new IllegalArgumentException("basePath must originate from the fileSystem");
        }
    }

    Path getBasePath() {
        return basePath;
    }

    @Override
    public int classify(Throwable error) {
        if (error instanceof ResourceNotFoundException) {
            return ERROR_NOT_FOUND;
        }
        return ERROR_OTHER;
    }

    private WriteOp effectiveFileOp(WriteOp wanted, GetTask task) {
        if (task.getDataPath() != null && task.getDataPath().getFileSystem() == fileSystem) {
            return wanted;
        }
        // not default FS or task carries no path (caller wants in-memory read) = COPY must be used
        return WriteOp.COPY;
    }

    @Override
    protected void implPeek(PeekTask task) throws Exception {
        getPath(task, true);
    }

    @Override
    protected void implGet(GetTask task) throws Exception {
        Path path = getPath(task, true);
        long size = Files.size(path);
        WriteOp effective = effectiveFileOp(writeOp, task);
        switch (effective) {
            case COPY:
                utilGet(task, Files.newInputStream(path), true, size, false);
                break;
            case SYMLINK:
            case HARDLINK:
                Files.deleteIfExists(task.getDataPath());
                task.getListener().transportStarted(0L, size);
                if (effective == WriteOp.HARDLINK) {
                    Files.createLink(task.getDataPath(), path);
                } else {
                    Files.createSymbolicLink(task.getDataPath(), path);
                }
                if (size > 0) {
                    try (FileChannel fc = FileChannel.open(path)) {
                        try {
                            task.getListener().transportProgressed(fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size()));
                        } catch (UnsupportedOperationException e) {
                            // not all FS support mmap: fallback to "plain read loop"
                            ByteBuffer byteBuffer = ByteBuffer.allocate(1024 * 32);
                            while (fc.read(byteBuffer) != -1) {
                                byteBuffer.flip();
                                task.getListener().transportProgressed(byteBuffer);
                                byteBuffer.clear();
                            }
                        }
                    }
                }
                break;
            default:
                throw new IllegalStateException("Unknown fileOp" + writeOp);
        }
    }

    @Override
    protected void implPut(PutTask task) throws Exception {
        if (!writableFileSystem) {
            throw new UnsupportedOperationException("Read only FileSystem");
        }
        Path path = getPath(task, false);
        Files.createDirectories(path.getParent());
        try {
            utilPut(task, Files.newOutputStream(path), true);
        } catch (Exception e) {
            Files.deleteIfExists(path);
            throw e;
        }
    }

    private Path getPath(TransportTask task, boolean required) throws Exception {
        String path = task.getLocation().getPath();
        if (path.contains("../")) {
            throw new IllegalArgumentException("illegal resource path: " + path);
        }
        Path file = basePath.resolve(path);
        if (required && !Files.isRegularFile(file)) {
            throw new ResourceNotFoundException("Could not locate " + file);
        }
        return file;
    }

    @Override
    protected void implClose() {
        if (closeFileSystem) {
            try {
                fileSystem.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
