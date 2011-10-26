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
package org.eclipse.aether.util.filter;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.util.filter.ExclusionsDependencyFilter;

/**
 * A simple filter to exclude artifacts based either artifact id or group id and artifact id.
 */
public final class ExclusionsDependencyFilter
    implements DependencyFilter
{

    private final Collection<String> excludes = new HashSet<String>();

    /**
     * Creates a new filter using the specified exclude patterns. A pattern can either be of the form
     * {@code groupId:artifactId} (recommended) or just {@code artifactId}.
     * 
     * @param excludes The exclude patterns, may be {@code null} or empty to exclude no artifacts.
     */
    public ExclusionsDependencyFilter( Collection<String> excludes )
    {
        if ( excludes != null )
        {
            this.excludes.addAll( excludes );
        }
    }

    public boolean accept( DependencyNode node, List<DependencyNode> parents )
    {
        Dependency dependency = node.getDependency();

        if ( dependency == null )
        {
            return true;
        }

        String id = dependency.getArtifact().getArtifactId();

        if ( excludes.contains( id ) )
        {
            return false;
        }

        id = dependency.getArtifact().getGroupId() + ':' + id;

        if ( excludes.contains( id ) )
        {
            return false;
        }

        return true;
    }

    @Override
    public boolean equals( Object obj )
    {
        if ( this == obj )
        {
            return true;
        }

        if ( obj == null || !getClass().equals( obj.getClass() ) )
        {
            return false;
        }

        ExclusionsDependencyFilter that = (ExclusionsDependencyFilter) obj;

        return this.excludes.equals( that.excludes );
    }

    @Override
    public int hashCode()
    {
        int hash = 17;
        hash = hash * 31 + excludes.hashCode();
        return hash;
    }

}
