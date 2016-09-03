package org.eclipse.aether.internal.test.util;

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

import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionConstraint;
import org.eclipse.aether.version.VersionRange;
import org.eclipse.aether.version.VersionScheme;

/**
 * A version scheme using a generic version syntax.
 */
final class TestVersionScheme
    implements VersionScheme
{

    public Version parseVersion( final String version )
        throws InvalidVersionSpecificationException
    {
        return new TestVersion( version );
    }

    public VersionRange parseVersionRange( final String range )
        throws InvalidVersionSpecificationException
    {
        return new TestVersionRange( range );
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
            result = new TestVersionConstraint( parseVersion( constraint ) );
        }
        else
        {
            result = new TestVersionConstraint( ranges.iterator().next() );
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
