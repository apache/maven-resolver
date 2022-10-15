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
package org.eclipse.aether.internal.impl.synccontext;

import static java.util.Objects.requireNonNull;

import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.internal.impl.synccontext.named.NameMapper;
import org.eclipse.aether.internal.impl.synccontext.named.NamedLockFactoryAdapter;
import org.eclipse.aether.internal.impl.synccontext.named.providers.DiscriminatingNameMapperProvider;
import org.eclipse.aether.internal.impl.synccontext.named.providers.FileGAVNameMapperProvider;
import org.eclipse.aether.internal.impl.synccontext.named.providers.FileHashingGAVNameMapperProvider;
import org.eclipse.aether.internal.impl.synccontext.named.providers.GAVNameMapperProvider;
import org.eclipse.aether.internal.impl.synccontext.named.providers.StaticNameMapperProvider;
import org.eclipse.aether.named.NamedLockFactory;
import org.eclipse.aether.named.providers.FileLockNamedLockFactory;
import org.eclipse.aether.named.providers.LocalReadWriteLockNamedLockFactory;
import org.eclipse.aether.named.providers.LocalSemaphoreNamedLockFactory;
import org.eclipse.aether.named.providers.NoopNamedLockFactory;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.spi.synccontext.SyncContextFactory;
import org.eclipse.aether.util.ConfigUtils;

/**
 * Default {@link SyncContextFactory} implementation that uses named locks.
 */
@Singleton
@Named
public final class DefaultSyncContextFactory implements SyncContextFactory, Service {
    private static final String ADAPTER_KEY = DefaultSyncContextFactory.class.getName() + ".adapter";

    private static final String NAME_MAPPER_KEY = "aether.syncContext.named.nameMapper";

    private static final String DEFAULT_NAME_MAPPER_NAME = GAVNameMapperProvider.NAME;

    private static final String FACTORY_KEY = "aether.syncContext.named.factory";

    private static final String DEFAULT_FACTORY_NAME = LocalReadWriteLockNamedLockFactory.NAME;

    private Map<String, NameMapper> nameMappers;

    private Map<String, NamedLockFactory> namedLockFactories;

    /**
     * Constructor used with DI, where factories are injected and selected based on key.
     */
    @Inject
    public DefaultSyncContextFactory(
            final Map<String, NameMapper> nameMappers, final Map<String, NamedLockFactory> namedLockFactories) {
        this.nameMappers = requireNonNull(nameMappers);
        this.namedLockFactories = requireNonNull(namedLockFactories);
    }

    /**
     * ServiceLocator default ctor.
     *
     * @deprecated Will be removed once ServiceLocator removed.
     */
    @Deprecated
    public DefaultSyncContextFactory() {
        // ctor for ServiceLoader
    }

    @Override
    public void initService(final ServiceLocator locator) {
        HashMap<String, NameMapper> mappers = new HashMap<>();
        mappers.put(StaticNameMapperProvider.NAME, new StaticNameMapperProvider().get());
        mappers.put(GAVNameMapperProvider.NAME, new GAVNameMapperProvider().get());
        mappers.put(DiscriminatingNameMapperProvider.NAME, new DiscriminatingNameMapperProvider().get());
        mappers.put(FileGAVNameMapperProvider.NAME, new FileGAVNameMapperProvider().get());
        mappers.put(FileHashingGAVNameMapperProvider.NAME, new FileHashingGAVNameMapperProvider().get());
        this.nameMappers = mappers;

        HashMap<String, NamedLockFactory> factories = new HashMap<>();
        factories.put(NoopNamedLockFactory.NAME, new NoopNamedLockFactory());
        factories.put(LocalReadWriteLockNamedLockFactory.NAME, new LocalReadWriteLockNamedLockFactory());
        factories.put(LocalSemaphoreNamedLockFactory.NAME, new LocalSemaphoreNamedLockFactory());
        factories.put(FileLockNamedLockFactory.NAME, new FileLockNamedLockFactory());
        this.namedLockFactories = factories;
    }

    @Override
    public SyncContext newInstance(final RepositorySystemSession session, final boolean shared) {
        requireNonNull(session, "session cannot be null");
        NamedLockFactoryAdapter adapter = getOrCreateSessionAdapter(session);
        return adapter.newInstance(session, shared);
    }

    private NamedLockFactoryAdapter getOrCreateSessionAdapter(final RepositorySystemSession session) {
        return (NamedLockFactoryAdapter) session.getData().computeIfAbsent(ADAPTER_KEY, () -> {
            String nameMapperName = ConfigUtils.getString(session, DEFAULT_NAME_MAPPER_NAME, NAME_MAPPER_KEY);
            String namedLockFactoryName = ConfigUtils.getString(session, DEFAULT_FACTORY_NAME, FACTORY_KEY);
            NameMapper nameMapper = nameMappers.get(nameMapperName);
            if (nameMapper == null) {
                throw new IllegalArgumentException(
                        "Unknown NameMapper name: " + nameMapperName + ", known ones: " + nameMappers.keySet());
            }
            NamedLockFactory namedLockFactory = namedLockFactories.get(namedLockFactoryName);
            if (namedLockFactory == null) {
                throw new IllegalArgumentException("Unknown NamedLockFactory name: " + namedLockFactoryName
                        + ", known ones: " + namedLockFactories.keySet());
            }
            session.addOnCloseHandler(this::shutDownSessionAdapter);
            return new NamedLockFactoryAdapter(nameMapper, namedLockFactory);
        });
    }

    private void shutDownSessionAdapter(RepositorySystemSession session) {
        NamedLockFactoryAdapter adapter =
                (NamedLockFactoryAdapter) session.getData().get(ADAPTER_KEY);
        if (adapter != null) {
            adapter.shutdown();
        }
    }
}
