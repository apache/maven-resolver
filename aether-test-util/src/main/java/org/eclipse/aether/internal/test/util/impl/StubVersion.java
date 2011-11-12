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
package org.eclipse.aether.internal.test.util.impl;

import org.eclipse.aether.version.Version;

/**
 * Version ordering by {@link String#compareToIgnoreCase(String)}.
 */
public final class StubVersion
    implements Version
{

    private String version;

    public StubVersion( String version )
    {
        this.version = version == null ? "" : version;
    }

    public int compareTo( Version o )
    {
        return version.compareTo( o.toString() );
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ( ( version == null ) ? 0 : version.hashCode() );
        return result;
    }

    @Override
    public boolean equals( Object obj )
    {
        if ( this == obj )
            return true;
        if ( obj == null )
            return false;
        if ( getClass() != obj.getClass() )
            return false;
        StubVersion other = (StubVersion) obj;
        if ( version == null )
        {
            if ( other.version != null )
                return false;
        }
        else if ( !version.equals( other.version ) )
            return false;
        return true;
    }

    @Override
    public String toString()
    {
        return version;
    }

}
