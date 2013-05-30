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
package org.eclipse.aether.spi.connector.transport;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * A factory to create transporters. A transporter is responsible for uploads/downloads to/from a remote repository
 * using a particular transport protocol. When the repository system needs a transporter for a given remote repository,
 * it iterates the registered factories in descending order of their priority and calls
 * {@link #newInstance(RepositorySystemSession, RemoteRepository)} on them. The first transporter returned by a factory
 * will then be used for the transfer.
 */
public interface TransporterFactory
{

    /**
     * Tries to create a transporter for the specified remote repository. Typically, a factory will inspect
     * {@link RemoteRepository#getProtocol()} to determine whether it can handle a repository.
     * 
     * @param session The repository system session from which to configure the transporter, must not be {@code null}.
     *            In particular, a transporter should obey the timeouts configured for the session.
     * @param repository The remote repository to create a transporter for, must not be {@code null}.
     * @return The transporter for the given repository, never {@code null}.
     * @throws NoTransporterException If the factory cannot create a transporter for the specified remote repository.
     */
    Transporter newInstance( RepositorySystemSession session, RemoteRepository repository )
        throws NoTransporterException;

    /**
     * The priority of this factory. When multiple factories can handle a given repository, factories with higher
     * priority are preferred over those with lower priority.
     * 
     * @return The priority of this factory.
     */
    float getPriority();

}
