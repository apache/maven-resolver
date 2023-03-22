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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.internal.impl.synccontext.named.NamedLockFactoryAdapterFactory;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.spi.synccontext.SyncContextFactory;
import org.eclipse.aether.util.ConfigUtils;

import static java.util.Objects.requireNonNull;

/**
 * Default {@link SyncContextFactory} implementation that uses named locks.
 * <p>
 * The implementation relies fully on {@link NamedLockFactoryAdapterFactory} and all it does is just "stuff" the
 * adapter instance into session, hence factory is called only when given session has no instance created.
 */
@Singleton
@Named
public final class DefaultSyncContextFactory implements SyncContextFactory, Service {

    public static final String FACTORY_KEY = "aether.syncContext.factory";

    public static final String DEFAULT_FACTORY_NAME = NamedLockSyncContextFactory.NAME;

    private static Map<String, SyncContextFactory> getManuallyCreatedFactories() {
        HashMap<String, SyncContextFactory> factories = new HashMap<>();
        factories.put(NamedLockSyncContextFactory.NAME, new NamedLockSyncContextFactory());
        return Collections.unmodifiableMap(factories);
    }

    private final Map<String, SyncContextFactory> factories;
    private final String defaultFactoryName;

    /**
     * Default constructor for non Eclipse Sisu uses.
     *
     * @deprecated for use in SL only.
     */
    @Deprecated
    public DefaultSyncContextFactory() {
        this(getManuallyCreatedFactories(), DEFAULT_FACTORY_NAME);
    }

    /**
     * Constructor used with DI, where factories are injected and selected based on key.
     */
    @Inject
    public DefaultSyncContextFactory(Map<String, SyncContextFactory> factories) {
        this(factories, DEFAULT_FACTORY_NAME);
    }

    public DefaultSyncContextFactory(Map<String, SyncContextFactory> factories, String defaultFactoryName) {
        this.factories = requireNonNull(factories);
        this.defaultFactoryName = requireNonNull(defaultFactoryName);
    }

    @Override
    public void initService(final ServiceLocator locator) {
        for (SyncContextFactory factory : factories.values()) {
            if (factory instanceof Service) {
                ((Service) factory).initService(locator);
            }
        }
    }

    @Override
    public SyncContext newInstance(final RepositorySystemSession session, final boolean shared) {
        requireNonNull(session, "session cannot be null");
        String name = ConfigUtils.getString(session, defaultFactoryName, FACTORY_KEY);
        SyncContextFactory factory = factories.get(name);
        if (factory == null) {
            throw new IllegalArgumentException(
                    "Unknown SyncContextFactory name: '" + name + "', known ones: " + factories.keySet());
        }
        return factory.newInstance(session, shared);
    }
}
