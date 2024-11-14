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
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.layout.RepositoryLayout;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutFactory;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutProvider;
import org.eclipse.aether.transfer.NoRepositoryLayoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 */
@Singleton
@Named
public final class DefaultRepositoryLayoutProvider implements RepositoryLayoutProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultRepositoryLayoutProvider.class);

    private final Map<String, RepositoryLayoutFactory> layoutFactories;

    @Inject
    public DefaultRepositoryLayoutProvider(Map<String, RepositoryLayoutFactory> layoutFactories) {
        this.layoutFactories = Collections.unmodifiableMap(layoutFactories);
    }

    @Override
    public RepositoryLayout newRepositoryLayout(RepositorySystemSession session, RemoteRepository repository)
            throws NoRepositoryLayoutException {
        requireNonNull(session, "session cannot be null");
        requireNonNull(repository, "remote repository cannot be null");

        PrioritizedComponents<RepositoryLayoutFactory> factories = PrioritizedComponents.reuseOrCreate(
                session, RepositoryLayoutFactory.class, layoutFactories, RepositoryLayoutFactory::getPriority);

        List<NoRepositoryLayoutException> errors = new ArrayList<>();
        for (PrioritizedComponent<RepositoryLayoutFactory> factory : factories.getEnabled()) {
            try {
                return factory.getComponent().newInstance(session, repository);
            } catch (NoRepositoryLayoutException e) {
                // continue and try next factory
                errors.add(e);
            }
        }
        if (LOGGER.isDebugEnabled() && errors.size() > 1) {
            for (Exception e : errors) {
                LOGGER.debug("Could not obtain layout factory for {}", repository, e);
            }
        }

        StringBuilder buffer = new StringBuilder(256);
        if (factories.isEmpty()) {
            buffer.append("No layout factories registered");
        } else {
            buffer.append("Cannot access ").append(repository.getUrl());
            buffer.append(" with type ").append(repository.getContentType());
            buffer.append(" using the available layout factories: ");
            factories.list(buffer);
        }

        throw new NoRepositoryLayoutException(repository, buffer.toString(), errors.size() == 1 ? errors.get(0) : null);
    }
}
