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

import java.util.Arrays;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.DependencyNode;

/**
 * @see DefaultDependencyCollector
 */
final class NodeStack
{

    private final DependencyNode[] nodes;

    NodeStack( DependencyNode node )
    {
        this( new DependencyNode[1] );
        nodes[0] = node;
    }

    private NodeStack( DependencyNode[] nodes )
    {
        this.nodes = nodes;
    }

    public DependencyNode top()
    {
        if ( nodes.length == 0 )
        {
            throw new IllegalStateException( "stack empty" );
        }
        return nodes[nodes.length - 1];
    }

    public NodeStack push( DependencyNode node )
    {
        DependencyNode[] copyNodes = new DependencyNode[nodes.length + 1];
        System.arraycopy( nodes, 0, copyNodes, 0, nodes.length );
        copyNodes[copyNodes.length - 1] = node;
        return new NodeStack( copyNodes );
    }

    public int find( Artifact artifact )
    {
        for ( int i = nodes.length - 1; i >= 0; i-- )
        {
            DependencyNode node = nodes[i];

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

    public int size()
    {
        return nodes.length;
    }

    public DependencyNode get( int index )
    {
        return nodes[index];
    }

    @Override
    public String toString()
    {
        return Arrays.toString( nodes );
    }

}
