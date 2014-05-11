/*******************************************************************************
 * Copyright (c) 2014 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.util.graph.traverser;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencyTraverser;
import org.eclipse.aether.graph.Dependency;

/**
 * A dependency traverser that combines zero or more other traversers using a logical {@code AND}. The resulting
 * traverser enables processing of child dependencies if and only if all constituent traversers request traversal.
 */
public final class AndDependencyTraverser
    implements DependencyTraverser
{

    private final Set<? extends DependencyTraverser> traversers;

    private int hashCode;

    /**
     * Creates a new traverser from the specified traversers. Prefer
     * {@link #newInstance(DependencyTraverser, DependencyTraverser)} if any of the input traversers might be
     * {@code null}.
     * 
     * @param traversers The traversers to combine, may be {@code null} but must not contain {@code null} elements.
     */
    public AndDependencyTraverser( DependencyTraverser... traversers )
    {
        if ( traversers != null && traversers.length > 0 )
        {
            this.traversers = new LinkedHashSet<DependencyTraverser>( Arrays.asList( traversers ) );
        }
        else
        {
            this.traversers = Collections.emptySet();
        }
    }

    /**
     * Creates a new traverser from the specified traversers.
     * 
     * @param traversers The traversers to combine, may be {@code null} but must not contain {@code null} elements.
     */
    public AndDependencyTraverser( Collection<? extends DependencyTraverser> traversers )
    {
        if ( traversers != null && !traversers.isEmpty() )
        {
            this.traversers = new LinkedHashSet<DependencyTraverser>( traversers );
        }
        else
        {
            this.traversers = Collections.emptySet();
        }
    }

    private AndDependencyTraverser( Set<DependencyTraverser> traversers )
    {
        if ( traversers != null && !traversers.isEmpty() )
        {
            this.traversers = traversers;
        }
        else
        {
            this.traversers = Collections.emptySet();
        }
    }

    /**
     * Creates a new traverser from the specified traversers.
     * 
     * @param traverser1 The first traverser to combine, may be {@code null}.
     * @param traverser2 The second traverser to combine, may be {@code null}.
     * @return The combined traverser or {@code null} if both traversers were {@code null}.
     */
    public static DependencyTraverser newInstance( DependencyTraverser traverser1, DependencyTraverser traverser2 )
    {
        if ( traverser1 == null )
        {
            return traverser2;
        }
        else if ( traverser2 == null || traverser2.equals( traverser1 ) )
        {
            return traverser1;
        }
        return new AndDependencyTraverser( traverser1, traverser2 );
    }

    public boolean traverseDependency( Dependency dependency )
    {
        for ( DependencyTraverser traverser : traversers )
        {
            if ( !traverser.traverseDependency( dependency ) )
            {
                return false;
            }
        }
        return true;
    }

    public DependencyTraverser deriveChildTraverser( DependencyCollectionContext context )
    {
        int seen = 0;
        Set<DependencyTraverser> childTraversers = null;

        for ( DependencyTraverser traverser : traversers )
        {
            DependencyTraverser childTraverser = traverser.deriveChildTraverser( context );
            if ( childTraversers != null )
            {
                if ( childTraverser != null )
                {
                    childTraversers.add( childTraverser );
                }
            }
            else if ( traverser != childTraverser )
            {
                childTraversers = new LinkedHashSet<DependencyTraverser>();
                if ( seen > 0 )
                {
                    for ( DependencyTraverser s : traversers )
                    {
                        if ( childTraversers.size() >= seen )
                        {
                            break;
                        }
                        childTraversers.add( s );
                    }
                }
                if ( childTraverser != null )
                {
                    childTraversers.add( childTraverser );
                }
            }
            else
            {
                seen++;
            }
        }

        if ( childTraversers == null )
        {
            return this;
        }
        if ( childTraversers.size() <= 1 )
        {
            if ( childTraversers.isEmpty() )
            {
                return null;
            }
            return childTraversers.iterator().next();
        }
        return new AndDependencyTraverser( childTraversers );
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

        AndDependencyTraverser that = (AndDependencyTraverser) obj;
        return traversers.equals( that.traversers );
    }

    @Override
    public int hashCode()
    {
        if ( hashCode == 0 )
        {
            int hash = 17;
            hash = hash * 31 + traversers.hashCode();
            hashCode = hash;
        }
        return hashCode;
    }

}
