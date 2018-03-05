package org.eclipse.aether.internal.impl.collect;

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
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.collection.VersionFilter;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionConstraint;

/**
 * @see DefaultDependencyCollector
 */
final class DefaultVersionFilterContext
    implements VersionFilter.VersionFilterContext
{

    private final Iterator<Version> EMPTY = Collections.<Version>emptySet().iterator();

    private final RepositorySystemSession session;

    private Dependency dependency;

    VersionRangeResult result;

    int count;

    byte[] deleted = new byte[64];

    DefaultVersionFilterContext( RepositorySystemSession session )
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
                deleted[i] = (byte) 0;
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

    public List<RemoteRepository> getRepositories()
    {
        return Collections.unmodifiableList( result.getRequest().getRepositories() );
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

        VersionIterator()
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
            for ( next = index + 1; next < size && deleted[next] != (byte) 0; next++ )
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
            if ( index < 0 || deleted[index] == (byte) 1 )
            {
                throw new IllegalStateException();
            }
            deleted[index] = (byte) 1;
            count = --DefaultVersionFilterContext.this.count;
        }

        @Override
        public String toString()
        {
            return ( index < 0 ) ? "null" : String.valueOf( versions.get( index ) );
        }

    }

}
