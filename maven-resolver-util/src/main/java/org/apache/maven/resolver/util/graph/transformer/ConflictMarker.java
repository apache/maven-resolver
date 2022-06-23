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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import static java.util.Objects.requireNonNull;

import org.apache.maven.resolver.RepositoryException;
import org.apache.maven.resolver.artifact.Artifact;
import org.apache.maven.resolver.collection.DependencyGraphTransformationContext;
import org.apache.maven.resolver.collection.DependencyGraphTransformer;
import org.apache.maven.resolver.graph.Dependency;
import org.apache.maven.resolver.graph.DependencyNode;

/**
 * A dependency graph transformer that identifies conflicting dependencies. When this transformer has executed, the
 * transformation context holds a {@code Map<DependencyNode, Object>} where dependency nodes that belong to the same
 * conflict group will have an equal conflict identifier. This map is stored using the key
 * {@link TransformationContextKeys#CONFLICT_IDS}.
 */
public final class ConflictMarker
    implements DependencyGraphTransformer
{

    /**
     * After the execution of this method, every DependencyNode with an attached dependency is member of one conflict
     * group.
     * 
     * @see DependencyGraphTransformer#transformGraph(DependencyNode, DependencyGraphTransformationContext)
     */
    public DependencyNode transformGraph( DependencyNode node, DependencyGraphTransformationContext context )
        throws RepositoryException
    {
        requireNonNull( node, "node cannot be null" );
        requireNonNull( context, "context cannot be null" );
        @SuppressWarnings( "unchecked" )
        Map<String, Object> stats = (Map<String, Object>) context.get( TransformationContextKeys.STATS );
        long time1 = System.nanoTime();

        Map<DependencyNode, Object> nodes = new IdentityHashMap<>( 1024 );
        Map<Object, ConflictGroup> groups = new HashMap<>( 1024 );

        analyze( node, nodes, groups, new int[] { 0 } );

        long time2 = System.nanoTime();

        Map<DependencyNode, Object> conflictIds = mark( nodes.keySet(), groups );

        context.put( TransformationContextKeys.CONFLICT_IDS, conflictIds );

        if ( stats != null )
        {
            long time3 = System.nanoTime();
            stats.put( "ConflictMarker.analyzeTime", time2 - time1 );
            stats.put( "ConflictMarker.markTime", time3 - time2 );
            stats.put( "ConflictMarker.nodeCount", nodes.size() );
        }

        return node;
    }

    private void analyze( DependencyNode node, Map<DependencyNode, Object> nodes, Map<Object, ConflictGroup> groups,
                          int[] counter )
    {
        if ( nodes.put( node, Boolean.TRUE ) != null )
        {
            return;
        }

        Set<Object> keys = getKeys( node );
        if ( !keys.isEmpty() )
        {
            ConflictGroup group = null;
            boolean fixMappings = false;

            for ( Object key : keys )
            {
                ConflictGroup g = groups.get( key );

                if ( group != g )
                {
                    if ( group == null )
                    {
                        Set<Object> newKeys = merge( g.keys, keys );
                        if ( newKeys == g.keys )
                        {
                            group = g;
                            break;
                        }
                        else
                        {
                            group = new ConflictGroup( newKeys, counter[0]++ );
                            fixMappings = true;
                        }
                    }
                    else if ( g == null )
                    {
                        fixMappings = true;
                    }
                    else
                    {
                        Set<Object> newKeys = merge( g.keys, group.keys );
                        if ( newKeys == g.keys )
                        {
                            group = g;
                            fixMappings = false;
                            break;
                        }
                        else if ( newKeys != group.keys )
                        {
                            group = new ConflictGroup( newKeys, counter[0]++ );
                            fixMappings = true;
                        }
                    }
                }
            }

            if ( group == null )
            {
                group = new ConflictGroup( keys, counter[0]++ );
                fixMappings = true;
            }
            if ( fixMappings )
            {
                for ( Object key : group.keys )
                {
                    groups.put( key, group );
                }
            }
        }

        for ( DependencyNode child : node.getChildren() )
        {
            analyze( child, nodes, groups, counter );
        }
    }

    private Set<Object> merge( Set<Object> keys1, Set<Object> keys2 )
    {
        int size1 = keys1.size();
        int size2 = keys2.size();

        if ( size1 < size2 )
        {
            if ( keys2.containsAll( keys1 ) )
            {
                return keys2;
            }
        }
        else
        {
            if ( keys1.containsAll( keys2 ) )
            {
                return keys1;
            }
        }

        Set<Object> keys = new HashSet<>();
        keys.addAll( keys1 );
        keys.addAll( keys2 );
        return keys;
    }

    private Set<Object> getKeys( DependencyNode node )
    {
        Set<Object> keys;

        Dependency dependency = node.getDependency();

        if ( dependency == null )
        {
            keys = Collections.emptySet();
        }
        else
        {
            Object key = toKey( dependency.getArtifact() );

            if ( node.getRelocations().isEmpty() && node.getAliases().isEmpty() )
            {
                keys = Collections.singleton( key );
            }
            else
            {
                keys = new HashSet<>();
                keys.add( key );

                for ( Artifact relocation : node.getRelocations() )
                {
                    key = toKey( relocation );
                    keys.add( key );
                }

                for ( Artifact alias : node.getAliases() )
                {
                    key = toKey( alias );
                    keys.add( key );
                }
            }
        }

        return keys;
    }

    private Map<DependencyNode, Object> mark( Collection<DependencyNode> nodes, Map<Object, ConflictGroup> groups )
    {
        Map<DependencyNode, Object> conflictIds = new IdentityHashMap<>( nodes.size() + 1 );

        for ( DependencyNode node : nodes )
        {
            Dependency dependency = node.getDependency();
            if ( dependency != null )
            {
                Object key = toKey( dependency.getArtifact() );
                conflictIds.put( node, groups.get( key ).index );
            }
        }

        return conflictIds;
    }

    private static Object toKey( Artifact artifact )
    {
        return new Key( artifact );
    }

    static class ConflictGroup
    {

        final Set<Object> keys;

        final int index;

        ConflictGroup( Set<Object> keys, int index )
        {
            this.keys = keys;
            this.index = index;
        }

        @Override
        public String toString()
        {
            return String.valueOf( keys );
        }

    }

    static class Key
    {

        private final Artifact artifact;

        Key( Artifact artifact )
        {
            this.artifact = artifact;
        }

        @Override
        public boolean equals( Object obj )
        {
            if ( obj == this )
            {
                return true;
            }
            else if ( !( obj instanceof Key ) )
            {
                return false;
            }
            Key that = (Key) obj;
            return artifact.getArtifactId().equals( that.artifact.getArtifactId() )
                && artifact.getGroupId().equals( that.artifact.getGroupId() )
                && artifact.getExtension().equals( that.artifact.getExtension() )
                && artifact.getClassifier().equals( that.artifact.getClassifier() );
        }

        @Override
        public int hashCode()
        {
            int hash = 17;
            hash = hash * 31 + artifact.getArtifactId().hashCode();
            hash = hash * 31 + artifact.getGroupId().hashCode();
            hash = hash * 31 + artifact.getClassifier().hashCode();
            hash = hash * 31 + artifact.getExtension().hashCode();
            return hash;
        }

        @Override
        public String toString()
        {
            return artifact.getGroupId() + ':' + artifact.getArtifactId() + ':' + artifact.getClassifier() + ':'
                + artifact.getExtension();
        }

    }

}
