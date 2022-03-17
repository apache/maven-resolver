package org.eclipse.aether.internal.impl.collect.bf;

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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyCycle;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;

/**
 * @see BfDependencyCollector
 */
final class BfDependencyCycle
    implements DependencyCycle
{

    private final List<Dependency> dependencies;

    private final int cycleEntry;

    BfDependencyCycle( List<DependencyNode> nodes, int cycleEntry, Dependency dependency )
    {
        // skip root node unless it actually has a dependency or is considered the cycle entry (due to its label)
        int offset = ( cycleEntry > 0 && nodes.get( 0 ).getDependency() == null ) ? 1 : 0;
        Dependency[] dependencies = new Dependency[nodes.size() - offset + 1];
        for ( int i = 0, n = dependencies.length - 1; i < n; i++ )
        {
            DependencyNode node = nodes.get( i + offset );
            dependencies[i] = node.getDependency();
            // when cycle starts at root artifact as opposed to root dependency, synthesize a dependency
            if ( dependencies[i] == null )
            {
                dependencies[i] = new Dependency( node.getArtifact(), null );
            }
        }
        dependencies[dependencies.length - 1] = dependency;
        this.dependencies = Collections.unmodifiableList( Arrays.asList( dependencies ) );
        this.cycleEntry = cycleEntry;
    }

    public List<Dependency> getPrecedingDependencies()
    {
        return dependencies.subList( 0, cycleEntry );
    }

    public List<Dependency> getCyclicDependencies()
    {
        return dependencies.subList( cycleEntry, dependencies.size() );
    }

    /**
     * Searches for a node associated with the given artifact. A version of the artifact is not considered during the
     * search.
     *
     * @param nodes a list representing single path in the dependency graph. First element is the root.
     * @param artifact to find among the parent nodes.
     * @return the index of the node furthest from the root and associated with the given artifact, or {@literal -1} if
     * there is no such node.
     */
    public static int find( List<DependencyNode> nodes, Artifact artifact )
    {

        for ( int i = nodes.size() - 1; i >= 0; i-- )
        {
            DependencyNode node = nodes.get( i );

            Artifact a = node.getArtifact();
            if ( a == null )
            {
                break;
            }

            if ( !a.getArtifactId().equals( artifact.getArtifactId() ) )
            {
                continue;
            }
            if ( !a.getGroupId().equals( artifact.getGroupId() ) )
            {
                continue;
            }
            if ( !a.getExtension().equals( artifact.getExtension() ) )
            {
                continue;
            }
            if ( !a.getClassifier().equals( artifact.getClassifier() ) )
            {
                continue;
            }
            /*
             * NOTE: While a:1 and a:2 are technically different artifacts, we want to consider the path a:2 -> b:2 ->
             * a:1 a cycle in the current context. The artifacts themselves might not form a cycle but their producing
             * projects surely do. Furthermore, conflict resolution will always have to consider a:1 a loser (otherwise
             * its ancestor a:2 would get pruned and so would a:1) so there is no point in building the sub graph of
             * a:1.
             */

            return i;
        }

        return -1;
    }

    @Override
    public String toString()
    {
        StringBuilder buffer = new StringBuilder( 256 );
        int i = 0;
        for ( Dependency dependency : dependencies )
        {
            if ( i++ > 0 )
            {
                buffer.append( " -> " );
            }
            buffer.append( ArtifactIdUtils.toVersionlessId( dependency.getArtifact() ) );
        }
        return buffer.toString();
    }

}
