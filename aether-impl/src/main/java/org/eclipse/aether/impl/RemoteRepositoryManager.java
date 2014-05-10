/*******************************************************************************
 * Copyright (c) 2010, 2014 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.impl;

import java.util.List;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;

/**
 * Helps dealing with remote repository definitions.
 * 
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 * @provisional This type is provisional and can be changed, moved or removed without prior notice.
 */
public interface RemoteRepositoryManager
{

    /**
     * Aggregates repository definitions by merging duplicate repositories and optionally applies mirror, proxy and
     * authentication settings from the supplied session.
     * 
     * @param session The repository session during which the repositories will be accessed, must not be {@code null}.
     * @param dominantRepositories The current list of remote repositories to merge the new definitions into, must not
     *            be {@code null}.
     * @param recessiveRepositories The remote repositories to merge into the existing list, must not be {@code null}.
     * @param recessiveIsRaw {@code true} if the recessive repository definitions have not yet been subjected to mirror,
     *            proxy and authentication settings, {@code false} otherwise.
     * @return The aggregated list of remote repositories, never {@code null}.
     * @see RepositorySystemSession#getMirrorSelector()
     * @see RepositorySystemSession#getProxySelector()
     * @see RepositorySystemSession#getAuthenticationSelector()
     */
    List<RemoteRepository> aggregateRepositories( RepositorySystemSession session,
                                                  List<RemoteRepository> dominantRepositories,
                                                  List<RemoteRepository> recessiveRepositories, boolean recessiveIsRaw );

    /**
     * Gets the effective repository policy for the specified remote repository by merging the applicable
     * snapshot/release policy of the repository with global settings from the supplied session.
     * 
     * @param session The repository session during which the repository will be accessed, must not be {@code null}.
     * @param repository The remote repository to determine the effective policy for, must not be {@code null}.
     * @param releases {@code true} if the policy for release artifacts needs to be considered, {@code false} if not.
     * @param snapshots {@code true} if the policy for snapshot artifacts needs to be considered, {@code false} if not.
     * @return The effective repository policy, never {@code null}.
     * @see RepositorySystemSession#getChecksumPolicy()
     * @see RepositorySystemSession#getUpdatePolicy()
     */
    RepositoryPolicy getPolicy( RepositorySystemSession session, RemoteRepository repository, boolean releases,
                                boolean snapshots );

}
