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
package org.eclipse.aether.spi.connector.filter;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Remote repository filter that decides should the given artifact or metadata be accepted (for further processing)
 * from remote repository or not.
 *
 * @since 1.9.0
 */
public interface RemoteRepositoryFilter {
    /**
     * The check result, is immutable.
     */
    interface Result {
        /**
         * Returns {@code true} if accepted.
         */
        boolean isAccepted();

        /**
         * Returns string "reasoning" for {@link #isAccepted()} result, meant for human consumption, never {@code null}.
         */
        String reasoning();
    }

    /**
     * Decides should artifact be accepted from given remote repository.
     *
     * @param remoteRepository The remote repository, not {@code null}.
     * @param artifact         The artifact, not {@code null}.
     * @return the result, never {@code null}.
     */
    Result acceptArtifact(RemoteRepository remoteRepository, Artifact artifact);

    /**
     * Decides should metadata be accepted from given remote repository.
     *
     * @param remoteRepository The remote repository, not {@code null}.
     * @param metadata         The artifact, not {@code null}.
     * @return the result, never {@code null}.
     */
    Result acceptMetadata(RemoteRepository remoteRepository, Metadata metadata);
}
