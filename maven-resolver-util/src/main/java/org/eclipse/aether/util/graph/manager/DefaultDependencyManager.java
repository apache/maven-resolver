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
 * A dependency manager managing dependencies on all levels supporting transitive dependency management.
 * <p>
 * <b>Note:</b>Unlike the {@code ClassicDependencyManager} and the {@code TransitiveDependencyManager} this
 * implementation applies management also on the first level. This is considered the resolver default behaviour. It
 * ignores all management overrides supported by the {@code MavenModelBuilder}.
 * </p>
 *
 * @author Christian Schulte
 * @since 1.2.0
 */
public final class DefaultDependencyManager
    implements DependencyManager
{

    private final Map<Object, String> managedVersions;

    private final Map<Object, String> managedScopes;

    private final Map<Object, Boolean> managedOptionals;

    private final Map<Object, String> managedLocalPaths;

    private final Map<Object, Collection<Exclusion>> managedExclusions;

    private int hashCode;

    /**
     * Creates a new dependency manager without any management information.
     */
    public DefaultDependencyManager()
    {
        this( Collections.<Object, String>emptyMap(), Collections.<Object, String>emptyMap(),
              Collections.<Object, Boolean>emptyMap(), Collections.<Object, String>emptyMap(),
              Collections.<Object, Collection<Exclusion>>emptyMap() );
    }

    private DefaultDependencyManager( final Map<Object, String> managedVersions,
                                      final Map<Object, String> managedScopes,
                                      final Map<Object, Boolean> managedOptionals,
                                      final Map<Object, String> managedLocalPaths,
                                      final Map<Object, Collection<Exclusion>> managedExclusions )
    {
        super();
        this.managedVersions = managedVersions;
        this.managedScopes = managedScopes;
        this.managedOptionals = managedOptionals;
        this.managedLocalPaths = managedLocalPaths;
        this.managedExclusions = managedExclusions;
    }

    public DependencyManager deriveChildManager( final DependencyCollectionContext context )
    {
        Map<Object, String> versions = this.managedVersions;
        Map<Object, String> scopes = this.managedScopes;
        Map<Object, Boolean> optionals = this.managedOptionals;
        Map<Object, String> localPaths = this.managedLocalPaths;
        Map<Object, Collection<Exclusion>> exclusions = this.managedExclusions;

        for ( Dependency managedDependency : context.getManagedDependencies() )
        {
            Artifact artifact = managedDependency.getArtifact();
            Object key = getKey( artifact );

            String version = artifact.getVersion();
            if ( version.length() > 0 && !versions.containsKey( key ) )
            {
                if ( versions == this.managedVersions )
                {
                    versions = new HashMap<Object, String>( this.managedVersions );
                }
                versions.put( key, version );
            }

            String scope = managedDependency.getScope();
            if ( scope.length() > 0 && !scopes.containsKey( key ) )
            {
                if ( scopes == this.managedScopes )
                {
                    scopes = new HashMap<Object, String>( this.managedScopes );
                }
                scopes.put( key, scope );
            }

            Boolean optional = managedDependency.getOptional();
            if ( optional != null && !optionals.containsKey( key ) )
            {
                if ( optionals == this.managedOptionals )
                {
                    optionals = new HashMap<Object, Boolean>( this.managedOptionals );
                }
                optionals.put( key, optional );
            }

            String localPath = managedDependency.getArtifact().getProperty( ArtifactProperties.LOCAL_PATH, null );
            if ( localPath != null && !localPaths.containsKey( key ) )
            {
                if ( localPaths == this.managedLocalPaths )
                {
                    localPaths = new HashMap<Object, String>( this.managedLocalPaths );
                }
                localPaths.put( key, localPath );
            }

            if ( !managedDependency.getExclusions().isEmpty() )
            {
                if ( exclusions == this.managedExclusions )
                {
                    exclusions = new HashMap<Object, Collection<Exclusion>>( this.managedExclusions );
                }
                Collection<Exclusion> managed = exclusions.get( key );
                if ( managed == null )
                {
                    managed = new LinkedHashSet<Exclusion>();
                    exclusions.put( key, managed );
                }
                managed.addAll( managedDependency.getExclusions() );
            }
        }

        return new DefaultDependencyManager( versions, scopes, optionals, localPaths, exclusions );
    }

    public DependencyManagement manageDependency( Dependency dependency )
    {
        DependencyManagement management = null;

        Object key = getKey( dependency.getArtifact() );

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

        Boolean optional = managedOptionals.get( key );
        if ( optional != null )
        {
            if ( management == null )
            {
                management = new DependencyManagement();
            }
            management.setOptional( optional );
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
    public boolean equals( final Object obj )
    {
        boolean equal = obj instanceof DefaultDependencyManager;

        if ( equal )
        {
            final DefaultDependencyManager that = (DefaultDependencyManager) obj;
            equal = this.managedVersions.equals( that.managedVersions )
                        && this.managedScopes.equals( that.managedScopes )
                        && this.managedOptionals.equals( that.managedOptionals )
                        && this.managedExclusions.equals( that.managedExclusions );

        }

        return equal;
    }

    @Override
    public int hashCode()
    {
        if ( this.hashCode == 0 )
        {
            int hash = 17;
            hash = hash * 31 + this.managedVersions.hashCode();
            hash = hash * 31 + this.managedScopes.hashCode();
            hash = hash * 31 + this.managedOptionals.hashCode();
            hash = hash * 31 + this.managedExclusions.hashCode();
            this.hashCode = hash;
        }
        return this.hashCode;
    }

    static class Key
    {

        private final Artifact artifact;

        private final int hashCode;

        public Key( final Artifact artifact )
        {
            this.artifact = artifact;

            int hash = 17;
            hash = hash * 31 + artifact.getGroupId().hashCode();
            hash = hash * 31 + artifact.getArtifactId().hashCode();
            this.hashCode = hash;
        }

        @Override
        public boolean equals( final Object obj )
        {
            boolean equal = obj instanceof Key;

            if ( equal )
            {
                final Key that = (Key) obj;
                return this.artifact.getArtifactId().equals( that.artifact.getArtifactId() )
                           && this.artifact.getGroupId().equals( that.artifact.getGroupId() )
                           && this.artifact.getExtension().equals( that.artifact.getExtension() )
                           && this.artifact.getClassifier().equals( that.artifact.getClassifier() );

            }

            return equal;
        }

        @Override
        public int hashCode()
        {
            return this.hashCode;
        }

    }

}
