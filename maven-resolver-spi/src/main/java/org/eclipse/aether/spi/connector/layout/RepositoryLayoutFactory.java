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
package org.eclipse.aether.spi.connector.layout;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.transfer.NoRepositoryLayoutException;

/**
 * A factory to obtain repository layouts. A repository layout is responsible to map an artifact or some metadata to a
 * URI relative to the repository root where the resource resides. When the repository system needs to access a given
 * remote repository, it iterates the registered factories in descending order of their priority and calls
 * {@link #newInstance(RepositorySystemSession, RemoteRepository)} on them. The first layout returned by a factory will
 * then be used for transferring artifacts/metadata.
 */
public interface RepositoryLayoutFactory {

    /**
     * Tries to create a repository layout for the specified remote repository. Typically, a factory will inspect
     * {@link RemoteRepository#getContentType()} to determine whether it can handle a repository.
     *
     * @param session The repository system session from which to configure the layout, must not be {@code null}.
     * @param repository The remote repository to create a layout for, must not be {@code null}.
     * @return The layout for the given repository, never {@code null}.
     * @throws NoRepositoryLayoutException If the factory cannot create a repository layout for the specified remote
     *             repository.
     */
    RepositoryLayout newInstance(RepositorySystemSession session, RemoteRepository repository)
            throws NoRepositoryLayoutException;

    /**
     * The priority of this factory. When multiple factories can handle a given repository, factories with higher
     * priority are preferred over those with lower priority.
     *
     * @return The priority of this factory.
     */
    float getPriority();
}
