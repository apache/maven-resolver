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
import org.eclipse.aether.util.repository.RepositoryIdHelper;

import static java.util.Objects.requireNonNull;

/**
 * Creates local repository managers for repository type {@code "simple"}.
 */
@Singleton
@Named(SimpleLocalRepositoryManagerFactory.NAME)
public class SimpleLocalRepositoryManagerFactory implements LocalRepositoryManagerFactory {
    public static final String NAME = "simple";
    private float priority;

    private final LocalPathComposer localPathComposer;

    /**
     * No-arg constructor, as "simple" local repository is meant mainly for use in tests.
     */
    public SimpleLocalRepositoryManagerFactory() {
        this.localPathComposer = new DefaultLocalPathComposer();
    }

    @Inject
    public SimpleLocalRepositoryManagerFactory(final LocalPathComposer localPathComposer) {
        this.localPathComposer = requireNonNull(localPathComposer);
    }

    @Override
    public LocalRepositoryManager newInstance(RepositorySystemSession session, LocalRepository repository)
            throws NoLocalRepositoryManagerException {
        requireNonNull(session, "session cannot be null");
        requireNonNull(repository, "repository cannot be null");

        if ("".equals(repository.getContentType()) || "simple".equals(repository.getContentType())) {
            return new SimpleLocalRepositoryManager(
                    repository.getBasePath(),
                    "simple",
                    localPathComposer,
                    Utils.repositoryKeyFunction(
                            SimpleLocalRepositoryManagerFactory.class,
                            session,
                            RepositoryIdHelper.RepositoryKeyType.SIMPLE.name(),
                            null));
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
