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

    DefaultDependencyCycle( NodeStack nodes, int cycleEntry, Dependency dependency )
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
