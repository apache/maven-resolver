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
package org.eclipse.aether.graph;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.NoSuchElementException;
import java.util.Set;

import org.eclipse.aether.artifact.Artifact;

/**
 * A dependency to some artifact. <em>Note:</em> Instances of this class are immutable and the exposed mutators return
 * new objects rather than changing the current instance.
 */
public final class Dependency
{

    private final Artifact artifact;

    private final String scope;

    private final boolean optional;

    private final Set<Exclusion> exclusions;

    /**
     * Creates a mandatory dependency on the specified artifact with the given scope.
     * 
     * @param artifact The artifact being depended on, must not be {@code null}.
     * @param scope The scope of the dependency, may be {@code null}.
     */
    public Dependency( Artifact artifact, String scope )
    {
        this( artifact, scope, false );
    }

    /**
     * Creates a dependency on the specified artifact with the given scope.
     * 
     * @param artifact The artifact being depended on, must not be {@code null}.
     * @param scope The scope of the dependency, may be {@code null}.
     * @param optional A flag whether the dependency is optional or mandatory.
     */
    public Dependency( Artifact artifact, String scope, boolean optional )
    {
        this( artifact, scope, optional, null );
    }

    /**
     * Creates a dependency on the specified artifact with the given scope and exclusions.
     * 
     * @param artifact The artifact being depended on, must not be {@code null}.
     * @param scope The scope of the dependency, may be {@code null}.
     * @param optional A flag whether the dependency is optional or mandatory.
     * @param exclusions The exclusions that apply to transitive dependencies, may be {@code null} if none.
     */
    public Dependency( Artifact artifact, String scope, boolean optional, Collection<Exclusion> exclusions )
    {
        this( artifact, scope, Exclusions.copy( exclusions ), optional );
    }

    private Dependency( Artifact artifact, String scope, Set<Exclusion> exclusions, boolean optional )
    {
        // NOTE: This constructor assumes immutability of the provided exclusion collection, for internal use only
        if ( artifact == null )
        {
            throw new IllegalArgumentException( "no artifact specified for dependency" );
        }
        this.artifact = artifact;
        this.scope = ( scope != null ) ? scope : "";
        this.optional = optional;
        this.exclusions = exclusions;
    }

    /**
     * Gets the artifact being depended on.
     * 
     * @return The artifact, never {@code null}.
     */
    public Artifact getArtifact()
    {
        return artifact;
    }

    /**
     * Sets the artifact being depended on.
     * 
     * @param artifact The artifact, must not be {@code null}.
     * @return The new dependency, never {@code null}.
     */
    public Dependency setArtifact( Artifact artifact )
    {
        if ( this.artifact.equals( artifact ) )
        {
            return this;
        }
        return new Dependency( artifact, scope, exclusions, optional );
    }

    /**
     * Gets the scope of the dependency. The scope defines in which context this dependency is relevant.
     * 
     * @return The scope or an empty string if not set, never {@code null}.
     */
    public String getScope()
    {
        return scope;
    }

    /**
     * Sets the scope of the dependency, e.g. "compile".
     * 
     * @param scope The scope of the dependency, may be {@code null}.
     * @return The new dependency, never {@code null}.
     */
    public Dependency setScope( String scope )
    {
        if ( this.scope.equals( scope ) || ( scope == null && this.scope.length() <= 0 ) )
        {
            return this;
        }
        return new Dependency( artifact, scope, exclusions, optional );
    }

    /**
     * Indicates whether this dependency is optional or not. Optional dependencies can usually be ignored during
     * transitive dependency resolution.
     * 
     * @return {@code true} if the dependency is optional, {@code false} otherwise.
     */
    public boolean isOptional()
    {
        return optional;
    }

    /**
     * Sets the optional flag for the dependency.
     * 
     * @param optional {@code true} if the dependency is optional, {@code false} if the dependency is mandatory.
     * @return The new dependency, never {@code null}.
     */
    public Dependency setOptional( boolean optional )
    {
        if ( this.optional == optional )
        {
            return this;
        }
        return new Dependency( artifact, scope, exclusions, optional );
    }

    /**
     * Gets the exclusions for this dependency. Exclusions can be used to remove transitive dependencies during
     * resolution.
     * 
     * @return The (read-only) exclusions, never {@code null}.
     */
    public Collection<Exclusion> getExclusions()
    {
        return exclusions;
    }

    /**
     * Sets the exclusions for the dependency.
     * 
     * @param exclusions The exclusions, may be {@code null}.
     * @return The new dependency, never {@code null}.
     */
    public Dependency setExclusions( Collection<Exclusion> exclusions )
    {
        if ( hasEquivalentExclusions( exclusions ) )
        {
            return this;
        }
        return new Dependency( artifact, scope, optional, exclusions );
    }

    private boolean hasEquivalentExclusions( Collection<Exclusion> exclusions )
    {
        if ( exclusions == null || exclusions.isEmpty() )
        {
            return this.exclusions.isEmpty();
        }
        if ( exclusions instanceof Set )
        {
            return this.exclusions.equals( exclusions );
        }
        return exclusions.size() >= this.exclusions.size() && this.exclusions.containsAll( exclusions )
            && exclusions.containsAll( this.exclusions );
    }

    @Override
    public String toString()
    {
        return String.valueOf( getArtifact() ) + " (" + getScope() + ( isOptional() ? "?" : "" ) + ")";
    }

    @Override
    public boolean equals( Object obj )
    {
        if ( obj == this )
        {
            return true;
        }
        else if ( obj == null || !getClass().equals( obj.getClass() ) )
        {
            return false;
        }

        Dependency that = (Dependency) obj;

        return artifact.equals( that.artifact ) && scope.equals( that.scope ) && optional == that.optional
            && exclusions.equals( that.exclusions );
    }

    @Override
    public int hashCode()
    {
        int hash = 17;
        hash = hash * 31 + artifact.hashCode();
        hash = hash * 31 + scope.hashCode();
        hash = hash * 31 + ( optional ? 1 : 0 );
        hash = hash * 31 + exclusions.size();
        return hash;
    }

    private static class Exclusions
        extends AbstractSet<Exclusion>
    {

        private final Exclusion[] exclusions;

        public static Set<Exclusion> copy( Collection<Exclusion> exclusions )
        {
            if ( exclusions == null || exclusions.isEmpty() )
            {
                return Collections.emptySet();
            }
            return new Exclusions( exclusions );
        }

        private Exclusions( Collection<Exclusion> exclusions )
        {
            if ( exclusions.size() > 1 && !( exclusions instanceof Set ) )
            {
                exclusions = new LinkedHashSet<Exclusion>( exclusions );
            }
            this.exclusions = exclusions.toArray( new Exclusion[exclusions.size()] );
        }

        @Override
        public Iterator<Exclusion> iterator()
        {
            return new Iterator<Exclusion>()
            {

                private int cursor = 0;

                public boolean hasNext()
                {
                    return cursor < exclusions.length;
                }

                public Exclusion next()
                {
                    try
                    {
                        Exclusion exclusion = exclusions[cursor];
                        cursor++;
                        return exclusion;
                    }
                    catch ( IndexOutOfBoundsException e )
                    {
                        throw new NoSuchElementException();
                    }
                }

                public void remove()
                {
                    throw new UnsupportedOperationException();
                }

            };
        }

        @Override
        public int size()
        {
            return exclusions.length;
        }

    }

}
