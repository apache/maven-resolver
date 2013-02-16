/*******************************************************************************
 * Copyright (c) 2010, 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.spi.localrepo;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;

/**
 * A factory to create managers for the local repository. A local repository manager needs to keep track of artifacts
 * and metadata and manage access. When the repository system needs a repository manager for a given local repository,
 * it iterates the registered factories in descending order of their priority and calls
 * {@link #newInstance(LocalRepository)} on them. The first manager returned by a factory will then be used for the
 * local repository.
 */
public interface LocalRepositoryManagerFactory
{

    /**
     * Tries to create a repository manager for the specified local repository. The distinguishing property of a local
     * repository is its {@link LocalRepository#getContentType() type}, which may for example denote the used directory
     * structure.
     * 
     * @param session The repository system session from which to configure the manager, must not be {@code null}.
     * @param repository The local repository to create a manager for, must not be {@code null}.
     * @return The manager for the given repository, never {@code null}.
     * @throws NoLocalRepositoryManagerException If the factory cannot create a manager for the specified local
     *             repository.
     */
    LocalRepositoryManager newInstance( RepositorySystemSession session, LocalRepository repository )
        throws NoLocalRepositoryManagerException;

    /**
     * The priority of this factory. Factories with higher priority are preferred over those with lower priority.
     * 
     * @return The priority of this factory.
     */
    float getPriority();

}
