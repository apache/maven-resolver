/*******************************************************************************
 * Copyright (c) 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
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
public interface ChecksumPolicyProvider
{

    /**
     * Retrieves the checksum policy with the specified identifier for use on the given remote resource.
     * 
     * @param session The repository system session during which the request is made, must not be {@code null}.
     * @param repository The repository hosting the resource being transferred, must not be {@code null}.
     * @param resource The transfer resource on which the policy will be applied, must not be {@code null}.
     * @param policy The identifier of the policy to apply, must not be {@code null}.
     * @return The policy to apply or {@code null} if checksums should be ignored.
     */
    ChecksumPolicy newChecksumPolicy( RepositorySystemSession session, RemoteRepository repository,
                                      TransferResource resource, String policy );

    /**
     * Returns the least strict policy. A checksum policy is said to be less strict than another policy if it would
     * accept a downloaded resource in all cases where the other policy would reject the resource.
     * 
     * @param session The repository system session during which the request is made, must not be {@code null}.
     * @param policy1 A policy to compare, must not be {@code null}.
     * @param policy2 A policy to compare, must not be {@code null}.
     * @return The least strict policy among the two input policies.
     */
    String getEffectiveChecksumPolicy( RepositorySystemSession session, String policy1, String policy2 );

}
