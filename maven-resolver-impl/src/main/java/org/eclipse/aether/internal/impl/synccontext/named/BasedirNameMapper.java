package org.eclipse.aether.internal.impl.synccontext.named;

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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.stream.Collectors;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.util.DirectoryUtils;

import static java.util.Objects.requireNonNull;

/**
 * Wrapping {@link NameMapper} class that is file system friendly: it wraps another
 * {@link NameMapper} and resolves the resulting "file system friendly" names against local
 * repository basedir.
 *
 * @since 1.9.0
 */
public class BasedirNameMapper implements NameMapper
{
    private static final String CONFIG_PROP_LOCKS_DIR = "aether.syncContext.named.basedir.locksDir";

    private final NameMapper delegate;

    public BasedirNameMapper( final NameMapper delegate )
    {
        this.delegate = requireNonNull( delegate );
    }

    @Override
    public boolean isFileSystemFriendly()
    {
        return delegate.isFileSystemFriendly();
    }

    @Override
    public Collection<String> nameLocks( final RepositorySystemSession session,
                                         final Collection<? extends Artifact> artifacts,
                                         final Collection<? extends Metadata> metadatas )
    {
        try
        {
            final Path basedir = DirectoryUtils.resolveDirectory(
                    session, ".locks", CONFIG_PROP_LOCKS_DIR, false );

            return delegate.nameLocks( session, artifacts, metadatas ).stream()
                    .map( name -> basedir.resolve( name ).toAbsolutePath().toString() )
                    .collect( Collectors.toList() );
        }
        catch ( IOException e )
        {
            throw new UncheckedIOException( e );
        }
    }
}
