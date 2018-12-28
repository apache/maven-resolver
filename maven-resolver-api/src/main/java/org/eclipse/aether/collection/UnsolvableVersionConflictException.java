package org.eclipse.aether.collection;

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
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.version.VersionConstraint;

/**
 * Thrown in case of an unsolvable conflict between different version constraints for a dependency.
 */
public class UnsolvableVersionConflictException
    extends RepositoryException
{

    private final transient Collection<String> versions;

    private final transient Collection<? extends List<? extends DependencyNode>> paths;

    /**
     * Creates a new exception with the specified paths to conflicting nodes in the dependency graph.
     * 
     * @param paths The paths to the dependency nodes that participate in the version conflict, may be {@code null}.
     */
    public UnsolvableVersionConflictException( Collection<? extends List<? extends DependencyNode>> paths )
    {
        super( "Could not resolve version conflict among " + toPaths( paths ) );
        if ( paths == null )
        {
            this.paths = Collections.emptyList();
            this.versions = Collections.emptyList();
        }
        else
        {
            this.paths = paths;
            this.versions = new LinkedHashSet<>();
            for ( List<? extends DependencyNode> path : paths )
            {
                VersionConstraint constraint = path.get( path.size() - 1 ).getVersionConstraint();
                if ( constraint != null && constraint.getRange() != null )
                {
                    versions.add( constraint.toString() );
                }
            }
        }
    }

    private static String toPaths( Collection<? extends List<? extends DependencyNode>> paths )
    {
        String result = "";

        if ( paths != null )
        {
            Collection<String> strings = new LinkedHashSet<>();

            for ( List<? extends DependencyNode> path : paths )
            {
                strings.add( toPath( path ) );
            }

            result = strings.toString();
        }

        return result;
    }

    private static String toPath( List<? extends DependencyNode> path )
    {
        StringBuilder buffer = new StringBuilder( 256 );

        for ( Iterator<? extends DependencyNode> it = path.iterator(); it.hasNext(); )
        {
            DependencyNode node = it.next();
            if ( node.getDependency() == null )
            {
                continue;
            }

            Artifact artifact = node.getDependency().getArtifact();
            buffer.append( artifact.getGroupId() );
            buffer.append( ':' ).append( artifact.getArtifactId() );
            buffer.append( ':' ).append( artifact.getExtension() );
            if ( artifact.getClassifier().length() > 0 )
            {
                buffer.append( ':' ).append( artifact.getClassifier() );
            }
            buffer.append( ':' ).append( node.getVersionConstraint() );

            if ( it.hasNext() )
            {
                buffer.append( " -> " );
            }
        }

        return buffer.toString();
    }

    /**
     * Gets the paths leading to the conflicting dependencies.
     * 
     * @return The (read-only) paths leading to the conflicting dependencies, never {@code null}.
     */
    public Collection<? extends List<? extends DependencyNode>> getPaths()
    {
        return paths;
    }

    /**
     * Gets the conflicting version constraints of the dependency.
     * 
     * @return The (read-only) conflicting version constraints, never {@code null}.
     */
    public Collection<String> getVersions()
    {
        return versions;
    }

}
