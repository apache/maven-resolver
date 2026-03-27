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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.eclipse.aether.named.NamedLock;
import org.eclipse.aether.named.NamedLockFactory;
import org.eclipse.aether.named.NamedLockKey;
import org.eclipse.aether.util.StringDigestUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages access to a properties file using named locks.
 * <p>
 * This implementation uses {@link NamedLock} to protect tracking files from concurrent access, and can be used
 * when it is single "modern" Maven version used to access same local repository concurrently.
 *
 * @since 2.0.17
 * @see LegacyTrackingFileManager
 * @see TrackingFileManagerProvider
 */
public final class NamedLocksTrackingFileManager implements TrackingFileManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(NamedLocksTrackingFileManager.class);

    private final NamedLockFactory namedLockFactory;
    private final long time;
    private final TimeUnit unit;

    public NamedLocksTrackingFileManager(NamedLockFactory namedLockFactory, long time, TimeUnit unit) {
        this.namedLockFactory = namedLockFactory;
        this.time = time;
        this.unit = unit;
    }

    @Deprecated
    @Override
    public Properties read(File file) {
        return read(file.toPath());
    }

    @Override
    public Properties read(Path path) {
        try (NamedLock namedLock = namedLock(path)) {
            if (namedLock.lockShared(time, unit)) {
                try {
                    Properties props = new Properties();
                    try (InputStream in = Files.newInputStream(path)) {
                        props.load(in);
                    }
                    return props;
                } catch (NoSuchFileException e) {
                    LOGGER.debug("No such file to read {}: {}", path, e.getMessage());
                    return null;
                } catch (IOException e) {
                    LOGGER.warn("Failed to read tracking file '{}'", path, e);
                    throw new UncheckedIOException(e);
                } finally {
                    namedLock.unlock();
                }
            }
            throw new IllegalStateException("Failed to lock for read the tracking file " + path);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading tracking file " + path, e);
        }
    }

    @Deprecated
    @Override
    public Properties update(File file, Map<String, String> updates) {
        return update(file.toPath(), updates);
    }

    @Override
    public Properties update(Path path, Map<String, String> updates) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to create tracking file parent '{}'", path, e);
            throw new UncheckedIOException(e);
        }
        try (NamedLock lock = namedLock(path)) {
            if (lock.lockExclusively(time, unit)) {
                try {
                    Properties props = new Properties();
                    if (Files.isRegularFile(path)) {
                        try (InputStream stream = Files.newInputStream(path, StandardOpenOption.READ)) {
                            props.load(stream);
                        }
                    }
                    for (Map.Entry<String, String> update : updates.entrySet()) {
                        if (update.getValue() == null) {
                            props.remove(update.getKey());
                        } else {
                            props.setProperty(update.getKey(), update.getValue());
                        }
                    }
                    LOGGER.debug("Writing tracking file '{}'", path);
                    try (OutputStream out = Files.newOutputStream(path)) {
                        props.store(
                                out,
                                "NOTE: This is a Maven Resolver internal implementation file"
                                        + ", its format can be changed without prior notice.");
                    }
                    return props;
                } catch (IOException e) {
                    LOGGER.warn("Failed to write tracking file '{}'", path, e);
                    throw new UncheckedIOException(e);
                } finally {
                    lock.unlock();
                }
            }
            throw new IllegalStateException("Failed to lock for update the tracking file " + path);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while updating tracking file " + path, e);
        }
    }

    @Deprecated
    @Override
    public boolean delete(File file) {
        return delete(file.toPath());
    }

    @Override
    public boolean delete(Path path) {
        try (NamedLock lock = namedLock(path)) {
            if (lock.lockExclusively(time, unit)) {
                try {
                    return Files.deleteIfExists(path);
                } catch (NoSuchFileException e) {
                    LOGGER.debug("No such file to delete {}: {}", path, e.getMessage());
                    return false;
                } catch (IOException e) {
                    LOGGER.warn("Failed to delete tracking file '{}'", path, e);
                    throw new UncheckedIOException(e);
                } finally {
                    lock.unlock();
                }
            }
            throw new IllegalStateException("Failed to lock for delete the tracking file " + path);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while deleting tracking file " + path, e);
        }
    }

    /**
     * Creates unique named lock for given path with name that is unique for paths, but carries some extra
     * information useful for debugging.
     * <p>
     * Note: it is important that created named lock names remain (and carry) file friendly URLs, as this makes it
     * work with all lock factories, even the file one. Using non-file friendly names would make it work it with
     * all <strong>except the file lock factory</strong>.
     */
    private NamedLock namedLock(Path path) {
        Path canonical = canonicalPath(path);
        // Place lock file next to the tracking file, using its hash as filename
        Path lockPath = canonical.resolveSibling("tracking-" + StringDigestUtil.sha1(canonical.toString()) + ".lock");
        return namedLockFactory.getLock(
                NamedLockKey.of(lockPath.toAbsolutePath().toUri().toASCIIString(), path.toString()));
    }

    /**
     * Tries the best it can to figure out actual file the workload is about, while resolving cases like symlinked
     * local repository etc.
     */
    private static Path canonicalPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return path.getParent() != null
                    ? canonicalPath(path.getParent()).resolve(path.getFileName())
                    : path.toAbsolutePath();
        }
    }
}
