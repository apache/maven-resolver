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
package org.eclipse.aether.resolution;

import org.eclipse.aether.RepositorySystemSession;

/**
 * Controls the handling of errors related to reading an artifact descriptor.
 *
 * @see RepositorySystemSession#getArtifactDescriptorPolicy()
 */
public interface ArtifactDescriptorPolicy {

    /**
     * Bit mask indicating that errors while reading the artifact descriptor should not be tolerated.
     */
    int STRICT = 0x00;

    /**
     * Bit flag indicating that missing artifact descriptors should be silently ignored.
     */
    int IGNORE_MISSING = 0x01;

    /**
     * Bit flag indicating that existent but invalid artifact descriptors should be silently ignored.
     */
    int IGNORE_INVALID = 0x02;

    /**
     * Bit mask indicating that all errors should be silently ignored.
     */
    int IGNORE_ERRORS = IGNORE_MISSING | IGNORE_INVALID;

    /**
     * Gets the error policy for an artifact's descriptor.
     *
     * @param session The repository session during which the policy is determined, must not be {@code null}.
     * @param request The policy request holding further details, must not be {@code null}.
     * @return The bit mask describing the desired error policy.
     */
    int getPolicy(RepositorySystemSession session, ArtifactDescriptorPolicyRequest request);
}
