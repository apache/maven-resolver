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
 * Retrieves a repository layout from the installed layout factories.
 *
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 */
public interface RepositoryLayoutProvider {

    /**
     * Tries to retrieve a repository layout for the specified remote repository.
     *
     * @param session The repository system session from which to configure the layout, must not be {@code null}.
     * @param repository The remote repository to create a layout for, must not be {@code null}.
     * @return The layout for the given repository, never {@code null}.
     * @throws NoRepositoryLayoutException If none of the installed layout factories can provide a repository layout for
     *             the specified remote repository.
     */
    RepositoryLayout newRepositoryLayout(RepositorySystemSession session, RemoteRepository repository)
            throws NoRepositoryLayoutException;
}
