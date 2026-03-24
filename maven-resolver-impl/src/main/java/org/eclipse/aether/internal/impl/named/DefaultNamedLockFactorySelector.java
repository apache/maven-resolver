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
package org.eclipse.aether.internal.impl.named;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.MultiRuntimeException;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.impl.NamedLockFactorySelector;
import org.eclipse.aether.impl.RepositorySystemLifecycle;
import org.eclipse.aether.internal.impl.synccontext.named.NamedLockFactoryAdapter;
import org.eclipse.aether.internal.impl.synccontext.named.NamedLockFactoryAdapterFactoryImpl;
import org.eclipse.aether.named.NamedLockFactory;
import org.eclipse.aether.named.providers.FileLockNamedLockFactory;
import org.eclipse.aether.util.ConfigUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

@Singleton
@Named
public class DefaultNamedLockFactorySelector implements NamedLockFactorySelector {
    public static final String CONFIG_PROPS_PREFIX = ConfigurationProperties.PREFIX_SYSTEM + "named.";

    /**
     * Name of the lock factory to use in system. Out of the box supported ones are "file-lock", "rwlock-local",
     * "semaphore-local", "noop". By adding extensions one can extend available lock factories (for example IPC locking).
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #DEFAULT_FACTORY_NAME}
     */
    public static final String CONFIG_PROP_FACTORY_NAME = CONFIG_PROPS_PREFIX + "factory";

    public static final String DEFAULT_FACTORY_NAME = FileLockNamedLockFactory.NAME;

    /**
     * The maximum amount of time to be blocked, while obtaining a named lock.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.Long}
     * @configurationDefaultValue {@link #DEFAULT_LOCK_WAIT_TIME}
     */
    public static final String CONFIG_PROP_LOCK_WAIT_TIME = CONFIG_PROPS_PREFIX + "time";

    public static final long DEFAULT_LOCK_WAIT_TIME = 900L;

    /**
     * The unit of maximum amount of time. Accepts TimeUnit enum names.
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #DEFAULT_LOCK_WAIT_TIME_UNIT}
     */
    public static final String CONFIG_PROP_LOCK_WAIT_TIME_UNIT = CONFIG_PROPS_PREFIX + "timeUnit";

    public static final String DEFAULT_LOCK_WAIT_TIME_UNIT = "SECONDS";

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final Map<String, NamedLockFactory> factories;

    protected final ConcurrentMap<String, NamedLockFactory> usedFactories;

    @Inject
    public DefaultNamedLockFactorySelector(
            Map<String, NamedLockFactory> factories, RepositorySystemLifecycle lifecycle) {
        this.factories = requireNonNull(factories);
        this.usedFactories = new ConcurrentHashMap<>();
        lifecycle.addOnSystemEndedHandler(this::shutdown);

        logger.debug("Created lock factory selector; available lock factories {}", factories.keySet());
    }

    @Override
    public Set<String> getAvailableLockFactories() {
        return Collections.unmodifiableSet(new HashSet<>(factories.keySet()));
    }

    @Override
    public NamedLockFactory getNamedLockFactory(Map<String, ?> configuration) {
        String factoryName = ConfigUtils.getString(
                configuration,
                DEFAULT_FACTORY_NAME,
                CONFIG_PROP_FACTORY_NAME,
                NamedLockFactoryAdapterFactoryImpl.CONFIG_PROP_FACTORY_KEY);
        NamedLockFactory factory = factories.get(factoryName);
        if (factory == null) {
            throw new IllegalArgumentException(
                    "Unknown NamedLockFactory name: '" + factoryName + "', known ones: " + factories.keySet());
        }
        usedFactories.putIfAbsent(factoryName, factory);
        return factory;
    }

    @Override
    public long getLockWaitTime(Map<String, ?> configuration) {
        long result = ConfigUtils.getLong(
                configuration,
                DEFAULT_LOCK_WAIT_TIME,
                CONFIG_PROP_LOCK_WAIT_TIME,
                NamedLockFactoryAdapter.CONFIG_PROP_TIME);
        if (result <= 0) {
            throw new IllegalArgumentException(
                    "The " + CONFIG_PROP_LOCK_WAIT_TIME_UNIT + " configuration but be grater than zero");
        }
        return result;
    }

    @Override
    public TimeUnit getLockWaitTimeUnit(Map<String, ?> configuration) {
        return TimeUnit.valueOf(ConfigUtils.getString(
                configuration,
                DEFAULT_LOCK_WAIT_TIME_UNIT,
                CONFIG_PROP_LOCK_WAIT_TIME_UNIT,
                NamedLockFactoryAdapter.CONFIG_PROP_TIME_UNIT));
    }

    private void shutdown() {
        logger.debug("Shutting down used lock factories {}", usedFactories.keySet());
        ArrayList<Exception> exceptions = new ArrayList<>();
        for (Map.Entry<String, NamedLockFactory> entry : usedFactories.entrySet()) {
            try {
                logger.debug("Shutting down '{}' factory", entry.getKey());
                entry.getValue().shutdown();
            } catch (Exception e) {
                exceptions.add(e);
            }
        }
        MultiRuntimeException.mayThrow("Problem shutting down used lock factories", exceptions);
    }
}
