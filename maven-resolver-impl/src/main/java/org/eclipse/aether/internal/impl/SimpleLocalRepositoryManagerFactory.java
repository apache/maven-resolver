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

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
import org.eclipse.aether.spi.localrepo.LocalRepositoryManagerFactory;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;

import static java.util.Objects.requireNonNull;

/**
 * Creates local repository managers for repository type {@code "simple"}.
 */
@Singleton
@Named("simple")
public class SimpleLocalRepositoryManagerFactory implements LocalRepositoryManagerFactory, Service {
    private float priority;

    private LocalPathComposer localPathComposer;

    @Deprecated
    public SimpleLocalRepositoryManagerFactory() {
        // enable no-arg constructor
        this.localPathComposer = new DefaultLocalPathComposer(); // maven UTs needs this
    }

    @Inject
    public SimpleLocalRepositoryManagerFactory(final LocalPathComposer localPathComposer) {
        this.localPathComposer = requireNonNull(localPathComposer);
    }

    @Override
    public void initService(final ServiceLocator locator) {
        this.localPathComposer = requireNonNull(locator.getService(LocalPathComposer.class));
    }

    @Override
    public LocalRepositoryManager newInstance(RepositorySystemSession session, LocalRepository repository)
            throws NoLocalRepositoryManagerException {
        requireNonNull(session, "session cannot be null");
        requireNonNull(repository, "repository cannot be null");

        if ("".equals(repository.getContentType()) || "simple".equals(repository.getContentType())) {
            return new SimpleLocalRepositoryManager(repository.getBasedir(), "simple", localPathComposer);
        } else {
            throw new NoLocalRepositoryManagerException(repository);
        }
    }

    @Override
    public float getPriority() {
        return priority;
    }

    /**
     * Sets the priority of this component.
     *
     * @param priority The priority.
     * @return This component for chaining, never {@code null}.
     */
    public SimpleLocalRepositoryManagerFactory setPriority(float priority) {
        this.priority = priority;
        return this;
    }
}
