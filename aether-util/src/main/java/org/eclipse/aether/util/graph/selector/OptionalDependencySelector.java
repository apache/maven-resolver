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
package org.eclipse.aether.util.graph.selector;

import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.Dependency;

/**
 * A dependency selector that excludes optional dependencies which occur beyond level one of the dependency graph.
 * 
 * @see Dependency#isOptional()
 */
public final class OptionalDependencySelector
    implements DependencySelector
{

    private final int depth;

    /**
     * Creates a new selector to exclude optional transitive dependencies.
     */
    public OptionalDependencySelector()
    {
        depth = 0;
    }

    private OptionalDependencySelector( int depth )
    {
        this.depth = depth;
    }

    public boolean selectDependency( Dependency dependency )
    {
        return depth < 2 || !dependency.isOptional();
    }

    public DependencySelector deriveChildSelector( DependencyCollectionContext context )
    {
        if ( depth >= 2 )
        {
            return this;
        }

        return new OptionalDependencySelector( depth + 1 );
    }

    @Override
    public boolean equals( Object obj )
    {
        if ( this == obj )
        {
            return true;
        }
        else if ( null == obj || !getClass().equals( obj.getClass() ) )
        {
            return false;
        }

        OptionalDependencySelector that = (OptionalDependencySelector) obj;
        return depth == that.depth;
    }

    @Override
    public int hashCode()
    {
        int hash = getClass().hashCode();
        hash = hash * 31 + depth;
        return hash;
    }

}
