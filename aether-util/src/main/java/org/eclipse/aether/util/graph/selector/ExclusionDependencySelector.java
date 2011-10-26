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
package org.eclipse.aether.util.graph.selector;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;

/**
 * A dependency selector that applies exclusions based on artifact coordinates.
 * @see Dependency#getExclusions()
 */
public final class ExclusionDependencySelector
    implements DependencySelector
{

    private final Collection<Exclusion> exclusions;

    /**
     * Creates a new selector without any exclusions.
     */
    public ExclusionDependencySelector()
    {
        this( Collections.<Exclusion> emptySet() );
    }

    /**
     * Creates a new selector with the specified exclusions.
     * 
     * @param exclusions The exclusions, may be {@code null}.
     */
    public ExclusionDependencySelector( Set<Exclusion> exclusions )
    {
        if ( exclusions != null && !exclusions.isEmpty() )
        {
            this.exclusions = exclusions;
        }
        else
        {
            this.exclusions = Collections.emptySet();
        }
    }

    public boolean selectDependency( Dependency dependency )
    {
        Artifact artifact = dependency.getArtifact();
        for ( Exclusion exclusion : exclusions )
        {
            if ( matches( exclusion, artifact ) )
            {
                return false;
            }
        }
        return true;
    }

    private boolean matches( Exclusion exclusion, Artifact artifact )
    {
        if ( !matches( exclusion.getArtifactId(), artifact.getArtifactId() ) )
        {
            return false;
        }
        if ( !matches( exclusion.getGroupId(), artifact.getGroupId() ) )
        {
            return false;
        }
        if ( !matches( exclusion.getExtension(), artifact.getExtension() ) )
        {
            return false;
        }
        if ( !matches( exclusion.getClassifier(), artifact.getClassifier() ) )
        {
            return false;
        }
        return true;
    }

    private boolean matches( String pattern, String value )
    {
        return "*".equals( pattern ) || pattern.equals( value );
    }

    public DependencySelector deriveChildSelector( DependencyCollectionContext context )
    {
        Dependency dependency = context.getDependency();
        Collection<Exclusion> exclusions = ( dependency != null ) ? dependency.getExclusions() : null;
        if ( exclusions == null || exclusions.isEmpty() )
        {
            return this;
        }

        Set<Exclusion> merged = new LinkedHashSet<Exclusion>();
        merged.addAll( this.exclusions );
        merged.addAll( exclusions );

        return new ExclusionDependencySelector( merged );
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

        ExclusionDependencySelector that = (ExclusionDependencySelector) obj;
        return exclusions.equals( that.exclusions );
    }

    @Override
    public int hashCode()
    {
        int hash = getClass().hashCode();
        hash = hash * 31 + exclusions.hashCode();
        return hash;
    }

}
