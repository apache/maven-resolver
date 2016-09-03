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
