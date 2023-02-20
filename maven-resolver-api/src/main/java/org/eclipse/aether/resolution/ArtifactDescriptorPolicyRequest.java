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

import org.eclipse.aether.artifact.Artifact;

/**
 * A query for the error policy for a given artifact's descriptor.
 *
 * @see ArtifactDescriptorPolicy
 */
public final class ArtifactDescriptorPolicyRequest {

    private Artifact artifact;

    private String context = "";

    /**
     * Creates an uninitialized request.
     */
    public ArtifactDescriptorPolicyRequest() {
        // enables default constructor
    }

    /**
     * Creates a request for the specified artifact.
     *
     * @param artifact The artifact for whose descriptor to determine the error policy, may be {@code null}.
     * @param context The context in which this request is made, may be {@code null}.
     */
    public ArtifactDescriptorPolicyRequest(Artifact artifact, String context) {
        setArtifact(artifact);
        setRequestContext(context);
    }

    /**
     * Gets the artifact for whose descriptor to determine the error policy.
     *
     * @return The artifact for whose descriptor to determine the error policy or {@code null} if not set.
     */
    public Artifact getArtifact() {
        return artifact;
    }

    /**
     * Sets the artifact for whose descriptor to determine the error policy.
     *
     * @param artifact The artifact for whose descriptor to determine the error policy, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public ArtifactDescriptorPolicyRequest setArtifact(Artifact artifact) {
        this.artifact = artifact;
        return this;
    }

    /**
     * Gets the context in which this request is made.
     *
     * @return The context, never {@code null}.
     */
    public String getRequestContext() {
        return context;
    }

    /**
     * Sets the context in which this request is made.
     *
     * @param context The context, may be {@code null}.
     * @return This request for chaining, never {@code null}.
     */
    public ArtifactDescriptorPolicyRequest setRequestContext(String context) {
        this.context = (context != null) ? context : "";
        return this;
    }

    @Override
    public String toString() {
        return String.valueOf(getArtifact());
    }
}
