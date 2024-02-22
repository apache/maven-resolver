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

/**
 * IPC named locks factory.
 *
 * @since 2.0.0
 */
@Singleton
@Named(IpcNamedLockFactory.NAME)
public class IpcNamedLockFactory extends NamedLockFactorySupport {
    public static final String NAME = "ipc";

    private final Path ipcHome;

    private final Path repository;

    private final Path logPath;

    private final Path syncPath;

    protected final IpcClient client;

    @Inject
    public IpcNamedLockFactory() {
        this.ipcHome = Paths.get(System.getProperty("user.home")).resolve(".ipc-sync");
        this.repository = ipcHome.resolve("repository");
        this.logPath = ipcHome.resolve("log");
        this.syncPath = null;
        this.client = new IpcClient(repository, logPath, syncPath);
    }

    @Override
    protected NamedLock doGetLock(Collection<NamedLockKey> keys) {
        StringDigestUtil sha1 = StringDigestUtil.sha1();
        keys.forEach(k -> sha1.update(k.name()));
        NamedLockKey key = NamedLockKey.of(
                sha1.digest(),
                keys.stream()
                        .map(NamedLockKey::resources)
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList()));
        return getLockAndRefTrack(
                key,
                () -> new IpcNamedLock(
                        key, this, client, keys.stream().map(NamedLockKey::name).collect(Collectors.toList())));
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
