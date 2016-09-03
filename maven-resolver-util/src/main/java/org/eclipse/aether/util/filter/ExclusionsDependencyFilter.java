package org.eclipse.aether.util.filter;

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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;

/**
 * A simple filter to exclude artifacts based on either artifact id or group id and artifact id.
 */
public final class ExclusionsDependencyFilter
    implements DependencyFilter
{

    private final Set<String> excludes = new HashSet<String>();

    /**
     * Creates a new filter using the specified exclude patterns. A pattern can either be of the form
     * {@code groupId:artifactId} (recommended) or just {@code artifactId} (deprecated).
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
