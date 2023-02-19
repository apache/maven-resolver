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
package org.eclipse.aether.impl;

import org.eclipse.aether.RepositorySystemSession;

/**
 * Evaluates update policies.
 *
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 * @provisional This type is provisional and can be changed, moved or removed without prior notice.
 */
public interface UpdatePolicyAnalyzer {

    /**
     * Returns the policy with the shorter update interval.
     *
     * @param session The repository system session during which the request is made, must not be {@code null}.
     * @param policy1 A policy to compare, may be {@code null}.
     * @param policy2 A policy to compare, may be {@code null}.
     * @return The policy with the shorter update interval.
     */
    String getEffectiveUpdatePolicy(RepositorySystemSession session, String policy1, String policy2);

    /**
     * Determines whether the specified modification timestamp satisfies the freshness constraint expressed by the given
     * update policy.
     *
     * @param session The repository system session during which the check is made, must not be {@code null}.
     * @param lastModified The timestamp to check against the update policy.
     * @param policy The update policy, may be {@code null}.
     * @return {@code true} if the specified timestamp is older than acceptable by the update policy, {@code false}
     *         otherwise.
     */
    boolean isUpdatedRequired(RepositorySystemSession session, long lastModified, String policy);
}
