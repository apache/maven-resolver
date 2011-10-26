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
package org.eclipse.aether.util.filter;

import java.util.List;

import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.util.filter.NotDependencyFilter;

/**
 * A dependency filter that negates another filter.
 */
public final class NotDependencyFilter
    implements DependencyFilter
{

    private final DependencyFilter filter;

    /**
     * Creates a new filter negatint the specified filter.
     * 
     * @param filter The filter to negate, must not be {@code null}.
     */
    public NotDependencyFilter( DependencyFilter filter )
    {
        if ( filter == null )
        {
            throw new IllegalArgumentException( "no filter specified" );
        }
        this.filter = filter;
    }

    public boolean accept( DependencyNode node, List<DependencyNode> parents )
    {
        return !filter.accept( node, parents );
    }

    @Override
    public boolean equals( Object obj )
    {
        if ( this == obj )
        {
            return true;
        }

        if ( obj == null || !getClass().equals( obj.getClass() ) )
        {
            return false;
        }

        NotDependencyFilter that = (NotDependencyFilter) obj;

        return this.filter.equals( that.filter );
    }

    @Override
    public int hashCode()
    {
        int hash = getClass().hashCode();
        hash = hash * 31 + filter.hashCode();
        return hash;
    }

}
