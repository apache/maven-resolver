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

import java.io.FileInputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.aether.spi.connector.transport.AbstractTransporter;
import org.eclipse.aether.spi.connector.transport.GetTask;
import org.eclipse.aether.spi.connector.transport.PeekTask;
import org.eclipse.aether.spi.connector.transport.PutTask;
import org.eclipse.aether.spi.connector.transport.TransportTask;
import org.eclipse.aether.transfer.NoTransporterException;

/**
 * A transporter using {@link java.io.File}.
 */
final class FileTransporter extends AbstractTransporter {

    private final Path basePath;
    private final FileOp fileOp;

    FileTransporter(Path basePath, FileOp fileOp) throws NoTransporterException {
        this.basePath = basePath;
        this.fileOp = fileOp;
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

    private FileOp effectiveFileOp(FileOp wanted, GetTask task) {
        if (task.getDataPath() != null) {
            return wanted;
        }
        // task carries no path, caller wants in-memory read, so COPY must be used
        return FileOp.COPY;
    }

    @Override
    protected void implPeek(PeekTask task) throws Exception {
        getPath(task, true);
    }

    @Override
    protected void implGet(GetTask task) throws Exception {
        Path path = getPath(task, true);
        long size = Files.size(path);
        FileOp effective = effectiveFileOp(fileOp, task);
        switch (effective) {
            case COPY:
                utilGet(task, Files.newInputStream(path), true, size, false);
                break;
            case SYMLINK:
            case HARDLINK:
                Files.deleteIfExists(task.getDataPath());
                task.getListener().transportStarted(0L, size);
                if (effective == FileOp.HARDLINK) {
                    Files.createLink(task.getDataPath(), path);
                } else {
                    Files.createSymbolicLink(task.getDataPath(), path);
                }
                try (FileInputStream fis = new FileInputStream(path.toFile())) {
                    task.getListener()
                            .transportProgressed(fis.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, size));
                }
                break;
            default:
                throw new IllegalStateException("Unknown fileOp" + fileOp);
        }
    }

    @Override
    protected void implPut(PutTask task) throws Exception {
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
    protected void implClose() {}
}
