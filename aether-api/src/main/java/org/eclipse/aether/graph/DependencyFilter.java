/*******************************************************************************
 * Copyright (c) 2010, 2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.graph;

import java.util.List;

/**
 * A filter to include/exclude dependency nodes during other operations.
 */
public interface DependencyFilter
{

    /**
     * Indicates whether the specified dependency node shall be included or excluded.
     * 
     * @param node The dependency node to filter, must not be {@code null}.
     * @param parents The (read-only) chain of parent nodes that leads to the node to be filtered, must not be
     *            {@code null}. Iterating this (possibly empty) list walks up the dependency graph towards the root
     *            node, i.e. the immediate parent node (if any) is the first node in the list. The size of the list also
     *            denotes the zero-based depth of the filtered node.
     * @return {@code true} to include the dependency node, {@code false} to exclude it.
     */
    boolean accept( DependencyNode node, List<DependencyNode> parents );

}
