/*******************************************************************************
 * Copyright (c) 2012 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.impl;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.transfer.RepositoryOfflineException;

/**
 * Determines whether a remote repository is accessible in offline mode.
 * 
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 */
public interface OfflineController
{

    /**
     * Determines whether the specified repository is accessible according to the offline policy of the given session.
     * 
     * @param session The repository session during which the check is made, must not be {@code null}
     * @param repository The remote repository to check for accessibility, must not be {@code null}.
     * @throws RepositoryOfflineException If the session forbids access to the repository.
     */
    void checkOffline( RepositorySystemSession session, RemoteRepository repository )
        throws RepositoryOfflineException;

}
