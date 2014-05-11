/*******************************************************************************
 * Copyright (c) 2012, 2014 Sonatype, Inc.
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
 * @provisional This type is provisional and can be changed, moved or removed without prior notice.
 */
public interface OfflineController
{

    /**
     * Determines whether the specified repository is accessible if the system was in offline mode. A simple
     * implementation might unconditionally throw {@link RepositoryOfflineException} to block all remote repository
     * access when in offline mode. More sophisticated implementations might inspect
     * {@link RepositorySystemSession#getConfigProperties() configuration properties} of the session to check for some
     * kind of whitelist that allows certain remote repositories even when offline. At any rate, the session's current
     * {@link RepositorySystemSession#isOffline() offline state} is irrelevant to the outcome of the check.
     * 
     * @param session The repository session during which the check is made, must not be {@code null}.
     * @param repository The remote repository to check for offline access, must not be {@code null}.
     * @throws RepositoryOfflineException If the repository is not accessible in offline mode. If the method returns
     *             normally, the repository is considered accessible even in offline mode.
     * @see RepositorySystemSession#isOffline()
     */
    void checkOffline( RepositorySystemSession session, RemoteRepository repository )
        throws RepositoryOfflineException;

}
