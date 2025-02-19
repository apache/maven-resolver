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

import javax.inject.Named;
import javax.inject.Singleton;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages access to a properties file.
 * <p>
 * Note: the file locking in this component (that predates {@link org.eclipse.aether.SyncContext}) is present only
 * to back off two parallel implementations that coexist in Maven (this class and {@code maven-compat} one), as in
 * certain cases the two implementations may collide on properties files. This locking must remain in place for as long
 * as {@code maven-compat} code exists.
 */
@Singleton
@Named
public final class DefaultTrackingFileManager implements TrackingFileManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultTrackingFileManager.class);

    @Deprecated
    @Override
    public Properties read(File file) {
        return read(file.toPath());
    }

    @Override
    public Properties read(Path path) {
        if (Files.isReadable(path)) {
            synchronized (getMutex(path)) {
                try {
                    long fileSize = Files.size(path);
                    try (FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.READ);
                            FileLock unused = fileLock(fileChannel, Math.max(1, fileSize), true)) {
                        Properties props = new Properties();
                        props.load(Channels.newInputStream(fileChannel));
                        return props;
                    }
                } catch (IOException e) {
                    LOGGER.warn("Failed to read tracking file '{}'", path, e);
                    throw new UncheckedIOException(e);
                }
            }
        }
        return null;
    }

    @Deprecated
    @Override
    public Properties update(File file, Map<String, String> updates) {
        return update(file.toPath(), updates);
    }

    @Override
    public Properties update(Path path, Map<String, String> updates) {
        Properties props = new Properties();
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException e) {
            LOGGER.warn("Failed to create tracking file parent '{}'", path, e);
            throw new UncheckedIOException(e);
        }

        synchronized (getMutex(path)) {
            try {
                long fileSize;
                try {
                    fileSize = Files.size(path);
                } catch (IOException e) {
                    fileSize = 0L;
                }
                try (FileChannel fileChannel = FileChannel.open(
                                path, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
                        FileLock unused = fileLock(fileChannel, Math.max(1, fileSize), false)) {
                    if (fileSize > 0) {
                        props.load(Channels.newInputStream(fileChannel));
                    }

                    for (Map.Entry<String, String> update : updates.entrySet()) {
                        if (update.getValue() == null) {
                            props.remove(update.getKey());
                        } else {
                            props.setProperty(update.getKey(), update.getValue());
                        }
                    }

                    LOGGER.debug("Writing tracking file '{}'", path);
                    ByteArrayOutputStream stream = new ByteArrayOutputStream(1024 * 2);
                    props.store(
                            stream,
                            "NOTE: This is a Maven Resolver internal implementation file"
                                    + ", its format can be changed without prior notice.");
                    fileChannel.position(0);
                    int written = fileChannel.write(ByteBuffer.wrap(stream.toByteArray()));
                    fileChannel.truncate(written);
                }
            } catch (IOException e) {
                LOGGER.warn("Failed to write tracking file '{}'", path, e);
                throw new UncheckedIOException(e);
            }
        }

        return props;
    }

    private Object getMutex(Path path) {
        // The interned string of path is (mis)used as mutex, to exclude different threads going for same file,
        // as JVM file locking happens on JVM not on Thread level. This is how original code did it  ¯\_(ツ)_/¯
        /*
         * NOTE: Locks held by one JVM must not overlap and using the canonical path is our best bet, still another
         * piece of code might have locked the same file (unlikely though) or the canonical path fails to capture file
         * identity sufficiently as is the case with Java 1.6 and symlinks on Windows.
         */
        return path.toAbsolutePath().normalize().toString().intern();
    }

    @SuppressWarnings({"checkstyle:magicnumber"})
    private FileLock fileLock(FileChannel channel, long size, boolean shared) throws IOException {
        FileLock lock = null;
        for (int attempts = 8; attempts >= 0; attempts--) {
            try {
                lock = channel.lock(0, size, shared);
                break;
            } catch (OverlappingFileLockException e) {
                if (attempts <= 0) {
                    throw new IOException(e);
                }
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException e1) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        if (lock == null) {
            throw new IOException("Could not lock file");
        }
        return lock;
    }
}
