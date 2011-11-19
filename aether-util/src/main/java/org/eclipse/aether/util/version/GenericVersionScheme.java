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
package org.eclipse.aether.util.version;

import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionConstraint;
import org.eclipse.aether.version.VersionRange;
import org.eclipse.aether.version.VersionScheme;

/**
 * A version scheme using a generic version syntax and common sense sorting. This scheme accepts versions of any form,
 * interpreting a version as a sequence of numeric and alphabetic components. The characters '-', '_', and '.' as well
 * as the mere transitions from digit to letter and vice versa delimit the version components. Delimiters are treated as
 * equivalent. Numeric components are compared mathematically, alphabetic components are compared lexicographically and
 * case-insensitively. However, the following qualifier strings are recognized and treated specially: "alpha" < "beta" <
 * "milestone" < "cr" = "rc" < "snapshot" < "final" = "ga" < "sp". All of those well-known qualifiers are considered
 * smaller/older than other strings. An empty component/string is equivalent to 0. Numbers and strings are considered
 * incomparable against each other. Where version components of different kind would collide, comparison will instead
 * assume that the previous components are padded with 0 or "ga", respectively, until the kind mismatch is resolved,
 * i.e. 1-alpha = 1.0.0-alpha < 1.0.1-ga = 1.0.1.
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
