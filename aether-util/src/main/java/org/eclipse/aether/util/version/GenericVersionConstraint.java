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

import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionConstraint;
import org.eclipse.aether.version.VersionRange;

/**
 * A constraint on versions for a dependency.
 */
final class GenericVersionConstraint
    implements VersionConstraint
{

    private final VersionRange range;

    private final Version version;

    /**
     * Creates a version constraint from the specified version range.
     * 
     * @param range The version range, must not be {@code null}.
     */
    public GenericVersionConstraint( VersionRange range )
    {
        if ( range == null )
        {
            throw new IllegalArgumentException( "version range missing" );
        }
        this.range = range;
        this.version = null;
    }

    /**
     * Creates a version constraint from the specified version.
     * 
     * @param version The version, must not be {@code null}.
     */
    public GenericVersionConstraint( Version version )
    {
        if ( version == null )
        {
            throw new IllegalArgumentException( "version missing" );
        }
        this.version = version;
        this.range = null;
    }

    public VersionRange getRange()
    {
        return range;
    }

    public Version getVersion()
    {
        return version;
    }

    public boolean containsVersion( Version version )
    {
        if ( range == null )
        {
            return version.equals( this.version );
        }
        else
        {
            return range.containsVersion( version );
        }
    }

    @Override
    public String toString()
    {
        return String.valueOf( ( range == null ) ? version : range );
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

        GenericVersionConstraint that = (GenericVersionConstraint) obj;

        return eq( range, that.range ) && eq( version, that.getVersion() );
    }

    private static <T> boolean eq( T s1, T s2 )
    {
        return s1 != null ? s1.equals( s2 ) : s2 == null;
    }

    @Override
    public int hashCode()
    {
        int hash = 17;
        hash = hash * 31 + hash( getRange() );
        hash = hash * 31 + hash( getVersion() );
        return hash;
    }

    private static int hash( Object obj )
    {
        return obj != null ? obj.hashCode() : 0;
    }

}
