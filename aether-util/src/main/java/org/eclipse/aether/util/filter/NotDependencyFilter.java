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
package org.eclipse.aether.util.filter;

import java.util.List;

import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;

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
