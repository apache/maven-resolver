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
package org.eclipse.aether.collection;

import java.util.Iterator;

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionConstraint;

/**
 * Decides which versions matching a version range should actually be considered for the dependency graph.
 * Implementations must be stateless. <em>Note:</em> This hook is called from a hot spot and therefore implementations
 * should pay attention to performance. Among others, implementations should provide a semantic
 * {@link Object#equals(Object) equals()} method.
 * 
 * @see org.eclipse.aether.RepositorySystemSession#getVersionFilter()
 * @see org.eclipse.aether.RepositorySystem#collectDependencies(org.eclipse.aether.RepositorySystemSession,
 *      CollectRequest)
 */
public interface VersionFilter
{

    /**
     * A context used during version filtering to hold relevant data.
     * 
     * @noimplement This interface is not intended to be implemented by clients.
     * @noextend This interface is not intended to be extended by clients.
     */
    interface VersionFilterContext
        extends Iterable<Version>
    {

        /**
         * Gets the repository system session during which the version filtering happens.
         * 
         * @return The repository system session, never {@code null}.
         */
        RepositorySystemSession getSession();

        /**
         * Gets the dependency whose version range is being filtered.
         * 
         * @return The dependency, never {@code null}.
         */
        Dependency getDependency();

        /**
         * Gets the total number of available versions. This count reflects any removals made during version filtering.
         * 
         * @return The total number of available versions.
         */
        int getCount();

        /**
         * Gets an iterator over the available versions of the dependency. The iterator returns versions in ascending
         * order. Use {@link Iterator#remove()} to exclude a version from further consideration in the dependency graph.
         * 
         * @return The iterator of available versions, never {@code null}.
         */
        Iterator<Version> iterator();

        /**
         * Gets the version constraint that was parsed from the dependency's version string.
         * 
         * @return The parsed version constraint, never {@code null}.
         */
        VersionConstraint getVersionConstraint();

        /**
         * Gets the repository from which the specified version was resolved.
         * 
         * @param version The version whose source repository should be retrieved, must not be {@code null}.
         * @return The repository from which the version was resolved or {@code null} if unknown.
         */
        ArtifactRepository getRepository( Version version );

    }

    /**
     * Filters the available versions for a given dependency. Implementations will usually call
     * {@link VersionFilterContext#iterator() context.iterator()} to inspect the available versions and use
     * {@link java.util.Iterator#remove()} to delete unacceptable versions. If no versions remain after all filtering
     * has been performed, the dependency collection process will automatically fail, i.e. implementations need not
     * handle this situation on their own.
     * 
     * @param context The version filter context, must not be {@code null}.
     * @throws RepositoryException If the filtering could not be performed.
     */
    void filterVersions( VersionFilterContext context )
        throws RepositoryException;

    /**
     * Derives a version filter for the specified collection context. The derived filter will be used to handle version
     * ranges encountered in child dependencies of the current node. When calculating the child filter, implementors are
     * strongly advised to simply return the current instance if nothing changed to help save memory.
     * 
     * @param context The dependency collection context, must not be {@code null}.
     * @return The version filter for the target node or {@code null} if versions should not be filtered any more.
     */
    VersionFilter deriveChildFilter( DependencyCollectionContext context );

}
