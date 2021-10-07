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

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.named.support.FileLockNamedLockFactorySupport;
import org.eclipse.aether.named.support.NamedLockSupport;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link SessionAwareNamedLockFactory} that uses advisory file locking.
 *
 * @since TBD
 */
@Singleton
@Named( FileLockNamedLockFactory.NAME )
public class FileLockNamedLockFactory
    extends FileLockNamedLockFactorySupport
    implements SessionAwareNamedLockFactory
{
    public static final String NAME = "file-lock";

    private final ConcurrentHashMap<String, Path> baseDirs;

    public FileLockNamedLockFactory()
    {
        this.baseDirs = new ConcurrentHashMap<>();
    }

    @Override
    public NamedLockSupport getLock( final RepositorySystemSession session, final String name )
    {
        File localRepositoryBasedir = session.getLocalRepository().getBasedir();
        Path baseDir = baseDirs.computeIfAbsent(
            localRepositoryBasedir.getPath(), k ->
            {
                try
                {
                    return new File( localRepositoryBasedir, ".locks" ).getCanonicalFile().toPath();
                }
                catch ( IOException e )
                {
                    throw new UncheckedIOException( e );
                }
            }
        );

        String fileName = baseDir.resolve( name ).toAbsolutePath().toString();
        return super.getLock( fileName );
    }

    @Override
    public NamedLockSupport getLock( final String filename )
    {
        throw new UnsupportedOperationException( "This factory is session aware" );
    }

    /**
     * Use name as is, it is already resolved in {@link #getLock(RepositorySystemSession, String)} method.
     */
    @Override
    protected Path resolveName( final String name )
    {
        return Paths.get( name );
    }
}