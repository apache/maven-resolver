/*******************************************************************************
 * Copyright (c) 2010, 2014 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.internal.impl;

import java.util.Arrays;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.DependencyNode;

/**
 * @see DefaultDependencyCollector
 */
final class NodeStack
{

    private DependencyNode[] nodes = new DependencyNode[96];

    private int size;

    public DependencyNode top()
    {
        if ( size <= 0 )
        {
            throw new IllegalStateException( "stack empty" );
        }
        return nodes[size - 1];
    }

    public void push( DependencyNode node )
    {
        if ( size >= nodes.length )
        {
            DependencyNode[] tmp = new DependencyNode[size + 64];
            System.arraycopy( nodes, 0, tmp, 0, nodes.length );
            nodes = tmp;
        }
        nodes[size++] = node;
    }

    public void pop()
    {
        if ( size <= 0 )
        {
            throw new IllegalStateException( "stack empty" );
        }
        size--;
    }

    public int find( Artifact artifact )
    {
        for ( int i = size - 1; i >= 0; i-- )
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
        return size;
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
