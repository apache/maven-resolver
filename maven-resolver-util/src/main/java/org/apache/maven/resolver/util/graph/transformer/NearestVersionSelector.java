package org.apache.maven.resolver.util.graph.transformer;

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
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

import org.apache.maven.resolver.RepositoryException;
import org.apache.maven.resolver.collection.UnsolvableVersionConflictException;
import org.apache.maven.resolver.graph.DependencyFilter;
import org.apache.maven.resolver.graph.DependencyNode;
import org.apache.maven.resolver.util.graph.visitor.PathRecordingDependencyVisitor;
import org.apache.maven.resolver.version.Version;
import org.apache.maven.resolver.version.VersionConstraint;

import static java.util.Objects.requireNonNull;

/**
 * A version selector for use with {@link ConflictResolver} that resolves version conflicts using a nearest-wins
 * strategy. If there is no single node that satisfies all encountered version ranges, the selector will fail.
 */
public final class NearestVersionSelector
    extends ConflictResolver.VersionSelector
{

    /**
     * Creates a new instance of this version selector.
     */
    public NearestVersionSelector()
    {
    }

    @Override
    public void selectVersion( ConflictResolver.ConflictContext context )
        throws RepositoryException
    {
        ConflictGroup group = new ConflictGroup();
        for ( ConflictResolver.ConflictItem item : context.getItems() )
        {
            DependencyNode node = item.getNode();
            VersionConstraint constraint = node.getVersionConstraint();

            boolean backtrack = false;
            boolean hardConstraint = constraint.getRange() != null;

            if ( hardConstraint )
            {
                if ( group.constraints.add( constraint ) )
                {
                    if ( group.winner != null && !constraint.containsVersion( group.winner.getNode().getVersion() ) )
                    {
                        backtrack = true;
                    }
                }
            }

            if ( isAcceptable( group, node.getVersion() ) )
            {
                group.candidates.add( item );

                if ( backtrack )
                {
                    backtrack( group, context );
                }
                else if ( group.winner == null || isNearer( item, group.winner ) )
                {
                    group.winner = item;
                }
            }
            else if ( backtrack )
            {
                backtrack( group, context );
            }
        }
        context.setWinner( group.winner );
    }

    private void backtrack( ConflictGroup group, ConflictResolver.ConflictContext context )
        throws UnsolvableVersionConflictException
    {
        group.winner = null;

        for ( Iterator<ConflictResolver.ConflictItem> it = group.candidates.iterator(); it.hasNext(); )
        {
            ConflictResolver.ConflictItem candidate = it.next();

            if ( !isAcceptable( group, candidate.getNode().getVersion() ) )
            {
                it.remove();
            }
            else if ( group.winner == null || isNearer( candidate, group.winner ) )
            {
                group.winner = candidate;
            }
        }

        if ( group.winner == null )
        {
            throw newFailure( context );
        }
    }

    private boolean isAcceptable( ConflictGroup group, Version version )
    {
        for ( VersionConstraint constraint : group.constraints )
        {
            if ( !constraint.containsVersion( version ) )
            {
                return false;
            }
        }
        return true;
    }

    private boolean isNearer( ConflictResolver.ConflictItem item1, ConflictResolver.ConflictItem item2 )
    {
        if ( item1.isSibling( item2 ) )
        {
            return item1.getNode().getVersion().compareTo( item2.getNode().getVersion() ) > 0;
        }
        else
        {
            return item1.getDepth() < item2.getDepth();
        }
    }

    private UnsolvableVersionConflictException newFailure( final ConflictResolver.ConflictContext context )
    {
        DependencyFilter filter = ( node, parents ) ->
        {
            requireNonNull( node, "node cannot be null" );
            requireNonNull( parents, "parents cannot be null" );
            return context.isIncluded( node );
        };
        PathRecordingDependencyVisitor visitor = new PathRecordingDependencyVisitor( filter );
        context.getRoot().accept( visitor );
        return new UnsolvableVersionConflictException( visitor.getPaths() );
    }

    static final class ConflictGroup
    {

        final Collection<VersionConstraint> constraints;

        final Collection<ConflictResolver.ConflictItem> candidates;

        ConflictResolver.ConflictItem winner;

        ConflictGroup()
        {
            constraints = new HashSet<>();
            candidates = new ArrayList<>( 64 );
        }

        @Override
        public String toString()
        {
            return String.valueOf( winner );
        }

    }

}
