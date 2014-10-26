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
package org.eclipse.aether.collection;

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.graph.DependencyNode;

/**
 * Transforms a given dependency graph.
 * <p>
 * <strong>Note:</strong> Implementations must be stateless.
 * <p>
 * <em>Warning:</em> Dependency graphs may generally contain cycles. As such a graph transformer that cannot assume for
 * sure that cycles have already been eliminated must gracefully handle cyclic graphs, e.g. guard against infinite
 * recursion.
 * 
 * @see org.eclipse.aether.RepositorySystemSession#getDependencyGraphTransformer()
 */
public interface DependencyGraphTransformer
{

    /**
     * Transforms the dependency graph denoted by the specified root node. The transformer may directly change the
     * provided input graph or create a new graph, the former is recommended for performance reasons.
     * 
     * @param node The root node of the (possibly cyclic!) graph to transform, must not be {@code null}.
     * @param context The graph transformation context, must not be {@code null}.
     * @return The result graph of the transformation, never {@code null}.
     * @throws RepositoryException If the transformation failed.
     */
    DependencyNode transformGraph( DependencyNode node, DependencyGraphTransformationContext context )
        throws RepositoryException;

}
