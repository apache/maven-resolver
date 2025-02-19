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
package org.eclipse.aether.spi.localrepo;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;

/**
 * A factory to create managers for the local repository. A local repository manager needs to keep track of artifacts
 * and metadata and manage access. When the repository system needs a repository manager for a given local repository,
 * it iterates the registered factories in descending order of their priority and calls
 * {@link #newInstance(RepositorySystemSession, LocalRepository)} on them. The first manager returned by a factory will
 * then be used for the local repository.
 */
public interface LocalRepositoryManagerFactory {

    /**
     * Tries to create a repository manager for the specified local repository. The distinguishing property of a local
     * repository is its {@link LocalRepository#getContentType() type}, which may for example denote the used directory
     * structure.
     *
     * @param session The repository system session from which to configure the manager, must not be {@code null}.
     * @param repository The local repository to create a manager for, must not be {@code null}.
     * @return The manager for the given repository, never {@code null}.
     * @throws NoLocalRepositoryManagerException If the factory cannot create a manager for the specified local
     *             repository.
     */
    LocalRepositoryManager newInstance(RepositorySystemSession session, LocalRepository repository)
            throws NoLocalRepositoryManagerException;

    /**
     * The priority of this factory. Factories with higher priority are preferred over those with lower priority.
     *
     * @return The priority of this factory.
     */
    float getPriority();
}
