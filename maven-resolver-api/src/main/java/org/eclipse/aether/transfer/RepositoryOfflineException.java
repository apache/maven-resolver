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
package org.eclipse.aether.transfer;

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Thrown when a transfer could not be performed because a remote repository is not accessible in offline mode.
 */
public class RepositoryOfflineException extends RepositoryException {

    private final transient RemoteRepository repository;

    private static String getMessage(RemoteRepository repository) {
        if (repository == null) {
            return "Cannot access remote repositories in offline mode";
        } else {
            return "Cannot access " + repository.getId() + " (" + repository.getUrl() + ") in offline mode";
        }
    }

    /**
     * Creates a new exception with the specified repository.
     *
     * @param repository The inaccessible remote repository, may be {@code null}.
     */
    public RepositoryOfflineException(RemoteRepository repository) {
        super(getMessage(repository));
        this.repository = repository;
    }

    /**
     * Creates a new exception with the specified repository and detail message.
     *
     * @param repository The inaccessible remote repository, may be {@code null}.
     * @param message The detail message, may be {@code null}.
     */
    public RepositoryOfflineException(RemoteRepository repository, String message) {
        super(message);
        this.repository = repository;
    }

    /**
     * Gets the remote repository that could not be accessed due to offline mode.
     *
     * @return The inaccessible remote repository or {@code null} if unknown.
     */
    public RemoteRepository getRepository() {
        return repository;
    }
}
