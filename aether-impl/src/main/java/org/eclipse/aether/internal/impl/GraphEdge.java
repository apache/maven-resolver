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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionConstraint;

/**
 */
final class GraphEdge
    implements DependencyNode
{

    private GraphNode target;

    private Dependency dependency;

    private String context;

    private String premanagedScope;

    private String premanagedVersion;

    private List<Artifact> relocations;

    private VersionConstraint versionConstraint;

    private Version version;

    private Map<Object, Object> data = Collections.emptyMap();

    public GraphEdge( GraphNode target )
    {
        this.target = target;
    }

    public GraphNode getTarget()
    {
        return target;
    }

    public List<DependencyNode> getChildren()
    {
        return getTarget().getOutgoingEdges();
    }

    public Dependency getDependency()
    {
        return dependency;
    }

    public void setDependency( Dependency dependency )
    {
        this.dependency = dependency;
    }

    public void setArtifact( Artifact artifact )
    {
        this.dependency = dependency.setArtifact( artifact );
    }

    public List<RemoteRepository> getRepositories()
    {
        return getTarget().getRepositories();
    }

    public void setScope( String scope )
    {
        this.dependency = dependency.setScope( scope );
    }

    public String getPremanagedScope()
    {
        return premanagedScope;
    }

    public void setPremanagedScope( String premanagedScope )
    {
        this.premanagedScope = premanagedScope;
    }

    public String getPremanagedVersion()
    {
        return premanagedVersion;
    }

    public void setPremanagedVersion( String premanagedVersion )
    {
        this.premanagedVersion = premanagedVersion;
    }

    public String getRequestContext()
    {
        return context;
    }

    public void setRequestContext( String context )
    {
        this.context = ( context != null ) ? context : "";
    }

    public List<Artifact> getRelocations()
    {
        return relocations;
    }

    public void setRelocations( List<Artifact> relocations )
    {
        if ( relocations == null || relocations.isEmpty() )
        {
            this.relocations = Collections.emptyList();
        }
        else
        {
            this.relocations = relocations;
        }
    }

    public Collection<Artifact> getAliases()
    {
        return getTarget().getAliases();
    }

    public VersionConstraint getVersionConstraint()
    {
        return versionConstraint;
    }

    public void setVersionConstraint( VersionConstraint versionConstraint )
    {
        this.versionConstraint = versionConstraint;
    }

    public Version getVersion()
    {
        return version;
    }

    public void setVersion( Version version )
    {
        this.version = version;
    }

    public Map<Object, Object> getData()
    {
        return data;
    }

    public void setData( Object key, Object value )
    {
        if ( key == null )
        {
            throw new IllegalArgumentException( "key must not be null" );
        }

        if ( value == null )
        {
            if ( !data.isEmpty() )
            {
                data.remove( key );

                if ( data.isEmpty() )
                {
                    data = Collections.emptyMap();
                }
            }
        }
        else
        {
            if ( data.isEmpty() )
            {
                data = new HashMap<Object, Object>();
            }
            data.put( key, value );
        }
    }

    public boolean accept( DependencyVisitor visitor )
    {
        if ( visitor.visitEnter( this ) )
        {
            List<DependencyNode> children = getChildren();
            for ( int i = 0, n = children.size(); i < n; i++ )
            {
                DependencyNode child = children.get( i );
                if ( !child.accept( visitor ) )
                {
                    break;
                }
            }
        }

        return visitor.visitLeave( this );
    }

    @Override
    public String toString()
    {
        Dependency dep = getDependency();
        if ( dep == null )
        {
            return String.valueOf( getChildren() );
        }
        return dep.toString();
    }

}
