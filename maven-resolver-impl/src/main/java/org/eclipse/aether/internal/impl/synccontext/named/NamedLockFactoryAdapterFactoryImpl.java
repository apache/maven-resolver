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
package org.eclipse.aether.internal.impl.synccontext.named;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Predicate;

import org.eclipse.aether.MultiRuntimeException;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.impl.RepositorySystemLifecycle;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.named.NamedLockFactory;
import org.eclipse.aether.named.providers.FileLockNamedLockFactory;
import org.eclipse.aether.spi.locking.LockingInhibitorFactory;
import org.eclipse.aether.util.ConfigUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 * Default implementation of {@link NamedLockFactoryAdapterFactory}. This implementation creates new instances of the
 * adapter on every call. In turn, on shutdown, it will shut down all existing named lock factories. This is merely for
 * simplicity, to not have to track "used" named lock factories, while it exposes all available named lock factories to
 * callers.
 * <p>
 * Most members and methods of this class are protected. It is meant to be extended in case of need to customize its
 * behavior. An exception from this are private static methods, mostly meant to provide out of the box
 * defaults and to be used when no Eclipse Sisu component container is used.
 *
 * @since 1.9.1
 */
@Singleton
@Named
public class NamedLockFactoryAdapterFactoryImpl implements NamedLockFactoryAdapterFactory {
    public static final String DEFAULT_FACTORY_NAME = FileLockNamedLockFactory.NAME;

    public static final String DEFAULT_NAME_MAPPER_NAME = NameMappers.FILE_GAECV_NAME;

    /**
     * Name of the lock factory to use in session. Out of the box supported ones are "file-lock", "rwlock-local",
     * "semaphore-local", "noop". By adding extensions one can extend available lock factories (for example IPC locking).
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #DEFAULT_FACTORY_NAME}
     */
    public static final String CONFIG_PROP_FACTORY_KEY = NamedLockFactoryAdapter.CONFIG_PROPS_PREFIX + "factory";

    /**
     * Name of the name mapper to use in session. Out of the box supported ones are "static", "gav", "gaecv", "file-gav",
     * "file-gaecv", "file-hgav", "file-hgaecv", "file-static" and "discriminating".
     *
     * @configurationSource {@link RepositorySystemSession#getConfigProperties()}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #DEFAULT_NAME_MAPPER_NAME}
     */
    public static final String CONFIG_PROP_NAME_MAPPER_KEY = NamedLockFactoryAdapter.CONFIG_PROPS_PREFIX + "nameMapper";

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final Map<String, NamedLockFactory> factories;

    protected final String defaultFactoryName;

    protected final Map<String, NameMapper> nameMappers;

    protected final String defaultNameMapperName;

    protected final Map<String, LockingInhibitorFactory> lockingInhibitorFactories;

    @Inject
    public NamedLockFactoryAdapterFactoryImpl(
            final Map<String, NamedLockFactory> factories,
            final Map<String, NameMapper> nameMappers,
            final Map<String, LockingInhibitorFactory> lockingInhibitorFactories,
            final RepositorySystemLifecycle lifecycle) {
        this(
                factories,
                DEFAULT_FACTORY_NAME,
                nameMappers,
                DEFAULT_NAME_MAPPER_NAME,
                lockingInhibitorFactories,
                lifecycle);
    }

    public NamedLockFactoryAdapterFactoryImpl(
            final Map<String, NamedLockFactory> factories,
            final String defaultFactoryName,
            final Map<String, NameMapper> nameMappers,
            final String defaultNameMapperName,
            final Map<String, LockingInhibitorFactory> lockingInhibitorFactories,
            final RepositorySystemLifecycle lifecycle) {
        this.factories = requireNonNull(factories);
        this.defaultFactoryName = requireNonNull(defaultFactoryName);
        this.nameMappers = requireNonNull(nameMappers);
        this.defaultNameMapperName = requireNonNull(defaultNameMapperName);
        this.lockingInhibitorFactories = requireNonNull(lockingInhibitorFactories);
        lifecycle.addOnSystemEndedHandler(this::shutdown);

        logger.debug(
                "Created adapter factory; available factories {}; available name mappers {}",
                factories.keySet(),
                nameMappers.keySet());
    }

    /**
     * Current implementation simply delegates to {@link #createAdapter(RepositorySystemSession)}.
     */
    @Override
    public NamedLockFactoryAdapter getAdapter(RepositorySystemSession session) {
        return createAdapter(session);
    }

    /**
     * Creates a new adapter instance, never returns {@code null}.
     */
    protected NamedLockFactoryAdapter createAdapter(RepositorySystemSession session) {
        final String nameMapperName = requireNonNull(getNameMapperName(session));
        final String factoryName = requireNonNull(getFactoryName(session));
        final NameMapper nameMapper = selectNameMapper(session, nameMapperName);
        final NamedLockFactory factory = selectFactory(factoryName);
        logger.debug("Creating adapter using nameMapper '{}' and factory '{}'", nameMapperName, factoryName);
        return new NamedLockFactoryAdapter(nameMapper, factory);
    }

    /**
     * Returns the selected (user configured or default) named lock factory name, never {@code null}.
     */
    protected String getFactoryName(RepositorySystemSession session) {
        return ConfigUtils.getString(session, getDefaultFactoryName(), CONFIG_PROP_FACTORY_KEY);
    }

    /**
     * Returns the default named lock factory name, never {@code null}.
     */
    protected String getDefaultFactoryName() {
        return defaultFactoryName;
    }

    /**
     * Returns the selected (user configured or default) name mapper name, never {@code null}.
     */
    protected String getNameMapperName(RepositorySystemSession session) {
        return ConfigUtils.getString(session, getDefaultNameMapperName(), CONFIG_PROP_NAME_MAPPER_KEY);
    }

    /**
     * Returns the default name mapper name, never {@code null}.
     */
    protected String getDefaultNameMapperName() {
        return defaultNameMapperName;
    }

    /**
     * Selects a named lock factory, never returns {@code null}.
     */
    protected NamedLockFactory selectFactory(final String factoryName) {
        NamedLockFactory factory = factories.get(factoryName);
        if (factory == null) {
            throw new IllegalArgumentException(
                    "Unknown NamedLockFactory name: '" + factoryName + "', known ones: " + factories.keySet());
        }
        return factory;
    }

    /**
     * Selects a name mapper, never returns {@code null}. Applies inhibitors.
     */
    protected NameMapper selectNameMapper(final RepositorySystemSession session, final String nameMapperName) {
        NameMapper nameMapper = nameMappers.get(nameMapperName);
        if (nameMapper == null) {
            throw new IllegalArgumentException(
                    "Unknown NameMapper name: '" + nameMapperName + "', known ones: " + nameMappers.keySet());
        }
        if (!lockingInhibitorFactories.isEmpty()) {
            ArrayList<Predicate<Artifact>> artifactPredicates = new ArrayList<>();
            ArrayList<Predicate<Metadata>> metadataPredicates = new ArrayList<>();
            for (LockingInhibitorFactory factory : lockingInhibitorFactories.values()) {
                factory.newInstance(session).ifPresent(i -> {
                    i.inhibitArtifactLocking().ifPresent(artifactPredicates::add);
                    i.inhibitMetadataLocking().ifPresent(metadataPredicates::add);
                });
            }
            if (!artifactPredicates.isEmpty() || !metadataPredicates.isEmpty()) {
                Predicate<Artifact> ap = null;
                Predicate<Metadata> md = null;
                for (Predicate<Artifact> predicate : artifactPredicates) {
                    if (ap == null) {
                        ap = predicate;
                    } else {
                        ap = ap.or(predicate);
                    }
                }
                for (Predicate<Metadata> predicate : metadataPredicates) {
                    if (md == null) {
                        md = predicate;
                    } else {
                        md = md.or(predicate);
                    }
                }
                return new InhibitingNameMapper(nameMapper, ap, md);
            }
        }
        return nameMapper;
    }

    /**
     * To be invoked on repository system shut down. This method will shut down each {@link NamedLockFactory}.
     */
    protected void shutdown() {
        logger.debug(
                "Shutting down adapter factory; available factories {}; available name mappers {}",
                factories.keySet(),
                nameMappers.keySet());
        ArrayList<Exception> exceptions = new ArrayList<>();
        for (Map.Entry<String, NamedLockFactory> entry : factories.entrySet()) {
            try {
                logger.debug("Shutting down '{}' factory", entry.getKey());
                entry.getValue().shutdown();
            } catch (Exception e) {
                exceptions.add(e);
            }
        }
        MultiRuntimeException.mayThrow("Problem shutting down factories", exceptions);
    }
}
