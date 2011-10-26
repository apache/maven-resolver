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
 * A dependency selector that always includes or excludes dependencies.
 */
public final class StaticDependencySelector
    implements DependencySelector
{

    private final boolean select;

    /**
     * Creates a new selector with the specified selection behavior.
     * 
     * @param select {@code true} to select all dependencies, {@code false} to exclude all dependencies.
     */
    public StaticDependencySelector( boolean select )
    {
        this.select = select;
    }

    public boolean selectDependency( Dependency dependency )
    {
        return select;
    }

    public DependencySelector deriveChildSelector( DependencyCollectionContext context )
    {
        return this;
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

        StaticDependencySelector that = (StaticDependencySelector) obj;
        return select == that.select;
    }

    @Override
    public int hashCode()
    {
        int hash = getClass().hashCode();
        hash = hash * 31 + ( select ? 1 : 0 );
        return hash;
    }

}
