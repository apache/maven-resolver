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
package org.eclipse.aether.test.util.impl;

import java.util.Collection;
import java.util.HashSet;

import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionConstraint;
import org.eclipse.aether.version.VersionRange;

/**
 * A constraint on versions for a dependency.
 */
final class TestVersionConstraint
    implements VersionConstraint
{

    private Collection<VersionRange> ranges = new HashSet<VersionRange>();

    private Version version;

    /**
     * Adds the specified version range to this constraint. All versions matched by the given range satisfy this
     * constraint.
     * 
     * @param range The version range to add, may be {@code null}.
     * @return This constraint for chaining, never {@code null}.
     */
    public TestVersionConstraint addRange( VersionRange range )
    {
        if ( range != null )
        {
            ranges.add( range );
        }
        return this;
    }

    public Collection<VersionRange> getRanges()
    {
        return ranges;
    }

    /**
     * Sets the recommended version to satisfy this constraint.
     * 
     * @param version The recommended version for this constraint, may be {@code null} if none.
     * @return This constraint for chaining, never {@code null}.
     */
    public TestVersionConstraint setVersion( Version version )
    {
        this.version = version;
        return this;
    }

    public Version getVersion()
    {
        return version;
    }

    public boolean containsVersion( Version version )
    {
        if ( ranges.isEmpty() )
        {
            return version.equals( this.version );
        }
        else
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
    }

    @Override
    public String toString()
    {
        StringBuilder buffer = new StringBuilder( 128 );

        for ( VersionRange range : getRanges() )
        {
            if ( buffer.length() > 0 )
            {
                buffer.append( "," );
            }
            buffer.append( range );
        }

        if ( buffer.length() <= 0 )
        {
            buffer.append( getVersion() );
        }

        return buffer.toString();
    }

    @Override
    public boolean equals( Object obj )
    {
        if ( this == obj )
        {
            return true;
        }
        if ( obj == null || !getClass().equals( obj.getClass() ) )
        {
            return false;
        }

        TestVersionConstraint that = (TestVersionConstraint) obj;

        return ranges.equals( that.getRanges() ) && eq( version, that.getVersion() );
    }

    private static <T> boolean eq( T s1, T s2 )
    {
        return s1 != null ? s1.equals( s2 ) : s2 == null;
    }

    @Override
    public int hashCode()
    {
        int hash = 17;
        hash = hash * 31 + hash( getRanges() );
        hash = hash * 31 + hash( getVersion() );
        return hash;
    }

    private static int hash( Object obj )
    {
        return obj != null ? obj.hashCode() : 0;
    }

}
