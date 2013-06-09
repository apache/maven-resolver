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
import org.eclipse.aether.transfer.NoRepositoryLayoutException;

/**
 * Retrieves a repository layout from the installed layout factories.
 * 
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 */
public interface RepositoryLayoutProvider
{

    /**
     * Tries to retrieve a repository layout for the specified remote repository.
     * 
     * @param session The repository system session from which to configure the layout, must not be {@code null}.
     * @param repository The remote repository to create a layout for, must not be {@code null}.
     * @return The layout for the given repository, never {@code null}.
     * @throws NoRepositoryLayoutException If none of the installed layout factories can provide a repository layout for
     *             the specified remote repository.
     */
    RepositoryLayout newRepositoryLayout( RepositorySystemSession session, RemoteRepository repository )
        throws NoRepositoryLayoutException;

}
