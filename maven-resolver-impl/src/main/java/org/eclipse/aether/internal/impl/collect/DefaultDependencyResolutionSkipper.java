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

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.impl.DependencyResolutionSkipper;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

final class DefaultDependencyResolutionSkipper implements DependencyResolutionSkipper
{
    private static final Logger LOGGER = LoggerFactory.getLogger( DefaultDependencyResolutionSkipper.class );

    private Map<DependencyNode, DependencyResolutionResult> results = new LinkedHashMap<>( 256 );
    private CacheManager cacheManager = new CacheManager();
    private CoordinateManager coordinateManager = new CoordinateManager();

    DefaultDependencyResolutionSkipper()
    {
        // enables default constructor
    }

    @Override
    public boolean skipResolution( DependencyNode node, List<DependencyNode> parents )
    {
        DependencyResolutionResult result = new DependencyResolutionResult( node );
        results.put( node, result );

        int depth = parents.size() + 1;
        coordinateManager.createCoordinate( node, depth );

        if ( cacheManager.isVersionConflict( node ) )
        {
            /*
             * Skip resolving version conflict losers (omitted for conflict)
             */
            result.skippedAsVersionConflict = true;
            if ( LOGGER.isTraceEnabled() )
            {
                LOGGER.trace( "Skipped resolving node: {} as version conflict",
                        ArtifactIdUtils.toId( node.getArtifact() ) );
            }
        }
        else if ( cacheManager.isDuplicate( node ) )
        {
            if ( coordinateManager.isLeftmost( node, parents ) )
            {
                /*
                 * Force resolving the node to retain conflict paths when its coordinate is more left than last resolved
                 * This is because Maven picks the widest scope present among conflicting dependencies
                 */
                result.forceResolution = true;
                if ( LOGGER.isTraceEnabled() )
                {
                    LOGGER.trace( "Force resolving node: {} for scope selection",
                            ArtifactIdUtils.toId( node.getArtifact() ) );
                }
            }
            else
            {
                /*
                 * Skip resolving as duplicate (depth deeper, omitted for duplicate)
                 * No need to compare depth as the depth of winner for given artifact is always shallower
                 */
                result.skippedAsDuplicate = true;
                if ( LOGGER.isTraceEnabled() )
                {
                    LOGGER.trace( "Skipped resolving node: {} as duplicate",
                            ArtifactIdUtils.toId( node.getArtifact() ) );
                }
            }
        }
        else
        {
            result.resolve = true;
            if ( LOGGER.isTraceEnabled() )
            {
                LOGGER.trace( "Resolving node: {}",
                        ArtifactIdUtils.toId( node.getArtifact() ) );
            }
        }

        if ( result.toResolve() )
        {
            coordinateManager.updateLeftmost( node );
            return false;
        }

        return true;
    }

    @Override
    public void cache( DependencyNode node, List<DependencyNode> parents )
    {
        boolean parentForceResolution = parents.stream()
                .anyMatch( n -> results.containsKey( n ) && results.get( n ).forceResolution );
        if ( parentForceResolution )
        {
            if ( LOGGER.isTraceEnabled() )
            {
                LOGGER.trace(
                        "Won't cache as node: {} inherits from a force-resolved node and will be omitted for duplicate",
                        ArtifactIdUtils.toId( node.getArtifact() ) );
            }
        }
        else
        {
            cacheManager.cacheWinner( node );
        }
    }

    @Override
    public void report()
    {
        if ( LOGGER.isTraceEnabled() )
        {
            LOGGER.trace( "Skipped {} nodes as duplicate",
                    results.entrySet().stream().filter( n -> n.getValue().skippedAsDuplicate ).count() );
            LOGGER.trace( "Skipped {} nodes as having version conflict",
                    results.entrySet().stream().filter( n -> n.getValue().skippedAsVersionConflict ).count() );
            LOGGER.trace( "Resolved {} nodes",
                    results.entrySet().stream().filter( n -> n.getValue().resolve ).count() );
            LOGGER.trace( "Forced resolving {} nodes for scope selection",
                    results.entrySet().stream().filter( n -> n.getValue().forceResolution ).count() );
        }
    }

    public Map<DependencyNode, DependencyResolutionResult> getResults()
    {
        return results;
    }

    static final class DependencyResolutionResult
    {
        DependencyNode current;
        boolean skippedAsVersionConflict; //omitted for conflict
        boolean skippedAsDuplicate; //omitted for duplicate, depth is deeper
        boolean resolve; //node to resolve (winner node)
        boolean forceResolution; //force resolving (duplicate node) for scope selection

        DependencyResolutionResult( DependencyNode current )
        {
            this.current = current;
        }

        boolean toResolve()
        {
            return resolve || forceResolution;
        }
    }

    static final class CacheManager
    {

        /**
         * artifact -> node
         */
        private final Map<Artifact, DependencyNode> winners = new HashMap<>( 256 );


        /**
         * versionLessId -> Artifact, only cache winners
         */
        private final Map<String, Artifact> winnerGAs = new HashMap<>( 256 );

        boolean isVersionConflict( DependencyNode node )
        {
            String ga = ArtifactIdUtils.toVersionlessId( node.getArtifact() );
            if ( winnerGAs.containsKey( ga ) )
            {
                Artifact result = winnerGAs.get( ga );
                return !node.getArtifact().getVersion().equals( result.getVersion() );
            }

            return false;
        }

        void cacheWinner( DependencyNode node )
        {
            winners.put( node.getArtifact(), node );
            winnerGAs.put( ArtifactIdUtils.toVersionlessId( node.getArtifact() ), node.getArtifact() );
        }

        boolean isDuplicate( DependencyNode node )
        {
            return winners.containsKey( node.getArtifact() );
        }

    }


    static final class CoordinateManager
    {
        private final Map<Integer, AtomicInteger> sequenceGen = new HashMap<>( 256 );

        /**
         * Dependency node -> Coordinate
         */
        private final Map<DependencyNode, Coordinate> coordinateMap = new HashMap<>( 256 );

        /**
         * Leftmost coordinate of given artifact
         */
        private final Map<Artifact, Coordinate> leftmostCoordinates = new HashMap<>( 256 );


        Coordinate getCoordinate( DependencyNode node )
        {
            return coordinateMap.get( node );
        }

        Coordinate createCoordinate( DependencyNode node, int depth )
        {
            int seq = sequenceGen.computeIfAbsent( depth, k -> new AtomicInteger() ).incrementAndGet();
            Coordinate coordinate = new Coordinate( depth, seq );
            coordinateMap.put( node, coordinate );
            return coordinate;
        }

        void updateLeftmost( DependencyNode current )
        {
            leftmostCoordinates.put( current.getArtifact(), getCoordinate( current ) );
        }

        boolean isLeftmost( DependencyNode node, List<DependencyNode> parents )
        {
            Coordinate leftmost = leftmostCoordinates.get( node.getArtifact() );
            if ( leftmost != null && leftmost.depth <= parents.size() )
            {
                DependencyNode sameLevelNode = parents.get( leftmost.depth - 1 );
                if ( getCoordinate( sameLevelNode ).sequence < leftmost.sequence )
                {
                    return true;
                }
            }

            return false;
        }
    }

    static final class Coordinate
    {
        int depth;
        int sequence;

        Coordinate( int depth, int sequence )
        {
            this.depth = depth;
            this.sequence = sequence;
        }

        @Override
        public String toString()
        {
            return "{"
                    + "depth="
                    + depth
                    + ", sequence="
                    + sequence
                    + '}';
        }
    }


}
