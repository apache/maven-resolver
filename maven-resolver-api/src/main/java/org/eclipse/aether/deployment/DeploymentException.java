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
package org.eclipse.aether.deployment;

import org.eclipse.aether.RepositoryException;

/**
 * Thrown in case of a deployment error like authentication failure.
 */
public class DeploymentException extends RepositoryException {

    /**
     * Creates a new exception with the specified detail message.
     *
     * @param message The detail message, may be {@code null}.
     */
    public DeploymentException(String message) {
        super(message);
    }

    /**
     * Creates a new exception with the specified detail message and cause.
     *
     * @param message The detail message, may be {@code null}.
     * @param cause The exception that caused this one, may be {@code null}.
     */
    public DeploymentException(String message, Throwable cause) {
        super(message, cause);
    }
}
