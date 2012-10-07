/*******************************************************************************
 * Copyright (c) 2011, 2012 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.util.version;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionRange;

/**
 * A union of version ranges.
 */
final class UnionVersionRange
    implements VersionRange
{

    private final Set<VersionRange> ranges;

    private final Bound lowerBound;

    private final Bound upperBound;

    public static VersionRange from( VersionRange... ranges )
    {
        if ( ranges == null )
        {
            return from( Collections.<VersionRange> emptySet() );
        }
        return from( Arrays.asList( ranges ) );
    }

    public static VersionRange from( Collection<? extends VersionRange> ranges )
    {
        if ( ranges != null && ranges.size() == 1 )
        {
            return ranges.iterator().next();
        }
        return new UnionVersionRange( ranges );
    }

    private UnionVersionRange( Collection<? extends VersionRange> ranges )
    {
        if ( ranges == null || ranges.isEmpty() )
        {
            this.ranges = Collections.emptySet();
            lowerBound = upperBound = null;
        }
        else
        {
            this.ranges = new HashSet<VersionRange>( ranges );
            Bound lowerBound = null, upperBound = null;
            for ( VersionRange range : this.ranges )
            {
                Bound lb = range.getLowerBound();
                if ( lb == null )
                {
                    lowerBound = null;
                    break;
                }
                else if ( lowerBound == null )
                {
                    lowerBound = lb;
                }
                else
                {
                    int c = lb.getVersion().compareTo( lowerBound.getVersion() );
                    if ( c < 0 || ( c == 0 && !lowerBound.isInclusive() ) )
                    {
                        lowerBound = lb;
                    }
                }
            }
            for ( VersionRange range : this.ranges )
            {
                Bound ub = range.getUpperBound();
                if ( ub == null )
                {
                    upperBound = null;
                    break;
                }
                else if ( upperBound == null )
                {
                    upperBound = ub;
                }
                else
                {
                    int c = ub.getVersion().compareTo( upperBound.getVersion() );
                    if ( c > 0 || ( c == 0 && !upperBound.isInclusive() ) )
                    {
                        upperBound = ub;
                    }
                }
            }
            this.lowerBound = lowerBound;
            this.upperBound = upperBound;
        }
    }

    public boolean containsVersion( Version version )
    {
        for ( VersionRange range : ranges )
        {
            if ( range.containsVersion( version ) )
            {
                return true;
            }
        }
        return false;
    }

    public Bound getLowerBound()
    {
        return lowerBound;
    }

    public Bound getUpperBound()
    {
        return upperBound;
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

        UnionVersionRange that = (UnionVersionRange) obj;

        return ranges.equals( that.ranges );
    }

    @Override
    public int hashCode()
    {
        int hash = 97 * ranges.hashCode();
        return hash;
    }

    @Override
    public String toString()
    {
        StringBuilder buffer = new StringBuilder( 128 );
        for ( VersionRange range : ranges )
        {
            if ( buffer.length() > 0 )
            {
                buffer.append( ", " );
            }
            buffer.append( range );
        }
        return buffer.toString();
    }

}
