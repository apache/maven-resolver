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
package org.eclipse.aether.util.graph.manager;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencyManagement;
import org.eclipse.aether.collection.DependencyManager;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.util.artifact.ArtifactProperties;
import org.eclipse.aether.util.artifact.JavaScopes;

/**
 * A dependency manager that mimics the way Maven 2.x works.
 */
public final class ClassicDependencyManager
    implements DependencyManager
{

    private final int depth;

    private final Map<Object, String> managedVersions;

    private final Map<Object, String> managedScopes;

    private final Map<Object, String> managedLocalPaths;

    private final Map<Object, Collection<Exclusion>> managedExclusions;

    /**
     * Creates a new dependency manager without any management information.
     */
    public ClassicDependencyManager()
    {
        this( 0, Collections.<Object, String> emptyMap(), Collections.<Object, String> emptyMap(),
              Collections.<Object, String> emptyMap(), Collections.<Object, Collection<Exclusion>> emptyMap() );
    }

    private ClassicDependencyManager( int depth, Map<Object, String> managedVersions,
                                      Map<Object, String> managedScopes, Map<Object, String> managedLocalPaths,
                                      Map<Object, Collection<Exclusion>> managedExclusions )
    {
        this.depth = depth;
        this.managedVersions = managedVersions;
        this.managedScopes = managedScopes;
        this.managedLocalPaths = managedLocalPaths;
        this.managedExclusions = managedExclusions;
    }

    public DependencyManager deriveChildManager( DependencyCollectionContext context )
    {
        if ( depth >= 2 )
        {
            return this;
        }
        else if ( depth == 1 )
        {
            return new ClassicDependencyManager( depth + 1, managedVersions, managedScopes, managedLocalPaths,
                                                 managedExclusions );
        }

        Map<Object, String> managedVersions = this.managedVersions;
        Map<Object, String> managedScopes = this.managedScopes;
        Map<Object, String> managedLocalPaths = this.managedLocalPaths;
        Map<Object, Collection<Exclusion>> managedExclusions = this.managedExclusions;

        for ( Dependency managedDependency : context.getManagedDependencies() )
        {
            Artifact artifact = managedDependency.getArtifact();
            Object key = getKey( artifact );

            String version = artifact.getVersion();
            if ( version.length() > 0 && !managedVersions.containsKey( key ) )
            {
                if ( managedVersions == this.managedVersions )
                {
                    managedVersions = new HashMap<Object, String>( this.managedVersions );
                }
                managedVersions.put( key, version );
            }

            String scope = managedDependency.getScope();
            if ( scope.length() > 0 && !managedScopes.containsKey( key ) )
            {
                if ( managedScopes == this.managedScopes )
                {
                    managedScopes = new HashMap<Object, String>( this.managedScopes );
                }
                managedScopes.put( key, scope );
            }

            String localPath = managedDependency.getArtifact().getProperty( ArtifactProperties.LOCAL_PATH, null );
            if ( localPath != null && !managedLocalPaths.containsKey( key ) )
            {
                if ( managedLocalPaths == this.managedLocalPaths )
                {
                    managedLocalPaths = new HashMap<Object, String>( this.managedLocalPaths );
                }
                managedLocalPaths.put( key, localPath );
            }

            Collection<Exclusion> exclusions = managedDependency.getExclusions();
            if ( !exclusions.isEmpty() )
            {
                if ( managedExclusions == this.managedExclusions )
                {
                    managedExclusions = new HashMap<Object, Collection<Exclusion>>( this.managedExclusions );
                }
                Collection<Exclusion> managed = managedExclusions.get( key );
                if ( managed == null )
                {
                    managed = new LinkedHashSet<Exclusion>();
                    managedExclusions.put( key, managed );
                }
                managed.addAll( exclusions );
            }
        }

        return new ClassicDependencyManager( depth + 1, managedVersions, managedScopes, managedLocalPaths,
                                             managedExclusions );
    }

    public DependencyManagement manageDependency( Dependency dependency )
    {
        DependencyManagement management = null;

        Object key = getKey( dependency.getArtifact() );

        if ( depth >= 2 )
        {
            String version = managedVersions.get( key );
            if ( version != null )
            {
                if ( management == null )
                {
                    management = new DependencyManagement();
                }
                management.setVersion( version );
            }

            String scope = managedScopes.get( key );
            if ( scope != null )
            {
                if ( management == null )
                {
                    management = new DependencyManagement();
                }
                management.setScope( scope );

                if ( !JavaScopes.SYSTEM.equals( scope )
                    && dependency.getArtifact().getProperty( ArtifactProperties.LOCAL_PATH, null ) != null )
                {
                    Map<String, String> properties =
                        new HashMap<String, String>( dependency.getArtifact().getProperties() );
                    properties.remove( ArtifactProperties.LOCAL_PATH );
                    management.setProperties( properties );
                }
            }

            if ( ( scope != null && JavaScopes.SYSTEM.equals( scope ) )
                || ( scope == null && JavaScopes.SYSTEM.equals( dependency.getScope() ) ) )
            {
                String localPath = managedLocalPaths.get( key );
                if ( localPath != null )
                {
                    if ( management == null )
                    {
                        management = new DependencyManagement();
                    }
                    Map<String, String> properties =
                        new HashMap<String, String>( dependency.getArtifact().getProperties() );
                    properties.put( ArtifactProperties.LOCAL_PATH, localPath );
                    management.setProperties( properties );
                }
            }
        }

        Collection<Exclusion> exclusions = managedExclusions.get( key );
        if ( exclusions != null )
        {
            if ( management == null )
            {
                management = new DependencyManagement();
            }
            Collection<Exclusion> result = new LinkedHashSet<Exclusion>( dependency.getExclusions() );
            result.addAll( exclusions );
            management.setExclusions( result );
        }

        return management;
    }

    private Object getKey( Artifact a )
    {
        return new Key( a );
    }

    @Override
    public boolean equals( Object obj )
    {
        if ( this == obj )
        {
            return true;
        }
        else if ( null == obj || !getClass().equals( obj.getClass() ) )
        {
            return false;
        }

        ClassicDependencyManager that = (ClassicDependencyManager) obj;
        return depth == that.depth && managedVersions.equals( that.managedVersions )
            && managedScopes.equals( that.managedScopes ) && managedExclusions.equals( that.managedExclusions );
    }

    @Override
    public int hashCode()
    {
        int hash = 17;
        hash = hash * 31 + depth;
        hash = hash * 31 + managedVersions.hashCode();
        hash = hash * 31 + managedScopes.hashCode();
        hash = hash * 31 + managedExclusions.hashCode();
        return hash;
    }

    static class Key
    {

        private final Artifact artifact;

        private final int hashCode;

        public Key( Artifact artifact )
        {
            this.artifact = artifact;

            int hash = 17;
            hash = hash * 31 + artifact.getGroupId().hashCode();
            hash = hash * 31 + artifact.getArtifactId().hashCode();
            hashCode = hash;
        }

        @Override
        public boolean equals( Object obj )
        {
            if ( obj == this )
            {
                return true;
            }
            else if ( !( obj instanceof Key ) )
            {
                return false;
            }
            Key that = (Key) obj;
            return artifact.getArtifactId().equals( that.artifact.getArtifactId() )
                && artifact.getGroupId().equals( that.artifact.getGroupId() )
                && artifact.getExtension().equals( that.artifact.getExtension() )
                && artifact.getClassifier().equals( that.artifact.getClassifier() );
        }

        @Override
        public int hashCode()
        {
            return hashCode;
        }

    }

}
