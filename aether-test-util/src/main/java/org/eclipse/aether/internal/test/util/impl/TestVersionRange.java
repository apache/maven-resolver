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
package org.eclipse.aether.internal.test.util.impl;

import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionRange;

/**
 * A version range inspired by mathematical range syntax. For example, "[1.0,2.0)", "[1.0,)" or "[1.0]".
 */
final class TestVersionRange
    implements VersionRange
{

    private final Version lowerBound;

    private final boolean lowerBoundInclusive;

    private final Version upperBound;

    private final boolean upperBoundInclusive;

    /**
     * Creates a version range from the specified range specification.
     * 
     * @param range The range specification to parse, must not be {@code null}.
     * @throws InvalidVersionSpecificationException If the range could not be parsed.
     */
    public TestVersionRange( String range )
        throws InvalidVersionSpecificationException
    {
        String process = range;

        if ( range.startsWith( "[" ) )
        {
            lowerBoundInclusive = true;
        }
        else if ( range.startsWith( "(" ) )
        {
            lowerBoundInclusive = false;
        }
        else
        {
            throw new InvalidVersionSpecificationException( range, "Invalid version range " + range
                + ", a range must start with either [ or (" );
        }

        if ( range.endsWith( "]" ) )
        {
            upperBoundInclusive = true;
        }
        else if ( range.endsWith( ")" ) )
        {
            upperBoundInclusive = false;
        }
        else
        {
            throw new InvalidVersionSpecificationException( range, "Invalid version range " + range
                + ", a range must end with either [ or (" );
        }

        process = process.substring( 1, process.length() - 1 );

        int index = process.indexOf( "," );

        if ( index < 0 )
        {
            if ( !lowerBoundInclusive || !upperBoundInclusive )
            {
                throw new InvalidVersionSpecificationException( range, "Invalid version range " + range
                    + ", single version must be surrounded by []" );
            }

            lowerBound = upperBound = new StubVersion( process.trim() );
        }
        else
        {
            String parsedLowerBound = process.substring( 0, index ).trim();
            String parsedUpperBound = process.substring( index + 1 ).trim();

            // more than two bounds, e.g. (1,2,3)
            if ( parsedUpperBound.contains( "," ) )
            {
                throw new InvalidVersionSpecificationException( range, "Invalid version range " + range
                    + ", bounds may not contain additional ','" );
            }

            lowerBound = parsedLowerBound.length() > 0 ? new StubVersion( parsedLowerBound ) : null;
            upperBound = parsedUpperBound.length() > 0 ? new StubVersion( parsedUpperBound ) : null;

            if ( upperBound != null && lowerBound != null )
            {
                if ( upperBound.compareTo( lowerBound ) < 0 )
                {
                    throw new InvalidVersionSpecificationException( range, "Invalid version range " + range
                        + ", lower bound must not be greater than upper bound" );
                }
            }
        }
    }

    public Bound getLowerBound()
    {
        return new Bound( lowerBound, lowerBoundInclusive );
    }

    public Bound getUpperBound()
    {
        return new Bound( upperBound, upperBoundInclusive );
    }

    public boolean acceptsSnapshots()
    {
        return isSnapshot( lowerBound ) || isSnapshot( upperBound );
    }

    public boolean containsVersion( Version version )
    {
        boolean snapshot = isSnapshot( version );

        if ( lowerBound != null )
        {
            int comparison = lowerBound.compareTo( version );

            if ( snapshot && comparison == 0 )
            {
                return true;
            }

            if ( comparison == 0 && !lowerBoundInclusive )
            {
                return false;
            }
            if ( comparison > 0 )
            {
                return false;
            }
        }

        if ( upperBound != null )
        {
            int comparison = upperBound.compareTo( version );

            if ( snapshot && comparison == 0 )
            {
                return true;
            }

            if ( comparison == 0 && !upperBoundInclusive )
            {
                return false;
            }
            if ( comparison < 0 )
            {
                return false;
            }
        }

        if ( lowerBound != null || upperBound != null )
        {
            return !snapshot;
        }

        return true;
    }

    private boolean isSnapshot( Version version )
    {
        return version != null && version.toString().endsWith( "SNAPSHOT" );
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

        TestVersionRange that = (TestVersionRange) obj;

        return upperBoundInclusive == that.upperBoundInclusive && lowerBoundInclusive == that.lowerBoundInclusive
            && eq( upperBound, that.upperBound ) && eq( lowerBound, that.lowerBound );
    }

    private static <T> boolean eq( T s1, T s2 )
    {
        return s1 != null ? s1.equals( s2 ) : s2 == null;
    }

    @Override
    public int hashCode()
    {
        int hash = 17;
        hash = hash * 31 + hash( upperBound );
        hash = hash * 31 + ( upperBoundInclusive ? 1 : 0 );
        hash = hash * 31 + hash( lowerBound );
        hash = hash * 31 + ( lowerBoundInclusive ? 1 : 0 );
        return hash;
    }

    private static int hash( Object obj )
    {
        return obj != null ? obj.hashCode() : 0;
    }

    @Override
    public String toString()
    {
        StringBuilder buffer = new StringBuilder( 64 );
        buffer.append( lowerBoundInclusive ? '[' : '(' );
        if ( lowerBound != null )
        {
            buffer.append( lowerBound );
        }
        buffer.append( ',' );
        if ( upperBound != null )
        {
            buffer.append( upperBound );
        }
        buffer.append( upperBoundInclusive ? ']' : ')' );
        return buffer.toString();
    }

}
