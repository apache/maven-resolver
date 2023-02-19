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
import org.eclipse.aether.artifact.ArtifactProperties;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Thrown when an artifact was not found in a particular repository.
 */
public class ArtifactNotFoundException extends ArtifactTransferException {

    /**
     * Creates a new exception with the specified artifact and repository.
     *
     * @param artifact The missing artifact, may be {@code null}.
     * @param repository The involved remote repository, may be {@code null}.
     */
    public ArtifactNotFoundException(Artifact artifact, RemoteRepository repository) {
        super(artifact, repository, getMessage(artifact, repository));
    }

    private static String getMessage(Artifact artifact, RemoteRepository repository) {
        StringBuilder buffer = new StringBuilder(256);
        buffer.append("Could not find artifact ").append(artifact);
        buffer.append(getString(" in ", repository));
        if (artifact != null) {
            String localPath = artifact.getProperty(ArtifactProperties.LOCAL_PATH, null);
            if (localPath != null && repository == null) {
                buffer.append(" at specified path ").append(localPath);
            }
            String downloadUrl = artifact.getProperty(ArtifactProperties.DOWNLOAD_URL, null);
            if (downloadUrl != null) {
                buffer.append(", try downloading from ").append(downloadUrl);
            }
        }
        return buffer.toString();
    }

    /**
     * Creates a new exception with the specified artifact, repository and detail message.
     *
     * @param artifact The missing artifact, may be {@code null}.
     * @param repository The involved remote repository, may be {@code null}.
     * @param message The detail message, may be {@code null}.
     */
    public ArtifactNotFoundException(Artifact artifact, RemoteRepository repository, String message) {
        super(artifact, repository, message);
    }

    /**
     * Creates a new exception with the specified artifact, repository and detail message.
     *
     * @param artifact The missing artifact, may be {@code null}.
     * @param repository The involved remote repository, may be {@code null}.
     * @param message The detail message, may be {@code null}.
     * @param fromCache {@code true} if the exception was played back from the error cache, {@code false} if the
     *            exception actually just occurred.
     */
    public ArtifactNotFoundException(
            Artifact artifact, RemoteRepository repository, String message, boolean fromCache) {
        super(artifact, repository, message, fromCache);
    }

    /**
     * Creates a new exception with the specified artifact, repository, detail message and cause.
     *
     * @param artifact The missing artifact, may be {@code null}.
     * @param repository The involved remote repository, may be {@code null}.
     * @param message The detail message, may be {@code null}.
     * @param cause The exception that caused this one, may be {@code null}.
     */
    public ArtifactNotFoundException(Artifact artifact, RemoteRepository repository, String message, Throwable cause) {
        super(artifact, repository, message, cause);
    }
}
