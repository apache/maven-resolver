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
package org.eclipse.aether.named.ipc;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.stream.Collectors;

import org.eclipse.aether.named.NamedLock;
import org.eclipse.aether.named.NamedLockKey;
import org.eclipse.aether.named.support.NamedLockFactorySupport;
import org.eclipse.aether.named.support.NamedLockSupport;
import org.eclipse.aether.util.StringDigestUtil;

import static java.util.Objects.requireNonNull;

/**
 * IPC named locks factory.
 *
 * @since 2.0.1
 */
@Singleton
@Named(IpcNamedLockFactory.NAME)
public class IpcNamedLockFactory extends NamedLockFactorySupport {
    public static final String NAME = "ipc";

    protected final IpcClient client;

    @Inject
    public IpcNamedLockFactory() {
        this(Paths.get(System.getProperty("user.home")).resolve(".ipc-sync"));
    }

    public IpcNamedLockFactory(Path ipcHome) {
        requireNonNull(ipcHome);
        Path repository = ipcHome.resolve("repository");
        Path logPath = ipcHome.resolve("log");
        this.client = new IpcClient(repository, logPath, null);
    }

    @Override
    protected NamedLock doGetLock(Collection<NamedLockKey> keys) {
        NamedLockKey key = NamedLockKey.of(
                digestKeys(keys),
                keys.stream()
                        .map(NamedLockKey::resources)
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList()));
        return getLockAndRefTrack(
                key,
                () -> new IpcNamedLock(
                        key, this, client, keys.stream().map(NamedLockKey::name).collect(Collectors.toList())));
    }

    /**
     * Computes the digest identifying given (ordered) collection of lock keys. The encoding is unambiguous —
     * netstring-like, the key count followed by each name length-prefixed — so distinct key collections always
     * yield distinct digests. Plainly concatenating the names would give boundary-shifted collections (for
     * example {@code ["foo", "bar"]} vs {@code ["foob", "ar"]} — and lock names embed wire-supplied artifact
     * coordinates) the same digest and hence the same lock identity, letting two racing processes mutate the
     * same local repository paths while holding what they believe are different locks.
     */
    static String digestKeys(Collection<NamedLockKey> keys) {
        StringDigestUtil sha1 = StringDigestUtil.sha1();
        sha1.update(keys.size() + ":");
        for (NamedLockKey key : keys) {
            String name = key.name();
            sha1.update(name.length() + ":");
            sha1.update(name);
        }
        return sha1.digest();
    }

    @Override
    protected NamedLockSupport createLock(NamedLockKey key) {
        throw new IllegalStateException("should not get here");
    }

    @Override
    protected void doShutdown() {
        client.close();
    }
}
