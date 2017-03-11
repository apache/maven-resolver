package org.eclipse.aether.util.graph.selector;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.TreeSet;

import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.Dependency;

/**
 * A dependency selector that filters transitive dependencies based on their scope. Direct dependencies are always
 * included regardless of their scope. <em>Note:</em> This filter does not assume any relationships between the scopes.
 * In particular, the filter is not aware of scopes that logically include other scopes.
 *
 * @see Dependency#getScope()
 */
public final class ScopeDependencySelector
    implements DependencySelector
{

    private final int depth;

    private final Collection<String> included;

    private final Collection<String> excluded;

    /**
     * Creates a new selector using the specified includes and excludes.
     *
     * @param included The set of scopes to include, may be {@code null} or empty to include any scope.
     * @param excluded The set of scopes to exclude, may be {@code null} or empty to exclude no scope.
     */
    public ScopeDependencySelector( Collection<String> included, Collection<String> excluded )
    {
        this.depth = 0;
        this.included = clone( included );
        this.excluded = clone( excluded );
    }

    private static Collection<String> clone( Collection<String> scopes )
    {
        Collection<String> copy;
        if ( scopes == null || scopes.isEmpty() )
        {
            // checking for null is faster than isEmpty()
            copy = null;
        }
        else
        {
            copy = new HashSet<>( scopes );
            if ( copy.size() <= 2 )
            {
                // contains() is faster for smallish array (sorted for equals()!)
                copy = new ArrayList<>( new TreeSet<>( copy ) );
            }
        }
        return copy;
    }

    /**
     * Creates a new selector using the specified excludes.
     *
     * @param excluded The set of scopes to exclude, may be {@code null} or empty to exclude no scope.
     */
    public ScopeDependencySelector( String... excluded )
    {
        this( null, ( excluded != null ) ? Arrays.asList( excluded ) : null );
    }

    private ScopeDependencySelector( int depth, Collection<String> included, Collection<String> excluded )
    {
        this.depth = depth;
        this.included = included;
        this.excluded = excluded;
    }

    public boolean selectDependency( Dependency dependency )
    {
        return depth < 2
                   || ( ( included == null || included.contains( dependency.getScope() ) )
                        && ( excluded == null || !excluded.contains( dependency.getScope() ) ) );
    }

    public DependencySelector deriveChildSelector( DependencyCollectionContext context )
    {
        return depth >= 2 ? this : new ScopeDependencySelector( depth + 1, included, excluded );
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

        ScopeDependencySelector that = (ScopeDependencySelector) obj;
        return depth == that.depth && Objects.equals( included, that.included )
                && Objects.equals( excluded, that.excluded );
    }

    @Override
    public int hashCode()
    {
        int hash = 17;
        hash = hash * 31 + depth;
        hash = hash * 31 + ( included != null ? included.hashCode() : 0 );
        hash = hash * 31 + ( excluded != null ? excluded.hashCode() : 0 );
        return hash;
    }

}
