/*******************************************************************************
 * Copyright (c) 2010, 2011 Sonatype, Inc.
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

/* * @see DefaultDependencyCollector
 */
class EdgeStack
{

    private GraphEdge[] edges = new GraphEdge[64];

    private int size;

    public GraphEdge top()
    {
        if ( size <= 0 )
        {
            throw new IllegalStateException( "stack empty" );
        }
        return edges[size - 1];
    }

    public void push( GraphEdge edge )
    {
        if ( size >= edges.length )
        {
            GraphEdge[] tmp = new GraphEdge[size + 64];
            System.arraycopy( edges, 0, tmp, 0, edges.length );
            edges = tmp;
        }
        edges[size++] = edge;
    }

    public void pop()
    {
        if ( size <= 0 )
        {
            throw new IllegalStateException( "stack empty" );
        }
        size--;
    }

    public GraphEdge find( Artifact artifact )
    {
        for ( int i = size - 1; i >= 0; i-- )
        {
            GraphEdge edge = edges[i];

            Dependency dependency = edge.getDependency();
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

            return edge;
        }

        return null;
    }

    @Override
    public String toString()
    {
        return Arrays.toString( edges );
    }

}
