package org.eclipse.aether.repository;

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
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.aether.artifact.Artifact;

/**
 * A skeleton implementation for custom workspace readers. The default implementation represents an empty repository
 * that never finds anything.
 */
public abstract class AbstractWorkspaceReader
    implements WorkspaceReader
{

    protected final WorkspaceRepository repository;

    protected AbstractWorkspaceReader()
    {
        this( "empty" );
    }

    protected AbstractWorkspaceReader( String type )
    {
        this( type, null );
    }


    protected AbstractWorkspaceReader( String type, Object key )
    {
        repository = new WorkspaceRepository( type, key );
    }

    @Override
    public WorkspaceRepository getRepository()
    {
        return repository;
    }

    @Override
    public File findArtifact( Artifact artifact )
    {
        return null;
    }

    @Override
    public List<String> findVersions( Artifact artifact )
    {
        return Collections.emptyList();
    }

    @Override
    public Stream<Artifact> listArtifacts()
    {
        return Stream.empty();
    }
}
