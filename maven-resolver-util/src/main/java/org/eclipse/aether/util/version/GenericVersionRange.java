package org.eclipse.aether.util.version;

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

import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionRange;

import java.util.Objects;

/**
 * A version range inspired by mathematical range syntax. For example, "[1.0,2.0)", "[1.0,)" or "[1.0]".
 */
final class GenericVersionRange
    implements VersionRange
{

    private final Bound lowerBound;

    private final Bound upperBound;

    /**
     * Creates a version range from the specified range specification.
     * 
     * @param range The range specification to parse, must not be {@code null}.
     * @throws InvalidVersionSpecificationException If the range could not be parsed.
     */
    GenericVersionRange( String range )
        throws InvalidVersionSpecificationException
    {
        String process = range;

        boolean lowerBoundInclusive, upperBoundInclusive;
        Version lowerBound, upperBound;

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

            String version = process.trim();
            if ( version.endsWith( ".*" ) )
            {
                String prefix = version.substring( 0, version.length() - 1 );
                lowerBound = parse( prefix + "min" );
                upperBound = parse( prefix + "max" );
            }
            else
            {
                lowerBound = parse( version );
                upperBound = lowerBound;
            }
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

            lowerBound = parsedLowerBound.length() > 0 ? parse( parsedLowerBound ) : null;
            upperBound = parsedUpperBound.length() > 0 ? parse( parsedUpperBound ) : null;

            if ( upperBound != null && lowerBound != null )
            {
                if ( upperBound.compareTo( lowerBound ) < 0 )
                {
                    throw new InvalidVersionSpecificationException( range, "Invalid version range " + range
                        + ", lower bound must not be greater than upper bound" );
                }
            }
        }

        this.lowerBound = ( lowerBound != null ) ? new Bound( lowerBound, lowerBoundInclusive ) : null;
        this.upperBound = ( upperBound != null ) ? new Bound( upperBound, upperBoundInclusive ) : null;
    }

    private Version parse( String version )
    {
        return new GenericVersion( version );
    }

    public Bound getLowerBound()
    {
        return lowerBound;
    }

    public Bound getUpperBound()
    {
        return upperBound;
    }

    public boolean containsVersion( Version version )
    {
        if ( lowerBound != null )
        {
            int comparison = lowerBound.getVersion().compareTo( version );

            if ( comparison == 0 && !lowerBound.isInclusive() )
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
            int comparison = upperBound.getVersion().compareTo( version );

            if ( comparison == 0 && !upperBound.isInclusive() )
            {
                return false;
            }
            if ( comparison < 0 )
            {
                return false;
            }
        }

        return true;
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

        GenericVersionRange that = (GenericVersionRange) obj;

        return Objects.equals( upperBound, that.upperBound )
                && Objects.equals( lowerBound, that.lowerBound );
    }

    @Override
    public int hashCode()
    {
        int hash = 17;
        hash = hash * 31 + hash( upperBound );
        hash = hash * 31 + hash( lowerBound );
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
        if ( lowerBound != null )
        {
            buffer.append( lowerBound.isInclusive() ? '[' : '(' );
            buffer.append( lowerBound.getVersion() );
        }
        else
        {
            buffer.append( '(' );
        }
        buffer.append( ',' );
        if ( upperBound != null )
        {
            buffer.append( upperBound.getVersion() );
            buffer.append( upperBound.isInclusive() ? ']' : ')' );
        }
        else
        {
            buffer.append( ')' );
        }
        return buffer.toString();
    }

}
