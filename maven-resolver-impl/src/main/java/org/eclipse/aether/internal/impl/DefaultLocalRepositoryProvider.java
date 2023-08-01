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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.impl.LocalRepositoryProvider;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
import org.eclipse.aether.spi.localrepo.LocalRepositoryManagerFactory;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 */
@Singleton
@Named
public class DefaultLocalRepositoryProvider implements LocalRepositoryProvider, Service {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultLocalRepositoryProvider.class);

    private Collection<LocalRepositoryManagerFactory> managerFactories = new ArrayList<>();

    @Deprecated
    public DefaultLocalRepositoryProvider() {
        // enables default constructor
    }

    @Inject
    public DefaultLocalRepositoryProvider(Set<LocalRepositoryManagerFactory> factories) {
        setLocalRepositoryManagerFactories(factories);
    }

    public void initService(ServiceLocator locator) {
        setLocalRepositoryManagerFactories(locator.getServices(LocalRepositoryManagerFactory.class));
    }

    public DefaultLocalRepositoryProvider addLocalRepositoryManagerFactory(LocalRepositoryManagerFactory factory) {
        managerFactories.add(requireNonNull(factory, "local repository manager factory cannot be null"));
        return this;
    }

    public DefaultLocalRepositoryProvider setLocalRepositoryManagerFactories(
            Collection<LocalRepositoryManagerFactory> factories) {
        if (factories == null) {
            managerFactories = new ArrayList<>(2);
        } else {
            managerFactories = factories;
        }
        return this;
    }

    public LocalRepositoryManager newLocalRepositoryManager(RepositorySystemSession session, LocalRepository repository)
            throws NoLocalRepositoryManagerException {
        requireNonNull(session, "session cannot be null");
        requireNonNull(repository, "repository cannot be null");
        PrioritizedComponents<LocalRepositoryManagerFactory> factories = new PrioritizedComponents<>(session);
        for (LocalRepositoryManagerFactory factory : this.managerFactories) {
            factories.add(factory, factory.getPriority());
        }

        List<NoLocalRepositoryManagerException> errors = new ArrayList<>();
        for (PrioritizedComponent<LocalRepositoryManagerFactory> factory : factories.getEnabled()) {
            try {
                LocalRepositoryManager manager = factory.getComponent().newInstance(session, repository);

                if (LOGGER.isDebugEnabled()) {
                    StringBuilder buffer = new StringBuilder(256);
                    buffer.append("Using manager ").append(manager.getClass().getSimpleName());
                    Utils.appendClassLoader(buffer, manager);
                    buffer.append(" with priority ").append(factory.getPriority());
                    buffer.append(" for ").append(repository.getBasedir());

                    LOGGER.debug(buffer.toString());
                }

                return manager;
            } catch (NoLocalRepositoryManagerException e) {
                // continue and try next factory
                errors.add(e);
            }
        }
        if (LOGGER.isDebugEnabled() && errors.size() > 1) {
            for (Exception e : errors) {
                LOGGER.debug("Could not obtain local repository manager for {}", repository, e);
            }
        }

        StringBuilder buffer = new StringBuilder(256);
        if (factories.isEmpty()) {
            buffer.append("No local repository managers registered");
        } else {
            buffer.append("Cannot access ").append(repository.getBasedir());
            buffer.append(" with type ").append(repository.getContentType());
            buffer.append(" using the available factories ");
            factories.list(buffer);
        }

        throw new NoLocalRepositoryManagerException(
                repository, buffer.toString(), errors.size() == 1 ? errors.get(0) : null);
    }
}
