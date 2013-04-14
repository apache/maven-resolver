/*******************************************************************************
 * Copyright (c) 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.util.graph.version;

import java.util.Iterator;

import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.VersionFilter;
import org.eclipse.aether.version.Version;

/**
 * A version filter that excludes any version except the highest one.
 */
public final class HighestVersionFilter
    implements VersionFilter
{

    /**
     * Creates a new instance of this version filter.
     */
    public HighestVersionFilter()
    {
    }

    public void filterVersions( VersionFilterContext context )
    {
        Iterator<Version> it = context.iterator();
        for ( boolean hasNext = it.hasNext(); hasNext; )
        {
            it.next();
            if ( hasNext = it.hasNext() )
            {
                it.remove();
            }
        }
    }

    public VersionFilter deriveChildFilter( DependencyCollectionContext context )
    {
        return this;
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
        return true;
    }

    @Override
    public int hashCode()
    {
        return getClass().hashCode();
    }

}
