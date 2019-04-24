package org.eclipse.aether.util.graph.manager;

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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactProperties;
import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencyManagement;
import org.eclipse.aether.collection.DependencyManager;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
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

    private final Map<Object, Boolean> managedOptionals;

    private final Map<Object, String> managedLocalPaths;

    private final Map<Object, Collection<Exclusion>> managedExclusions;

    private int hashCode;

    /**
     * Creates a new dependency manager without any management information.
     */
    public ClassicDependencyManager()
    {
        this( 0, Collections.<Object, String>emptyMap(), Collections.<Object, String>emptyMap(),
              Collections.<Object, Boolean>emptyMap(), Collections.<Object, String>emptyMap(),
              Collections.<Object, Collection<Exclusion>>emptyMap() );
    }

    private ClassicDependencyManager( int depth, Map<Object, String> managedVersions,
                                      Map<Object, String> managedScopes, Map<Object, Boolean> managedOptionals,
                                      Map<Object, String> managedLocalPaths,
                                      Map<Object, Collection<Exclusion>> managedExclusions )
    {
        this.depth = depth;
        this.managedVersions = managedVersions;
        this.managedScopes = managedScopes;
        this.managedOptionals = managedOptionals;
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
            return new ClassicDependencyManager( depth + 1, managedVersions, managedScopes, managedOptionals,
                                                 managedLocalPaths, managedExclusions );
        }

        Map<Object, String> managedVersions = this.managedVersions;
        Map<Object, String> managedScopes = this.managedScopes;
        Map<Object, Boolean> managedOptionals = this.managedOptionals;
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
                    managedVersions = new HashMap<>( this.managedVersions );
                }
                managedVersions.put( key, version );
            }

            String scope = managedDependency.getScope();
            if ( scope.length() > 0 && !managedScopes.containsKey( key ) )
            {
                if ( managedScopes == this.managedScopes )
                {
                    managedScopes = new HashMap<>( this.managedScopes );
                }
                managedScopes.put( key, scope );
            }

            Boolean optional = managedDependency.getOptional();
            if ( optional != null && !managedOptionals.containsKey( key ) )
            {
                if ( managedOptionals == this.managedOptionals )
                {
                    managedOptionals = new HashMap<>( this.managedOptionals );
                }
                managedOptionals.put( key, optional );
            }

            String localPath = managedDependency.getArtifact().getProperty( ArtifactProperties.LOCAL_PATH, null );
            if ( localPath != null && !managedLocalPaths.containsKey( key ) )
            {
                if ( managedLocalPaths == this.managedLocalPaths )
                {
                    managedLocalPaths = new HashMap<>( this.managedLocalPaths );
                }
                managedLocalPaths.put( key, localPath );
            }

            Collection<Exclusion> exclusions = managedDependency.getExclusions();
            if ( !exclusions.isEmpty() )
            {
                if ( managedExclusions == this.managedExclusions )
                {
                    managedExclusions = new HashMap<>( this.managedExclusions );
                }
                Collection<Exclusion> managed = managedExclusions.get( key );
                if ( managed == null )
                {
                    managed = new LinkedHashSet<>();
                    managedExclusions.put( key, managed );
                }
                managed.addAll( exclusions );
            }
        }

        return new ClassicDependencyManager( depth + 1, managedVersions, managedScopes, managedOptionals,
                                             managedLocalPaths, managedExclusions );
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
                        new HashMap<>( dependency.getArtifact().getProperties() );
                    properties.remove( ArtifactProperties.LOCAL_PATH );
                    management.setProperties( properties );
                }
            }

            if ( ( JavaScopes.SYSTEM.equals( scope ) )
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
                        new HashMap<>( dependency.getArtifact().getProperties() );
                    properties.put( ArtifactProperties.LOCAL_PATH, localPath );
                    management.setProperties( properties );
                }
            }

            Boolean optional = managedOptionals.get( key );
            if ( optional != null )
            {
                if ( management == null )
                {
                    management = new DependencyManagement();
                }
                management.setOptional( optional );
            }
        }

        Collection<Exclusion> exclusions = managedExclusions.get( key );
        if ( exclusions != null )
        {
            if ( management == null )
            {
                management = new DependencyManagement();
            }
            Collection<Exclusion> result = new LinkedHashSet<>( dependency.getExclusions() );
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
            && managedScopes.equals( that.managedScopes ) && managedOptionals.equals( that.managedOptionals )
            && managedExclusions.equals( that.managedExclusions );
    }

    @Override
    public int hashCode()
    {
        if ( hashCode == 0 )
        {
            int hash = 17;
            hash = hash * 31 + depth;
            hash = hash * 31 + managedVersions.hashCode();
            hash = hash * 31 + managedScopes.hashCode();
            hash = hash * 31 + managedOptionals.hashCode();
            hash = hash * 31 + managedExclusions.hashCode();
            hashCode = hash;
        }
        return hashCode;
    }

    static class Key
    {

        private final Artifact artifact;

        private final int hashCode;

        Key( Artifact artifact )
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
