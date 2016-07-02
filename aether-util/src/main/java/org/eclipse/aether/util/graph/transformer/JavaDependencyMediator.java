package org.eclipse.aether.util.graph.transformer;

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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.collection.DependencyGraphTransformationContext;
import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.util.artifact.JavaScopes;

/**
 * A {@code DependencyGraphTransformer} applying the Maven dependency mediation mechanism.
 *
 * @author Christian Schulte
 * @since 1.2
 * @see <a href="http://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Dependency_Scope">Introduction to the Dependency Mechanism</a>
 */
public final class JavaDependencyMediator
    implements DependencyGraphTransformer
{

    @Override
    public DependencyNode transformGraph( final DependencyNode node,
                                          final DependencyGraphTransformationContext context )
        throws RepositoryException
    {
        DependencyNode result = node;
        result = this.removeNonTransitiveNodes( result );
        result = this.updateTransitiveScopes( result );
        result = this.removeDuplicateNodes( result, new HashMap<ConflictMarker.Key, DependencyNode>( 1024 ) );
        return result;
    }

    private DependencyNode removeNonTransitiveNodes( final DependencyNode parent )
    {
        final String parentScope = parent.getDependency() != null
                                       ? parent.getDependency().getScope() != null
                                             && parent.getDependency().getScope().length() >= 0
                                             ? parent.getDependency().getScope()
                                             : JavaScopes.COMPILE
                                       : null;

        for ( final Iterator<DependencyNode> it = parent.getChildren().iterator(); it.hasNext(); )
        {
            final DependencyNode child = it.next();

            recurse:
            {
                if ( parentScope != null )
                {
                    String childScope = child.getDependency().getScope() != null
                                            && child.getDependency().getScope().length() >= 0
                                            ? child.getDependency().getScope()
                                            : JavaScopes.COMPILE;

                    // Provided and test scopes are non-transitive.
                    // Optional dependencies are non-transitive.
                    if ( JavaScopes.PROVIDED.equals( childScope )
                             || JavaScopes.TEST.equals( childScope )
                             || child.getDependency().isOptional() )
                    {
                        it.remove();
                        break recurse;
                    }
                }

                this.removeNonTransitiveNodes( child );
            }
        }

        return parent;
    }

    private DependencyNode updateTransitiveScopes( final DependencyNode parent )
    {
        final String parentScope = parent.getDependency() != null
                                       ? parent.getDependency().getScope() != null
                                             && parent.getDependency().getScope().length() >= 0
                                             ? parent.getDependency().getScope()
                                             : JavaScopes.COMPILE
                                       : null;

        for ( final DependencyNode child : parent.getChildren() )
        {
            if ( parentScope != null )
            {
                String childScope = child.getDependency().getScope() != null
                                        && child.getDependency().getScope().length() >= 0
                                        ? child.getDependency().getScope()
                                        : JavaScopes.COMPILE;

                if ( ( child.getManagedBits() & DependencyNode.MANAGED_SCOPE ) == 0 )
                {
                    // Non-managed child scopes are updated according to the table in the "Dependency Scope" section
                    // of the "Introduction to the Dependency Mechanism" document.

                    if ( JavaScopes.PROVIDED.equals( parentScope ) )
                    {
                        // Compile and runtime become provided.
                        if ( JavaScopes.COMPILE.equals( childScope )
                                 || JavaScopes.RUNTIME.equals( childScope ) )
                        {
                            childScope = JavaScopes.PROVIDED;
                            child.setScope( childScope );
                        }
                    }
                    else if ( JavaScopes.RUNTIME.equals( parentScope ) )
                    {
                        // Compile becomes runtime.
                        if ( JavaScopes.COMPILE.equals( childScope ) )
                        {
                            childScope = JavaScopes.RUNTIME;
                            child.setScope( childScope );
                        }
                    }
                    else if ( JavaScopes.TEST.equals( parentScope ) )
                    {
                        // Compile and runtime become test.
                        if ( JavaScopes.COMPILE.equals( childScope )
                                 || JavaScopes.RUNTIME.equals( childScope ) )
                        {
                            childScope = JavaScopes.TEST;
                            child.setScope( childScope );
                        }
                    }
                }
            }

            this.updateTransitiveScopes( child );
        }

        return parent;
    }

    private DependencyNode removeDuplicateNodes( final DependencyNode candidate,
                                                 final Map<ConflictMarker.Key, DependencyNode> nodes )
    {
        recurse:
        {
            if ( candidate.getDependency() != null )
            {
                final ConflictMarker.Key candidateKey = new ConflictMarker.Key( candidate.getArtifact() );
                final DependencyNode existing = nodes.get( candidateKey );

                if ( existing == null )
                {
                    // Candidate is selected.
                    nodes.put( candidateKey, candidate );
                }
                else if ( this.isPreferredNode( existing, candidate ) )
                {
                    // Candidate is selected.
                    nodes.put( candidateKey, candidate );
                    existing.getParent().getChildren().remove( existing );
                }
                else
                {
                    // Candidate is not selected.
                    candidate.getParent().getChildren().remove( candidate );
                    // No need to inspect children.
                    break recurse;
                }
            }

            for ( final DependencyNode child : new ArrayList<DependencyNode>( candidate.getChildren() ) )
            {
                this.removeDuplicateNodes( child, nodes );
            }
        }

        return candidate;
    }

    private boolean isPreferredNode( final DependencyNode existing, final DependencyNode candidate )
    {
        boolean preferred = false;
        final Integer p1 = SCOPE_PRIORITIES.get( existing.getDependency().getScope() );
        final Integer p2 = SCOPE_PRIORITIES.get( candidate.getDependency().getScope() );
        final boolean candidateScopePrioritized = p1 != null && p2 != null ? p2 > p1 : false;
        final boolean equalPriority = existing.getDependency().getScope().
            equals( candidate.getDependency().getScope() );

        if ( candidate.getDepth() < existing.getDepth() )
        {
            preferred = equalPriority || candidateScopePrioritized;
        }
        else if ( candidate.getDepth() == existing.getDepth() )
        {
            preferred = !equalPriority && candidateScopePrioritized;
        }

        return preferred;
    }

    private static final Map<String, Integer> SCOPE_PRIORITIES = new HashMap<String, Integer>();

    static
    {
        SCOPE_PRIORITIES.put( JavaScopes.PROVIDED, 0 );
        SCOPE_PRIORITIES.put( JavaScopes.TEST, 0 );
        SCOPE_PRIORITIES.put( JavaScopes.RUNTIME, 1 );
        SCOPE_PRIORITIES.put( JavaScopes.COMPILE, 2 );
        SCOPE_PRIORITIES.put( JavaScopes.SYSTEM, 3 );
    }

}
