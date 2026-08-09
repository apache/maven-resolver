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
 * Thrown in case of an unsupported repository layout.
 */
public class NoRepositoryLayoutException extends RepositoryException {

    private final transient RemoteRepository repository;

    /**
     * Creates a new exception with the specified repository.
     *
     * @param repository The remote repository whose layout is not supported, may be {@code null}.
     */
    public NoRepositoryLayoutException(RemoteRepository repository) {
        this(repository, toMessage(repository));
    }

    /**
     * Creates a new exception with the specified repository and detail message.
     *
     * @param repository The remote repository whose layout is not supported, may be {@code null}.
     * @param message The detail message, may be {@code null}.
     */
    public NoRepositoryLayoutException(RemoteRepository repository, String message) {
        super(message);
        this.repository = repository;
    }

    /**
     * Creates a new exception with the specified repository and cause.
     *
     * @param repository The remote repository whose layout is not supported, may be {@code null}.
     * @param cause The exception that caused this one, may be {@code null}.
     */
    public NoRepositoryLayoutException(RemoteRepository repository, Throwable cause) {
        this(repository, toMessage(repository), cause);
    }

    /**
     * Creates a new exception with the specified repository, detail message and cause.
     *
     * @param repository The remote repository whose layout is not supported, may be {@code null}.
     * @param message The detail message, may be {@code null}.
     * @param cause The exception that caused this one, may be {@code null}.
     */
    public NoRepositoryLayoutException(RemoteRepository repository, String message, Throwable cause) {
        super(message, cause);
        this.repository = repository;
    }

    private static String toMessage(RemoteRepository repository) {
        if (repository != null) {
            return "Unsupported repository layout " + repository.getContentType();
        } else {
            return "Unsupported repository layout";
        }
    }

    /**
     * Gets the remote repository whose layout is not supported.
     *
     * @return The unsupported remote repository or {@code null} if unknown.
     */
    public RemoteRepository getRepository() {
        return repository;
    }
}
