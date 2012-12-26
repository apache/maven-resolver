/*******************************************************************************
 * Copyright (c) 2010, 2012 Sonatype, Inc.
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
import org.eclipse.aether.graph.Dependency;
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

    public DependencyNode find( Artifact artifact )
    {
        for ( int i = size - 1; i >= 0; i-- )
        {
            DependencyNode node = nodes[i];

            Dependency dependency = node.getDependency();
            if ( dependency == null )
            {
                break;
            }

            Artifact a = dependency.getArtifact();
            if ( !a.getArtifactId().equals( artifact.getArtifactId() ) )
            {
                continue;
            }
            if ( !a.getGroupId().equals( artifact.getGroupId() ) )
            {
                continue;
            }
            if ( !a.getBaseVersion().equals( artifact.getBaseVersion() ) )
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

            return node;
        }

        return null;
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
