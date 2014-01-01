/*******************************************************************************
 * Copyright (c) 2013, 2014 Sonatype, Inc.
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
import java.util.Collections;
import java.util.List;

import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyCycle;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;

/**
 * @see DefaultDependencyCollector
 */
final class DefaultDependencyCycle
    implements DependencyCycle
{

    private final List<Dependency> dependencies;

    private final int cycleEntry;

    public DefaultDependencyCycle( NodeStack nodes, int cycleEntry, Dependency dependency )
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

    @Override
    public String toString()
    {
        StringBuilder buffer = new StringBuilder( 256 );
        for ( int i = 0, n = dependencies.size(); i < n; i++ )
        {
            if ( i > 0 )
            {
                buffer.append( " -> " );
            }
            buffer.append( ArtifactIdUtils.toVersionlessId( dependencies.get( i ).getArtifact() ) );
        }
        return buffer.toString();
    }

}
