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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.util.filter.ScopeDependencyFilter;

/**
 * A dependency filter based on dependency scopes. <em>Note:</em> This filter does not assume any relationships between
 * the scopes. In particular, the filter is not aware of scopes that logically include other scopes.
 * 
 * @see Dependency#getScope()
 */
public final class ScopeDependencyFilter
    implements DependencyFilter
{

    private final Set<String> included = new HashSet<String>();

    private final Set<String> excluded = new HashSet<String>();

    /**
     * Creates a new filter using the specified includes and excludes.
     * 
     * @param included The set of scopes to include, may be {@code null} or empty to include any scope.
     * @param excluded The set of scopes to exclude, may be {@code null} or empty to exclude no scope.
     */
    public ScopeDependencyFilter( Collection<String> included, Collection<String> excluded )
    {
        if ( included != null )
        {
            this.included.addAll( included );
        }
        if ( excluded != null )
        {
            this.excluded.addAll( excluded );
        }
    }

    /**
     * Creates a new filter using the specified excludes.
     * 
     * @param excluded The set of scopes to exclude, may be {@code null} or empty to exclude no scope.
     */
    public ScopeDependencyFilter( String... excluded )
    {
        if ( excluded != null )
        {
            this.excluded.addAll( Arrays.asList( excluded ) );
        }
    }

    public boolean accept( DependencyNode node, List<DependencyNode> parents )
    {
        Dependency dependency = node.getDependency();

        if ( dependency == null )
        {
            return true;
        }

        String scope = node.getDependency().getScope();
        return ( included.isEmpty() || included.contains( scope ) )
            && ( excluded.isEmpty() || !excluded.contains( scope ) );
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

        ScopeDependencyFilter that = (ScopeDependencyFilter) obj;

        return this.included.equals( that.included ) && this.excluded.equals( that.excluded );
    }

    @Override
    public int hashCode()
    {
        int hash = 17;
        hash = hash * 31 + included.hashCode();
        hash = hash * 31 + excluded.hashCode();
        return hash;
    }

}
