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
package org.eclipse.aether.graph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionConstraint;

/**
 * A node within a dependency graph.
 */
public final class DefaultDependencyNode
    implements DependencyNode
{

    private List<DependencyNode> children = new ArrayList<DependencyNode>( 0 );

    private Dependency dependency;

    private List<Artifact> relocations = Collections.emptyList();

    private Collection<Artifact> aliases = Collections.emptyList();

    private VersionConstraint versionConstraint;

    private Version version;

    private String premanagedVersion;

    private String premanagedScope;

    private List<RemoteRepository> repositories = Collections.emptyList();

    private String context = "";

    private Map<Object, Object> data = Collections.emptyMap();

    /**
     * Creates an empty dependency node.
     */
    public DefaultDependencyNode()
    {
        // enables no-arg constructor
    }

    /**
     * Creates a new root node with the specified dependency.
     * 
     * @param dependency The dependency associated with this node, may be {@code null}.
     */
    public DefaultDependencyNode( Dependency dependency )
    {
        this.dependency = dependency;
    }

    /**
     * Creates a shallow clone of the specified node.
     * 
     * @param node The node to copy, must not be {@code null}.
     */
    public DefaultDependencyNode( DependencyNode node )
    {
        setDependency( node.getDependency() );
        setAliases( node.getAliases() );
        setRequestContext( node.getRequestContext() );
        setPremanagedScope( node.getPremanagedScope() );
        setPremanagedVersion( node.getPremanagedVersion() );
        setRelocations( node.getRelocations() );
        setRepositories( node.getRepositories() );
        setVersion( node.getVersion() );
        setVersionConstraint( node.getVersionConstraint() );
        setData( node.getData() );
    }

    public List<DependencyNode> getChildren()
    {
        return children;
    }

    /**
     * Sets the child nodes of this node.
     * 
     * @param children The child nodes, may be {@code null}
     */
    public void setChildren( List<DependencyNode> children )
    {
        if ( children == null )
        {
            this.children = new ArrayList<DependencyNode>( 0 );
        }
        else
        {
            this.children = children;
        }
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
        dependency = dependency.setArtifact( artifact );
    }

    public List<Artifact> getRelocations()
    {
        return relocations;
    }

    /**
     * Sets the sequence of relocations that was followed to resolve this dependency's artifact.
     * 
     * @param relocations The sequence of relocations, may be {@code null}.
     */
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
        return aliases;
    }

    /**
     * Sets the known aliases for this dependency's artifact.
     * 
     * @param aliases The known aliases, may be {@code null}.
     */
    public void setAliases( Collection<Artifact> aliases )
    {
        if ( aliases == null || aliases.isEmpty() )
        {
            this.aliases = Collections.emptyList();
        }
        else
        {
            this.aliases = aliases;
        }
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

    public void setScope( String scope )
    {
        dependency = dependency.setScope( scope );
    }

    public String getPremanagedVersion()
    {
        return premanagedVersion;
    }

    /**
     * Sets the version or version range for this dependency before dependency management was applied (if any).
     * 
     * @param premanagedVersion The originally declared dependency version or {@code null} if the version was not
     *            managed.
     */
    public void setPremanagedVersion( String premanagedVersion )
    {
        this.premanagedVersion = premanagedVersion;
    }

    public String getPremanagedScope()
    {
        return premanagedScope;
    }

    /**
     * Sets the scope for this dependency before dependency management was applied (if any).
     * 
     * @param premanagedScope The originally declared dependency scope or {@code null} if the scope was not managed.
     */
    public void setPremanagedScope( String premanagedScope )
    {
        this.premanagedScope = premanagedScope;
    }

    public List<RemoteRepository> getRepositories()
    {
        return repositories;
    }

    /**
     * Sets the remote repositories from which this node's artifact shall be resolved.
     * 
     * @param repositories The remote repositories to use for artifact resolution, may be {@code null}.
     */
    public void setRepositories( List<RemoteRepository> repositories )
    {
        if ( repositories == null || repositories.isEmpty() )
        {
            this.repositories = Collections.emptyList();
        }
        else
        {
            this.repositories = repositories;
        }
    }

    public String getRequestContext()
    {
        return context;
    }

    public void setRequestContext( String context )
    {
        this.context = ( context != null ) ? context : "";
    }

    public Map<Object, Object> getData()
    {
        return data;
    }

    public void setData( Map<Object, Object> data )
    {
        if ( data == null )
        {
            this.data = Collections.emptyMap();
        }
        else
        {
            this.data = data;
        }
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
