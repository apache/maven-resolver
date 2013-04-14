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
package org.eclipse.aether.internal.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.collection.VersionFilter;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionConstraint;

/**
 * @see DefaultDependencyCollector
 */
final class DefaultVersionFilterContext
    implements VersionFilter.VersionFilterContext
{

    private final Iterator<Version> EMPTY = Collections.<Version> emptySet().iterator();

    private final RepositorySystemSession session;

    private Dependency dependency;

    VersionRangeResult result;

    int count;

    byte[] deleted = new byte[64];

    public DefaultVersionFilterContext( RepositorySystemSession session )
    {
        this.session = session;
    }

    public void set( Dependency dependency, VersionRangeResult result )
    {
        this.dependency = dependency;
        this.result = result;
        count = result.getVersions().size();
        if ( deleted.length < count )
        {
            deleted = new byte[count];
        }
        else
        {
            for ( int i = count - 1; i >= 0; i-- )
            {
                deleted[i] = 0;
            }
        }
    }

    public List<Version> get()
    {
        if ( count == result.getVersions().size() )
        {
            return result.getVersions();
        }
        if ( count <= 1 )
        {
            if ( count <= 0 )
            {
                return Collections.emptyList();
            }
            return Collections.singletonList( iterator().next() );
        }
        List<Version> versions = new ArrayList<Version>( count );
        for ( Version version : this )
        {
            versions.add( version );
        }
        return versions;
    }

    public RepositorySystemSession getSession()
    {
        return session;
    }

    public Dependency getDependency()
    {
        return dependency;
    }

    public VersionConstraint getVersionConstraint()
    {
        return result.getVersionConstraint();
    }

    public int getCount()
    {
        return count;
    }

    public ArtifactRepository getRepository( Version version )
    {
        return result.getRepository( version );
    }

    public Iterator<Version> iterator()
    {
        return ( count > 0 ) ? new VersionIterator() : EMPTY;
    }

    @Override
    public String toString()
    {
        return dependency + " " + result.getVersions();
    }

    private class VersionIterator
        implements Iterator<Version>
    {

        private final List<Version> versions;

        private final int size;

        private int count;

        private int index;

        private int next;

        public VersionIterator()
        {
            count = DefaultVersionFilterContext.this.count;
            index = -1;
            next = 0;
            versions = result.getVersions();
            size = versions.size();
            advance();
        }

        private void advance()
        {
            for ( next = index + 1; next < size && deleted[next] != 0; next++ )
            {
                // just advancing index
            }
        }

        public boolean hasNext()
        {
            return next < size;
        }

        public Version next()
        {
            if ( count != DefaultVersionFilterContext.this.count )
            {
                throw new ConcurrentModificationException();
            }
            if ( next >= size )
            {
                throw new NoSuchElementException();
            }
            index = next;
            advance();
            return versions.get( index );
        }

        public void remove()
        {
            if ( count != DefaultVersionFilterContext.this.count )
            {
                throw new ConcurrentModificationException();
            }
            if ( index < 0 || deleted[index] == 1 )
            {
                throw new IllegalStateException();
            }
            deleted[index] = 1;
            count = --DefaultVersionFilterContext.this.count;
        }

        @Override
        public String toString()
        {
            return ( index < 0 ) ? "null" : String.valueOf( versions.get( index ) );
        }

    }

}
