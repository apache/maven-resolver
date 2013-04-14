/*******************************************************************************
 * Copyright (c) 2010, 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.collection;

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
            this.versions = new LinkedHashSet<String>();
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
            Collection<String> strings = new LinkedHashSet<String>();

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
