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
package org.eclipse.aether.util.repository;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.resolution.ArtifactDescriptorPolicy;
import org.eclipse.aether.resolution.ArtifactDescriptorPolicyRequest;

import static java.util.Objects.requireNonNull;

/**
 * An artifact descriptor error policy that allows to control error handling at a global level.
 */
public final class SimpleArtifactDescriptorPolicy implements ArtifactDescriptorPolicy {

    private final int policy;

    /**
     * Creates a new error policy with the specified behavior.
     *
     * @param ignoreMissing {@code true} to ignore missing descriptors, {@code false} to fail resolution.
     * @param ignoreInvalid {@code true} to ignore invalid descriptors, {@code false} to fail resolution.
     */
    public SimpleArtifactDescriptorPolicy(boolean ignoreMissing, boolean ignoreInvalid) {
        this((ignoreMissing ? IGNORE_MISSING : 0) | (ignoreInvalid ? IGNORE_INVALID : 0));
    }

    /**
     * Creates a new error policy with the specified bit mask.
     *
     * @param policy The bit mask describing the policy.
     */
    public SimpleArtifactDescriptorPolicy(int policy) {
        this.policy = policy;
    }

    public int getPolicy(RepositorySystemSession session, ArtifactDescriptorPolicyRequest request) {
        requireNonNull(session, "session cannot be null");
        requireNonNull(request, "request cannot be null");
        return policy;
    }
}
