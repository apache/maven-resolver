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
package org.eclipse.aether.impl;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;

/**
 * Retrieves a local repository manager from the installed local repository manager factories.
 *
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 * @provisional This type is provisional and can be changed, moved or removed without prior notice.
 */
public interface LocalRepositoryProvider {

    /**
     * Creates a new manager for the specified local repository. If the specified local repository has no type, the
     * default local repository type of the system will be used. <em>Note:</em> It is expected that this method
     * invocation is one of the last steps of setting up a new session, in particular any configuration properties
     * should have been set already.
     *
     * @param session The repository system session from which to configure the manager, must not be {@code null}.
     * @param localRepository The local repository to create a manager for, must not be {@code null}.
     * @return The local repository manager, never {@code null}.
     * @throws NoLocalRepositoryManagerException If the specified repository type is not recognized or no base directory
     *             is given.
     * @see RepositorySystem#newLocalRepositoryManager(RepositorySystemSession, LocalRepository)
     */
    LocalRepositoryManager newLocalRepositoryManager(RepositorySystemSession session, LocalRepository localRepository)
            throws NoLocalRepositoryManagerException;
}
