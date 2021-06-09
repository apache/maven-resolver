package org.eclipse.aether.util.graph.selector;

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

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.TreeSet;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;

import static java.util.Objects.requireNonNull;

/**
 * A dependency selector that applies exclusions based on artifact coordinates.
 * 
 * @see Dependency#getExclusions()
 */
public final class ExclusionDependencySelector
    implements DependencySelector
{

    // sorted and dupe-free array, faster to iterate than LinkedHashSet
    private final Exclusion[] exclusions;

    private int hashCode;

    /**
     * Creates a new selector without any exclusions.
     */
    public ExclusionDependencySelector()
    {
        this.exclusions = new Exclusion[0];
    }

    /**
     * Creates a new selector with the specified exclusions.
     * 
     * @param exclusions The exclusions, may be {@code null}.
     */
    public ExclusionDependencySelector( Collection<Exclusion> exclusions )
    {
        if ( exclusions != null && !exclusions.isEmpty() )
        {
            TreeSet<Exclusion> sorted = new TreeSet<>( ExclusionComparator.INSTANCE );
            sorted.addAll( exclusions );
            this.exclusions = sorted.toArray( new Exclusion[sorted.size()] );
        }
        else
        {
            this.exclusions = new Exclusion[0];
        }
    }

    private ExclusionDependencySelector( Exclusion[] exclusions )
    {
        this.exclusions = exclusions;
    }

    public boolean selectDependency( Dependency dependency )
    {
        requireNonNull( dependency, "dependency cannot be null" );
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
        requireNonNull( context, "context cannot be null" );
        Dependency dependency = context.getDependency();
        Collection<Exclusion> exclusions = ( dependency != null ) ? dependency.getExclusions() : null;
        if ( exclusions == null || exclusions.isEmpty() )
        {
            return this;
        }

        Exclusion[] merged = this.exclusions;
        int count = merged.length;
        for ( Exclusion exclusion : exclusions )
        {
            int index = Arrays.binarySearch( merged, exclusion, ExclusionComparator.INSTANCE );
            if ( index < 0 )
            {
                index = -( index + 1 );
                if ( count >= merged.length )
                {
                    Exclusion[] tmp = new Exclusion[merged.length + exclusions.size()];
                    System.arraycopy( merged, 0, tmp, 0, index );
                    tmp[index] = exclusion;
                    System.arraycopy( merged, index, tmp, index + 1, count - index );
                    merged = tmp;
                }
                else
                {
                    System.arraycopy( merged, index, merged, index + 1, count - index );
                    merged[index] = exclusion;
                }
                count++;
            }
        }
        if ( merged == this.exclusions )
        {
            return this;
        }
        if ( merged.length != count )
        {
            Exclusion[] tmp = new Exclusion[count];
            System.arraycopy( merged, 0, tmp, 0, count );
            merged = tmp;
        }

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
        return Arrays.equals( exclusions, that.exclusions );
    }

    @Override
    public int hashCode()
    {
        if ( hashCode == 0 )
        {
            int hash = getClass().hashCode();
            hash = hash * 31 + Arrays.hashCode( exclusions );
            hashCode = hash;
        }
        return hashCode;
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder().append( this.getClass().getSimpleName() ).append( '(' );
        for ( int i = 0; i < this.exclusions.length; i++ )
        {
            builder.append( this.exclusions[i] );
            if ( i < this.exclusions.length - 1 )
            {
                builder.append( ", " );
            }
        }
        return builder.append( ')' ).toString();
    }

    private static class ExclusionComparator
        implements Comparator<Exclusion>
    {

        static final ExclusionComparator INSTANCE = new ExclusionComparator();

        public int compare( Exclusion e1, Exclusion e2 )
        {
            if ( e1 == null )
            {
                return ( e2 == null ) ? 0 : 1;
            }
            else if ( e2 == null )
            {
                return -1;
            }
            int rel = e1.getArtifactId().compareTo( e2.getArtifactId() );
            if ( rel == 0 )
            {
                rel = e1.getGroupId().compareTo( e2.getGroupId() );
                if ( rel == 0 )
                {
                    rel = e1.getExtension().compareTo( e2.getExtension() );
                    if ( rel == 0 )
                    {
                        rel = e1.getClassifier().compareTo( e2.getClassifier() );
                    }
                }
            }
            return rel;
        }

    }

}
