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

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Thrown when an artifact was filtered from a particular repository.
 *
 * @since 1.9.6
 */
public class ArtifactFilteredOutException extends ArtifactNotFoundException {

    /**
     * Creates a new exception with the specified artifact, repository and detail message.
     *
     * @param artifact The missing artifact, may be {@code null}.
     * @param repository The involved remote repository, may be {@code null}.
     * @param message The detail message, may be {@code null}.
     */
    public ArtifactFilteredOutException(Artifact artifact, RemoteRepository repository, String message) {
        super(artifact, repository, message);
    }
}
