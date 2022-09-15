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

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.named.support.FileSystemFriendly;
import org.eclipse.aether.util.ConfigUtils;

import static java.util.Objects.requireNonNull;

/**
 * Wrapping {@link NameMapper} class that is {@link FileSystemFriendly}: it wraps another
 * {@link FileSystemFriendlyNameMapper} and resolves the resulting "file system friendly" names against local
 * repository basedir.
 *
 * @since TBD
 */
public class BasedirNameMapper implements FileSystemFriendlyNameMapper
{
    private static final String CONFIG_PROP_LOCKS_DIR_NAME = "aether.syncContext.named.basedir.locksDirName";

    private final FileSystemFriendlyNameMapper delegate;

    private final ConcurrentMap<String, Path> basedirs;

    public BasedirNameMapper( final FileSystemFriendlyNameMapper delegate )
    {
        this.delegate = requireNonNull( delegate );
        this.basedirs = new ConcurrentHashMap<>();
    }

    @Override
    public Collection<String> nameLocks( final RepositorySystemSession session,
                                         final Collection<? extends Artifact> artifacts,
                                         final Collection<? extends Metadata> metadatas )
    {
        final String locksDirName = ConfigUtils.getString( session, ".locks", CONFIG_PROP_LOCKS_DIR_NAME );
        final File localRepositoryBasedir = session.getLocalRepository().getBasedir();
        // here we abuse concurrent hash map to make sure costly getCanonicalFile is invoked only once
        final Path basedir = basedirs.computeIfAbsent( localRepositoryBasedir.getPath(), k ->
        {
            try
            {
                return new File( localRepositoryBasedir, locksDirName ).getCanonicalFile().toPath();
            }
            catch ( IOException e )
            {
                throw new UncheckedIOException( e );
            }
        } );

        return delegate.nameLocks( session, artifacts, metadatas ).stream()
                .map( name -> basedir.resolve( name ).toAbsolutePath().toString() )
                .collect( Collectors.toList() );
    }
}
