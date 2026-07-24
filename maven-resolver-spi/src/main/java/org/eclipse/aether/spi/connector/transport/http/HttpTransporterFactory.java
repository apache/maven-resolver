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
package org.eclipse.aether.spi.connector.transport.http;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transfer.NoTransporterException;

/**
 * A factory for {@link HttpTransporter}.
 *
 * @since 2.0.0
 */
public interface HttpTransporterFactory extends TransporterFactory {

    @Override
    default boolean canHandle(String repositoryProtocol) {
        return "http".equalsIgnoreCase(repositoryProtocol) || "https".equalsIgnoreCase(repositoryProtocol);
    }

    /**
     * Tries to create HTTP transporter for the specified remote repository.
     *
     * @param session The repository system session from which to configure the transporter, must not be {@code null}.
     *            In particular, a transporter should obey the timeouts configured for the session.
     * @param repository The remote repository to create a transporter for, must not be {@code null}.
     * @return The transporter for the given repository, never {@code null}.
     * @throws NoTransporterException If the factory cannot create a transporter for the specified remote repository.
     */
    @Override
    HttpTransporter newInstance(RepositorySystemSession session, RemoteRepository repository)
            throws NoTransporterException;
}
