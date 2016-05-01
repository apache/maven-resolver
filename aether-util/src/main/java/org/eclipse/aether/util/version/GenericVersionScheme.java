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
package org.eclipse.aether.util.version;

import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionConstraint;
import org.eclipse.aether.version.VersionRange;
import org.eclipse.aether.version.VersionScheme;

/**
 * A version scheme using a generic version syntax and common sense sorting.
 * <p>
 * This scheme accepts versions of any form, interpreting a version as a sequence of numeric and alphabetic segments.
 * The characters '-', '_', and '.' as well as the mere transitions from digit to letter and vice versa delimit the
 * version segments. Delimiters are treated as equivalent.
 * </p>
 * <p>
 * Numeric segments are compared mathematically, alphabetic segments are compared lexicographically and
 * case-insensitively. However, the following qualifier strings are recognized and treated specially: "alpha" = "a" &lt;
 * "beta" = "b" &lt; "milestone" = "m" &lt; "cr" = "rc" &lt; "snapshot" &lt; "final" = "ga" &lt; "sp". All of those
 * well-known qualifiers are considered smaller/older than other strings. An empty segment/string is equivalent to 0.
 * </p>
 * <p>
 * In addition to the above mentioned qualifiers, the tokens "min" and "max" may be used as final version segment to
 * denote the smallest/greatest version having a given prefix. For example, "1.2.min" denotes the smallest version in
 * the 1.2 line, "1.2.max" denotes the greatest version in the 1.2 line. A version range of the form "[M.N.*]" is short
 * for "[M.N.min, M.N.max]".
 * </p>
 * <p>
 * Numbers and strings are considered incomparable against each other. Where version segments of different kind would
 * collide, comparison will instead assume that the previous segments are padded with trailing 0 or "ga" segments,
 * respectively, until the kind mismatch is resolved, e.g. "1-alpha" = "1.0.0-alpha" &lt; "1.0.1-ga" = "1.0.1".
 * </p>
 */
public final class GenericVersionScheme
    implements VersionScheme
{

    /**
     * Creates a new instance of the version scheme for parsing versions.
     */
    public GenericVersionScheme()
    {
    }

    public Version parseVersion( final String version )
        throws InvalidVersionSpecificationException
    {
        return new GenericVersion( version );
    }

    public VersionRange parseVersionRange( final String range )
        throws InvalidVersionSpecificationException
    {
        return new GenericVersionRange( range );
    }

    public VersionConstraint parseVersionConstraint( final String constraint )
        throws InvalidVersionSpecificationException
    {
        Collection<VersionRange> ranges = new ArrayList<VersionRange>();

        String process = constraint;

        while ( process.startsWith( "[" ) || process.startsWith( "(" ) )
        {
            int index1 = process.indexOf( ')' );
            int index2 = process.indexOf( ']' );

            int index = index2;
            if ( index2 < 0 || ( index1 >= 0 && index1 < index2 ) )
            {
                index = index1;
            }

            if ( index < 0 )
            {
                throw new InvalidVersionSpecificationException( constraint, "Unbounded version range " + constraint );
            }

            VersionRange range = parseVersionRange( process.substring( 0, index + 1 ) );
            ranges.add( range );

            process = process.substring( index + 1 ).trim();

            if ( process.length() > 0 && process.startsWith( "," ) )
            {
                process = process.substring( 1 ).trim();
            }
        }

        if ( process.length() > 0 && !ranges.isEmpty() )
        {
            throw new InvalidVersionSpecificationException( constraint, "Invalid version range " + constraint
                + ", expected [ or ( but got " + process );
        }

        VersionConstraint result;
        if ( ranges.isEmpty() )
        {
            result = new GenericVersionConstraint( parseVersion( constraint ) );
        }
        else
        {
            result = new GenericVersionConstraint( UnionVersionRange.from( ranges ) );
        }

        return result;
    }

    @Override
    public boolean equals( final Object obj )
    {
        if ( this == obj )
        {
            return true;
        }

        return obj != null && getClass().equals( obj.getClass() );
    }

    @Override
    public int hashCode()
    {
        return getClass().hashCode();
    }

}
