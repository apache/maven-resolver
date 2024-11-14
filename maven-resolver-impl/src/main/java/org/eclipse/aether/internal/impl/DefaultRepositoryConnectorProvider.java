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
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.impl.RemoteRepositoryFilterManager;
import org.eclipse.aether.impl.RepositoryConnectorProvider;
import org.eclipse.aether.internal.impl.filter.FilteringRepositoryConnector;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.RepositoryConnector;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.filter.RemoteRepositoryFilter;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 */
@Singleton
@Named
public class DefaultRepositoryConnectorProvider implements RepositoryConnectorProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultRepositoryConnectorProvider.class);

    private final Map<String, RepositoryConnectorFactory> connectorFactories;

    private final RemoteRepositoryFilterManager remoteRepositoryFilterManager;

    @Inject
    public DefaultRepositoryConnectorProvider(
            Map<String, RepositoryConnectorFactory> connectorFactories,
            RemoteRepositoryFilterManager remoteRepositoryFilterManager) {
        this.connectorFactories = Collections.unmodifiableMap(connectorFactories);
        this.remoteRepositoryFilterManager = requireNonNull(remoteRepositoryFilterManager);
    }

    @Override
    public RepositoryConnector newRepositoryConnector(RepositorySystemSession session, RemoteRepository repository)
            throws NoRepositoryConnectorException {
        requireNonNull(repository, "remote repository cannot be null");

        if (repository.isBlocked()) {
            if (repository.getMirroredRepositories().isEmpty()) {
                throw new NoRepositoryConnectorException(repository, "Blocked repository: " + repository);
            } else {
                throw new NoRepositoryConnectorException(
                        repository, "Blocked mirror for repositories: " + repository.getMirroredRepositories());
            }
        }

        PrioritizedComponents<RepositoryConnectorFactory> factories = PrioritizedComponents.reuseOrCreate(
                session, RepositoryConnectorFactory.class, connectorFactories, RepositoryConnectorFactory::getPriority);

        RemoteRepositoryFilter filter = remoteRepositoryFilterManager.getRemoteRepositoryFilter(session);
        List<NoRepositoryConnectorException> errors = new ArrayList<>();
        for (PrioritizedComponent<RepositoryConnectorFactory> factory : factories.getEnabled()) {
            try {
                RepositoryConnector connector = factory.getComponent().newInstance(session, repository);

                if (LOGGER.isDebugEnabled()) {
                    StringBuilder buffer = new StringBuilder(256);
                    buffer.append("Using connector ")
                            .append(connector.getClass().getSimpleName());
                    Utils.appendClassLoader(buffer, connector);
                    buffer.append(" with priority ").append(factory.getPriority());
                    buffer.append(" for ").append(repository.getUrl());
                    LOGGER.debug(buffer.toString());
                }

                if (filter != null) {
                    return new FilteringRepositoryConnector(repository, connector, filter);
                } else {
                    return connector;
                }
            } catch (NoRepositoryConnectorException e) {
                // continue and try next factory
                LOGGER.debug("Could not obtain connector factory for {}", repository, e);
                errors.add(e);
            }
        }

        StringBuilder buffer = new StringBuilder(256);
        if (factories.isEmpty()) {
            buffer.append("No connector factories available");
        } else {
            buffer.append("Cannot access ").append(repository.getUrl());
            buffer.append(" with type ").append(repository.getContentType());
            buffer.append(" using the available connector factories: ");
            factories.list(buffer);
        }

        // create exception: if one error, make it cause
        NoRepositoryConnectorException ex = new NoRepositoryConnectorException(
                repository, buffer.toString(), errors.size() == 1 ? errors.get(0) : null);
        // if more errors, make them all suppressed
        if (errors.size() > 1) {
            errors.forEach(ex::addSuppressed);
        }
        throw ex;
    }
}
