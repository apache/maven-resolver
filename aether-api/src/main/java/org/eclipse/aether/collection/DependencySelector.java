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
package org.eclipse.aether.collection;

import org.eclipse.aether.graph.Dependency;

/**
 * Decides what dependencies to include in the dependency graph. Implementations must be stateless. <em>Note:</em> This
 * hook is called from a hot spot and therefore implementations should pay attention to performance. Among others,
 * implementations should provide a semantic {@link Object#equals(Object) equals()} method.
 * 
 * @see org.eclipse.aether.RepositorySystemSession#getDependencySelector()
 * @see org.eclipse.aether.RepositorySystem#collectDependencies(org.eclipse.aether.RepositorySystemSession,
 *      CollectRequest)
 */
public interface DependencySelector
{

    /**
     * Decides whether the specified dependency should be included in the dependency graph.
     * 
     * @param dependency The dependency to check, must not be {@code null}.
     * @return {@code false} if the dependency should be excluded from the children of the current node, {@code true}
     *         otherwise.
     */
    boolean selectDependency( Dependency dependency );

    /**
     * Derives a dependency selector for the specified collection context. When calculating the child selector,
     * implementors are strongly advised to simply return the current instance if nothing changed to help save memory.
     * 
     * @param context The dependency collection context, must not be {@code null}.
     * @return The dependency selector for the target node, must not be {@code null}.
     */
    DependencySelector deriveChildSelector( DependencyCollectionContext context );

}
