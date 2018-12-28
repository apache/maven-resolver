package org.eclipse.aether.util.graph.visitor;

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

import static java.util.Objects.requireNonNull;

import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;

/**
 * A dependency visitor that delegates to another visitor if nodes match a filter. Note that in case of a mismatching
 * node, the children of that node are still visisted and presented to the filter.
 */
public final class FilteringDependencyVisitor
    implements DependencyVisitor
{

    private final DependencyFilter filter;

    private final DependencyVisitor visitor;

    private final Stack<Boolean> accepts;

    private final Stack<DependencyNode> parents;

    /**
     * Creates a new visitor that delegates traversal of nodes matching the given filter to the specified visitor.
     *
     * @param visitor The visitor to delegate to, must not be {@code null}.
     * @param filter The filter to apply, may be {@code null} to not filter.
     */
    public FilteringDependencyVisitor( DependencyVisitor visitor, DependencyFilter filter )
    {
        this.visitor = requireNonNull( visitor, "dependency visitor delegate cannot be null" );
        this.filter = filter;
        this.accepts = new Stack<>();
        this.parents = new Stack<>();
    }

    /**
     * Gets the visitor to which this visitor delegates to.
     * 
     * @return The visitor being delegated to, never {@code null}.
     */
    public DependencyVisitor getVisitor()
    {
        return visitor;
    }

    /**
     * Gets the filter being applied before delegation.
     * 
     * @return The filter being applied or {@code null} if none.
     */
    public DependencyFilter getFilter()
    {
        return filter;
    }

    public boolean visitEnter( DependencyNode node )
    {
        boolean accept = filter == null || filter.accept( node, parents );

        accepts.push( accept );

        parents.push( node );

        if ( accept )
        {
            return visitor.visitEnter( node );
        }
        else
        {
            return true;
        }
    }

    public boolean visitLeave( DependencyNode node )
    {
        parents.pop();

        Boolean accept = accepts.pop();

        if ( accept )
        {
            return visitor.visitLeave( node );
        }
        else
        {
            return true;
        }
    }

}
