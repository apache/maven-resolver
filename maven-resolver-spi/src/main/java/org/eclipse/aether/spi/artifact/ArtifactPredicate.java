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
package org.eclipse.aether.spi.artifact;

import org.eclipse.aether.artifact.Artifact;

/**
 * An artifact predicate.
 *
 * @since 2.0.0
 */
public interface ArtifactPredicate {
    /**
     * Returns {@code true} if passed in artifact should have checksums.
     * <p>
     * Artifact should have checksum if it is not a checksum artifact, or any artifact that has been configured (in
     * session) that should not have them.
     */
    default boolean hasChecksums(Artifact artifact) {
        return !isWithoutChecksum(artifact) && !isChecksum(artifact);
    }

    /**
     * Returns {@code true} if passed in artifact is configured to not have checksums.
     */
    boolean isWithoutChecksum(Artifact artifact);

    /**
     * Returns {@code true} if passed in artifact is a checksum artifact.
     */
    boolean isChecksum(Artifact artifact);
}
