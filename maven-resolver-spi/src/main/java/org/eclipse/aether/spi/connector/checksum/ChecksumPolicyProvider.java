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
package org.eclipse.aether.spi.connector.checksum;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.transfer.TransferResource;

/**
 * Assists repository connectors in applying checksum policies to downloaded resources.
 *
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 */
public interface ChecksumPolicyProvider {

    /**
     * Retrieves the checksum policy with the specified identifier for use on the given remote resource.
     *
     * @param session The repository system session during which the request is made, must not be {@code null}.
     * @param repository The repository hosting the resource being transferred, must not be {@code null}.
     * @param resource The transfer resource on which the policy will be applied, must not be {@code null}.
     * @param policy The identifier of the policy to apply, must not be {@code null}.
     * @return The policy to apply or {@code null} if checksums should be ignored.
     */
    ChecksumPolicy newChecksumPolicy(
            RepositorySystemSession session, RemoteRepository repository, TransferResource resource, String policy);

    /**
     * Returns the least strict policy. A checksum policy is said to be less strict than another policy if it would
     * accept a downloaded resource in all cases where the other policy would reject the resource.
     *
     * @param session The repository system session during which the request is made, must not be {@code null}.
     * @param policy1 A policy to compare, must not be {@code null}.
     * @param policy2 A policy to compare, must not be {@code null}.
     * @return The least strict policy among the two input policies.
     */
    String getEffectiveChecksumPolicy(RepositorySystemSession session, String policy1, String policy2);
}
