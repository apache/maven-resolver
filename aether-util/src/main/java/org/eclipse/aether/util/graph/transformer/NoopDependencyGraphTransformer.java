/*******************************************************************************
 * Copyright (c) 2010, 2012 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.util.graph.transformer;

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.collection.DependencyGraphTransformationContext;
import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.graph.DependencyNode;

/**
 * A dependency graph transformer that does not perform any changes on its input.
 */
public final class NoopDependencyGraphTransformer
    implements DependencyGraphTransformer
{

    /**
     * A ready-made instance of this dependency graph transformer which can safely be reused throughout an entire
     * application regardless of multi-threading.
     */
    public static final DependencyGraphTransformer INSTANCE = new NoopDependencyGraphTransformer();

    /**
     * Creates a new instance of this graph transformer. Usually, {@link #INSTANCE} should be used instead.
     */
    public NoopDependencyGraphTransformer()
    {
    }

    public DependencyNode transformGraph( DependencyNode node, DependencyGraphTransformationContext context )
        throws RepositoryException
    {
        return node;
    }

}
