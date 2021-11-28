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

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Skip resolve if depth is deeper, then reconcile the result by resolving the version conflicts.
 */
public class DependencyResolveReconciler
{

    private static final Logger LOGGER = LoggerFactory.getLogger( DependencyResolveReconciler.class );

    /**
     * Cache maven dependency resolve result by depth gav -> result
     */
    private final HashMap<String, DependencyResolveResult> nodesWithDepth;

    /**
     * Track the nodes that have been skipped to resolve for later reconciling
     */
    private final LinkedHashSet<DependencyResolveSkip> skippedNodes;

    public DependencyResolveReconciler()
    {
        this.nodesWithDepth = new HashMap<>( 256 );
        this.skippedNodes = new LinkedHashSet<>();
    }

    public void cacheChildrenWithDepth( DependencyNode node, List<DependencyNode> parents )
    {
        int depth = parents.size() + 1;
        Artifact artifact = node.getArtifact();
        List<DependencyNode> children = node.getChildren();
        nodesWithDepth.put( gav( artifact ), new DependencyResolveResult( children, parents, depth ) );
    }


    public CacheResult findCache( DependencyNode current, List<DependencyNode> cacheByGraphKey, int depth )
    {
        DependencyResolveResult result = nodesWithDepth.get( gav( current.getArtifact() ) );
        if ( result != null )
        {
            if ( result.depth <= depth )
            {
                CacheResult cache = new CacheResult();
                cache.dependencyNodes = result.children;
                cache.candidateWithLowerDepth = true;
                cache.parentPathsOfCandidateLowerDepth = result.parentPaths;
                return cache;
            }
        }

        return null;
    }

    /**
     * Record the nodes has been skipped
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
        ConflictWinnerFinder winnerFinder = new ConflictWinnerFinder();
        Collection<ConflictWinnerFinder.Conflict> conflicts = winnerFinder.getVersionConflicts( session, result );
        if ( conflicts == null || conflicts.isEmpty() )
        {
            return Collections.emptyList();
        }

        //find all nodes that contain conflict nodes.
        HashSet<String> conflictedNodes = new HashSet<>( findNodeHasConflictsInParentPaths( conflicts ) );

        //Evict node from cache if the cached node is based on parent paths contains conflicting nodes
        for ( String gav : conflictedNodes )
        {
            nodesWithDepth.remove( gav );
        }

        //get the least set of nodes need to reconcile
        Set<DependencyResolveSkip> filteredSkips = filterSkippedNodes( conflictedNodes );
        if ( filteredSkips.size() == 0 )
        {
            return Collections.emptyList();
        }

        LOGGER.debug( "Skipped resolving {} nodes, and reconciled {} nodes to solve {} dependency conflicts.",
                skippedNodes.size(), filteredSkips.size(), conflicts.size() );
        return filteredSkips;
    }


    private LinkedHashSet<DependencyResolveSkip> filterSkippedNodes( Set<String> conflictedNodes )
    {
        LinkedHashSet<DependencyResolveSkip> skips = new LinkedHashSet<>();
        for ( DependencyResolveSkip skip : skippedNodes )
        {
            //SKIP: skipped node's GAV differs with the one evicted from cache
            if ( !conflictedNodes.contains( gav( skip.node.getArtifact() ) ) )
            {
                continue;
            }

            //SKIP: the node is skipped as expected when the parent path includes all segments of skipped path
            boolean selfContained = true;
            for ( DependencyNode parentNode : skip.parentPathsOfCandidateLowerDepth )
            {
                if ( !skip.parentPathsOfCurrentNode.contains( parentNode ) )
                {
                    selfContained = false;
                    break;
                }
            }

            if ( selfContained )
            {
                continue;
            }

            skips.add( skip );
        }

        //group nodes by artifact
        HashMap<Artifact, ArrayList<DependencyResolveSkip>> reconcileNodes = new HashMap<>();
        for ( DependencyResolveSkip skip : skips )
        {
            reconcileNodes.computeIfAbsent( skip.node.getArtifact(), k -> new ArrayList<>() ).add( skip );
        }

        //only reconcile the node with lowest depth
        LinkedHashSet<DependencyResolveSkip> filteredSkips = new LinkedHashSet<>();
        for ( Artifact key : reconcileNodes.keySet() )
        {
            Collection<DependencyResolveSkip> col = reconcileNodes.get( key );
            List<DependencyResolveSkip> list = new ArrayList<>( col );
            list.sort( Comparator.comparingInt( o -> o.depth ) );
            LOGGER.debug( "Reconcile: {}", list.get( 0 ) );
            filteredSkips.add( list.get( 0 ) );
        }
        return filteredSkips;
    }


    private List<String> findNodeHasConflictsInParentPaths( Collection<ConflictWinnerFinder.Conflict> conflicts )
    {
        Set<String> keys = nodesWithDepth.keySet();
        Set<String> conflictIds = new HashSet<>();
        for ( ConflictWinnerFinder.Conflict c : conflicts )
        {
            conflictIds.add( c.gav );
        }

        List<String> results = new ArrayList<>();
        for ( String key : keys )
        {
            List<DependencyNode> parents = nodesWithDepth.get( key ).parentPaths;
            for ( DependencyNode p : parents )
            {
                if ( p.getArtifact() != null )
                {
                    if ( conflictIds.contains( gav( p.getArtifact() ) ) )
                    {
                        results.add( key );
                        break;
                    }
                }
            }
        }

        return results;
    }

    static String ga( Artifact artifact )
    {
        return artifact.getGroupId() + ":" + artifact.getArtifactId();
    }

    static String gav( Artifact artifact )
    {
        return ga( artifact ) + ":" + artifact.getVersion();
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
        private final List<DependencyNode> children;
        private final List<DependencyNode> parentPaths;
        private int depth;

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
        boolean candidateWithSameKey;
        boolean candidateWithLowerDepth;
    }


}
