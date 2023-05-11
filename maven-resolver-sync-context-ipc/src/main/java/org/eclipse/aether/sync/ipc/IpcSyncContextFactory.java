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
package org.eclipse.aether.sync.ipc;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.impl.RepositorySystemLifecycle;
import org.eclipse.aether.spi.synccontext.SyncContextFactory;
import org.eclipse.sisu.Priority;

/**
 * The SyncContextFactory implementation.
 */
@Named(IpcSyncContextFactory.NAME)
@Priority(-1)
@Singleton
public class IpcSyncContextFactory implements SyncContextFactory {

    public static final String NAME = "ipc";

    private final Map<Path, IpcClient> clients = new ConcurrentHashMap<>();

    /** Constructor for tests */
    protected IpcSyncContextFactory() {
        this(null);
    }

    @Inject
    public IpcSyncContextFactory(RepositorySystemLifecycle repositorySystemLifecycle) {
        if (repositorySystemLifecycle != null) {
            repositorySystemLifecycle.addOnSystemEndedHandler(this::shutdown);
        }
    }

    @Override
    public SyncContext newInstance(RepositorySystemSession session, boolean shared) {
        Path repository = session.getLocalRepository().getBasedir().toPath();
        Path logPath = Optional.ofNullable(System.getProperty(IpcServer.STORAGE_PROP))
                .map(Paths::get)
                .orElseGet(() -> Paths.get(System.getProperty("user.home")).resolve(".m2/ipc-sync/"));
        Path syncPath = null; // this is the path to a natively built server
        IpcClient client = clients.computeIfAbsent(repository, r -> new IpcClient(r, logPath, syncPath));
        return new IpcSyncContext(client, shared);
    }

    /**
     * To be invoked on repository system shut down. This method will shut down each {@link IpcClient}.
     */
    protected void shutdown() {
        clients.values().forEach(IpcClient::close);
    }

    @Override
    public String toString() {
        return "IpcSyncContextFactory{" + "clients=" + clients + '}';
    }
}
