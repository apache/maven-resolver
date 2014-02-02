/*******************************************************************************
 * Copyright (c) 2013, 2014 Sonatype, Inc.
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
 * A cycle within a dependency graph, that is a sequence of dependencies d_1, d_2, ..., d_n where d_1 and d_n have the
 * same versionless coordinates. In more practical terms, a cycle occurs when a project directly or indirectly depends
 * on its own output artifact.
 * 
 * @noimplement This interface is not intended to be implemented by clients.
 * @noextend This interface is not intended to be extended by clients.
 */
public interface DependencyCycle
{

    /**
     * Gets the dependencies that lead to the first dependency on the cycle, starting from the root of the dependency
     * graph.
     * 
     * @return The (read-only) sequence of dependencies that precedes the cycle in the graph, potentially empty but
     *         never {@code null}.
     */
    List<Dependency> getPrecedingDependencies();

    /**
     * Gets the dependencies that actually form the cycle. For example, a -&gt; b -&gt; c -&gt; a, i.e. the last
     * dependency in this sequence duplicates the first element and closes the cycle. Hence the length of the cycle is
     * the size of the returned sequence minus 1.
     * 
     * @return The (read-only) sequence of dependencies that forms the cycle, never {@code null}.
     */
    List<Dependency> getCyclicDependencies();

}
