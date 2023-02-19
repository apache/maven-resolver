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
package org.eclipse.aether.spi.connector.transport;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.transfer.NoTransporterException;

/**
 * A factory to create transporters. A transporter is responsible for uploads/downloads to/from a remote repository
 * using a particular transport protocol. When the repository system needs a transporter for a given remote repository,
 * it iterates the registered factories in descending order of their priority and calls
 * {@link #newInstance(RepositorySystemSession, RemoteRepository)} on them. The first transporter returned by a factory
 * will then be used for the transfer.
 */
public interface TransporterFactory {

    /**
     * Tries to create a transporter for the specified remote repository. Typically, a factory will inspect
     * {@link RemoteRepository#getProtocol()} to determine whether it can handle a repository.
     *
     * @param session The repository system session from which to configure the transporter, must not be {@code null}.
     *            In particular, a transporter should obey the timeouts configured for the session.
     * @param repository The remote repository to create a transporter for, must not be {@code null}.
     * @return The transporter for the given repository, never {@code null}.
     * @throws NoTransporterException If the factory cannot create a transporter for the specified remote repository.
     */
    Transporter newInstance(RepositorySystemSession session, RemoteRepository repository) throws NoTransporterException;

    /**
     * The priority of this factory. When multiple factories can handle a given repository, factories with higher
     * priority are preferred over those with lower priority.
     *
     * @return The priority of this factory.
     */
    float getPriority();
}
