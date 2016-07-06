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
import java.util.Collection;
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
 * @see <a href="http://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html">Introduction to the Dependency Mechanism</a>
 */
public final class JavaDependencyMediator
    implements DependencyGraphTransformer
{

    private static final Map<String, Integer> APPLICATION_SCOPE_PRIORITIES = new HashMap<String, Integer>( 5 );

    private static final Map<String, Integer> TEST_SCOPE_PRIORITIES = new HashMap<String, Integer>( 5 );

    static
    {
        APPLICATION_SCOPE_PRIORITIES.put( JavaScopes.TEST, 0 );
        APPLICATION_SCOPE_PRIORITIES.put( JavaScopes.RUNTIME, 1 );
        APPLICATION_SCOPE_PRIORITIES.put( JavaScopes.PROVIDED, 2 );
        APPLICATION_SCOPE_PRIORITIES.put( JavaScopes.COMPILE, 3 );
        APPLICATION_SCOPE_PRIORITIES.put( JavaScopes.SYSTEM, 4 );

        TEST_SCOPE_PRIORITIES.put( JavaScopes.RUNTIME, 0 );
        TEST_SCOPE_PRIORITIES.put( JavaScopes.PROVIDED, 1 );
        TEST_SCOPE_PRIORITIES.put( JavaScopes.COMPILE, 2 );
        TEST_SCOPE_PRIORITIES.put( JavaScopes.TEST, 3 );
        TEST_SCOPE_PRIORITIES.put( JavaScopes.SYSTEM, 4 );
    }

    /**
     * Application scope nodes are prioritized over non application scope nodes.
     */
    public static final int APPLICATION_SCOPE_PRIORITIZATION = 1 << 1;

    /**
     * Test scope nodes are prioritized over non test scope nodes.
     */
    public static final int TEST_SCOPE_PRIORITIZATION = 1 << 2;

    /**
     * Nearest wins only strategy. No scopes are prioritized.
     */
    public static final int NO_PRIORITIZATION = 1 << 3;

    /**
     * The prioritization to apply.
     */
    private final int prioritization;

    /**
     * Creates a new {@code DependencyGraphTransformer}.
     *
     * @param prioritization The prioritization to apply.
     *
     * @see #APPLICATION_SCOPE_PRIORITIZATION
     * @see #TEST_SCOPE_PRIORITIZATION
     * @see #NO_PRIORITIZATION
     */
    public JavaDependencyMediator( final int prioritization )
    {
        super();
        this.prioritization = prioritization;
    }

    @Override
    public DependencyNode transformGraph( final DependencyNode node,
                                          final DependencyGraphTransformationContext context )
        throws RepositoryException
    {
        DependencyNode result = node;
        result = this.removeNonTransitiveNodes( result );
        result = this.updateTransitiveScopes( result );

        for ( ;; )
        {
            if ( this.removeDuplicateNodes( result, result, new HashMap<ConflictMarker.Key, DependencyNode>( 8192 ),
                                            new HashMap<DependencyNode, DependencyNode>( 8192 ) ) )
            {
                break;
            }
        }

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

    private boolean removeDuplicateNodes( final DependencyNode rootNode,
                                          final DependencyNode candidateNode,
                                          final Map<ConflictMarker.Key, DependencyNode> winnerNodes,
                                          final Map<DependencyNode, DependencyNode> looserNodes )
    {
        boolean restart = false;

        recurse:
        {
            if ( candidateNode.getDependency() != null )
            {
                final ConflictMarker.Key candidateKey = new ConflictMarker.Key( candidateNode.getArtifact() );
                final DependencyNode winnerNode = winnerNodes.get( candidateKey );

                if ( winnerNode == null )
                {
                    // Conflict not yet seen. Candidate is selected.
                    winnerNodes.put( candidateKey, candidateNode );
                }
                else if ( this.isPreferredNode( winnerNode, candidateNode ) )
                {
                    // Conflict already seen. Candidate is preferred.
                    winnerNodes.put( candidateKey, candidateNode );
                    looserNodes.put( candidateNode, winnerNode );

                    if ( winnerNode.getParent() != null )
                    {
                        winnerNode.getParent().getChildren().remove( winnerNode );
                    }
                    else
                    {
                        rootNode.getChildren().remove( winnerNode );
                    }

                    final DependencyNode winningChild = getWinningChild( winnerNode, winnerNodes.values() );

                    if ( winningChild != null )
                    {
                        // The node eliminated by the current candidate node contains a child node which has been
                        // selected the winner in a previous iteration. As that winner is eliminated in this iteration,
                        // the former looser needs to be re-added and the whole transformation re-started (undo and
                        // restart). No need to maintain the maps here because they are thrown away when restarting.
                        // Doing it for completeness, however.
                        final DependencyNode looserNode = looserNodes.remove( winningChild ); // Can be get().

                        if ( looserNode != null )
                        {
                            if ( looserNode.getParent() != null )
                            {
                                if ( !looserNode.getParent().getChildren().contains( looserNode ) )
                                {
                                    looserNode.getParent().getChildren().add( looserNode );
                                }
                            }
                            else if ( !rootNode.getChildren().contains( looserNode ) )
                            {
                                rootNode.getChildren().add( looserNode );
                            }

                            // Not needed, but...
                            final DependencyNode winner =
                                winnerNodes.remove( new ConflictMarker.Key( looserNode.getArtifact() ) );

                            if ( winner != null )
                            {
                                looserNodes.remove( winner );
                            }
                        }

                        restart = true;
                        break recurse;
                    }
                }
                else
                {
                    // Conflict already seen. Candidate is not preferred.
                    looserNodes.put( winnerNode, candidateNode );
                    if ( candidateNode.getParent() != null )
                    {
                        candidateNode.getParent().getChildren().remove( candidateNode );
                    }
                    else
                    {
                        rootNode.getChildren().remove( candidateNode );
                    }
                    // No need to inspect children.
                    break recurse;
                }
            }

            for ( final DependencyNode child : new ArrayList<DependencyNode>( candidateNode.getChildren() ) )
            {
                if ( !this.removeDuplicateNodes( rootNode, child, winnerNodes, looserNodes ) )
                {
                    restart = true;
                    break recurse;
                }
            }
        }

        return !restart;
    }

    private boolean isPreferredNode( final DependencyNode existing, final DependencyNode candidate )
    {
        boolean preferred = false;
        Integer p1 = null;
        Integer p2 = null;
        boolean prioritize = true;

        if ( this.prioritization == APPLICATION_SCOPE_PRIORITIZATION )
        {
            p1 = APPLICATION_SCOPE_PRIORITIES.get( existing.getDependency().getScope() );
            p2 = APPLICATION_SCOPE_PRIORITIES.get( candidate.getDependency().getScope() );
        }
        else if ( this.prioritization == TEST_SCOPE_PRIORITIZATION )
        {
            p1 = TEST_SCOPE_PRIORITIES.get( existing.getDependency().getScope() );
            p2 = TEST_SCOPE_PRIORITIES.get( candidate.getDependency().getScope() );
        }
        else if ( this.prioritization == NO_PRIORITIZATION )
        {
            prioritize = false;
        }
        else
        {
            throw new AssertionError( this.prioritization );
        }

        final Boolean candidateScopePrioritized = p1 != null && p2 != null ? p2 > p1 : false;
        final boolean equalPriority =
            existing.getDependency().getScope().equals( candidate.getDependency().getScope() );

        if ( candidate.getDepth() < existing.getDepth() )
        {
            preferred = !prioritize || equalPriority || candidateScopePrioritized;
        }
        else if ( candidate.getDepth() == existing.getDepth() )
        {
            preferred = prioritize && !equalPriority && candidateScopePrioritized;
        }

        return preferred;
    }

    private static DependencyNode getWinningChild( final DependencyNode node,
                                                   final Collection<DependencyNode> winnerNodes )
    {
        DependencyNode winningChild = winnerNodes.contains( node )
                                          ? node
                                          : null;

        if ( winningChild == null )
        {
            for ( final DependencyNode child : node.getChildren() )
            {
                winningChild = getWinningChild( child, winnerNodes );

                if ( winningChild != null )
                {
                    break;
                }
            }
        }

        return winningChild;
    }

}
