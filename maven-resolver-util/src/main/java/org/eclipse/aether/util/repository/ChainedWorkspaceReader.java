package org.eclipse.aether.util.repository;

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

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.repository.WorkspaceRepository;

/**
 * A workspace reader that delegates to a chain of other readers, effectively aggregating their contents.
 */
public final class ChainedWorkspaceReader
    implements WorkspaceReader
{

    private List<WorkspaceReader> readers = new ArrayList<WorkspaceReader>();

    private WorkspaceRepository repository;

    /**
     * Creates a new workspace reader by chaining the specified readers.
     * 
     * @param readers The readers to chain, may be {@code null}.
     * @see #newInstance(WorkspaceReader, WorkspaceReader)
     */
    public ChainedWorkspaceReader( WorkspaceReader... readers )
    {
        if ( readers != null )
        {
            Collections.addAll( this.readers, readers );
        }

        StringBuilder buffer = new StringBuilder();
        for ( WorkspaceReader reader : this.readers )
        {
            if ( buffer.length() > 0 )
            {
                buffer.append( '+' );
            }
            buffer.append( reader.getRepository().getContentType() );
        }

        repository = new WorkspaceRepository( buffer.toString(), new Key( this.readers ) );
    }

    /**
     * Creates a new workspace reader by chaining the specified readers. In contrast to the constructor, this factory
     * method will avoid creating an actual chained reader if one of the specified readers is actually {@code null}.
     * 
     * @param reader1 The first workspace reader, may be {@code null}.
     * @param reader2 The second workspace reader, may be {@code null}.
     * @return The chained reader or {@code null} if no workspace reader was supplied.
     */
    public static WorkspaceReader newInstance( WorkspaceReader reader1, WorkspaceReader reader2 )
    {
        if ( reader1 == null )
        {
            return reader2;
        }
        else if ( reader2 == null )
        {
            return reader1;
        }
        return new ChainedWorkspaceReader( reader1, reader2 );
    }

    public File findArtifact( Artifact artifact )
    {
        File file = null;

        for ( WorkspaceReader reader : readers )
        {
            file = reader.findArtifact( artifact );
            if ( file != null )
            {
                break;
            }
        }

        return file;
    }

    public List<String> findVersions( Artifact artifact )
    {
        Collection<String> versions = new LinkedHashSet<String>();

        for ( WorkspaceReader reader : readers )
        {
            versions.addAll( reader.findVersions( artifact ) );
        }

        return Collections.unmodifiableList( new ArrayList<String>( versions ) );
    }

    public WorkspaceRepository getRepository()
    {
        Key key = new Key( readers );
        if ( !key.equals( repository.getKey() ) )
        {
            repository = new WorkspaceRepository( repository.getContentType(), key );
        }
        return repository;
    }

    private static class Key
    {

        private final List<Object> keys = new ArrayList<Object>();

        Key( List<WorkspaceReader> readers )
        {
            for ( WorkspaceReader reader : readers )
            {
                keys.add( reader.getRepository().getKey() );
            }
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
            return keys.equals( ( (Key) obj ).keys );
        }

        @Override
        public int hashCode()
        {
            return keys.hashCode();
        }

    }

}
