package org.eclipse.aether.internal.impl.collect;

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

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.eclipse.aether.util.graph.visitor.CloningDependencyVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Skip resolve a node if depth is deeper, then reconcile the result by resolving the dependency conflicts.
 */
public class DependencyResolveReconciler
{

    private static final Logger LOGGER = LoggerFactory.getLogger( DependencyResolveReconciler.class );

    /**
     * Cache maven dependency resolve result by depth artifact -> result
     */
    private final HashMap<Artifact, DependencyResolveResult> nodesWithDepth;

    /**
     * Track the nodes that have been skipped to resolve for later reconciling
     */
    private final LinkedHashSet<DependencyResolveSkip> skippedNodes;

    public DependencyResolveReconciler()
    {
        this.nodesWithDepth = new HashMap<>( 256 );
        this.skippedNodes = new LinkedHashSet<>();
    }

    /**
     * Cache the node with children, parents path & depth information
     *
     * @param node
     * @param parents
     */
    public void cacheChildrenWithDepth( DependencyNode node, List<DependencyNode> parents )
    {
        int depth = parents.size() + 1;
        Artifact artifact = node.getArtifact();
        List<DependencyNode> children = node.getChildren();
        nodesWithDepth.put( artifact, new DependencyResolveResult( children, parents, depth ) );
    }


    /**
     * Return a candidate node if the depth of current node is deeper than the cached one
     *
     * @param current
     * @param depth
     * @return
     */
    public CacheResult findCandidateWithLowerDepth( DependencyNode current, int depth )
    {
        DependencyResolveResult result = nodesWithDepth.get( current.getArtifact() );
        if ( result != null && result.depth <= depth )
        {
            CacheResult cache = new CacheResult();
            cache.dependencyNodes = result.children;
            cache.parentPathsOfCandidateLowerDepth = result.parentPaths;
            return cache;
        }

        return null;
    }

    /**
     * Record a node that has been skipped by another node
     *
     * @param current      current dependency node
     * @param key          the graph key
     * @param dependencies children of current dependency node
     * @param parents      parents of current dependency node
     * @param skippedBy    parents of the node that the skipped node is reusing
     */
    public void addSkip( DependencyNode current, Object key, List<Dependency> dependencies,
                         List<DependencyNode> parents, List<DependencyNode> skippedBy )
    {
        if ( dependencies != null && dependencies.size() > 0 )
        {
            DependencyResolveSkip skip =
                    new DependencyResolveSkip( current, (DataPool.GraphKey) key, dependencies, parents, skippedBy,
                            parents.size() + 1 );
            skippedNodes.add( skip );
        }
    }

    /**
     * Find all skipped Nodes that need to be reconciled.
     *
     * @return
     */
    public Collection<DependencyResolveSkip> getNodesToReconcile( RepositorySystemSession session,
                                                                  CollectResult result )
            throws DependencyCollectionException
    {
        long start = System.nanoTime();

        DefaultRepositorySystemSession cloned = new DefaultRepositorySystemSession( session );
        // set as verbose so that the winner will be recorded in the DependencyNode.getData
        cloned.setConfigProperty( ConflictResolver.CONFIG_PROP_VERBOSE, true );
        DependencyGraphTransformer transformer = session.getDependencyGraphTransformer();
        if ( transformer == null )
        {
            return Collections.emptyList();
        }

        //cloned root node so it won't affect original root node
        CloningDependencyVisitor vis = new CloningDependencyVisitor();
        DependencyNode root = result.getRoot();
        root.accept( vis );
        root = vis.getRootNode();

        try
        {
            DefaultDependencyGraphTransformationContext context =
                    new DefaultDependencyGraphTransformationContext( cloned );
            root = transformer.transformGraph( root, context );
        }
        catch ( RepositoryException e )
        {
            result.addException( e );
        }

        if ( !result.getExceptions().isEmpty() )
        {
            throw new DependencyCollectionException( result );
        }

        //transform the cloned root node to find out conflicts and nodes we want to reconcile
        ReconcilingClonedGraphVisitor reconciler = new ReconcilingClonedGraphVisitor(
                skippedNodes, vis.getClonedNodes() );
        root.accept( reconciler );

        Set<DependencyResolveSkip> toReconcile = reconciler.toReconcile;
        LOGGER.debug( "Skipped resolving {} nodes and decided to reconcile {} nodes to solve dependency conflicts",
                skippedNodes.size(), toReconcile.size() );
        LOGGER.debug( "Finished to compute the nodes required to be reconciled in: {} ",
                ( System.nanoTime() - start ) );

        if ( !toReconcile.isEmpty() )
        {
            removeConflictedNodes( toReconcile, reconciler.conflicts );
        }

        return toReconcile;
    }

    /**
     * Remove all cached nodes that has any conflict losers in parent paths from nodesWithDepth to make sure it won't
     * reuse the incorrect result when reconciling the given nodes.
     *
     * @param toReconcile
     * @param conflicts
     */
    private void removeConflictedNodes(
            Set<DependencyResolveSkip> toReconcile,
            Set<Conflict> conflicts )
    {
        //remove cache that belongs to the skipped nodes to be reconciled
        Set<Artifact> toRemove = toReconcile.stream().map( r -> r.node.getArtifact() ).collect( Collectors.toSet() );

        //remove cache if the parent path inherits from a invalid node
        Set<Artifact> invalidArtifacts = conflicts.stream().map( c -> c.artifact ).collect( Collectors.toSet() );
        invalidArtifacts.addAll( toRemove );
        Set<Artifact> keys = nodesWithDepth.keySet();
        for ( Artifact key : keys )
        {
            List<DependencyNode> parents = nodesWithDepth.get( key ).parentPaths;
            for ( DependencyNode p : parents )
            {
                if ( invalidArtifacts.contains( p.getArtifact() ) )
                {
                    toRemove.add( key );
                    break;
                }
            }
        }

        toRemove.forEach( r ->
        {
            nodesWithDepth.remove( r );
            LOGGER.trace( "Removed conflicted node from cache: {} ", r );
        } );
    }


    /**
     * Find out all nodes need to be reconciled: the nodes were skipped by other nodes but are actually selected. Note:
     * this visitor accepts the transformed graph based on the cloned root node.
     */
    static class ReconcilingClonedGraphVisitor
            implements DependencyVisitor
    {

        /**
         * Cloned Node -> Original Node
         */
        final Map<DependencyNode, DependencyNode> reverseClonedNodes;
        final Map<DependencyNode, DependencyResolveSkip> skippedNodes;
        final Set<DependencyResolveSkip> toReconcile;

        /**
         * Conflicts of dependencies
         */
        final Set<Conflict> conflicts;

        ReconcilingClonedGraphVisitor(
                LinkedHashSet<DependencyResolveSkip> skippedNodes,
                Map<DependencyNode, DependencyNode> clonedNodeMap )
        {
            this.toReconcile = new LinkedHashSet<>();
            this.conflicts = new LinkedHashSet<>();

            this.skippedNodes = skippedNodes.stream().collect(
                    Collectors.toMap( x -> x.node, x -> x ) );

            Map<DependencyNode, DependencyNode> reverseMap = new HashMap<>();
            clonedNodeMap.forEach( ( key, value ) -> reverseMap.put( value, key ) );
            this.reverseClonedNodes = reverseMap;
        }


        public boolean visitEnter( DependencyNode node )
        {
            // the cloned node after transformation
            DependencyNode clonedNode = node;
            //the original node (before transformation) corresponding to the cloned node
            DependencyNode origin = this.reverseClonedNodes.get( clonedNode );
            DependencyNode winner = (DependencyNode) clonedNode.getData().get( ConflictResolver.NODE_DATA_WINNER );
            // winner null means the node is actually selected, not a conflict loser
            if ( winner == null )
            {
                /*
                 * Cases when children is empty
                 * 1) skipped node：skipped by other nodes but we need to reconcile
                 *    as it is selected in the cloned dependency graph
                 * 2) discarded node: original node has children but current node (after transformation) has no children
                 *    Note: children has been removed by ConflictResolver.removeLosers after transformation
                 */
                if ( clonedNode.getChildren().size() == 0 && origin.getChildren().size() == 0 )
                {
                    if ( skippedNodes.containsKey( origin ) )
                    {
                        toReconcile.add( skippedNodes.get( origin ) );
                    }
                }
            }
            else
            {
                if ( !ArtifactIdUtils.equalsId( clonedNode.getArtifact(), winner.getArtifact() ) )
                {
                    Conflict conflict = new Conflict( clonedNode.getArtifact() );
                    conflict.conflictedWith = winner.getArtifact();
                    conflicts.add( conflict );
                }
            }
            return true;
        }

        public boolean visitLeave( DependencyNode node )
        {
            return true;
        }
    }

    static class DependencyResolveSkip
    {
        int depth;
        DependencyNode node;
        DataPool.GraphKey graphKey;
        List<Dependency> dependencies;
        List<DependencyNode> parentPathsOfCurrentNode;
        List<DependencyNode> parentPathsOfCandidateLowerDepth;
        private final int hashCode;

        DependencyResolveSkip( DependencyNode node, DataPool.GraphKey graphKey, List<Dependency> dependencies,
                               List<DependencyNode> parents, List<DependencyNode> skippedBy, int depth )
        {
            this.node = node;
            this.graphKey = graphKey;
            this.dependencies = dependencies;
            this.parentPathsOfCurrentNode = parents;
            this.parentPathsOfCandidateLowerDepth = skippedBy;
            this.depth = depth;
            hashCode = Objects.hash( this.node );
        }

        @Override
        public boolean equals( Object o )
        {
            if ( this == o )
            {
                return true;
            }
            if ( o == null || getClass() != o.getClass() )
            {
                return false;
            }
            DependencyResolveSkip that = (DependencyResolveSkip) o;
            return Objects.equals( node, that.node );
        }

        @Override
        public int hashCode()
        {
            return hashCode;
        }

        @Override
        public String toString()
        {
            return "{"
                    + "node="
                    + node.getArtifact()
                    + ", parentPathsOfCandidate="
                    + parentPathsOfCandidateLowerDepth
                    + ", parentPathsOfCurrentNode="
                    + parentPathsOfCurrentNode
                    + ", depth="
                    + depth
                    + '}';
        }
    }


    static final class DependencyResolveResult
    {
        final List<DependencyNode> children;
        final List<DependencyNode> parentPaths;
        int depth;

        DependencyResolveResult( List<DependencyNode> children, List<DependencyNode> parents, int depth )
        {
            this.children = children;
            this.parentPaths = parents;
            this.depth = depth;
        }
    }


    static class CacheResult
    {
        List<DependencyNode> dependencyNodes;
        List<DependencyNode> parentPathsOfCandidateLowerDepth;
    }


    static class Conflict
    {
        Artifact artifact;
        Artifact conflictedWith;
        private final int hashCode;

        Conflict( Artifact artifact )
        {
            this.artifact = artifact;
            hashCode = Objects.hash( this.artifact );
        }

        @Override
        public boolean equals( Object obj )
        {
            if ( obj == this )
            {
                return true;
            }
            else if ( !( obj instanceof Conflict ) )
            {
                return false;
            }
            Conflict that = (Conflict) obj;
            return Objects.equals( artifact, that.artifact );
        }

        @Override
        public int hashCode()
        {
            return hashCode;
        }

        @Override
        public String toString()
        {
            return "{"
                    + "artifact="
                    + artifact
                    + ( conflictedWith == null ? "" : ", conflicted with: " + conflictedWith )
                    + '}';
        }
    }


}
