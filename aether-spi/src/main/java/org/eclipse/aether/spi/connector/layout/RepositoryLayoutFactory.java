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
package org.eclipse.aether.spi.connector.layout;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * A factory to obtain repository layouts. A repository layout is responsible to map an artifact or some metadata to a
 * URI relative to the repository root where the resource resides. When the repository system needs to access a given
 * remote repository, it iterates the registered factories in descending order of their priority and calls
 * {@link #newInstance(RepositorySystemSession, RemoteRepository)} on them. The first layout returned by a factory will
 * then be used for transferring artifacts/metadata.
 */
public interface RepositoryLayoutFactory
{

    /**
     * Tries to create a repository layout for the specified remote repository. Typically, a factory will inspect
     * {@link RemoteRepository#getContentType()} to determine whether it can handle a repository.
     * 
     * @param session The repository system session from which to configure the layout, must not be {@code null}.
     * @param repository The remote repository to create a layout for, must not be {@code null}.
     * @return The layout for the given repository, never {@code null}.
     * @throws NoRepositoryLayoutException If the factory cannot create a repository layout for the specified remote
     *             repository.
     */
    RepositoryLayout newInstance( RepositorySystemSession session, RemoteRepository repository )
        throws NoRepositoryLayoutException;

    /**
     * The priority of this factory. When multiple factories can handle a given repository, factories with higher
     * priority are preferred over those with lower priority.
     * 
     * @return The priority of this factory.
     */
    float getPriority();

}
