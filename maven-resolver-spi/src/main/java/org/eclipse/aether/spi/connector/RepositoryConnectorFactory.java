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
package org.eclipse.aether.spi.connector;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.transfer.NoRepositoryConnectorException;

/**
 * A factory to create repository connectors. A repository connector is responsible for uploads/downloads to/from a
 * certain kind of remote repository. When the repository system needs a repository connector for a given remote
 * repository, it iterates the registered factories in descending order of their priority and calls
 * {@link #newInstance(RepositorySystemSession, RemoteRepository)} on them. The first connector returned by a factory
 * will then be used for the transfer.
 */
public interface RepositoryConnectorFactory {

    /**
     * Tries to create a repository connector for the specified remote repository. Typically, a factory will inspect
     * {@link RemoteRepository#getProtocol()} and {@link RemoteRepository#getContentType()} to determine whether it can
     * handle a repository.
     *
     * @param session The repository system session from which to configure the connector, must not be {@code null}. In
     *            particular, a connector must notify any {@link RepositorySystemSession#getTransferListener()} set for
     *            the session and should obey the timeouts configured for the session.
     * @param repository The remote repository to create a connector for, must not be {@code null}.
     * @return The connector for the given repository, never {@code null}.
     * @throws NoRepositoryConnectorException If the factory cannot create a connector for the specified remote
     *             repository.
     */
    RepositoryConnector newInstance(RepositorySystemSession session, RemoteRepository repository)
            throws NoRepositoryConnectorException;

    /**
     * The priority of this factory. When multiple factories can handle a given repository, factories with higher
     * priority are preferred over those with lower priority.
     *
     * @return The priority of this factory.
     */
    float getPriority();
}
