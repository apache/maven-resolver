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
package org.eclipse.aether.named.providers;

import javax.inject.Named;
import javax.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.eclipse.aether.named.NamedLock;
import org.eclipse.aether.named.NamedLockKey;
import org.eclipse.aether.named.support.FileLockNamedLock;
import org.eclipse.aether.named.support.NamedLockFactorySupport;
import org.eclipse.aether.named.support.NamedLockSupport;

import static org.eclipse.aether.named.support.Retry.retry;

/**
 * Named locks factory of {@link FileLockNamedLock}s. This is a bit of special implementation, as it
 * expects locks names to be proper URI string representations (use {@code file:} protocol for default
 * file system).
 *
 * @since 1.7.3
 */
@Singleton
@Named(FileLockNamedLockFactory.NAME)
public class FileLockNamedLockFactory extends NamedLockFactorySupport {
    public static final String NAME = "file-lock";

    // Logic borrowed from Commons-Lang3: we really need only this, to decide do we "atomic move" or not
    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "unknown").startsWith("Windows");

    /**
     * Tweak: on Windows, the presence of {@link StandardOpenOption#DELETE_ON_CLOSE} causes concurrency issues. This
     * flag allows to have it removed from effective flags, at the cost that lockfile directory becomes crowded
     * with 0 byte sized lock files that are never cleaned up. Default value is {@code true}.
     *
     * @see <a href="https://bugs.openjdk.org/browse/JDK-8252883">JDK-8252883</a>
     * @configurationSource {@link System#getProperty(String, String)}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue true
     */
    public static final String SYSTEM_PROP_DELETE_LOCK_FILES = "aether.named.file-lock.deleteLockFiles";

    private static final boolean DELETE_LOCK_FILES =
            Boolean.parseBoolean(System.getProperty(SYSTEM_PROP_DELETE_LOCK_FILES, Boolean.toString(!IS_WINDOWS)));

    /**
     * Tweak: on Windows, the presence of {@link StandardOpenOption#DELETE_ON_CLOSE} causes concurrency issues. This
     * flag allows to implement similar fix as referenced JDK bug report: retry and hope the best. Default value is
     * 5 attempts (will retry 4 times).
     *
     * @see <a href="https://bugs.openjdk.org/browse/JDK-8252883">JDK-8252883</a>
     * @configurationSource {@link System#getProperty(String, String)}
     * @configurationType {@link java.lang.Integer}
     * @configurationDefaultValue 5
     */
    public static final String SYSTEM_PROP_ATTEMPTS = "aether.named.file-lock.attempts";

    private static final int ATTEMPTS = Integer.parseInt(System.getProperty(SYSTEM_PROP_ATTEMPTS, "5"));

    /**
     * Tweak: When {@link #SYSTEM_PROP_ATTEMPTS} used, the amount of milliseconds to sleep between subsequent retries. Default
     * value is 50 milliseconds.
     *
     * @configurationSource {@link System#getProperty(String, String)}
     * @configurationType {@link java.lang.Long}
     * @configurationDefaultValue 50
     */
    public static final String SYSTEM_PROP_SLEEP_MILLIS = "aether.named.file-lock.sleepMillis";

    private static final long SLEEP_MILLIS = Long.parseLong(System.getProperty(SYSTEM_PROP_SLEEP_MILLIS, "50"));

    private final ConcurrentMap<NamedLockKey, FileChannel> fileChannels;

    public FileLockNamedLockFactory() {
        this.fileChannels = new ConcurrentHashMap<>();
    }

    @Override
    protected NamedLockSupport createLock(final NamedLockKey key) {
        Path path = Paths.get(URI.create(key.name()));
        FileChannel fileChannel = fileChannels.computeIfAbsent(key, k -> {
            try {
                Files.createDirectories(path.getParent());
                FileChannel channel = retry(
                        ATTEMPTS,
                        SLEEP_MILLIS,
                        () -> {
                            if (DELETE_LOCK_FILES) {
                                return FileChannel.open(
                                        path,
                                        StandardOpenOption.READ,
                                        StandardOpenOption.WRITE,
                                        StandardOpenOption.CREATE,
                                        StandardOpenOption.DELETE_ON_CLOSE);
                            } else {
                                return FileChannel.open(
                                        path,
                                        StandardOpenOption.READ,
                                        StandardOpenOption.WRITE,
                                        StandardOpenOption.CREATE);
                            }
                        },
                        null,
                        null);

                if (channel == null) {
                    throw new IllegalStateException(
                            "Could not open file channel for '" + key + "' after " + ATTEMPTS + " attempts; giving up");
                }
                return channel;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while opening file channel for '" + key + "'", e);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to open file channel for '" + key + "'", e);
            }
        });
        return new FileLockNamedLock(key, fileChannel, this);
    }

    @Override
    protected void destroyLock(final NamedLock namedLock) {
        if (namedLock instanceof FileLockNamedLock) {
            final NamedLockKey key = namedLock.key();
            FileChannel fileChannel = fileChannels.remove(key);
            if (fileChannel == null) {
                throw new IllegalStateException("File channel expected, but does not exist: " + key);
            }

            try {
                fileChannel.close();
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to close file channel for '" + key + "'", e);
            }
        }
    }
}
