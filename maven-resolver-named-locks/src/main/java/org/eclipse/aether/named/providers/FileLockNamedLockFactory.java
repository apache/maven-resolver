package org.eclipse.aether.named.providers;

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
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.aether.named.support.FileLockNamedLock;
import org.eclipse.aether.named.support.NamedLockFactorySupport;
import org.eclipse.aether.named.support.NamedLockSupport;

/**
 * Named locks factory of {@link FileLockNamedLock}s. This is a bit special implementation, as it is abstract. This
 * class does not "know" how to resolve lock names to FS paths.
 */
public abstract class FileLockNamedLockFactory
    extends NamedLockFactorySupport
{
    private final ConcurrentHashMap<String, FileChannel> channels;

    public FileLockNamedLockFactory()
    {
        this.channels = new ConcurrentHashMap<>();
    }

    /**
     * Implementations should be able to "resolve" lock name to {@link Path}, this method must <strong>never return
     * {@code null}!</strong>
     */
    protected abstract Path resolveName( final String name );

    @Override
    protected NamedLockSupport createLock( final String name )
    {
        Path path = resolveName( name );
        FileChannel fileChannel = channels.computeIfAbsent( name, k ->
        {
            try
            {
                Files.createDirectories( path.getParent() );
                return FileChannel.open(
                        path,
                        StandardOpenOption.READ, StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE, StandardOpenOption.DELETE_ON_CLOSE
                );
            }
            catch ( IOException e )
            {
                throw new UncheckedIOException( path.toString(), e );
            }
        } );
        return new FileLockNamedLock( name, fileChannel, this );
    }

    @Override
    protected void destroyLock( final String name )
    {
        try
        {
            FileChannel fileChannel = channels.remove( name );
            if ( fileChannel != null )
            {
                try
                {
                    fileChannel.close();
                }
                catch ( IOException e )
                {
                    throw new UncheckedIOException( e );
                }
            }
            else
            {
                logger.warn( "No FileChannel for lock '{}'", name );
            }
        }
        finally
        {
            super.destroyLock( name );
        }
    }
}